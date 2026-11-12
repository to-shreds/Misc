package com.jon.santafelab;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

public final class TaskerActionReceiver extends BroadcastReceiver {
    static final String CHANNEL="sf_tasker";
    static void notifyUser(Context c,String title,String description,String action){
        NotificationManager m=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(m==null)return;
        if(Build.VERSION.SDK_INT>=26)m.createNotificationChannel(new NotificationChannel(CHANNEL,"Santa Fe commands",NotificationManager.IMPORTANCE_DEFAULT));
        Intent open=new Intent(c,MainActivity.class);
        if(action!=null)open.putExtra("santa_fe_action",action);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent p=PendingIntent.getActivity(c,8120,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(c,CHANNEL).setSmallIcon(R.drawable.ic_santafe)
            .setContentTitle(title).setContentText(description).setContentIntent(p).setAutoCancel(true).build();
        try{m.notify(3827,n);}catch(SecurityException ignored){}
    }
    @Override public void onReceive(Context context,Intent intent){
        if(!"com.twofortyfouram.locale.intent.action.FIRE_SETTING".equals(intent.getAction()))return;
        Bundle bundle=intent.getBundleExtra(TaskerSecurity.BUNDLE);
        if(!TaskerSecurity.verified(context,bundle))return;
        String action=bundle.getString("action","");
        if("status".equals(action)){StatusJob.enqueue(context);return;}
        if(!action.matches("lock|unlock|stop|normal|cool|warm"))return;
        if(!TaskerSecurity.enabled(context)){
            notifyUser(context,"Santa Fe confirmation required","Enable unattended Tasker commands in app settings, or tap to open.",action);
            return;
        }
        if(action.matches("normal|cool|warm")){
            notifyUser(context,"Remote start needs approval","Tap to verify the vehicle is outdoors before starting.",action);
            return;
        }
        if(!CarCommandJob.enqueue(context,action))
            notifyUser(context,"Santa Fe command not queued","A command may be pending. Open the app and check the vehicle.",null);
    }
}
