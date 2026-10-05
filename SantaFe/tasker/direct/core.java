// Santa Fe Direct 1.1.0 entry and command state. Credentials are Tasker settings.
// Tokens and transaction details stay in a volatile global Java object.
import java.nio.channels.*;
import java.util.concurrent.locks.ReentrantLock;

void sfSaveMarker(String operation) {
    JSONObject marker = new JSONObject().put("action", operation).put("time", sfNow());
    File temporary = new File(sfMarkerFile.getPath() + ".tmp");
    FileOutputStream out = new FileOutputStream(temporary);
    try { out.write(marker.toString().getBytes("UTF-8")); out.getFD().sync(); } finally { out.close(); }
    if (!temporary.renameTo(sfMarkerFile)) sfFail("Could not save the pending-command guard. No command was sent.");
    if (!sfMarkerFile.isFile() || sfMarkerFile.length() == 0) sfFail("Could not verify the pending-command guard. No command was sent.");
}
void sfClearMarker() {
    if (sfMarkerFile.exists() && !sfMarkerFile.delete()) sfFail("Could not clear the pending-command guard. Check the vehicle before running SFD Resolve Unknown.");
    if (sfMarkerFile.exists()) sfFail("The pending-command guard is still present. No further command will be sent.");
    sfSession.remove("pending");
}
String sfPollOnce() {
    if (!sfMarkerFile.exists()) sfFail("No command is waiting for a result.");
    JSONObject pending = (JSONObject)sfSession.get("pending");
    if (pending == null || pending.optString("tid").length() == 0)
        sfFail("The previous command has no readable transaction in this session. Check the vehicle, then run SFD Resolve Unknown. It will not be retried.");
    JSONObject selected = (JSONObject)sfSession.get("vehicle");
    if (!pending.optString("vin").equals(selected.optString("vin")) || !pending.optString("regid").equals(selected.optString("regid"))
        || !pending.optString("identity").equals(sfSession.get("identity")))
        sfFail("Return to the account and vehicle that received the pending command. No command was sent.");
    Map extra = new HashMap(); extra.put("tid", pending.getString("tid")); extra.put("login_id", sfSession.get("email")); extra.put("service_type", "REMOTE_POLL");
    JSONObject data = sfRequest("GET", "/ac/v2/rmt/getRunningStatus", null, true, true, extra).getJSONObject("data");
    if (data.has("tid") && !pending.getString("tid").equals(data.optString("tid"))) sfFail("Hyundai returned a different transaction. The command outcome remains unresolved.");
    String status = data.optString("status");
    if (status.equals("SUCCESS")) { sfClearMarker(); return "SUCCESS"; }
    if (status.equals("ERROR")) { sfClearMarker(); return "FAILED"; }
    // The upstream client uses a fixed polling delay. The tester establishes
    // response states, but not the unit of nextPollingInterval. Do not guess it.
    int interval = 10;
    pending.put("interval", interval); pending.put("nextAllowedAt", sfNow() + interval * 1000L);
    return status.equals("PENDING") ? "PENDING" : "UNKNOWN";
}
String sfPollBounded(boolean waitFirst) {
    long deadline = sfNow() + 120000; int count = 0;
    if (waitFirst) sfDelay(10000);
    else {
        JSONObject previous = (JSONObject)sfSession.get("pending");
        long delay = previous == null ? 0 : previous.optLong("nextAllowedAt", sfNow()) - sfNow();
        if (delay >= 120000) return "PENDING";
        if (delay > 0) sfDelay(delay);
    }
    String outcome = "PENDING";
    while (count < 10 && sfNow() < deadline) {
        outcome = sfPollOnce(); count++;
        if (!outcome.equals("PENDING")) return outcome;
        JSONObject pending = (JSONObject)sfSession.get("pending");
        long delay = pending.optInt("interval", 10) * 1000L;
        if (sfNow() + delay >= deadline || count >= 10) break;
        sfDelay(delay);
    }
    return outcome;
}
String sfSubmit(String operation) {
    if (sfMarkerFile.exists()) sfFail("A previous command outcome is unresolved. Run SFD Check Command or check the vehicle and use SFD Resolve Unknown. No new command was sent.");
    JSONObject recipe = sfCommandRecipe(operation);
    if (operation.equals("start") && !sfConfirm("Remote start", "Is the vehicle parked outdoors, clear of people and safe to start?", "Start").equals("yes")) return "CANCELLED";
    // Obtain an actual status object before control submission, as in the tester.
    sfStatus(false);
    JSONObject selected = (JSONObject)sfSession.get("vehicle");
    JSONObject pending = new JSONObject().put("action", operation).put("vin", selected.getString("vin"))
        .put("regid", selected.getString("regid")).put("identity", sfSession.get("identity"));
    sfSaveMarker(operation); sfSession.put("pending", pending);
    Map extra = new HashMap(); JSONObject values = recipe.getJSONObject("extra");
    for (Iterator keys = values.keys(); keys.hasNext();) { String key = (String)keys.next(); extra.put(key, values.getString(key)); }
    JSONObject body = recipe.isNull("body") ? null : recipe.getJSONObject("body");
    JSONObject result = sfRequest("POST", recipe.getString("path"), body, true, true, extra);
    String id = result.optString("tid");
    if (id.length() == 0) return "UNKNOWN";
    sfHeader(id); pending.put("tid", id);
    tasker.showToast("Hyundai accepted " + operation + ". Checking completion...");
    return sfPollBounded(true);
}
String sfReport(String state, String message) {
    tasker.setVariable("SFDState", state); tasker.setVariable("SFDResult", message);
    tasker.showToast(message); return message;
}

