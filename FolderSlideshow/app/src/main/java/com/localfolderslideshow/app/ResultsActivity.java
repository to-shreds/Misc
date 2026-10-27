package com.localfolderslideshow.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.SparseBooleanArray;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class ResultsActivity extends Activity {
    private final ArrayList<SelectionStore.Item> results = new ArrayList<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private Uri treeUri;
    private boolean recursive;
    private int sortMode;
    private int regexTarget;
    private Pattern regexPattern;

    private TextView summary;
    private ProgressBar progress;
    private ListView listView;
    private Button selectAllButton;
    private Button clearButton;
    private Button slideshowButton;
    private Button shareButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(18, 18, 18));
        getWindow().setNavigationBarColor(Color.rgb(18, 18, 18));

        String uriString = getIntent().getStringExtra("root_uri");
        String regex = getIntent().getStringExtra("regex");
        if (uriString == null || regex == null) {
            finish();
            return;
        }

        treeUri = Uri.parse(uriString);
        recursive = getIntent().getBooleanExtra("recursive", false);
        sortMode = getIntent().getIntExtra("sort_mode", 0);
        regexTarget = getIntent().getIntExtra("regex_target", 0);
        boolean caseSensitive = getIntent().getBooleanExtra("regex_case_sensitive", false);

        try {
            int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
            regexPattern = Pattern.compile(regex, flags);
        } catch (PatternSyntaxException e) {
            Toast.makeText(this, "Invalid regular expression.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        buildUi(regex);
        scan();
    }

    private void buildUi(String regex) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(12));
        root.setBackgroundColor(Color.rgb(18, 18, 18));

        TextView title = new TextView(this);
        title.setText("Search results");
        title.setTextColor(Color.WHITE);
        title.setTextSize(26);
        root.addView(title, fullWidth());

        TextView pattern = new TextView(this);
        pattern.setText("Regex: " + regex);
        pattern.setTextColor(Color.rgb(180, 180, 180));
        pattern.setTextSize(13);
        pattern.setMaxLines(2);
        pattern.setPadding(0, dp(4), 0, dp(6));
        root.addView(pattern, fullWidth());

        summary = new TextView(this);
        summary.setText("Searching…");
        summary.setTextColor(Color.rgb(150, 205, 255));
        summary.setTextSize(14);
        root.addView(summary, fullWidth());

        progress = new ProgressBar(this);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        progressParams.gravity = Gravity.CENTER_HORIZONTAL;
        progressParams.topMargin = dp(8);
        progressParams.bottomMargin = dp(8);
        root.addView(progress, progressParams);

        listView = new ListView(this);
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        listView.setDividerHeight(1);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(listView, listParams);

        LinearLayout selectRow = new LinearLayout(this);
        selectRow.setOrientation(LinearLayout.HORIZONTAL);
        selectAllButton = button("Select all");
        clearButton = button("Clear");
        selectRow.addView(selectAllButton, weighted());
        selectRow.addView(clearButton, weighted());
        root.addView(selectRow, fullWidth());

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        slideshowButton = button("Start slideshow");
        shareButton = button("Share selected");
        actionRow.addView(slideshowButton, weighted());
        actionRow.addView(shareButton, weighted());
        root.addView(actionRow, fullWidth());

        setContentView(root);
        setButtonsEnabled(false);

        selectAllButton.setOnClickListener(v -> {
            for (int i = 0; i < results.size(); i++) listView.setItemChecked(i, true);
            updateSelectionSummary();
        });

        clearButton.setOnClickListener(v -> {
            listView.clearChoices();
            listView.requestLayout();
            updateSelectionSummary();
        });

        listView.setOnItemClickListener((parent, view, position, id) -> updateSelectionSummary());
        slideshowButton.setOnClickListener(v -> startSlideshow());
        shareButton.setOnClickListener(v -> shareSelected());
    }

    private void scan() {
        worker.execute(() -> {
            String rootId = DocumentsContract.getTreeDocumentId(treeUri);
            scanChildren(rootId, "");

            if (sortMode == 1) {
                results.sort((a, b) -> Long.compare(b.modified, a.modified));
            } else if (sortMode == 2) {
                results.sort(Comparator.comparingLong(a -> a.modified));
            } else {
                results.sort((a, b) -> a.relativePath.compareToIgnoreCase(b.relativePath));
            }

            ArrayList<String> labels = new ArrayList<>();
            for (SelectionStore.Item item : results) labels.add(item.relativePath);

            runOnUiThread(() -> {
                progress.setVisibility(View.GONE);
                ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_multiple_choice, labels) {
                    @Override
                    public View getView(int position, View convertView, android.view.ViewGroup parent) {
                        View view = super.getView(position, convertView, parent);
                        if (view instanceof TextView) {
                            ((TextView) view).setTextColor(Color.WHITE);
                            ((TextView) view).setTextSize(14);
                        }
                        return view;
                    }
                };
                listView.setAdapter(adapter);
                summary.setText(String.format(Locale.US, "%d image%s found", results.size(), results.size() == 1 ? "" : "s"));
                setButtonsEnabled(!results.isEmpty());
                if (results.isEmpty()) {
                    slideshowButton.setEnabled(false);
                    shareButton.setEnabled(false);
                }
            });
        });
    }

    private void scanChildren(String parentDocumentId, String relativeDir) {
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
                String safeName = name == null ? "" : name;
                String relativePath = relativeDir.isEmpty() ? safeName : relativeDir + "/" + safeName;

                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    if (recursive) {
                        try { scanChildren(docId, relativePath); } catch (Exception ignored) { }
                    }
                } else if (isImage(mime, name) && matches(name, relativePath)) {
                    Uri uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId);
                    results.add(new SelectionStore.Item(uri, name, relativePath, modified));
                }
            }
        } catch (Exception ignored) {
        }
    }

    private boolean matches(String name, String relativePath) {
        String candidate = regexTarget == 1 ? relativePath : (name == null ? "" : name);
        return regexPattern.matcher(candidate).find();
    }

    private boolean isImage(String mime, String name) {
        if (mime != null && mime.toLowerCase(Locale.US).startsWith("image/")) return true;
        if (name == null) return false;
        String n = name.toLowerCase(Locale.US);
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp") ||
                n.endsWith(".gif") || n.endsWith(".heic") || n.endsWith(".heif") || n.endsWith(".avif") ||
                n.endsWith(".bmp") || n.endsWith(".dng");
    }

    private ArrayList<SelectionStore.Item> selectedItems(boolean allWhenNone) {
        SparseBooleanArray checked = listView.getCheckedItemPositions();
        ArrayList<SelectionStore.Item> selected = new ArrayList<>();
        if (checked != null) {
            for (int i = 0; i < results.size(); i++) {
                if (checked.get(i)) selected.add(results.get(i));
            }
        }
        if (selected.isEmpty() && allWhenNone) selected.addAll(results);
        return selected;
    }

    private void startSlideshow() {
        ArrayList<SelectionStore.Item> selected = selectedItems(true);
        if (selected.isEmpty()) return;

        try {
            String selectionFile = SelectionStore.write(this, selected);
            Intent intent = new Intent(this, SlideshowActivity.class);
            copySlideshowExtras(intent);
            intent.putExtra("selection_file", selectionFile);
            intent.putExtra("search_mode", true);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Could not prepare the slideshow.", Toast.LENGTH_LONG).show();
        }
    }

    private void shareSelected() {
        ArrayList<SelectionStore.Item> selected = selectedItems(false);
        if (selected.isEmpty()) {
            Toast.makeText(this, "Select at least one image to share.", Toast.LENGTH_SHORT).show();
            return;
        }

        ArrayList<Uri> uris = new ArrayList<>();
        for (SelectionStore.Item item : selected) uris.add(item.uri);

        Intent share = new Intent(Intent.ACTION_SEND_MULTIPLE);
        share.setType("image/*");
        share.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        ClipData clipData = ClipData.newUri(getContentResolver(), "Selected images", uris.get(0));
        for (int i = 1; i < uris.size(); i++) clipData.addItem(new ClipData.Item(uris.get(i)));
        share.setClipData(clipData);

        startActivity(Intent.createChooser(share, "Share " + selected.size() + " image" + (selected.size() == 1 ? "" : "s")));
    }

    private void copySlideshowExtras(Intent intent) {
        intent.putExtra("root_uri", getIntent().getStringExtra("root_uri"));
        intent.putExtra("interval_ms", getIntent().getLongExtra("interval_ms", 5000));
        intent.putExtra("transition", getIntent().getStringExtra("transition"));
        intent.putExtra("transition_ms", getIntent().getLongExtra("transition_ms", 500));
        intent.putExtra("fit_mode", getIntent().getIntExtra("fit_mode", 0));
        intent.putExtra("sort_mode", getIntent().getIntExtra("sort_mode", 0));
        intent.putExtra("shuffle", getIntent().getBooleanExtra("shuffle", true));
        intent.putExtra("loop", getIntent().getBooleanExtra("loop", true));
        intent.putExtra("recursive", getIntent().getBooleanExtra("recursive", false));
    }

    private void updateSelectionSummary() {
        int selected = selectedItems(false).size();
        if (selected == 0) {
            summary.setText(String.format(Locale.US, "%d image%s found · slideshow will use all", results.size(), results.size() == 1 ? "" : "s"));
        } else {
            summary.setText(String.format(Locale.US, "%d of %d selected", selected, results.size()));
        }
    }

    private void setButtonsEnabled(boolean enabled) {
        selectAllButton.setEnabled(enabled);
        clearButton.setEnabled(enabled);
        slideshowButton.setEnabled(enabled);
        shareButton.setEnabled(enabled);
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(13);
        return button;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(dp(3), dp(4), dp(3), 0);
        return params;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
