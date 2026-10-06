// Join input is data, never executable code. Results stay on the phone.
import android.app.Notification;
import android.app.NotificationManager;

JSONObject sfAcceptRemote(String payload, boolean fromJoin) {
    if (payload.startsWith("hyundai=:=")) payload = payload.substring(10);
    if (payload.length() > 180) sfFail("Invalid remote command. No request was sent.");
    String[] fields = payload.split("\\|", -1);
    if (fromJoin && fields.length != 1) sfFail("Join accepts one command word only. No request was sent.");
    if (!fromJoin && fields.length != 4) sfFail("Invalid local web command. No request was sent.");
    String command = fields[0];
    if (!Arrays.asList(new String[]{"ignition_on", "ignition_on_cold", "ignition_on_hot", "ignition_off", "lock", "unlock", "ping", "status", "refresh", "location", "compare_location", "poll"}).contains(command))
        sfFail("Unsupported remote command. No request was sent.");
    sfRemoteCommand = command;
    boolean start = command.startsWith("ignition_on"), confirmed = false;
    if (fromJoin) {
        if (!sfValue("SFDJoinEnabled").equals("1")) sfFail("Join commands are disabled. Open SFD Join Settings on your phone.");
    } else {
        if (!fields[1].matches("[a-z0-9_-]{12,64}") || !fields[2].matches("[0-9]{13}") || !fields[3].matches("[01]")) sfFail("Invalid local web request identity. No request was sent.");
        sfRemoteId = fields[1];
        long issued = Long.parseLong(fields[2]), now = sfNow();
        if (issued < now - 90000 || issued > now + 10000) sfFail("Local web command expired or the clocks differ. No request was sent.");
        confirmed = fields[3].equals("1");
        if (start != confirmed) sfFail("A local web start needs an outdoors confirmation. No request was sent.");
        File file = new File(context.getNoBackupFilesDir(), "santa-fe-direct-web-seen.json");
        JSONArray seen = sfReadArray(file, true), retained = new JSONArray();
        for (int i = 0; i < seen.length(); i++) {
            JSONObject old = seen.getJSONObject(i);
            if (old.getString("id").equals(sfRemoteId)) sfFail("Duplicate local web command ignored. No request was sent.");
            if (old.getLong("expires") >= now) retained.put(old);
        }
        if (retained.length() >= 128) sfFail("Too many recent local web requests. Wait before trying again.");
        retained.put(new JSONObject().put("id", sfRemoteId).put("expires", issued + 100000));
        sfWriteArray(file, retained);
    }
    String operation = command.equals("ignition_on") ? "start" : command.equals("ignition_on_cold") ? "start_cold" : command.equals("ignition_on_hot") ? "start_hot" : command.equals("ignition_off") ? "stop" : command;
    return new JSONObject().put("command", operation).put("safe", start && confirmed);
}
JSONArray sfReadArray(File file, boolean strict) {
    if (!file.exists()) return new JSONArray();
    try {
        if (!file.isFile() || file.length() > 65536) throw new IOException("Storage limit");
        FileInputStream input = new FileInputStream(file); ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try { byte[] block = new byte[2048]; int count; while ((count = input.read(block)) != -1) { if (bytes.size() + count > 65536) throw new IOException("Storage limit"); bytes.write(block, 0, count); } }
        finally { input.close(); }
        return new JSONArray(new String(bytes.toByteArray(), "UTF-8"));
    } catch (Exception failure) {
        if (strict) sfFail("Remote receipt storage is invalid. No request was sent.");
        return new JSONArray();
    }
}
void sfWriteArray(File file, JSONArray data) {
    byte[] bytes = data.toString().getBytes("UTF-8");
    if (bytes.length > 65536) sfFail("Local storage limit reached. No new request was sent.");
    File temporary = new File(file.getPath() + ".tmp"); FileOutputStream output = new FileOutputStream(temporary);
    try { output.write(bytes); output.getFD().sync(); } finally { output.close(); }
    if (!temporary.renameTo(file) || !file.isFile() || file.length() == 0) sfFail("Could not save the Join receipt. No request was sent.");
}
String sfRemoteSummary(String operation, String output) {
    if (Arrays.asList(new String[]{"location", "compare_location", "periodic_location"}).contains(operation) || sfRemoteCommand.equals("location") || sfRemoteCommand.equals("compare_location")) {
        if (sfLocationFailed) return "Location lookup failed. No retry was sent. Open Location on the phone for details.";
        return "Car/phone comparison: " + (sfValue("SFDProximity").length() == 0 ? "Unknown" : sfValue("SFDProximity"))
            + (sfValue("SFDDistanceMeters").length() == 0 ? "" : ". Approximate distance: " + sfValue("SFDDistanceMeters") + " m") + ". Location timestamps are shown on the phone.";
    }
    // Only internally written summaries enter this log. Never a push or HTTP body.
    String message = output == null ? "" : output;
    for (String secret : new String[]{sfValue("SFDEmail"), sfValue("SFDPassword"), sfValue("SFDPin"), sfValue("SFDVin"), sfValue("SFDCarLat"), sfValue("SFDCarLon"), sfValue("SFDPhoneLat"), sfValue("SFDPhoneLon")})
        if (secret.length() >= 4) message = message.replace(secret, "[removed]");
    if (sfSession != null) {
        for (String name : new String[]{"token", "email", "pin"}) { Object value = sfSession.get(name); if (value != null && value.toString().length() >= 4) message = message.replace(value.toString(), "[removed]"); }
        JSONObject pending = (JSONObject)sfSession.get("pending"); if (pending != null && pending.optString("tid").length() >= 4) message = message.replace(pending.getString("tid"), "[removed]");
    }
    return message.length() > 1600 ? message.substring(0, 1600) : message;
}
String sfWebValue(String name, String fallback) { String value = sfValue(name); return value.length() == 0 ? fallback : value; }
String sfWebPreset(String prefix, String temperature, String defrost) {
    return sfWebValue(prefix + "Temperature", temperature) + " F / " + sfWebValue(prefix + "Duration", "10") + " min / defrost " + (sfWebValue(prefix + "Defrost", defrost).equals("1") ? "on" : "off");
}
void sfPublishWeb(String operation, String output, boolean append) {
    File file = new File(context.getNoBackupFilesDir(), "santa-fe-direct-activity.json");
    JSONArray log = sfReadArray(file, false);
    if (append) {
        boolean location = Arrays.asList(new String[]{"location", "compare_location", "periodic_location"}).contains(operation) || sfRemoteCommand.equals("location") || sfRemoteCommand.equals("compare_location");
        String state = location ? (sfLocationFailed ? "FAILED" : "READ") : sfWebValue("SFDState", "READY");
        JSONObject result = new JSONObject().put("id", sfRemoteId).put("command", sfRemoteCommand.length() > 0 ? sfRemoteCommand : "phone")
            .put("state", state).put("time", sfNow()).put("http", sfValue("SFDLastHttp")).put("message", sfRemoteSummary(operation, output));
        tasker.setVariable("SFDWebResult", result.toString());
        JSONArray retained = new JSONArray(); for (int i = Math.max(0, log.length() - 59); i < log.length(); i++) retained.put(log.getJSONObject(i));
        retained.put(result); log = retained; sfWriteArray(file, log);
    }
    String vin = sfValue("SFDVin"); JSONObject state = new JSONObject().put("version", 1).put("busy", sfValue("SFDWebBusy").equals("1"))
        .put("doors", sfWebValue("SFDDoorLock", "Unknown")).put("engine", sfWebValue("SFDEngine", "Unknown")).put("climate", sfWebValue("SFDClimate", "Unknown"))
        .put("vehicleTime", sfWebValue("SFDVehicleTime", "Not read yet")).put("statusReadAt", sfValue("SFDStatusReadAt"))
        .put("vehicle", vin.length() == 17 ? "VIN ending " + vin.substring(13) : "No car selected")
        .put("pending", sfMarkerFile.exists()).put("proximity", sfValue("SFDProximity")).put("distance", sfValue("SFDDistanceMeters"))
        .put("carLat", sfValue("SFDCarLat")).put("carLon", sfValue("SFDCarLon")).put("carTime", sfValue("SFDCarTime"))
        .put("phoneAccuracy", sfValue("SFDPhoneAccuracy")).put("comparedAt", sfValue("SFDLocationCheckedAt"))
        .put("autoLocation", sfValue("SFDAutoLocation").equals("1")).put("locationHours", sfWebValue("SFDLocationHours", "1"))
        .put("presets", new JSONObject().put("regular", sfWebPreset("SFD", "72", "0")).put("cold", sfWebPreset("SFDCold", "62", "0")).put("hot", sfWebPreset("SFDHot", "81", "1")));
    if (sfValue("SFDWebResult").length() > 0) state.put("result", new JSONObject(sfValue("SFDWebResult")));
    tasker.setVariable("SFDWebState", state.toString()); tasker.setVariable("SFDWebLog", log.toString());
}
void sfRemoteNotification(String message) {
    try {
        NotificationManager manager = (NotificationManager)context.getSystemService("notification");
        Class channelClass = Class.forName("android.app.NotificationChannel");
        Object channel = channelClass.getConstructor(new Class[]{String.class, CharSequence.class, Integer.TYPE}).newInstance(new Object[]{"sf_direct_results", "Santa Fe results", Integer.valueOf(2)});
        manager.getClass().getMethod("createNotificationChannel", new Class[]{channelClass}).invoke(manager, new Object[]{channel});
        Notification.Builder builder = new Notification.Builder(context).setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("Santa Fe: " + sfWebValue("SFDState", "Result")).setContentText(message).setAutoCancel(true);
        builder.getClass().getMethod("setChannelId", new Class[]{String.class}).invoke(builder, new Object[]{"sf_direct_results"});
        builder.setStyle(new Notification.BigTextStyle().bigText(message)); manager.notify(34502, builder.getNotification());
    } catch (Exception ignored) { tasker.setVariable("SFDNotificationNotice", "Allow Tasker notifications to see Join results while the interface is closed."); }
}
