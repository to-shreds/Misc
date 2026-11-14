package com.jon.santafelab;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.RemoteViews;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Native widget views and immutable Android-host click targets, independent of the WebView process. */
public class SantaFeWidgetProvider extends AppWidgetProvider {
    static final String REFRESH = "com.jon.santafelab.WIDGET_REFRESH";
    public static final class Compact extends SantaFeWidgetProvider {}
    public static final class Controls extends SantaFeWidgetProvider {}
    public static final class Dashboard extends SantaFeWidgetProvider {}

    @Override public void onUpdate(Context c,AppWidgetManager manager,int[] ids) {
        redrawAll(c);
        StatusJob.maintainWidgets(c,true);
        StatusJob.enqueue(c);
    }

    @Override public void onEnabled(Context c) {
        StatusJob.maintainWidgets(c,true);
        redrawAll(c);
    }

    @Override public void onDisabled(Context c) {
        if(!hasWidgets(c))StatusJob.maintainWidgets(c,false);
    }

    @Override public void onRestored(Context c,int[] oldIds,int[] newIds) {
        redrawIfInstalled(c);
    }

    @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager am,int widgetId,Bundle options) {
        redrawAll(c);
    }

    @Override public void onReceive(Context c,Intent intent) {
        super.onReceive(c,intent);
        if(intent==null)return;
        String action=intent.getAction();
        if(REFRESH.equals(action)) {
            StatusJob.manualRefreshRequested(c);
            redrawAll(c); // Acknowledge the tap before starting a potentially delayed network job.
            StatusJob.enqueue(c); // Read-only: cannot issue a Hyundai command.
        } else if(Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) ||
                  Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            redrawIfInstalled(c); // Repair all stored PendingIntents after package/launcher restoration.
        }
    }

    static boolean hasWidgets(Context c) {
        AppWidgetManager am=AppWidgetManager.getInstance(c);
        for(Class<?> type:new Class<?>[]{Compact.class,Controls.class,Dashboard.class}) {
            if(am.getAppWidgetIds(new ComponentName(c,type)).length>0)return true;
        }
        return false;
    }

    static void redrawIfInstalled(Context c) {
        if(!hasWidgets(c))return;
        StatusJob.maintainWidgets(c,true);
        redrawAll(c);
    }

    // An individual widget instance owns its immutable intents, so deleting an old instance
    // does not reuse its controls when a launcher later rebinds or recreates another one.
    private static int code(int widgetId,int operation) { return (widgetId<<4)|operation; }

    private static PendingIntent open(Context c,String operation,int widgetId,int operationId) {
        Intent i=new Intent(c,MainActivity.class);
        i.setAction("com.jon.santafelab.OPEN_"+operation.toUpperCase(Locale.US));
        i.setData(Uri.parse("santafe-widget://open/"+widgetId+"/"+operationId));
        if(!"dashboard".equals(operation))i.putExtra("santa_fe_action",operation);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c,code(widgetId,operationId),i,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent refresh(Context c,int widgetId) {
        Intent i=new Intent(c,Dashboard.class).setAction(REFRESH);
        i.setData(Uri.parse("santafe-widget://refresh/"+widgetId));
        return PendingIntent.getBroadcast(c,code(widgetId,2),i,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }

    static void redrawAll(Context c) {
        AppWidgetManager am=AppWidgetManager.getInstance(c);
        drawType(c,am,Compact.class,R.layout.widget_compact,1);
        drawType(c,am,Controls.class,R.layout.widget_controls,2);
        drawType(c,am,Dashboard.class,R.layout.widget_dashboard,3);
    }

    private static void drawType(Context c,AppWidgetManager am,Class<?> type,int layout,int kind) {
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
        updated=StatusJob.widgetFeedback(c,updated);
        for(int widgetId:ids) {
            RemoteViews views=new RemoteViews(c.getPackageName(),layout);
            if(kind==1||kind==3){
                views.setTextViewText(R.id.w_doors,doors);
                views.setTextViewText(R.id.w_engine,engine);
                views.setTextViewText(R.id.w_fuel,fuel);
                views.setTextViewText(R.id.w_updated,updated);
            }
            if(kind==3)views.setTextViewText(R.id.w_odometer,odometer);
            PendingIntent dashboard=open(c,"dashboard",widgetId,1);
            views.setOnClickPendingIntent(R.id.widget_root,dashboard);
            views.setOnClickPendingIntent(R.id.w_open,dashboard);
            views.setOnClickPendingIntent(R.id.w_refresh,refresh(c,widgetId));
            if(kind!=1){
                views.setOnClickPendingIntent(R.id.w_lock,open(c,"lock",widgetId,3));
                views.setOnClickPendingIntent(R.id.w_unlock,open(c,"unlock",widgetId,4));
                views.setOnClickPendingIntent(R.id.w_start,open(c,"normal",widgetId,5));
                views.setOnClickPendingIntent(R.id.w_stop,open(c,"stop",widgetId,6));
            }
            am.updateAppWidget(widgetId,views); // Complete RemoteViews, including every click binding.
        }
    }
}
