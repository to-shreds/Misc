package com.jon.calendarbridge.core;

/** Conservative rules for removing copies when their source events are no longer visible. */
public final class DeletionPolicy {
    public static final long MINIMUM_MISSING_TIME_MILLIS = 15L * 60L * 1000L;

    private DeletionPolicy() {}

    public static final class Result {
        public final boolean mayDelete;
        public final String reason;

        public Result(boolean mayDelete, String reason) {
            this.mayDelete = mayDelete;
            this.reason = reason;
        }
    }

    /**
     * The caller must persist the first missing observation time and consecutive missing scans.
     * Counts describe the same currently scanned time window. Explicit cancellation means a
     * positively identified canceled source event, rather than a missing query row.
     */
    public static Result evaluate(int observedCount, int trackedInWindowCount,
            int missingInWindowCount, long missingSince, int missingScans, long now,
            boolean sourceHealthy, boolean explicitCancellation) {
        if (!sourceHealthy) {
            return hold("Source calendar is unavailable or its snapshot is incomplete");
        }
        if (explicitCancellation) {
            return new Result(true, "Source explicitly reports this event as canceled");
        }
        if (observedCount < 0 || trackedInWindowCount < 0 || missingInWindowCount < 0
                || missingInWindowCount > trackedInWindowCount || missingScans < 0) {
            return hold("Calendar counts or missing observations are invalid");
        }
        if (observedCount == 0) {
            return hold("Source snapshot is empty; deletion is paused");
        }
        if (missingInWindowCount == 0 || trackedInWindowCount == 0) {
            return hold("No tracked event is confirmed missing in this window");
        }
        if (missingInWindowCount >= 5 && missingInWindowCount > trackedInWindowCount / 2) {
            return hold("Many source events disappeared together; deletion is paused");
        }
        if (missingScans < 2) {
            return hold("Waiting for a second consecutive missing observation");
        }
        if (missingSince <= 0 || now < missingSince) {
            return hold("Missing-event observation time is unavailable or the clock changed");
        }
        if (now - missingSince < MINIMUM_MISSING_TIME_MILLIS) {
            return hold("Waiting 15 minutes before removing a missing event");
        }
        return new Result(true, "Event remained missing across scans for at least 15 minutes");
    }

    private static Result hold(String reason) {
        return new Result(false, reason);
    }
}
