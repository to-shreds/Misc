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
            String id=bundle.getString("id","");
            if(!id.matches("[a-fA-F0-9-]{36}")){setResultCode(UNKNOWN);return;}
            try{
                String json=StatusStore.recentEvent(context);
                if(json.isEmpty()){setResultCode(FALSE);return;}
                JSONObject recent=new JSONObject(json);
                String kind=recent.optString("kind","");
                String observed=String.valueOf(recent.optLong("observation_ms",0));
                boolean matching="any".equals(event)||event.equals(kind);
                if(!matching){setResultCode(FALSE);return;}
                SharedPreferences p=context.getSharedPreferences("sf-event-queries",0);
                String key="event-"+id;
                if(observed.equals(p.getString(key,""))){setResultCode(FALSE);return;}
                if(!p.edit().putString(key,observed).commit()){setResultCode(UNKNOWN);return;}
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
            setResultCode(((Boolean)actual)==expected?TRUE:FALSE);
        }catch(Exception e){setResultCode(UNKNOWN);}
    }
}
