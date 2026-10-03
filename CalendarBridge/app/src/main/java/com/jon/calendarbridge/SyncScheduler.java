package com.jon.calendarbridge;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.provider.CalendarContract;

final class SyncScheduler {
    static final int PERIODIC = 4101, CHANGES = 4102;
    static void schedule(Context context) {
        JobScheduler jobs = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        BridgeApi.Config c = BridgeApi.load(context);
        if (!c.enabled || !c.approved) { jobs.cancel(PERIODIC); jobs.cancel(CHANGES); return; }
        ComponentName service = new ComponentName(context, SyncJobService.class);
        jobs.schedule(new JobInfo.Builder(PERIODIC, service)
                .setPeriodic(15 * 60_000L).setPersisted(true).build());
        armChanges(context);
    }
    static void armChanges(Context context) {
        BridgeApi.Config c = BridgeApi.load(context);
        if (!c.enabled || !c.approved) return;
        JobScheduler jobs = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        jobs.schedule(new JobInfo.Builder(CHANGES, new ComponentName(context, SyncJobService.class))
                .addTriggerContentUri(new JobInfo.TriggerContentUri(CalendarContract.Events.CONTENT_URI,
                        JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
                .addTriggerContentUri(new JobInfo.TriggerContentUri(CalendarContract.Attendees.CONTENT_URI,
                        JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
                .addTriggerContentUri(new JobInfo.TriggerContentUri(CalendarContract.Calendars.CONTENT_URI,
                        JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
                .setTriggerContentUpdateDelay(20_000L).setTriggerContentMaxDelay(60_000L).build());
    }
}