String sfDispatch(String operation) {
    if (operation.equals("controls")) {
        operation = sfChoose("Santa Fe controls", new String[]{"Status", "Refresh status", "Lock", "Unlock", "Remote start", "Remote stop", "Check command", "Account settings", "Climate settings"},
            new String[]{"status", "refresh", "lock", "unlock", "start", "stop", "poll", "setup", "climate"});
    }
    if (operation.equals("cancel")) return sfReport("CANCELLED", "Cancelled. No request was sent.");
    if (operation.equals("verify")) {
        int actions = tasker.getTask().getActionCount();
        if (actions < 1) sfFail("Tasker reports no executable actions.");
        JSONObject sample = new JSONObject().put("doorLock", true).put("engine", false).put("airCtrlOn", false);
        String flags = sfStatusFlag(sample, "doorLock", "Locked", "Unlocked") + " / " + sfStatusFlag(sample, "engine", "Running", "Off") + " / " + sfStatusFlag(sample, "airCtrlOn", "On", "Off");
        if (!flags.equals("Locked / Off / Off")) sfFail("The status parser self-check failed. No request was sent.");
        String message = sfReport("READY", "Santa Fe Direct 1.1.0 loaded. Core has " + actions + " executable action(s). Java, JSON and HTTP libraries are available. Status parser verified: " + flags + ". No network request was made.");
        sfMessage("Santa Fe Direct verification", message);
        return message;
    }
    if (operation.equals("setup")) {
        if (sfMarkerFile.exists()) sfFail("Check the pending command before changing account settings. Use SFD Check Command or SFD Resolve Unknown.");
        JSONObject account = sfAccountForm();
        if (account.optBoolean("cancel")) return sfReport("CANCELLED", "Account settings unchanged.");
        tasker.setVariable("SFDEmail", account.getString("email")); tasker.setVariable("SFDPassword", account.getString("password"));
        tasker.setVariable("SFDPin", account.getString("pin")); tasker.setVariable("SFDVin", account.getString("vin"));
        sfSession.clear();
        return sfReport("SAVED", "Account saved in Tasker. Run SFD Connect once to verify it. No request was sent during setup.");
    }
    if (operation.equals("climate")) {
        JSONObject settings = sfClimateForm();
        if (settings.optBoolean("cancel")) return sfReport("CANCELLED", "Climate settings unchanged.");
        tasker.setVariable("SFDTemperature", String.valueOf(settings.getInt("temperature")));
        tasker.setVariable("SFDDuration", String.valueOf(settings.getInt("duration")));
        tasker.setVariable("SFDDefrost", settings.getBoolean("defrost") ? "1" : "0");
        return sfReport("SAVED", "Remote start settings saved. No vehicle command was sent.");
    }
    if (operation.equals("clear")) { sfSession.clear(); return sfReport("CLEARED", "Live login cleared. Saved account and any pending-command guard are retained."); }
    if (operation.equals("forget")) {
        if (!sfConfirm("Forget account", "Remove the email, password, PIN and selected VIN saved in this Tasker project? A pending command warning will remain.", "Forget").equals("yes")) return sfReport("CANCELLED", "Account settings unchanged.");
        for (String name : new String[]{"SFDEmail", "SFDPassword", "SFDPin", "SFDVin"}) tasker.setVariable(name, "");
        sfSession.clear(); return sfReport("FORGOTTEN", "Account cleared from Tasker variables. Any earlier Tasker backups are separate. Pending-command guard retained.");
    }
    if (operation.equals("resolve")) {
        if (!sfMarkerFile.exists()) return sfReport("READY", "No unresolved command is recorded.");
        if (!sfConfirm("Unresolved command", "Check the car or MyHyundai first. Have you confirmed what the vehicle actually did? This clears the warning only. It does not cancel or repeat a command.", "I checked the vehicle").equals("yes")) return sfReport("CANCELLED", "Pending-command guard retained.");
        sfClearMarker(); return sfReport("ACKNOWLEDGED", "Physical outcome acknowledged. No API command was sent or retried.");
    }
    if (!Arrays.asList(new String[]{"connect", "choose", "status", "refresh", "lock", "unlock", "start", "stop", "poll"}).contains(operation))
        sfFail("Unknown task operation. No request was sent.");
    if (operation.equals("choose")) {
        if (sfMarkerFile.exists()) sfFail("Check the pending command before switching vehicles.");
        sfCheckAccount();
        if (!sfSession.containsKey("token") || sfNow() >= ((Number)sfSession.get("expires")).longValue()) {
            try { sfLogin(); } catch (IOException selectNeeded) {
                if (!sfSession.containsKey("vehicles") || Boolean.TRUE.equals(sfSession.get("authBlocked"))) throw selectNeeded;
            }
        }
        JSONArray vehicles = (JSONArray)sfSession.get("vehicles");
        if (vehicles == null || vehicles.length() == 0) sfFail("Hyundai returned no active vehicles.");
        String[] labels = new String[vehicles.length()]; String[] vins = new String[vehicles.length()];
        for (int i = 0; i < vehicles.length(); i++) {
            JSONObject vehicle = vehicles.getJSONObject(i); vins[i] = vehicle.getString("vin");
            labels[i] = "Vehicle " + (i + 1) + " | VIN ending " + vins[i].substring(13);
        }
        String chosen = sfChoose("Choose your Santa Fe", labels, vins);
        if (chosen.equals("cancel")) return sfReport("CANCELLED", "Vehicle selection unchanged.");
        tasker.setVariable("SFDVin", chosen); sfSelectVehicle();
        return sfReport("SELECTED", "Vehicle selected. Run SFD Status.");
    }
    // With an unresolved outcome, controls are blocked before even logging in.
    if (Arrays.asList(new String[]{"lock", "unlock", "start", "stop"}).contains(operation) && sfMarkerFile.exists())
        sfFail("A previous command outcome is unresolved. Run SFD Check Command or check the vehicle and use SFD Resolve Unknown. No new request was sent.");
    if (operation.equals("poll")) {
        JSONObject previous = (JSONObject)sfSession.get("pending");
        if (!sfMarkerFile.exists()) sfFail("No command is waiting for a result.");
        if (previous == null || previous.optString("tid").length() == 0)
            sfFail("The previous command has no readable transaction in this session. Check the vehicle, then run SFD Resolve Unknown. It will not be retried.");
    }
    sfEnsureSession(operation.equals("connect"));
    if (operation.equals("connect") || operation.equals("status") || operation.equals("refresh")) {
        String status = sfStatus(operation.equals("refresh"));
        sfReport("READ", (operation.equals("connect") ? "Connected.\n" : "") + status);
        if (!operation.equals("connect") && !sfValue("par2").equals("gui")) sfMessage("Santa Fe status", status);
        return sfValue("SFDResult");
    }
    String state = operation.equals("poll") ? sfPollBounded(false) : sfSubmit(operation);
    String message = state.equals("SUCCESS") ? "Hyundai confirmed command completion (SUCCESS)."
        : state.equals("FAILED") ? "Hyundai reported command failure (ERROR). No retry was sent."
        : state.equals("CANCELLED") ? "Cancelled. No vehicle command was sent."
        : state.equals("PENDING") ? "Command is still pending. Run SFD Check Command later. It will not be resubmitted."
        : "Command outcome is unknown. Check the vehicle before SFD Resolve Unknown. It will not be retried.";
    return sfReport(state, message);
}

