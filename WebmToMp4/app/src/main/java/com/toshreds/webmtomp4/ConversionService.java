package com.toshreds.webmtomp4;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.util.Log;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegKitConfig;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.ReturnCode;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The conversion is owned by a foreground service, never by an Activity.
 * The notification, cancel action and partial CPU wake lock remain active even
 * when the screen is locked or MainActivity is removed from Recents.
 */
public final class ConversionService extends Service {
    public static final String ACTION_CONVERT = "com.toshreds.webmtomp4.CONVERT";
    public static final String ACTION_CANCEL = "com.toshreds.webmtomp4.CANCEL";
    public static final String EXTRA_INPUT = "input";
    public static final String EXTRA_OUTPUT = "output";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_DURATION = "duration";
    public static final String EXTRA_LOSSLESS = "lossless";
    public static final String EXTRA_CRF = "crf";

    private static final String TAG = "WebmToMp4Service";
    private static final String CHANNEL_RUNNING = "conversions";
    private static final String CHANNEL_DONE = "conversion_results";
    private static final int RUNNING_NOTIFICATION = 1001;
    private static final int RESULT_NOTIFICATION = 1002;
    private static final long NOTIFY_INTERVAL_MS = 1800;
    // Android 15+ permits at most six hours of mediaProcessing in 24h.
    private static final long MAX_WAKELOCK_MS = 6L * 60 * 60 * 1000;

    public static final class Snapshot {
        public final boolean running;
        public final boolean succeeded;
        public final boolean indeterminate;
        public final int progress;
        public final String message;
        public final String fileName;

        private Snapshot(boolean running, boolean succeeded, boolean indeterminate,
                         int progress, String message, String fileName) {
            this.running = running;
            this.succeeded = succeeded;
            this.indeterminate = indeterminate;
            this.progress = progress;
            this.message = message;
            this.fileName = fileName;
        }
    }

    private static volatile Snapshot state =
        new Snapshot(false, false, true, 0, "Select a file to get started.", "");

    public static Snapshot snapshot() { return state; }

    public static void resetIfIdle() {
        if (!state.running) {
            state = new Snapshot(false, false, true, 0, "Select a file to get started.", "");
        }
    }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean active;
    private volatile boolean cancelled;
    private volatile boolean timedOut;
    private volatile FFmpegSession currentSession;
    private volatile String lastError = "";
    private volatile long durationMs = -1;
    private volatile long lastNotificationUpdateMs;
    private volatile int lastNotificationPercent = -1;
    private String fileName = "";
    private Uri outputUri;
    private PowerManager.WakeLock cpuLock;
    private NotificationManager notifications;

    @Override public void onCreate() {
        super.onCreate();
        notifications = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel ongoing = new NotificationChannel(CHANNEL_RUNNING,
            "Video conversions", NotificationManager.IMPORTANCE_LOW);
        ongoing.setDescription("Progress while converting a WebM to MP4");
        ongoing.setSound(null, null);
        notifications.createNotificationChannel(ongoing);
        NotificationChannel completed = new NotificationChannel(CHANNEL_DONE,
            "Conversion finished", NotificationManager.IMPORTANCE_DEFAULT);
        notifications.createNotificationChannel(completed);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_CANCEL.equals(action)) {
            cancel();
            return START_NOT_STICKY;
        }
        if (!ACTION_CONVERT.equals(action)) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (active) return START_NOT_STICKY;

