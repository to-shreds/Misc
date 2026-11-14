package com.jon.santafelab;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import java.security.MessageDigest;
import java.security.SecureRandom;
import android.util.Base64;

/** Install-specific Tasker plugin secret. Never return Hyundai credentials to Tasker. */
final class TaskerSecurity {
    static final String BUNDLE="com.twofortyfouram.locale.intent.extra.BUNDLE";
    static final String BLURB="com.twofortyfouram.locale.intent.extra.BLURB";
    static final String PREF="sf-tasker-v1";
    private static final Object LOCK=new Object();
    static String token(Context c){
        synchronized(LOCK){
            SharedPreferences p=c.getSharedPreferences(PREF,0);
            String t=p.getString("token",null);
            if(t!=null&&t.length()>=40)return t;
            byte[] b=new byte[32];new SecureRandom().nextBytes(b);
            t=Base64.encodeToString(b,Base64.NO_WRAP|Base64.URL_SAFE);
            return p.edit().putString("token",t).commit()?t:"";
        }
    }
    static boolean verified(Context c,Bundle b) {
        if(b==null)return false;
        String actual=b.getString("auth","");
        String expected=c.getSharedPreferences(PREF,0).getString("token","");
        return expected.length()>=40&&MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
            actual.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    static boolean enabled(Context c){return c.getSharedPreferences(PREF,0).getBoolean("unattended",false);}
    static boolean enable(Context c,boolean on){
        return c.getSharedPreferences(PREF,0).edit().putBoolean("unattended",on).commit();
    }
    private TaskerSecurity(){}
}
