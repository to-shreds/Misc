package com.jon.santafelab;

import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.PersistableBundle;
import android.widget.RemoteViews;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Three home-screen sizes; all read the same timestamped cache. */
public class SantaFeWidgetProvider extends AppWidgetProvider {
    static final String REFRESH="com.jon.santafelab.WIDGET_REFRESH";
    public static final class Compact extends SantaFeWidgetProvider {}
    public static final class Controls extends SantaFeWidgetProvider {}
    public static final class Dashboard extends SantaFeWidgetProvider {}
    @Override public void onUpdate(Context context,AppWidgetManager manager,int[] ids){
        redrawAll(context);StatusJob.enqueue(context);
    }
    @Override public void onReceive(Context c,Intent intent){
        super.onReceive(c,intent);
        if(REFRESH.equals(intent.getAction())){StatusJob.enqueue(c);redrawAll(c);}
    }
    private static PendingIntent action(Context ctx,String action,int requestId){
        Intent intent=new Intent(ctx,MainActivity.class);
        intent.setAction("com.jon.santafelab.OPEN_"+action.toUpperCase(Locale.US));
        if(!"dashboard".equals(action))intent.putExtra("santa_fe_action",action);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(ctx,requestId,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private static PendingIntent refresh(Context ctx,int requestId){
        Intent intent=new Intent(ctx,SantaFeWidgetProvider.Dashboard.class).setAction(REFRESH);
        return PendingIntent.getBroadcast(ctx,requestId,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    static void redrawAll(Context c) {
        AppWidgetManager manager=AppWidgetManager.getInstance(c);
        drawType(c,manager,Compact.class,R.layout.widget_compact,1);
        drawType(c,manager,Controls.class,R.layout.widget_controls,2);
        drawType(c,manager,Dashboard.class,R.layout.widget_dashboard,3);
    }
    private static void drawType(Context c,AppWidgetManager am,Class<?> type,int layout,int kind){
        int[] ids=am.getAppWidgetIds(new ComponentName(c,type));
        if(ids==null||ids.length==0)return;
        JSONObject data=null;
        try{data=new JSONObject(StatusStore.read(c));}catch(Exception ignored){}
        String doors="Unknown",engine="Unknown",fuel="--",odometer="--",updated="Not yet loaded";
        if(data!=null){
            if(data.opt("locked") instanceof Boolean)doors=data.optBoolean("locked")?"Locked":"Unlocked";
            if(data.opt("engine_running") instanceof Boolean)engine=data.optBoolean("engine_running")?"On":"Off";
            if(data.opt("fuel_percent") instanceof Number)fuel=Math.round(data.optDouble("fuel_percent"))+"%";
            if(data.opt("odometer") instanceof Number)odometer=String.format(Locale.US,"%,.0f",data.optDouble("odometer"))+" mi";
            long at=data.optLong("observed_ms",0);
            if(at>0){
                updated=new SimpleDateFormat("MMM d, h:mm a",Locale.US).format(new Date(at));
                if(System.currentTimeMillis()-at>1800000)updated+=" (cached)";
            }
        }
        for(int id:ids) {
            RemoteViews views=new RemoteViews(c.getPackageName(),layout);
            if(kind==1||kind==3){
                views.setTextViewText(R.id.w_doors,doors);
                views.setTextViewText(R.id.w_engine,engine);
                views.setTextViewText(R.id.w_fuel,fuel);
                views.setTextViewText(R.id.w_updated,updated);
            }
            if(kind==3)views.setTextViewText(R.id.w_odometer,odometer);
            views.setOnClickPendingIntent(R.id.w_open,action(c,"dashboard",100+kind));
            views.setOnClickPendingIntent(R.id.w_refresh,refresh(c,200+kind));
            if(kind!=1){
                views.setOnClickPendingIntent(R.id.w_lock,action(c,"lock",300+kind));
                views.setOnClickPendingIntent(R.id.w_unlock,action(c,"unlock",400+kind));
                views.setOnClickPendingIntent(R.id.w_start,action(c,"normal",500+kind));
                views.setOnClickPendingIntent(R.id.w_stop,action(c,"stop",600+kind));
            }
            am.updateAppWidget(id,views);
        }
    }
}
