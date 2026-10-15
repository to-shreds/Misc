package com.toshreds.webmtomp4;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegKitConfig;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.ReturnCode;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int OPEN_WEBM = 11, SAVE_MP4 = 12;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService writer = Executors.newSingleThreadExecutor();
    private Uri inputUri, outputUri;
    private String inputName = "";
    private long durationMs = -1;
    private volatile FFmpegSession session;
    private volatile boolean busy, cancelled;
    private volatile String lastError = "";
    private Button pickButton, convertButton, cancelButton;
    private RadioButton lossless, crf;
    private LinearLayout crfControls;
    private SeekBar crfSlider;
    private TextView fileLabel, description, crfLabel, status;
    private ProgressBar progress;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        makeUi();
        File[] old = getCacheDirectory().listFiles((f,n) -> n.startsWith("webm-result-") && n.endsWith(".mp4"));
        if (old != null) for (File f : old) f.delete();
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
        TextView subtitle = label("Local conversion. No uploads.",14);
        root.addView(subtitle);
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
        TextView note = label("Pick where to save your MP4. Keep the app open while it converts. Lossless MP4s can be very large and need plenty of free space.",12);
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
        // Some providers label WebM as application/octet-stream.
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
            inputUri=uri;
            inputName=name;
            durationMs=duration(uri);
            fileLabel.setText(name);
            convertButton.setEnabled(true);
            progress.setVisibility(View.GONE);
            status.setText("Ready.");
        }else if(code==SAVE_MP4){
            outputUri=data.getData();
            start();
        }
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
    private File getCacheDirectory(){
        File e=getExternalCacheDir();
        return e==null?getCacheDir():e;
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
        if(work)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    private void start(){
        if(inputUri==null || outputUri==null || busy)return;
        cancelled=false;
        lastError="";
        setBusy(true);
        progress.setProgress(0);
        progress.setIndeterminate(durationMs<=0);
        progress.setVisibility(View.VISIBLE);
        status.setText("Converting…");
        File temp=new File(getCacheDirectory(),"webm-result-"+System.currentTimeMillis()+".mp4");
        Uri target=outputUri;
        try{
            String src=FFmpegKitConfig.getSafParameterForRead(this,inputUri);
            if(src==null || src.isEmpty())throw new IllegalStateException("Source file cannot be read.");
            String[] cmd=CommandBuilder.build(src,temp.getAbsolutePath(),lossless.isChecked(),crfSlider.getProgress());
            session=FFmpegKit.executeWithArgumentsAsync(cmd, result -> {
                session=null;
                if(cancelled || ReturnCode.isCancel(result.getReturnCode()))
                    complete("Cancelled.",false,temp,target);
                else if(ReturnCode.isSuccess(result.getReturnCode()) && temp.length()>0)
                    save(temp,target);
                else complete("Conversion failed: "+(lastError.isEmpty()?"Unsupported codec or input.":lastError),false,temp,target);
            }, log -> {
                String s=log.getMessage();
                if(s!=null && (s.contains("Error")||s.contains("error")||s.contains("failed")||s.contains("Invalid")))
                    lastError=s.trim();
            }, stats -> {
                if(durationMs>0 && !cancelled){
                    int p=(int)Math.min(99,Math.max(0,stats.getTime()*100L/durationMs));
                    ui.post(() -> {
                        if(busy && !cancelled){
                            progress.setIndeterminate(false);
                            progress.setProgress(p);
                            status.setText("Converting… "+p+"%");
                        }
                    });
                }
            });
        }catch(Exception e){
            complete("Cannot start: "+e.getMessage(),false,temp,target);
        }
    }
    private void save(File temp,Uri dest){
        ui.post(() -> {progress.setIndeterminate(true);status.setText("Saving MP4…");});
        writer.execute(() -> {
            boolean ok=false;
            String reason="";
            try(InputStream in=new FileInputStream(temp);
                OutputStream out=getContentResolver().openOutputStream(dest,"w")){
                if(out==null)throw new IllegalStateException("Cannot open output document.");
                byte[] buf=new byte[1024*1024];
                int n;
                while((n=in.read(buf))!=-1){
                    if(cancelled)throw new InterruptedException("Cancelled");
                    if(n>0)out.write(buf,0,n);
                }
                if(cancelled)throw new InterruptedException("Cancelled");
                out.flush();
                ok=true;
            }catch(Exception e){reason=e.getMessage();}
            complete(ok?"MP4 saved successfully.":cancelled?"Cancelled.":"Could not save: "+reason,ok,temp,dest);
        });
    }
    private void cancel(){
        cancelled=true;
        cancelButton.setEnabled(false);
        status.setText("Cancelling…");
        FFmpegSession running=session;
        if(running!=null)FFmpegKit.cancel(running.getSessionId());
    }
    private void complete(String msg,boolean success,File temp,Uri dest){
        if(!success){
            try{DocumentsContract.deleteDocument(getContentResolver(),dest);}
            catch(Exception ignored){}
        }
        if(temp.exists())temp.delete();
        ui.post(() -> {
            setBusy(false);
            progress.setIndeterminate(false);
            progress.setProgress(success?100:0);
            status.setText(msg);
        });
    }
}
