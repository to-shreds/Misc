// BeanShell for Tasker 6.7.6-beta. No credentials are stored in Tasker variables.
import java.io.*;
import java.net.*;
import java.util.*;
import org.json.*;
import android.app.*;
import android.content.*;
import android.os.*;
import android.net.Uri;

String readSmall(File file) {
    if (!file.exists()) return "{}";
    if (file.length() > 32768) throw new IOException("Private settings file too large");
    BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
    StringBuilder b = new StringBuilder();
    try { String s; while ((s = r.readLine()) != null) b.append(s); }
    finally { r.close(); }
    return b.toString();
}
void saveSmall(File file, JSONObject data) {
    File temp = new File(file.getPath() + ".tmp");
    FileOutputStream out = new FileOutputStream(temp);
    try { out.write(data.toString().getBytes("UTF-8")); out.getFD().sync(); }
    finally { out.close(); }
    temp.setReadable(false, false); temp.setWritable(false, false);
    temp.setReadable(true, true); temp.setWritable(true, true);
    if (!temp.renameTo(file)) throw new IOException("Could not save private pairing");
}
JSONObject request(String method, String path, JSONObject body, String token) {
    // Fixed origin. Redirects are forbidden so a bearer token cannot be redirected elsewhere.
    if (!path.startsWith("/api/") && !path.equals("/health")) throw new IOException("Invalid local path");
    HttpURLConnection c = (HttpURLConnection)new URL("http://127.0.0.1:8293" + path).openConnection();
    c.setRequestMethod(method); c.setConnectTimeout(3000); c.setReadTimeout(5000);
    c.setInstanceFollowRedirects(false); c.setRequestProperty("X-SF-Client", "tasker");
    if (token != null && token.length() > 0) c.setRequestProperty("Authorization", "Bearer " + token);
    try {
        if (body != null) {
            c.setDoOutput(true); c.setRequestProperty("Content-Type", "application/json");
            byte[] data = body.toString().getBytes("UTF-8"); c.setFixedLengthStreamingMode(data.length);
            OutputStream stream = c.getOutputStream(); stream.write(data); stream.close();
        }
        int status = c.getResponseCode();
        InputStream stream = status >= 400 ? c.getErrorStream() : c.getInputStream();
        if (stream == null) throw new IOException("No response body");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] block = new byte[4096];
        try {
            int count;
            while ((count = stream.read(block)) != -1) {
                if (bytes.size() + count > 2097152) throw new IOException("Local response too large");
                bytes.write(block, 0, count);
            }
        } finally { stream.close(); }
        JSONObject result = new JSONObject(new String(bytes.toByteArray(), "UTF-8"));
        result.put("_http", status);
        return result;
    } finally { c.disconnect(); }
}
void sendNotice(String message) {
    NotificationManager nm = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
    nm.createNotificationChannel(new NotificationChannel("sf_control", "Santa Fe Control Center", NotificationManager.IMPORTANCE_DEFAULT));
    Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
    if (launch == null) throw new IOException("Tasker launcher unavailable");
    PendingIntent pi = PendingIntent.getActivity(context, 8293, launch, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    Notification n = new Notification.Builder(context, "sf_control")
        .setSmallIcon(context.getApplicationInfo().icon).setContentTitle("Santa Fe Control Center")
        .setContentText(message).setStyle(new Notification.BigTextStyle().bigText(message))
        .setContentIntent(pi).setVisibility(Notification.VISIBILITY_PRIVATE).setAutoCancel(true).build();
    nm.notify(8293, n);
}

String operation = tasker.getVariable("par1");
String argument = tasker.getVariable("par2");
if (operation == null || operation.startsWith("%")) operation = "sync";
if (argument == null || argument.startsWith("%")) argument = "";
File privateFile = new File(context.getNoBackupFilesDir(), "sf-control-client.json");
try {
    JSONObject saved = new JSONObject(readSmall(privateFile));
    if (operation.equals("pair")) {
        JSONObject response = request("POST", "/api/pair", new JSONObject().put("code", argument.trim()), null);
        if (response.optInt("_http") != 200 || !response.optString("role").equals("tasker")) {
            tasker.showToast("Pairing failed. Generate a Tasker pairing code in Settings, not the owner code.");
            return "pairing failed";
        }
        saved.put("token", response.getString("token")); saved.put("consumed", new JSONArray());
        saveSmall(privateFile, saved);
        tasker.setVariable("SFEnabled", "1");
        tasker.showToast("Tasker paired. Run SF Sync, then SF Open.");
        return "paired";
    }
    if (operation.equals("forget")) {
        if (privateFile.exists() && !privateFile.delete()) throw new IOException("Private pairing could not be removed");
        tasker.setVariable("SFEnabled", "0"); tasker.setVariable("SFVehicleState", "");
        tasker.setVariable("SFStatus", "Tasker unpaired"); return "unpaired";
    }
    String token = saved.optString("token", "");
    if (token.length() == 0) { tasker.setVariable("SFStatus", "Run SF Pair Tasker"); return "not paired"; }
    if (operation.equals("pause")) {
        JSONObject response = request("POST", "/api/pause", new JSONObject(), token);
        tasker.setVariable("SFStatus", response.optInt("_http") == 200 ? "All rules paused" : response.optString("error", "Pause failed"));
        return "pause requested";
    }
    if (operation.equals("event")) {
        JSONObject event = new JSONObject(argument);
        event.put("id", UUID.randomUUID().toString()); event.put("at", System.currentTimeMillis() / 1000.0);
        JSONObject response = request("POST", "/api/event", event, token);
        tasker.setVariable("SFStatus", response.optInt("_http") == 200 ? "Event delivered" : response.optString("error", "Event rejected"));
        return "event processed";
    }
    JSONObject snap = request("GET", "/api/snapshot", null, token);
    if (snap.optInt("_http") != 200) {
        tasker.setVariable("SFStatus", snap.optString("error", "Pair Tasker again")); return "connection rejected";
    }
    JSONObject settings = snap.getJSONObject("config").getJSONObject("settings");
    // These are harmless configuration/status variables, never authentication secrets.
    tasker.setVariable("SFBtName", settings.optString("bluetooth_name"));
    tasker.setVariable("SFHomeWifi", settings.optString("home_wifi"));
    tasker.setVariable("SFMode", snap.getString("mode"));
    tasker.setVariable("SFVehicleState", snap.getJSONObject("state").toString(), true);
    tasker.setVariable("SFStatus", snap.optBoolean("stale") ? "Vehicle status is stale or unknown" : "Bridge connected: " + snap.getString("mode"));
    // Refresh only context obtained directly from the phone now. Unknown stays unknown.
    JSONObject phone = new JSONObject();
    try {
        Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery != null && battery.getIntExtra("plugged", -1) >= 0) phone.put("charging", battery.getIntExtra("plugged", -1) > 0);
    } catch (Exception ignored) {}
    try {
        android.net.wifi.WifiManager wifi = (android.net.wifi.WifiManager)context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        String ssid = wifi.getConnectionInfo().getSSID();
        if (ssid != null && !ssid.equals("<unknown ssid>") && ssid.length() > 0) {
            if (ssid.startsWith("\"") && ssid.endsWith("\"")) ssid=ssid.substring(1,ssid.length()-1);
            String home = settings.optString("home_wifi");
            if (home.length() > 0) phone.put("home", ssid.equals(home));
            phone.put("wifi", true);
        }
    } catch (Exception ignored) {}
    if (phone.length() > 0) request("POST", "/api/context", new JSONObject().put("at",System.currentTimeMillis()/1000.0).put("context",phone), token);
    if (operation.equals("command")) {
        JSONObject command = new JSONObject(argument);
        command.put("request_id", UUID.randomUUID().toString());
        command.put("expected_mode", snap.getString("mode")); command.put("epoch", snap.getString("epoch"));
        if (command.optString("action").equals("climate_start") && !command.has("preset_id"))
            command.put("preset_id", settings.getString("default_preset"));
        JSONObject response = request("POST", "/api/command", command, token);
        String message = response.optString("error", response.optString("message", "Request sent"));
        tasker.setVariable("SFStatus", message); tasker.showToast(message);
        // Opening a screen never confirms a command. Confirmation happens in the owner dashboard.
        if (response.optString("state").equals("confirmation_required")) tasker.callTask("SF Open", null);
    }
    // At-most-once dispatch. Save a consumed ID BEFORE invoking a task or speech action.
    // A crash can lose a notification, but cannot replay an arbitrary task on restart.
    JSONObject reply = request("GET", "/api/effects", null, token);
    if (reply.optInt("_http") != 200) return "sync completed";
    JSONArray effects = reply.getJSONArray("effects");
    JSONArray consumed = saved.optJSONArray("consumed"); if (consumed == null) consumed = new JSONArray();
    HashSet seen = new HashSet();
    for (int i = 0; i < consumed.length(); i++) seen.add(consumed.getString(i));
    JSONArray ack = new JSONArray();
    for (int i = 0; i < effects.length(); i++) {
        JSONObject e = effects.getJSONObject(i); String eid = e.getString("id"); ack.put(eid);
        if (seen.contains(eid) || e.optDouble("expires", 0) < System.currentTimeMillis()/1000.0) continue;
        consumed.put(eid); seen.add(eid);
        while (consumed.length() > 150) consumed.remove(0);
        saved.put("consumed", consumed); saveSmall(privateFile, saved);
        try {
            String type = e.optString("type");
            if (type.equals("notify")) sendNotice(e.optString("text"));
            else if (type.equals("speak")) {
                HashMap params = new HashMap(); params.put("par1", e.optString("text"));
                if (!tasker.callTask("SF Speak", params)) throw new IOException("Speech task not queued");
            } else if (type.equals("set_variable")) {
                String name = e.optString("variable");
                if (!name.matches("SFUser[A-Za-z0-9_]{1,40}")) throw new IOException("Variable not allowed");
                tasker.setVariable(name, e.optString("value"));
            } else if (type.equals("run_task")) {
                JSONArray allowed = settings.getJSONArray("task_allowlist"); boolean permit = false;
                for (int j=0; j<allowed.length(); j++) if (allowed.getString(j).equals(e.optString("task"))) permit = true;
                if (!permit) throw new IOException("Task not allowed");
                if (!tasker.callTask(e.getString("task"), null)) throw new IOException("Task not queued");
            }
        } catch (Exception localFailure) {
            tasker.setVariable("SFStatus", "A phone action could not be delivered. Check notification permissions and allowed tasks.");
        }
    }
    if (ack.length() > 0) request("POST", "/api/effects/ack", new JSONObject().put("ids", ack), token);
    return "completed";
} catch (Exception problem) {
    // Never log exception messages: libraries can place request contents in them.
    tasker.setVariable("SFStatus", "Bridge unavailable or invalid response. Open Termux and run sf-start. No command was retried.");
    return "bridge unavailable";
}
