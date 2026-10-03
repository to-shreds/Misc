import com.jon.calendarbridge.core.DeletionPolicy;

/** Standalone regression tests for source-outage and delayed-deletion protections. */
public final class DeletionPolicyTest {
    private static final long FIRST_MISSING = 1_800_000_000_000L;
    private static final long DELAY = 15L * 60L * 1000L;
    private static int checks;

    public static void main(String[] args) {
        expect(false, "Unavailable source", 9, 10, 1, FIRST_MISSING, 3,
                FIRST_MISSING + DELAY * 2, false, false);
        expect(false, "Cancellation during outage", 0, 10, 10, FIRST_MISSING, 3,
                FIRST_MISSING + DELAY * 2, false, true);
        expect(false, "Empty snapshot", 0, 10, 10, FIRST_MISSING, 3,
                FIRST_MISSING + DELAY * 2, true, false);
        expect(false, "Small but empty snapshot", 0, 1, 1, FIRST_MISSING, 3,
                FIRST_MISSING + DELAY * 2, true, false);
        expect(false, "Abrupt majority loss", 3, 10, 7, FIRST_MISSING, 3,
                FIRST_MISSING + DELAY * 2, true, false);
        expect(false, "Five missing and majority", 4, 9, 5, FIRST_MISSING, 3,
                FIRST_MISSING + DELAY, true, false);
        expect(true, "Exactly half may delete after delay", 5, 10, 5, FIRST_MISSING, 2,
                FIRST_MISSING + DELAY, true, false);
        expect(true, "Four missing below bulk minimum", 3, 7, 4, FIRST_MISSING, 2,
                FIRST_MISSING + DELAY, true, false);
        expect(true, "Five missing below majority", 15, 20, 5, FIRST_MISSING, 2,
                FIRST_MISSING + DELAY, true, false);
        expect(false, "First missing scan", 9, 10, 1, FIRST_MISSING, 1,
                FIRST_MISSING + DELAY * 2, true, false);
        expect(false, "No missing scans", 9, 10, 1, FIRST_MISSING, 0,
                FIRST_MISSING + DELAY * 2, true, false);
        expect(false, "One millisecond before delay", 9, 10, 1, FIRST_MISSING, 2,
                FIRST_MISSING + DELAY - 1, true, false);
        expect(true, "Exact delay and two scans", 9, 10, 1, FIRST_MISSING, 2,
                FIRST_MISSING + DELAY, true, false);
        expect(true, "Past delay with multiple scans", 9, 10, 1, FIRST_MISSING, 4,
                FIRST_MISSING + DELAY * 3, true, false);
        expect(false, "Clock went backwards", 9, 10, 1, FIRST_MISSING, 3,
                FIRST_MISSING - 1, true, false);
        expect(false, "Missing timestamp unset", 9, 10, 1, 0, 3,
                FIRST_MISSING + DELAY, true, false);
        expect(false, "No missing tracked event", 10, 10, 0, FIRST_MISSING, 3,
                FIRST_MISSING + DELAY, true, false);
        expect(false, "Untracked event", 1, 0, 0, FIRST_MISSING, 3,
                FIRST_MISSING + DELAY, true, false);
        expect(false, "Impossible missing count", 2, 1, 2, FIRST_MISSING, 3,
                FIRST_MISSING + DELAY, true, false);
        expect(false, "Invalid observed count", -1, 10, 1, FIRST_MISSING, 3,
                FIRST_MISSING + DELAY, true, false);
        expect(false, "Invalid scan count", 9, 10, 1, FIRST_MISSING, -1,
                FIRST_MISSING + DELAY, true, false);
        expect(true, "Cancellation does not need scan delay", 9, 10, 1, 0, 0,
                FIRST_MISSING, true, true);
        expect(true, "Explicit cancellation in otherwise empty snapshot", 0, 10, 10, 0, 0,
                FIRST_MISSING, true, true);
        expect(true, "Explicit cancellation bypasses bulk missing hold", 3, 10, 7, 0, 0,
                FIRST_MISSING, true, true);
        expect(false, "Majority check at largest counts", 1, Integer.MAX_VALUE,
                Integer.MAX_VALUE - 1, FIRST_MISSING, 3, FIRST_MISSING + DELAY, true, false);
        System.out.println("DeletionPolicyTest: " + checks + " checks passed");
    }

    private static void expect(boolean expected, String label, int observedCount, int trackedCount,
            int missingCount, long missingSince, int missingScans, long now,
            boolean sourceHealthy, boolean explicitCancellation) {
        DeletionPolicy.Result result = DeletionPolicy.evaluate(observedCount, trackedCount,
                missingCount, missingSince, missingScans, now, sourceHealthy, explicitCancellation);
        if (result.mayDelete != expected) {
            throw new AssertionError(label + ": " + result.reason);
        }
        if (result.reason == null || result.reason.trim().isEmpty()) {
            throw new AssertionError(label + " returned no explanation");
        }
        checks++;
    }
}
