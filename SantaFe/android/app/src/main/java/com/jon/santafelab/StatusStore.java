package com.jon.santafelab;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** The vehicle observation time, not HTTP response time, controls whether an event is new. */
final class StatusStore {
    static final String PREF="sf-status-v1";
    private static final Object LOCK=new Object();
    static String read(Context c){return c.getSharedPreferences(PREF,0).getString("snapshot","");}
    static long observationMillis(String raw){
        if(raw==null||raw.length()>40)return -1;
        try {
            if(raw.matches("[0-9]{14}"))
                return LocalDateTime.parse(raw,DateTimeFormatter.ofPattern("yyyyMMddHHmmss",Locale.US))
                    .toInstant(ZoneOffset.UTC).toEpochMilli();
            return Instant.parse(raw).toEpochMilli();
        } catch(Exception e){return -1;}
    }
    static boolean accept(Context c,String json){
        if(json==null||json.length()>2048)return false;
        try{
            JSONObject incoming=new JSONObject(json);
            String stamp=incoming.optString("updated_at","");
            long time=observationMillis(stamp);
            if(time<=0||time>System.currentTimeMillis()+86400000L)return false;
            JSONObject safe=new JSONObject();
            for(String field:new String[]{"locked","engine_running"}){
                Object value=incoming.opt(field);
                if(value instanceof Boolean)safe.put(field,value);
            }
            for(String field:new String[]{"fuel_percent","odometer"}){
                Object value=incoming.opt(field);
                if(value instanceof Number){
                    double num=((Number)value).doubleValue();
                    if(Double.isFinite(num)&&num>=0&&num<10000000)safe.put(field,num);
                }
            }
            safe.put("updated_at",stamp);safe.put("observed_ms",time);
            safe.put("read_ms",System.currentTimeMillis());
            String source=incoming.optString("source","status");
            safe.put("source",source.length()<=25?source:"status");
            synchronized(LOCK){
                SharedPreferences pref=c.getSharedPreferences(PREF,0);
                long prevTime=pref.getLong("observed_ms",0);
                if(prevTime>=time)return false;
                JSONObject old=null;
                try{old=new JSONObject(pref.getString("snapshot","{}"));}catch(Exception ignored){}
                JSONObject event=null;
                if(old!=null&&prevTime>0){
                    for(String f:new String[]{"locked","engine_running"}){
                        Object prev=old.opt(f),now=safe.opt(f);
                        if(prev instanceof Boolean&&now instanceof Boolean&&!prev.equals(now)){
                            String name="locked".equals(f)?"doors":"engine";
                            String kind=name+"_"+((Boolean)now?("doors".equals(name)?"locked":"on"):("doors".equals(name)?"unlocked":"off"));
                            event=new JSONObject().put("kind",kind).put("field",f).put("previous",prev)
                               .put("value",now).put("observation_ms",time).put("observed_at",stamp)
                               .put("source",safe.optString("source"));
                            break;
                        }
                    }
                }
                SharedPreferences.Editor ed=pref.edit().putString("snapshot",safe.toString())
                   .putLong("observed_ms",time);
                if(event!=null)ed.putString("event",event.toString()).putLong("event_received_ms",System.currentTimeMillis());
                if(!ed.commit())return false;
                if(event!=null)notifyTasker(c);
                notifyWidgets(c);
                return true;
            }
        }catch(Exception ignored){return false;}
    }
    static String recentEvent(Context c) {
        SharedPreferences p=c.getSharedPreferences(PREF,0);
        long at=p.getLong("event_received_ms",0);
        if(System.currentTimeMillis()-at>120000)return "";
        return p.getString("event","");
    }
    static void notifyTasker(Context c) {
        Intent i=new Intent("com.twofortyfouram.locale.intent.action.REQUEST_QUERY");
        i.setPackage("net.dinglisch.android.taskerm");
        i.putExtra("com.twofortyfouram.locale.intent.extra.ACTIVITY_CLASS_NAME",TaskerEventConfig.class.getName());
        try{c.sendBroadcast(i);}catch(Exception ignored){}
        Intent state=new Intent("com.twofortyfouram.locale.intent.action.REQUEST_QUERY");
        state.setPackage("net.dinglisch.android.taskerm");
        state.putExtra("com.twofortyfouram.locale.intent.extra.ACTIVITY_CLASS_NAME",TaskerStateConfig.class.getName());
        try{c.sendBroadcast(state);}catch(Exception ignored){}
    }
    static void notifyWidgets(Context c){
        try{SantaFeWidgetProvider.redrawAll(c);}catch(Exception ignored){}
    }
    private StatusStore(){}
}
