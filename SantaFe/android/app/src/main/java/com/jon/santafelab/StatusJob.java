package com.jon.santafelab;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import javax.net.ssl.HttpsURLConnection;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Read-only public status from the existing private-credential Render backend. */
public final class StatusJob extends JobService {
    private static final String URL="https://santa-fe-emergency.onrender.com/api/web/public-status";
    private static final int ONE_SHOT=92341, PERIODIC=92342;
    private static final long FIFTEEN_MINUTES=15L*60L*1000L;
    private volatile Thread worker;
    static void enqueue(Context c){
        JobScheduler s=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(s==null)return;
        JobInfo request=new JobInfo.Builder(ONE_SHOT,new ComponentName(c,StatusJob.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setOverrideDeadline(15000).build();
        s.schedule(request);
    }
    static void monitor(Context c,boolean enabled){
        JobScheduler s=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(s==null)return;
        if(!enabled){s.cancel(PERIODIC);return;}
        JobInfo request=new JobInfo.Builder(PERIODIC,new ComponentName(c,StatusJob.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(FIFTEEN_MINUTES).setPersisted(true).build();
        s.schedule(request);
    }
    static boolean monitoring(Context c){
        JobScheduler s=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(s==null)return false;
        for(JobInfo item:s.getAllPendingJobs())if(item.getId()==PERIODIC)return true;
        return false;
    }
    @Override public boolean onStartJob(JobParameters params){
        worker=new Thread(()->{
            try{refresh(getApplicationContext());}finally{jobFinished(params,false);}
        },"santa-fe-status");
        worker.start();return true;
    }
    @Override public boolean onStopJob(JobParameters p){
        Thread t=worker;if(t!=null)t.interrupt();
        return false; // Read-only jobs can be scheduled again; never retry remote commands.
    }
    static void refresh(Context ctx){
        HttpsURLConnection con=null;
        try{
            con=(HttpsURLConnection)new URL(URL).openConnection(java.net.Proxy.NO_PROXY);
            con.setRequestMethod("GET");con.setInstanceFollowRedirects(false);
            con.setConnectTimeout(15000);con.setReadTimeout(70000);
            con.setRequestProperty("Accept","application/json");
            con.setRequestProperty("Cache-Control","no-store");
            if(con.getResponseCode()!=200)return;
            if(con.getContentLength()>8192)return;
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream in=con.getInputStream()){
                byte[] b=new byte[1024];int n;
                while((n=in.read(b))!=-1){if(bytes.size()+n>8192)return;bytes.write(b,0,n);}
            }
            JSONObject root=new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8));
            if(!"ready".equals(root.optString("availability")))return;
            JSONObject state=root.optJSONObject("status");if(state==null)return;
            JSONObject o=new JSONObject();
            o.put("locked",state.opt("locked"));
            o.put("engine_running",state.opt("engine_running"));
            o.put("fuel_percent",state.opt("fuel_percent"));
            o.put("odometer",state.opt("odometer"));
            o.put("updated_at",state.optString("updated_at",""));
            o.put("source","hyundai_cloud_cache");
            StatusStore.accept(ctx,o.toString());
        }catch(Exception ignored){}finally{
            if(con!=null)con.disconnect();
            SantaFeWidgetProvider.redrawAll(ctx);
        }
    }
}
