package com.toshreds.webmtomp4;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.widget.RadioButton;
import android.widget.TextView;

import com.arthenica.ffmpegkit.FFmpegKitConfig;
import com.arthenica.ffmpegkit.FFprobeKit;
import com.arthenica.ffmpegkit.FFprobeSession;
import com.arthenica.ffmpegkit.ReturnCode;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

public final class ConversionSmokeRunner extends Instrumentation {
    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        start();
    }

    @Override public void onStart() {
        Bundle report=new Bundle();
        try {
            runTest();
            report.putString("result","PASS");
            finish(Activity.RESULT_OK,report);
        } catch(Throwable t) {
            report.putString("result","FAIL: "+t);
            report.putString("stack",android.util.Log.getStackTraceString(t));
            finish(Activity.RESULT_CANCELED,report);
        }
    }

    private void runTest() throws Exception {
        Context ctx=getTargetContext();
        ContentResolver resolver=ctx.getContentResolver();
        Uri source=create(ctx,"webm-mp4-smoke.webm","video/webm");
        check(source!=null,"Couldn't create input");
        try(InputStream in=getContext().getAssets().open("smoke.webm");
            OutputStream out=resolver.openOutputStream(source,"w")) {
            check(out!=null,"Couldn't write input");
            byte[] data=new byte[8192];
            int n;
            while((n=in.read(data))!=-1)out.write(data,0,n);
        }
        Intent intent=new Intent(ctx,MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        MainActivity activity=(MainActivity)startActivitySync(intent);
        check(activity!=null,"Couldn't launch activity");
        waitForIdleSync();

        for(boolean isLossless:new boolean[]{true,false}) {
            Uri destination=create(ctx,isLossless?"webm-smoke-lossless.mp4":"webm-smoke-crf.mp4","video/mp4");
            check(destination!=null,"Couldn't create output");

            AtomicReference<Throwable> launchError=new AtomicReference<>();
            runOnMainSync(() -> {
                try {
                    field(activity,"inputUri",source);
                    field(activity,"outputUri",destination);
                    field(activity,"inputName","smoke.webm");
                    field(activity,"durationMs",500L);
                    ((RadioButton)readField(activity,"lossless")).setChecked(isLossless);
                    ((RadioButton)readField(activity,"crf")).setChecked(!isLossless);
                    Method m=MainActivity.class.getDeclaredMethod("start");
                    m.setAccessible(true);
                    m.invoke(activity);
                }catch(Throwable t){launchError.set(t);}
            });
            if(launchError.get()!=null)throw new RuntimeException("Cannot start conversion",launchError.get());

            String message="";
            long deadline=SystemClock.elapsedRealtime()+90_000;
            while(SystemClock.elapsedRealtime()<deadline){
                AtomicReference<String> current=new AtomicReference<>("");
                runOnMainSync(() -> {
                    try{current.set(((TextView)readField(activity,"status")).getText().toString());}
                    catch(Exception ignored){}
                });
                message=current.get();
                if(message.contains("successfully")||message.contains("failed")||
                   message.contains("Unable")||message.contains("Cancelled"))break;
                Thread.sleep(400);
            }
            check(message.contains("successfully"),(isLossless?"Lossless":"CRF")+" conversion: "+message);

            String saf=FFmpegKitConfig.getSafParameterForRead(ctx,destination);
            FFprobeSession probe=FFprobeKit.executeWithArguments(new String[]{
                "-v","error","-show_entries","stream=codec_name",
                "-of","default=noprint_wrappers=1",saf
            });
            check(ReturnCode.isSuccess(probe.getReturnCode()),
                "Cannot probe produced MP4: "+String.valueOf(probe.getOutput()));
            String info=String.valueOf(probe.getOutput()).toLowerCase();
            check(info.contains("h264"),"Missing H.264 video: "+info);
            check(info.contains(isLossless?"alac":"aac"),"Unexpected audio codec: "+info);

            long size=0;
            try(InputStream out=resolver.openInputStream(destination)){
                check(out!=null,"Cannot read completed MP4");
                byte[] block=new byte[8192];
                int n;
                while((n=out.read(block))!=-1)size+=n;
            }
            check(size>1000,"Output empty: "+size);
            resolver.delete(destination,null,null);
        }
        resolver.delete(source,null,null);
        runOnMainSync(activity::finish);
    }

    private static Uri create(Context ctx,String name,String mime) {
        ContentValues values=new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME,name);
        values.put(MediaStore.MediaColumns.MIME_TYPE,mime);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_MOVIES+"/WebmMp4Smoke");
        return ctx.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,values);
    }

    private static Object readField(MainActivity a,String name) throws Exception {
        Field f=MainActivity.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(a);
    }
    private static void field(MainActivity a,String name,Object value) throws Exception {
        Field f=MainActivity.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(a,value);
    }
    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError(why);
    }
}
