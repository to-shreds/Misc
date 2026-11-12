package com.jon.santafelab;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import org.json.JSONObject;

public final class TaskerConditionReceiver extends BroadcastReceiver {
    private static final int TRUE=16,FALSE=17,UNKNOWN=18;
    @Override public void onReceive(Context context,Intent intent){
        if(!"com.twofortyfouram.locale.intent.action.QUERY_CONDITION".equals(intent.getAction()))return;
        Bundle bundle=intent.getBundleExtra(TaskerSecurity.BUNDLE);
        if(bundle==null){setResultCode(UNKNOWN);return;}
        String event=bundle.getString("event",null);
        if(event!=null){
            Bundle data=intent.getBundleExtra("net.dinglisch.android.tasker.extras.PASS_THROUGH_DATA");
            int messageId=data==null?-1:data.getInt("net.dinglisch.android.tasker.MESSAGE_ID",-1);
            if(messageId<=0){setResultCode(UNKNOWN);return;}
            String id=bundle.getString("id","");
            if(!id.matches("[a-fA-F0-9-]{36}")){setResultCode(UNKNOWN);return;}
            try{
                String json=StatusStore.recentEvent(context);
                if(json.isEmpty()){setResultCode(FALSE);return;}
                JSONObject recent=new JSONObject(json);
                if(recent.optInt("message_id",-1)!=messageId){setResultCode(UNKNOWN);return;}
                String kind=recent.optString("kind","");
                String observed=String.valueOf(recent.optLong("observation_ms",0));
                boolean matching="any".equals(event)||event.equals(kind);
                if(!matching){setResultCode(FALSE);return;}
                SharedPreferences p=context.getSharedPreferences("sf-event-queries",0);
                String key="event-"+id;
                if(observed.equals(p.getString(key,""))){setResultCode(FALSE);return;}
                if(!p.edit().putString(key,observed).commit()){setResultCode(UNKNOWN);return;}
                addVariables(context,recent);
                setResultCode(TRUE);
            }catch(Exception e){setResultCode(UNKNOWN);}
            return;
        }
        String state=bundle.getString("state","");
        if(!state.matches("doors_locked|doors_unlocked|engine_on|engine_off")){setResultCode(UNKNOWN);return;}
        try{
            JSONObject s=new JSONObject(StatusStore.read(context));
            long age=System.currentTimeMillis()-s.optLong("observed_ms",0);
            if(age<0||age>30L*60L*1000L){setResultCode(UNKNOWN);return;}
            boolean lock=state.startsWith("doors"),expected=state.endsWith("locked")&&!state.endsWith("unlocked")||state.endsWith("_on");
            Object actual=s.opt(lock?"locked":"engine_running");
            if(!(actual instanceof Boolean)){setResultCode(UNKNOWN);return;}
            addVariables(context,null);
            setResultCode(((Boolean)actual)==expected?TRUE:FALSE);
        }catch(Exception e){setResultCode(UNKNOWN);}
    }
    void addVariables(Context context,JSONObject event){
        // Tasker treats only lower-case variable names as local variables.
        // Never include VIN, account information, tokens, coordinates or API responses.
        Bundle vars=new Bundle();
        try {
            JSONObject s=new JSONObject(StatusStore.read(context));
            Object e=s.opt("engine_running"),locked=s.opt("locked"),fuel=s.opt("fuel_percent"),odometer=s.opt("odometer");
            vars.putString("%sf_engine",e instanceof Boolean?(((Boolean)e)?"on":"off"):"unknown");
            vars.putString("%sf_locked",locked instanceof Boolean?(((Boolean)locked)?"true":"false"):"unknown");
            vars.putString("%sf_fuel",fuel instanceof Number?String.valueOf(fuel):"unknown");
            vars.putString("%sf_odometer",odometer instanceof Number?String.valueOf(odometer):"unknown");
            vars.putString("%sf_updated",s.optString("updated_at","unknown"));
            vars.putString("%sf_source",s.optString("source","unknown"));
            if(event!=null){
                vars.putString("%sf_event",event.optString("kind","unknown"));
                vars.putString("%sf_old",String.valueOf(event.opt("previous")));
                vars.putString("%sf_new",String.valueOf(event.opt("value")));
            }
        }catch(Exception ignored){return;}
        if(isOrderedBroadcast()){
            try{getResultExtras(true).putBundle("net.dinglisch.android.tasker.extras.VARIABLES",vars);}
            catch(Exception ignored){}
        }
    }
}
