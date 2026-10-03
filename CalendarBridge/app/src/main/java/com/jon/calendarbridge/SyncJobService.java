package com.jon.calendarbridge;

import android.app.job.JobParameters;
import android.app.job.JobService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ConcurrentHashMap;

public class SyncJobService extends JobService {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ConcurrentHashMap<Integer, Run> running = new ConcurrentHashMap<>();
    private static class Run { volatile boolean stopped; volatile Future<?> future; }
    @Override public boolean onStartJob(JobParameters params) {
        Run run = new Run(); running.put(params.getJobId(), run);
        run.future = worker.submit(() -> {
            try { if(!run.stopped) BridgeApi.automaticSync(this); }
            catch (Exception e) { if(!run.stopped) BridgeApi.recordFailure(this, e); }
            finally {
                running.remove(params.getJobId(), run);
                if(!run.stopped) {
                    if (params.getJobId() == SyncScheduler.CHANGES) SyncScheduler.armChanges(this);
                    jobFinished(params, false);
                }
            }
        });
        return true;
    }
    @Override public boolean onStopJob(JobParameters params) {
        Run run = running.remove(params.getJobId());
        if (run != null) { run.stopped = true; if(run.future != null)run.future.cancel(true); }
        return BridgeApi.load(this).enabled;
    }
    @Override public void onDestroy() { worker.shutdownNow(); super.onDestroy(); }
}