        String in = intent.getStringExtra(EXTRA_INPUT);
        String out = intent.getStringExtra(EXTRA_OUTPUT);
        if (in == null || out == null) {
            state = new Snapshot(false, false, true, 0, "Missing input or output file.", "");
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        final Uri input;
        try {
            input = Uri.parse(in);
            outputUri = Uri.parse(out);
        } catch (Throwable e) {
            state = new Snapshot(false, false, true, 0, "Invalid file selection.", "");
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        fileName = intent.getStringExtra(EXTRA_NAME);
        if (fileName == null || fileName.isEmpty()) fileName = "video.webm";
        durationMs = intent.getLongExtra(EXTRA_DURATION, -1);
        final boolean lossless = intent.getBooleanExtra(EXTRA_LOSSLESS, true);
        final int crf = intent.getIntExtra(EXTRA_CRF, 18);
        cancelled = false;
        timedOut = false;
        lastError = "";
        currentSession = null;
        active = true;
        state = new Snapshot(true, false, true, 0, "Starting conversion…", fileName);
        notifications.cancel(RESULT_NOTIFICATION);

        try {
            Notification notification = makeRunningNotification(state);
            if (Build.VERSION.SDK_INT >= 35) {
                startForeground(RUNNING_NOTIFICATION, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);
            } else if (Build.VERSION.SDK_INT >= 29) {
                startForeground(RUNNING_NOTIFICATION, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(RUNNING_NOTIFICATION, notification);
            }
            PowerManager pm = (PowerManager)getSystemService(POWER_SERVICE);
            cpuLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "com.toshreds.webmtomp4:conversion");
            cpuLock.setReferenceCounted(false);
            cpuLock.acquire(MAX_WAKELOCK_MS);
        } catch (Throwable error) {
            Log.e(TAG, "Could not start media-processing foreground service", error);
            active = false;
            state = new Snapshot(false, false, true, 0,
                "Could not start background conversion: " + messageFor(error), fileName);
            releaseCpu();
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        worker.execute(() -> launchFFmpeg(input, outputUri, lossless, crf));
        return START_NOT_STICKY;
    }

    private void launchFFmpeg(Uri input, Uri output, boolean lossless, int crf) {
        try {
            if (cancelled) {
                complete(false, "Cancelled.");
                return;
            }
            // SAF streams, not a full copy of the 3 GB source in RAM or cache.
            String src = FFmpegKitConfig.getSafParameterForRead(getApplicationContext(), input);
            String dst = FFmpegKitConfig.getSafParameterForWrite(getApplicationContext(), output);
            if (src == null || src.isEmpty() || dst == null || dst.isEmpty()) {
                complete(false, "Selected files could not be opened.");
                return;
            }
            String[] args = CommandBuilder.build(src, dst, lossless, crf);
            if (cancelled) {
                complete(false, "Cancelled.");
                return;
            }
            FFmpegSession s = FFmpegKit.executeWithArgumentsAsync(args, result -> {
                currentSession = null;
                if (timedOut) {
                    complete(false, "Android's background conversion time limit was reached.");
                } else if (cancelled || ReturnCode.isCancel(result.getReturnCode())) {
                    complete(false, "Cancelled.");
                } else if (ReturnCode.isSuccess(result.getReturnCode())) {
                    complete(true, "MP4 saved successfully.");
                } else {
                    String message = lastError.isEmpty() ?
                        "Unsupported source, codec or destination." : lastError;
                    complete(false, "Conversion failed: " + message);
                }
            }, log -> {
                String message = log.getMessage();
                if (message != null && (message.contains("Error") || message.contains("error")
                    || message.contains("failed") || message.contains("Invalid"))) {
                    lastError = messageFor(new IllegalStateException(message));
                }
            }, stats -> {
                if (!active || cancelled || timedOut) return;
                long total = durationMs;
                int percent = total > 0
                    ? (int)Math.min(99, Math.max(0, (stats.getTime() * 100L) / total))
                    : -1;
                showProgress(percent);
            });
            if (active) currentSession = s;
            if (cancelled && s != null) FFmpegKit.cancel(s.getSessionId());
        } catch (Throwable error) {
            Log.e(TAG, "Conversion failed to start", error);
            complete(false, "Conversion could not start: " + messageFor(error));
        }
    }

    private void showProgress(int percent) {
        long now = SystemClock.elapsedRealtime();
        if (percent == lastNotificationPercent && now - lastNotificationUpdateMs < NOTIFY_INTERVAL_MS)
            return;
        if (now - lastNotificationUpdateMs < NOTIFY_INTERVAL_MS && percent < 99)
            return;
        lastNotificationUpdateMs = now;
        lastNotificationPercent = percent;
        state = new Snapshot(true, false, percent < 0, Math.max(0, percent),
            percent < 0 ? "Converting…" : "Converting… " + percent + "%",
            fileName);
        ui.post(() -> {
            if (active && !cancelled && !timedOut)
                notifications.notify(RUNNING_NOTIFICATION, makeRunningNotification(state));
        });
    }

    private Notification makeRunningNotification(Snapshot snapshot) {
        Intent open = new Intent(this, MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openApp = PendingIntent.getActivity(this, 1, open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent cancel = new Intent(this, ConversionService.class).setAction(ACTION_CANCEL);
        PendingIntent cancelIntent = PendingIntent.getService(this, 2, cancel,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL_RUNNING)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Converting WebM to MP4")
            .setContentText(snapshot.message)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setProgress(100, snapshot.progress, snapshot.indeterminate)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelIntent)
            .build();
    }

    private void postCompletedNotification(boolean success, String message) {
        Intent open = new Intent(this, MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(this, 3, open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification complete = new Notification.Builder(this, CHANNEL_DONE)
            .setSmallIcon(success ? android.R.drawable.stat_sys_download_done
                : android.R.drawable.stat_notify_error)
            .setContentTitle(success ? "MP4 conversion completed" : "MP4 conversion stopped")
            .setContentText(message)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .build();
        try { notifications.notify(RESULT_NOTIFICATION, complete); }
        catch (RuntimeException error) { Log.w(TAG, "Completion notification unavailable", error); }
    }

    private void cancel() {
        if (!active) {
            stopSelf();
            return;
        }
        if (cancelled) return;
        cancelled = true;
        state = new Snapshot(true, false, true, 0, "Cancelling…", fileName);
        notifications.notify(RUNNING_NOTIFICATION, makeRunningNotification(state));
        FFmpegSession s = currentSession;
        if (s != null) {
            try { FFmpegKit.cancel(s.getSessionId()); }
            catch (Throwable error) { Log.w(TAG, "FFmpeg cancellation error", error); }
        }
    }

    private void complete(boolean success, String message) {
        // Called by FFmpeg or the worker; all service lifecycle operations run on main.
        if (!success && outputUri != null) {
            try { DocumentsContract.deleteDocument(getContentResolver(), outputUri); }
            catch (Exception error) { Log.w(TAG, "Partial MP4 could not be deleted", error); }
        }
        ui.post(() -> {
            if (!active) return;
            active = false;
            currentSession = null;
            state = new Snapshot(false, success, false, success ? 100 : 0,
                message, fileName);
            releaseCpu();
            stopForeground(STOP_FOREGROUND_REMOVE);
            postCompletedNotification(success, message);
            stopSelf();
        });
    }

    private void releaseCpu() {
        if (cpuLock != null && cpuLock.isHeld()) cpuLock.release();
        cpuLock = null;
    }

    private static String messageFor(Throwable e) {
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty())
            message = e.getClass().getSimpleName();
        message = message.replace('\r', ' ').replace('\n', ' ').trim();
        return message.length() > 180 ? message.substring(0, 180) : message;
    }

    // Android 15+ limits mediaProcessing foreground services to 6h per 24h.
    @Override public void onTimeout(int startId, int fgsType) {
        timedOut = true;
        cancelled = true;
        FFmpegSession s = currentSession;
        if (s != null) FFmpegKit.cancel(s.getSessionId());
        state = new Snapshot(false, false, false, 0,
            "Android's six-hour background-processing limit was reached.", fileName);
        active = false;
        releaseCpu();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override public void onDestroy() {
        if (active) {
            cancelled = true;
            FFmpegSession s = currentSession;
            if (s != null) {
                try { FFmpegKit.cancel(s.getSessionId()); }
                catch (Throwable error) { Log.w(TAG, "Error stopping FFmpeg", error); }
            }
            state = new Snapshot(false, false, false, 0,
                "Conversion interrupted by Android.", fileName);
        }
        active = false;
        releaseCpu();
        worker.shutdown();
        super.onDestroy();
    }

    @Override public void onTaskRemoved(Intent rootIntent) {
        // Removing the UI from Recents does not cancel a foreground conversion.
        super.onTaskRemoved(rootIntent);
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
