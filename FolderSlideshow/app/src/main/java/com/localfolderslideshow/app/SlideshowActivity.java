package com.localfolderslideshow.app;

import android.app.Activity;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class SlideshowActivity extends Activity {
    private static class MediaItem {
        final Uri uri;
        final String name;
        final long modified;
        MediaItem(Uri uri, String name, long modified) {
            this.uri = uri;
            this.name = name == null ? "Image" : name;
            this.modified = modified;
        }
    }

    private final ArrayList<MediaItem> items = new ArrayList<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicInteger loadGeneration = new AtomicInteger(0);

    private FrameLayout root;
    private ImageView imageA;
    private ImageView imageB;
    private LinearLayout controls;
    private TextView infoText;
    private TextView scanText;
    private Button pauseButton;

    private boolean showingA = true;
    private boolean firstDisplayed = false;
    private boolean paused = false;
    private boolean controlsVisible = true;
    private int currentIndex = 0;
    private int consecutiveErrors = 0;

    private Uri treeUri;
    private long intervalMs;
    private long transitionMs;
    private String transition;
    private boolean shuffle;
    private boolean loop;
    private boolean recursive;
    private int sortMode;

    private final Runnable nextRunnable = this::goNext;
    private final Runnable hideControlsRunnable = () -> setControlsVisible(false);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        enterImmersive();

        String uriString = getIntent().getStringExtra("root_uri");
        if (uriString == null) {
            finish();
            return;
        }
        treeUri = Uri.parse(uriString);
        intervalMs = getIntent().getLongExtra("interval_ms", 5000);
        transition = getIntent().getStringExtra("transition");
        if (transition == null) transition = "fade";
        transitionMs = Math.min(getIntent().getLongExtra("transition_ms", 500), Math.max(0, intervalMs - 100));
        shuffle = getIntent().getBooleanExtra("shuffle", true);
        loop = getIntent().getBooleanExtra("loop", true);
        recursive = getIntent().getBooleanExtra("recursive", false);
        sortMode = getIntent().getIntExtra("sort_mode", 0);
        int fitMode = getIntent().getIntExtra("fit_mode", 0);

        buildUi(fitMode);
        scanFolder();
    }

    private void buildUi(int fitMode) {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        FrameLayout photoLayer = new FrameLayout(this);
        root.addView(photoLayer, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        imageA = makeImageView(fitMode);
        imageB = makeImageView(fitMode);
        imageB.setVisibility(View.GONE);
        photoLayer.addView(imageA, matchParent());
        photoLayer.addView(imageB, matchParent());

        LinearLayout loading = new LinearLayout(this);
        loading.setOrientation(LinearLayout.VERTICAL);
        loading.setGravity(Gravity.CENTER);
        ProgressBar progress = new ProgressBar(this);
        loading.addView(progress);
        scanText = new TextView(this);
        scanText.setText("Scanning folder…");
        scanText.setTextColor(Color.WHITE);
        scanText.setTextSize(16);
        scanText.setGravity(Gravity.CENTER);
        scanText.setPadding(dp(16), dp(14), dp(16), 0);
        loading.addView(scanText, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams loadingParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        root.addView(loading, loadingParams);
        loading.setTag("loading");

        controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(12), dp(8), dp(12), dp(10));
        controls.setBackgroundColor(Color.argb(175, 0, 0, 0));

        infoText = new TextView(this);
        infoText.setTextColor(Color.WHITE);
        infoText.setTextSize(13);
        infoText.setSingleLine(true);
        infoText.setGravity(Gravity.CENTER);
        controls.addView(infoText, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        controls.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        Button previous = controlButton("Previous");
        pauseButton = controlButton("Pause");
        Button next = controlButton("Next");
        Button exit = controlButton("Exit");
        row.addView(previous, weighted());
        row.addView(pauseButton, weighted());
        row.addView(next, weighted());
        row.addView(exit, weighted());

        previous.setOnClickListener(v -> goPrevious());
        pauseButton.setOnClickListener(v -> togglePause());
        next.setOnClickListener(v -> goNext());
        exit.setOnClickListener(v -> finish());

        FrameLayout.LayoutParams controlsParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        root.addView(controls, controlsParams);

        photoLayer.setOnClickListener(v -> {
            setControlsVisible(!controlsVisible);
            if (controlsVisible) scheduleControlsHide();
        });

        setContentView(root);
    }

    private ImageView makeImageView(int fitMode) {
        ImageView v = new ImageView(this);
        v.setBackgroundColor(Color.BLACK);
        v.setScaleType(fitMode == 1 ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
        return v;
    }

    private void scanFolder() {
        worker.execute(() -> {
            try {
                String rootId = DocumentsContract.getTreeDocumentId(treeUri);
                scanChildren(rootId);

                if (sortMode == 1) {
                    items.sort((a, b) -> Long.compare(b.modified, a.modified));
                } else if (sortMode == 2) {
                    items.sort(Comparator.comparingLong(a -> a.modified));
                } else {
                    items.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
                }
                if (shuffle) Collections.shuffle(items);

                runOnUiThread(() -> {
                    removeLoading();
                    if (items.isEmpty()) {
                        TextView empty = new TextView(this);
                        empty.setText("No supported image files were found in this folder" + (recursive ? " or its subfolders." : "."));
                        empty.setTextColor(Color.WHITE);
                        empty.setTextSize(18);
                        empty.setGravity(Gravity.CENTER);
                        empty.setPadding(dp(28), dp(28), dp(28), dp(28));
                        root.addView(empty, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
                        infoText.setText("No images found");
                        setControlsVisible(true);
                    } else {
                        currentIndex = 0;
                        showCurrent(false);
                        scheduleControlsHide();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    removeLoading();
                    TextView error = new TextView(this);
                    error.setText("Could not read this folder. Try choosing it again.");
                    error.setTextColor(Color.WHITE);
                    error.setTextSize(18);
                    error.setGravity(Gravity.CENTER);
                    error.setPadding(dp(24), dp(24), dp(24), dp(24));
                    root.addView(error, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
                });
            }
        });
    }

    private void scanChildren(String parentDocumentId) {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId);
        String[] columns = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
        };

        try (Cursor cursor = getContentResolver().query(children, columns, null, null, null)) {
            if (cursor == null) return;
            int idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE);
            int modifiedCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED);

            while (cursor.moveToNext()) {
                String docId = cursor.getString(idCol);
                String name = cursor.getString(nameCol);
                String mime = cursor.getString(mimeCol);
                long modified = modifiedCol >= 0 && !cursor.isNull(modifiedCol) ? cursor.getLong(modifiedCol) : 0L;

                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    if (recursive) {
                        try { scanChildren(docId); } catch (Exception ignored) { }
                    }
                } else if (isImage(mime, name)) {
                    Uri uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId);
                    items.add(new MediaItem(uri, name, modified));
                }
            }
        } catch (Exception ignored) {
        }
    }

    private boolean isImage(String mime, String name) {
        if (mime != null && mime.toLowerCase(Locale.US).startsWith("image/")) return true;
        if (name == null) return false;
        String n = name.toLowerCase(Locale.US);
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp") ||
                n.endsWith(".gif") || n.endsWith(".heic") || n.endsWith(".heif") || n.endsWith(".avif") ||
                n.endsWith(".bmp") || n.endsWith(".dng");
    }

    private void showCurrent(boolean animate) {
        if (items.isEmpty() || currentIndex < 0 || currentIndex >= items.size()) return;
        final int generation = loadGeneration.incrementAndGet();
        final MediaItem item = items.get(currentIndex);
        handler.removeCallbacks(nextRunnable);
        infoText.setText(String.format(Locale.US, "%d / %d   %s", currentIndex + 1, items.size(), item.name));

        worker.execute(() -> {
            try {
                Drawable drawable = decodeDrawable(item.uri);
                runOnUiThread(() -> {
                    if (generation != loadGeneration.get() || isFinishing() || isDestroyed()) {
                        stopAnimated(drawable);
                        return;
                    }
                    consecutiveErrors = 0;
                    displayDrawable(drawable, animate && firstDisplayed);
                    firstDisplayed = true;
                    if (!paused) scheduleNext();
                });
            } catch (Exception e) {
                runOnUiThread(() -> handleLoadError(item.name));
            }
        });
    }

    private Drawable decodeDrawable(Uri uri) throws IOException {
        ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), uri);
        return ImageDecoder.decodeDrawable(source, (decoder, info, src) -> {
            int sourceW = info.getSize().getWidth();
            int sourceH = info.getSize().getHeight();
            int targetW = Math.max(1, getResources().getDisplayMetrics().widthPixels * 2);
            int targetH = Math.max(1, getResources().getDisplayMetrics().heightPixels * 2);
            if (sourceW > targetW || sourceH > targetH) {
                float ratio = Math.min((float) targetW / sourceW, (float) targetH / sourceH);
                decoder.setTargetSize(Math.max(1, Math.round(sourceW * ratio)), Math.max(1, Math.round(sourceH * ratio)));
            }
        });
    }

    private void displayDrawable(Drawable drawable, boolean animate) {
        imageA.animate().cancel();
        imageB.animate().cancel();

        if (!firstDisplayed) {
            clearDrawable(imageA);
            imageA.setImageDrawable(drawable);
            imageA.setVisibility(View.VISIBLE);
            imageA.setAlpha(1f);
            imageA.setTranslationX(0f);
            imageA.setScaleX(1f);
            imageA.setScaleY(1f);
            imageB.setVisibility(View.GONE);
            showingA = true;
            startAnimated(drawable);
            return;
        }

        ImageView current = showingA ? imageA : imageB;
        ImageView incoming = showingA ? imageB : imageA;
        showingA = !showingA;

        clearDrawable(incoming);
        incoming.setImageDrawable(drawable);
        incoming.setVisibility(View.VISIBLE);
        incoming.setAlpha(1f);
        incoming.setTranslationX(0f);
        incoming.setScaleX(1f);
        incoming.setScaleY(1f);
        startAnimated(drawable);

        if (!animate || "none".equals(transition) || transitionMs <= 0) {
            clearDrawable(current);
            current.setVisibility(View.GONE);
            return;
        }

        if ("slide".equals(transition)) {
            float width = Math.max(root.getWidth(), getResources().getDisplayMetrics().widthPixels);
            incoming.setTranslationX(width);
            incoming.animate().translationX(0f).setDuration(transitionMs).start();
            current.animate().translationX(-width * 0.20f).alpha(0f).setDuration(transitionMs)
                    .withEndAction(() -> resetAndHide(current)).start();
        } else if ("zoom".equals(transition)) {
            incoming.setAlpha(0f);
            incoming.setScaleX(1.08f);
            incoming.setScaleY(1.08f);
            incoming.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(transitionMs).start();
            current.animate().alpha(0f).setDuration(transitionMs)
                    .withEndAction(() -> resetAndHide(current)).start();
        } else {
            incoming.setAlpha(0f);
            incoming.animate().alpha(1f).setDuration(transitionMs).start();
            current.animate().alpha(0f).setDuration(transitionMs)
                    .withEndAction(() -> resetAndHide(current)).start();
        }
    }

    private void resetAndHide(ImageView view) {
        clearDrawable(view);
        view.setVisibility(View.GONE);
        view.setAlpha(1f);
        view.setTranslationX(0f);
        view.setScaleX(1f);
        view.setScaleY(1f);
    }

    private void clearDrawable(ImageView view) {
        Drawable old = view.getDrawable();
        stopAnimated(old);
        view.setImageDrawable(null);
    }

    private void startAnimated(Drawable drawable) {
        if (drawable instanceof AnimatedImageDrawable) ((AnimatedImageDrawable) drawable).start();
    }

    private void stopAnimated(Drawable drawable) {
        if (drawable instanceof AnimatedImageDrawable) ((AnimatedImageDrawable) drawable).stop();
    }

    private void handleLoadError(String name) {
        consecutiveErrors++;
        if (consecutiveErrors >= items.size()) {
            paused = true;
            pauseButton.setText("Resume");
            infoText.setText("None of the images could be decoded");
            Toast.makeText(this, "The images in this folder could not be opened.", Toast.LENGTH_LONG).show();
            setControlsVisible(true);
            return;
        }
        Toast.makeText(this, "Skipping unreadable image: " + name, Toast.LENGTH_SHORT).show();
        goNext();
    }

    private void scheduleNext() {
        handler.removeCallbacks(nextRunnable);
        if (!paused && items.size() > 1) handler.postDelayed(nextRunnable, intervalMs);
    }

    private void goNext() {
        if (items.isEmpty()) return;
        handler.removeCallbacks(nextRunnable);
        int next = currentIndex + 1;
        if (next >= items.size()) {
            if (!loop) {
                paused = true;
                pauseButton.setText("Resume");
                setControlsVisible(true);
                infoText.setText(String.format(Locale.US, "%d / %d   End of slideshow", items.size(), items.size()));
                return;
            }
            if (shuffle && items.size() > 1) Collections.shuffle(items);
            next = 0;
        }
        currentIndex = next;
        showCurrent(true);
    }

    private void goPrevious() {
        if (items.isEmpty()) return;
        handler.removeCallbacks(nextRunnable);
        int previous = currentIndex - 1;
        if (previous < 0) previous = loop ? items.size() - 1 : 0;
        currentIndex = previous;
        showCurrent(true);
    }

    private void togglePause() {
        paused = !paused;
        pauseButton.setText(paused ? "Resume" : "Pause");
        if (paused) {
            handler.removeCallbacks(nextRunnable);
            setControlsVisible(true);
        } else {
            scheduleNext();
            scheduleControlsHide();
        }
    }

    private void setControlsVisible(boolean visible) {
        controlsVisible = visible;
        controls.animate().cancel();
        if (visible) {
            controls.setVisibility(View.VISIBLE);
            controls.setAlpha(0f);
            controls.animate().alpha(1f).setDuration(120).start();
        } else {
            controls.animate().alpha(0f).setDuration(150).withEndAction(() -> controls.setVisibility(View.GONE)).start();
            enterImmersive();
        }
    }

    private void scheduleControlsHide() {
        handler.removeCallbacks(hideControlsRunnable);
        if (!paused) handler.postDelayed(hideControlsRunnable, 2800);
    }

    private void enterImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        );
    }

    private void removeLoading() {
        View found = root.findViewWithTag("loading");
        if (found != null) root.removeView(found);
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersive();
        if (firstDisplayed && !paused) scheduleNext();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(nextRunnable);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        loadGeneration.incrementAndGet();
        if (imageA != null) clearDrawable(imageA);
        if (imageB != null) clearDrawable(imageB);
        worker.shutdownNow();
        super.onDestroy();
    }

    private Button controlButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(12);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(4), dp(8), dp(4), dp(8));
        return b;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(dp(3), dp(4), dp(3), 0);
        return p;
    }

    private FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
