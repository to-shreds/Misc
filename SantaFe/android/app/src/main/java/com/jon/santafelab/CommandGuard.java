package com.jon.santafelab;
import android.content.Context;
import android.content.SharedPreferences;

/** Persist the uncertainty guard before any command transmission. Never automatically re-send. */
final class CommandGuard {
    private static final Object LOCK=new Object();
    private static final String PREF="sf-command-guard-v1";
    static boolean blocked(Context c) {
        synchronized(LOCK){return c.getSharedPreferences(PREF,0).getBoolean("pending",false);}
    }
    static boolean reserve(Context c,String action) {
        if(!("lock".equals(action)||"unlock".equals(action)||"stop".equals(action)||
            "normal".equals(action)||"cool".equals(action)||"warm".equals(action)))return false;
        synchronized(LOCK){
            SharedPreferences p=c.getSharedPreferences(PREF,0);
            if(p.getBoolean("pending",false))return false;
            return p.edit().putBoolean("pending",true).putString("action",action)
                .putLong("started",System.currentTimeMillis()).commit();
        }
    }
    static boolean complete(Context c) {
        synchronized(LOCK){return c.getSharedPreferences(PREF,0).edit().clear().commit();}
    }
    static String pendingLabel(Context c) {
        synchronized(LOCK){return c.getSharedPreferences(PREF,0).getString("action","");}
    }
    private CommandGuard(){}
}
