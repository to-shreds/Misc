package com.toshreds.webmtomp4;

import android.app.Activity;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;


import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int OPEN_WEBM = 11, SAVE_MP4 = 12, ASK_NOTIFICATIONS = 13;
    private static final String TAG = "WebMToMP4";
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Uri inputUri, outputUri;
    private String inputName = "";
    private volatile long durationMs = -1;
    private volatile boolean busy;
    private volatile boolean launchPending;
    private Button pickButton, convertButton, cancelButton;
    private RadioButton lossless, crf;
    private LinearLayout crfControls;
    private SeekBar crfSlider;
    private TextView fileLabel, description, crfLabel, status;
    private ProgressBar progress;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        makeUi();
    }

    private void makeUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xFFF8FAFD);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22),dp(38),dp(22),dp(25));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("WebM to MP4");
        title.setTextSize(28);
        title.setTypeface(null,Typeface.BOLD);
        title.setTextColor(0xFF17213B);
        root.addView(title);
        root.addView(label("Local conversion. No uploads.",14));
        space(root,32);

        fileLabel = label("No WebM selected",17);
        fileLabel.setTextColor(0xFF17213B);
        root.addView(fileLabel);
        space(root,9);
        pickButton = button("Choose WebM", () -> pickInput());
        root.addView(pickButton);
        space(root,22);

        TextView quality = label("QUALITY",13);
        quality.setTextColor(0xFF255BCC);
        quality.setTypeface(null,Typeface.BOLD);
        root.addView(quality);
        RadioGroup group = new RadioGroup(this);
        group.setOrientation(LinearLayout.VERTICAL);
        lossless = new RadioButton(this);
        lossless.setId(View.generateViewId());
        lossless.setText("Lossless (default)");
        lossless.setTextSize(17);
        group.addView(lossless);
        crf = new RadioButton(this);
        crf.setId(View.generateViewId());
        crf.setText("CRF (smaller file)");
        crf.setTextSize(17);
        group.addView(crf);
        root.addView(group);
        description = label("",13);
        root.addView(description);
        crfControls = new LinearLayout(this);
        crfControls.setOrientation(LinearLayout.VERTICAL);
        crfLabel = label("",15);
        crfControls.addView(crfLabel);
        crfSlider = new SeekBar(this);
        crfSlider.setMin(1);
        crfSlider.setMax(35);
        crfSlider.setProgress(18);
        crfControls.addView(crfSlider);
        root.addView(crfControls);
        crfSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar,int value,boolean user){ refreshCrf(); }
            public void onStartTrackingTouch(SeekBar bar){}
            public void onStopTrackingTouch(SeekBar bar){}
        });
        refreshCrf();
        group.setOnCheckedChangeListener((g,id) -> refreshQuality());
        lossless.setChecked(true);
        refreshQuality();
        space(root,18);

        convertButton = button("Convert to MP4", () -> pickOutput());
        convertButton.setEnabled(false);
        root.addView(convertButton);
        cancelButton = button("Cancel conversion", () -> cancel());
        cancelButton.setVisibility(View.GONE);
        root.addView(cancelButton);
        progress = new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.GONE);
        root.addView(progress);
        space(root,12);
        status = label("Select a file to get started.",14);
        root.addView(status);
        space(root,25);
        TextView note = label("Conversion continues when you leave the app or lock the screen. Watch progress or cancel from the Android notification. Lossless MP4s may be enormous. Everything stays on this device.",12);
        note.setTextColor(Color.GRAY);
        root.addView(note);
        setContentView(scroll);
    }

    private TextView label(String s,int size) {
        TextView t=new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(0xFF444A57);
        return t;
    }
    private Button button(String s,Runnable onClick) {
        Button b=new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setOnClickListener(v -> onClick.run());
        return b;
    }
    private int dp(int d){return (int)(d*getResources().getDisplayMetrics().density+0.5f);}
    private void space(LinearLayout root,int d){
        root.addView(new View(this),new LinearLayout.LayoutParams(1,dp(d)));
    }
    private void refreshCrf() {
        int n=crfSlider.getProgress();
        crfLabel.setText("CRF "+n+" • "+(n<=17?"Very high quality":n<=22?"High quality":n<=27?"Balanced":"Smaller file"));
    }
    private void refreshQuality() {
        boolean useCrf=crf.isChecked();
        crfControls.setVisibility(useCrf?View.VISIBLE:View.GONE);
        description.setText(useCrf
            ?"H.264 video and AAC audio. Lower CRF means higher quality and larger files."
            :"Lossless H.264 video and ALAC audio. Huge files; some players cannot decode lossless H.264.");
    }
    private void pickInput() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"video/webm","video/x-matroska","application/octet-stream"});
        startActivityForResult(i,OPEN_WEBM);
    }
    private void pickOutput() {
        if (inputUri==null || busy)return;
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("video/mp4");
        i.putExtra(Intent.EXTRA_TITLE,inputName.replaceFirst("(?i)\\.webm$","")+".mp4");
        startActivityForResult(i,SAVE_MP4);
    }
    @Override protected void onActivityResult(int code,int result,Intent data){
        super.onActivityResult(code,result,data);
        if(result!=RESULT_OK || data==null || data.getData()==null)return;
        if(code==OPEN_WEBM){
            Uri uri=data.getData();
            String name=displayName(uri);
            if(!name.toLowerCase(Locale.ROOT).endsWith(".webm")){
                status.setText("Choose a .webm file.");
                return;
            }
            try { getContentResolver().takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
            catch (SecurityException ignored) { }
            ConversionService.resetIfIdle();
            inputUri=uri;
            inputName=name;
            durationMs=-1;
            worker.execute(() -> {
                long length=duration(uri);
                if(uri.equals(inputUri))durationMs=length;
            });
            fileLabel.setText(name);
            convertButton.setEnabled(true);
            progress.setVisibility(View.GONE);
            status.setText("Ready.");
        }else if(code==SAVE_MP4){
            outputUri=data.getData();
            try { getContentResolver().takePersistableUriPermission(
                outputUri, Intent.FLAG_GRANT_READ_URI_PERMISSION |
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION); }
            catch (SecurityException ignored) {
                try { getContentResolver().takePersistableUriPermission(
                    outputUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION); }
                catch (SecurityException ignoredToo) { }
            }
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    ASK_NOTIFICATIONS);
            } else start();
        }
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // A foreground media conversion works even if the notification permission
        // is denied. Android will still show it in the active-apps task manager.
        if (requestCode == ASK_NOTIFICATIONS) start();
    }

    private String displayName(Uri uri){
        try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(c!=null && c.moveToFirst() && c.getString(0)!=null)return c.getString(0);
        }catch(Exception ignored){}
        return "video.webm";
    }
    private long duration(Uri uri){
        MediaMetadataRetriever m=new MediaMetadataRetriever();
        try {
            m.setDataSource(this,uri);
            String d=m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return d==null?-1:Long.parseLong(d);
        }catch(Exception ignored){return -1;}
        finally{try{m.release();}catch(Exception ignored){}}
    }
    private void setBusy(boolean work){
        busy=work;
        pickButton.setEnabled(!work);
        convertButton.setEnabled(!work && inputUri!=null);
        lossless.setEnabled(!work);
        crf.setEnabled(!work);
        crfSlider.setEnabled(!work);
        cancelButton.setVisibility(work?View.VISIBLE:View.GONE);
        cancelButton.setEnabled(work);
    }
    @Override protected void onResume() {
        super.onResume();
        ui.removeCallbacks(refreshLoop);
        ui.post(refreshLoop);
    }

    @Override protected void onPause() {
        ui.removeCallbacks(refreshLoop);
        super.onPause();
    }

    private final Runnable refreshLoop = new Runnable() {
        @Override public void run() {
            showServiceState();
            ui.postDelayed(this, 650);
        }
    };

    private void showServiceState() {
        ConversionService.Snapshot current = ConversionService.snapshot();
        if (current.running) launchPending = false;
        if (launchPending && !current.running &&
            current.message.startsWith("Could not")) launchPending = false;
        if (launchPending && !current.running &&
            current.message.startsWith("Missing")) launchPending = false;
        if (launchPending && !current.running) return;
        setBusy(current.running);
        if (!current.running && "Select a file to get started.".equals(current.message)
            && inputUri != null) return;
        if (!current.running && "Ready.".equals(status.getText().toString())
            && !current.succeeded && current.fileName.isEmpty()) return;
        status.setText(current.message);
        if (current.running || current.succeeded) {
            progress.setVisibility(View.VISIBLE);
            progress.setIndeterminate(current.indeterminate);
            progress.setProgress(current.progress);
        } else {
            progress.setVisibility(View.GONE);
        }
        if (current.running && !current.fileName.isEmpty()) fileLabel.setText(current.fileName);
    }

    private void start() {
        if (inputUri == null || outputUri == null ||
            ConversionService.snapshot().running || launchPending) return;
        launchPending = true;
        setBusy(true);
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(true);
        status.setText("Starting background conversion…");
        Intent work = new Intent(this, ConversionService.class);
        work.setAction(ConversionService.ACTION_CONVERT);
        work.putExtra(ConversionService.EXTRA_INPUT, inputUri.toString());
        work.putExtra(ConversionService.EXTRA_OUTPUT, outputUri.toString());
        work.putExtra(ConversionService.EXTRA_NAME, inputName);
        work.putExtra(ConversionService.EXTRA_DURATION, durationMs);
        work.putExtra(ConversionService.EXTRA_LOSSLESS, lossless.isChecked());
        work.putExtra(ConversionService.EXTRA_CRF, crfSlider.getProgress());
        try {
            // Directly initiated from the foreground user's Convert button.
            startForegroundService(work);
        } catch (RuntimeException error) {
            Log.e(TAG, "Could not start background conversion", error);
            launchPending = false;
            setBusy(false);
            progress.setVisibility(View.GONE);
            status.setText("Unable to start background conversion: " + error.getMessage());
        }
    }

    private void cancel() {
        if (!ConversionService.snapshot().running) return;
        Intent intent = new Intent(this, ConversionService.class);
        intent.setAction(ConversionService.ACTION_CANCEL);
        startService(intent);
        cancelButton.setEnabled(false);
        status.setText("Cancelling…");
    }
}