// ENTRY: the generator embeds api.java, ui.java and this file into one action.
String operation = sfValue("par1");
if (operation.length() == 0) operation = "verify";
Map sfSession = null;
File sfMarkerFile = new File(context.getNoBackupFilesDir(), "santa-fe-direct-pending.json");
RandomAccessFile sfLockFile = null; FileLock sfLock = null;
OkHttpClient sfClient = sfMakeClient();
String sfOutput = "";
ReentrantLock sfMutex = null; boolean sfMutexHeld = false;
try {
    synchronized ("santa.fe.direct.operation".intern()) {
        sfMutex = (ReentrantLock)tasker.getGlobalJavaVariables().get("sfDirectLock");
        if (sfMutex == null) { sfMutex = new ReentrantLock(); tasker.setJavaVariable("sfDirectLock", sfMutex); }
    }
    sfMutexHeld = sfMutex.tryLock();
    if (!sfMutexHeld) sfFail("Another Santa Fe task is running. No new request was sent.");
    sfLockFile = new RandomAccessFile(new File(context.getNoBackupFilesDir(), "santa-fe-direct.lock"), "rw");
    try { sfLock = sfLockFile.getChannel().tryLock(); } catch (OverlappingFileLockException busy) {}
    if (sfLock == null) sfOutput = sfReport("BUSY", "Another Santa Fe task is running. No new request was sent.");
    else {
        sfSession = (Map)tasker.getGlobalJavaVariables().get("sfDirectSession");
        if (sfSession == null) { sfSession = new HashMap(); tasker.setJavaVariable("sfDirectSession", sfSession); }
        tasker.setVariable("SFDLastHttp", "");
        sfOutput = sfDispatch(operation);
    }

} catch (Exception failure) {
    String message = failure.getMessage();
    if (message == null || !message.startsWith("SF: ")) message = "Connection or response failed. No request was retried.";
    else message = message.substring(4);
    boolean unresolved = sfMarkerFile.exists();
    if (unresolved) message += " A previous command may have reached the car. Run SFD Check Command or check the vehicle before SFD Resolve Unknown.";
    sfOutput = sfReport(unresolved ? "UNKNOWN" : "FAILED", message);
} finally {
    try {
        if (sfLock != null) sfLock.release();
    } finally {
        try { if (sfLockFile != null) sfLockFile.close(); }
        finally { if (sfMutexHeld) sfMutex.unlock(); }
    }
}

return sfOutput;
