// Shared BeanShell API implementation for Santa Fe Direct. Installed inside Tasker.
// Request recipes match the working API Lab 0.3.3. No external script is loaded.
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.security.MessageDigest;
import org.json.*;
import okhttp3.*;

String sfValue(String name) {
    String value = tasker.getVariable(name);
    return value == null ? "" : value;
}
void sfFail(String message) { throw new IOException("SF: " + message); }
long sfNow() { return System.currentTimeMillis(); }
void sfDelay(long milliseconds) { Thread.sleep(milliseconds); }
OkHttpClient sfMakeClient() {
    return new OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).cookieJar(CookieJar.NO_COOKIES).cache(null).build();
}
String sfIdentity(String email, String password, String pin) {
    JSONObject identity = new JSONObject().put("email", email).put("password", password).put("pin", pin);
    byte[] bytes = MessageDigest.getInstance("SHA-256").digest(identity.toString().getBytes("UTF-8"));
    StringBuilder encoded = new StringBuilder();
    for (int i = 0; i < bytes.length; i++) encoded.append(String.format("%02x", new Object[]{Integer.valueOf(bytes[i] & 255)}));
    return encoded.toString();
}
String sfHeader(Object value) {
    if (value == null) sfFail("An API header is missing. Run SFD Connect. No command was sent.");
    String text = String.valueOf(value);
    if (text.length() == 0 || text.length() > 8192 || text.indexOf('\r') >= 0 || text.indexOf('\n') >= 0)
        sfFail("An API header is invalid. No command was sent.");
    return text;
}
String sfEnrollmentPath(String email) {
    // Match encodeURIComponent, preserving literal @ only. Encode other delimiters.
    String encoded = URLEncoder.encode(email, "UTF-8").replace("+", "%20")
        .replace("%21", "!").replace("%27", "'").replace("%28", "(")
        .replace("%29", ")").replace("%7E", "~").replace("%40", "@");
    return "/ac/v2/enrollment/details/" + encoded;
}
boolean sfAllowedPath(String method, String path) {
    if (method.equals("GET")) return path.startsWith("/ac/v2/enrollment/details/")
        || path.equals("/ac/v2/rcs/rvs/vehicleStatus") || path.equals("/ac/v2/rmt/getRunningStatus");
    if (method.equals("POST")) return path.equals("/v2/ac/oauth/token")
        || path.equals("/ac/v2/rcs/rdo/on") || path.equals("/ac/v2/rcs/rdo/off")
        || path.equals("/ac/v2/rcs/rsc/start") || path.equals("/ac/v2/rcs/rsc/stop");
    return false;
}
JSONObject sfRequest(String method, String path, JSONObject body, boolean auth, boolean vehicle, Map extra) {
    if (!sfAllowedPath(method, path) || path.indexOf('?') >= 0 || path.indexOf('#') >= 0)
        sfFail("This API operation is not allowed. No request was sent.");
    long until = 0;
    try { until = Long.parseLong(sfValue("SFDWaitUntil")); } catch (Exception ignored) {}
    if (sfNow() < until) sfFail("Hyundai rate limited requests. Wait five minutes before trying again.");
    Request.Builder builder = new Request.Builder().url("https://api.telematics.hyundaiusa.com" + path);
    int offsetMillis = TimeZone.getDefault().getOffset(sfNow());
    String offset = offsetMillis % 3600000 == 0 ? String.valueOf(offsetMillis / 3600000) : String.valueOf(offsetMillis / 3600000.0);
    builder.header("Content-Type", "application/json;charset=UTF-8")
        .header("Accept", "application/json, text/plain, */*").header("from", "SPA").header("to", "ISS")
        .header("language", "0").header("offset", offset)
        .header("refresh", "false").header("encryptFlag", "false").header("brandIndicator", "H")
        .header("client_id", "m66129Bb-em93-SPAHYN-bZ91-am4540zp19920")
        .header("clientSecret", "v558o935-6nne-423i-baa8")
        .header("Origin", "https://api.telematics.hyundaiusa.com")
        .header("Referer", "https://api.telematics.hyundaiusa.com/login");
    if (auth) {
        if (!sfSession.containsKey("token") || sfNow() >= ((Number)sfSession.get("expires")).longValue())
            sfFail("Login expired. Run SFD Connect. No command was sent.");
        builder.header("username", sfHeader(sfSession.get("email")))
            .header("accessToken", sfHeader(sfSession.get("token")))
            .header("blueLinkServicePin", sfHeader(sfSession.get("pin")));
    }
    if (vehicle) {
        JSONObject selected = (JSONObject)sfSession.get("vehicle");
        if (selected == null) sfFail("No vehicle is selected. Run SFD Choose Vehicle.");
        builder.header("registrationId", sfHeader(selected.optString("regid")))
            .header("gen", sfHeader(selected.optString("vehicleGeneration", "2")))
            .header("vin", sfHeader(selected.optString("vin")));
    }
    if (extra != null) for (Object key : extra.keySet()) builder.header(sfHeader(key), sfHeader(extra.get(key)));
    if (method.equals("GET")) builder.get();
    else {
        byte[] bytes = body == null ? new byte[0] : body.toString().getBytes("UTF-8");
        builder.post(RequestBody.create(MediaType.parse("application/json;charset=UTF-8"), bytes));
    }
    Response response = null; JSONObject result = null;
    try {
        response = sfClient.newCall(builder.build()).execute();
        int status = response.code();
        tasker.setVariable("SFDLastHttp", String.valueOf(status));
        if (status == 429) {
            tasker.setVariable("SFDWaitUntil", String.valueOf(sfNow() + 300000));
            sfFail("Hyundai rate limited requests. Wait five minutes before trying again. No retry was sent.");
        }
        if (status == 401 || status == 403) {
            sfSession.remove("token"); sfSession.put("expires", Long.valueOf(0)); sfSession.put("authBlocked", Boolean.TRUE);
        }
        if (status < 200 || status >= 300)
            sfFail("Hyundai returned HTTP " + status + ". No retry was sent. Check SFDLastHttp and your account if needed.");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        InputStream stream = response.body() == null ? null : response.body().byteStream();
        if (stream != null) {
            byte[] block = new byte[4096]; int count;
            while ((count = stream.read(block)) != -1) {
                if (bytes.size() + count > 1048576) sfFail("Hyundai response exceeded the size limit. No retry was sent.");
                bytes.write(block, 0, count);
            }
        }
        String content = new String(bytes.toByteArray(), "UTF-8").trim();
        JSONObject data = content.length() == 0 ? new JSONObject() : new JSONObject(content);
        if (data.has("errorCode") || data.has("error")) sfFail("Hyundai reported an API error. No retry was sent.");
        String id = response.header("tmstid");
        if (id == null) id = response.header("transactionid");
        if (id == null) id = response.header("xid");
        result = new JSONObject().put("data", data).put("http", status);
        if (id != null) result.put("tid", id);
    } finally { if (response != null) response.close(); }
    // Older BeanShell versions lose a return value when finally ends in void.
    return result;
}
void sfCheckAccount() {
    String email = sfValue("SFDEmail").trim(); String password = sfValue("SFDPassword"); String pin = sfValue("SFDPin").trim();
    if (email.length() == 0 || email.length() > 320 || password.length() == 0 || password.length() > 4096 || !pin.matches("[0-9]{4}"))
        sfFail("Run SFD Setup once to enter your email, password and four-digit Bluelink PIN.");
    sfHeader(email); sfHeader(pin);
    String identity = sfIdentity(email, password, pin);
    if (!identity.equals(sfSession.get("identity"))) {
        if (sfMarkerFile.exists()) sfFail("Account changed while a command outcome is unresolved. Check the vehicle, then run SFD Resolve Unknown.");
        sfSession.clear(); sfSession.put("identity", identity);
    }
    sfSession.put("email", email); sfSession.put("pin", pin);
}
void sfSelectVehicle() {
    JSONArray list = (JSONArray)sfSession.get("vehicles"); String preferred = sfValue("SFDVin").trim().toUpperCase(Locale.US);
    JSONObject selected = null;
    if (list != null) for (int i = 0; i < list.length(); i++) {
        JSONObject vehicle = list.getJSONObject(i);
        if (preferred.length() > 0 && preferred.equals(vehicle.optString("vin"))) selected = vehicle;
        else if (preferred.length() == 0 && list.length() == 1) selected = vehicle;
    }
    if (selected == null) {
        sfSession.remove("vehicle");
        sfFail("Choose your active Santa Fe using SFD Choose Vehicle. No vehicle command was sent.");
    }
    sfSession.put("vehicle", selected);
}
void sfLogin() {
    sfSession.remove("token"); sfSession.put("expires", Long.valueOf(0)); sfSession.put("authBlocked", Boolean.TRUE);
    JSONObject body = new JSONObject().put("username", sfSession.get("email")).put("password", sfValue("SFDPassword"));
    JSONObject result = sfRequest("POST", "/v2/ac/oauth/token", body, false, false, null).getJSONObject("data");
    String token = result.optString("access_token"); long expires = result.optLong("expires_in", 1800);
    if (token.length() == 0 || expires <= 0) sfFail("Hyundai did not return a usable login. Check MyHyundai, then run SFD Connect.");
    sfHeader(token);
    sfSession.put("token", token); sfSession.put("expires", Long.valueOf(sfNow() + Math.min(expires, 86400) * 1000 - Math.min(30000, expires * 100)));
    JSONObject enrollment = sfRequest("GET", sfEnrollmentPath((String)sfSession.get("email")), null, true, false, null).getJSONObject("data");
    JSONArray source = enrollment.optJSONArray("enrolledVehicleDetails");
    if (source == null) sfFail("Hyundai returned no expected vehicle list. No command was sent.");
    JSONArray vehicles = new JSONArray();
    for (int i = 0; i < source.length(); i++) {
        JSONObject item = source.optJSONObject(i); JSONObject vehicle = item == null ? null : item.optJSONObject("vehicleDetails");
        if (vehicle != null && vehicle.optString("vin").matches("[A-HJ-NPR-Z0-9]{17}") && vehicle.optString("regid").length() > 0
                && vehicle.optString("enrollmentStatus").equals("ACTIVE")) vehicles.put(vehicle);
    }
    sfSession.put("vehicles", vehicles); sfSession.remove("vehicle");
    sfSession.put("authBlocked", Boolean.FALSE);
    sfSelectVehicle();
}
void sfEnsureSession(boolean explicit) {
    sfCheckAccount();
    if (!explicit && Boolean.TRUE.equals(sfSession.get("authBlocked")))
        sfFail("A previous account or authentication request failed. Check MyHyundai and run SFD Connect. No command was sent.");
    if (Boolean.TRUE.equals(sfSession.get("authBlocked")) || !sfSession.containsKey("token") || sfNow() >= ((Number)sfSession.get("expires")).longValue()) sfLogin();
    else sfSelectVehicle();
}
String sfStatusFlag(JSONObject status, String key, String yes, String no) {
    // Read the JSON value directly. Tasker's interpreter can represent a
    // returned Boolean differently from a boxed java.lang.Boolean type check.
    if (!status.has(key) || status.isNull(key)) return "Unknown";
    try { return status.getBoolean(key) ? yes : no; }
    catch (Exception unrecognized) { return "Unknown"; }
}
String sfStatus(boolean refresh) {
    Map extra = new HashMap(); extra.put("refresh", refresh ? "true" : "false");
    JSONObject data = sfRequest("GET", "/ac/v2/rcs/rvs/vehicleStatus", null, true, true, extra).getJSONObject("data");
    JSONObject status = data.optJSONObject("vehicleStatus");
    if (status == null) sfFail("Hyundai returned no expected vehicle status. No command was sent.");
    sfSession.put("statusRead", Boolean.TRUE);
    String timestamp = status.optString("dateTime", "Unknown");
    if (timestamp.length() > 60 || !timestamp.matches("[0-9TtZz:+ .\\-/]*")) timestamp = "Unknown";
    tasker.setVariable("SFDVehicleTime", timestamp);
    String lock = sfStatusFlag(status, "doorLock", "Locked", "Unlocked");
    String engine = sfStatusFlag(status, "engine", "Running", "Off");
    String climate = sfStatusFlag(status, "airCtrlOn", "On", "Off");
    tasker.setVariable("SFDDoorLock", lock); tasker.setVariable("SFDEngine", engine); tasker.setVariable("SFDClimate", climate);
    String summary = "Doors: " + lock + "\nEngine: " + engine + "\nClimate: " + climate + "\nVehicle timestamp: " + timestamp
        + "\nHyundai may return cached data, including after a refresh request.";
    tasker.setVariable("SFDStatus", summary); return summary;
}
JSONObject sfCommandRecipe(String operation) {
    JSONObject selected = (JSONObject)sfSession.get("vehicle");
    if (selected == null || !selected.optString("enrollmentStatus").equals("ACTIVE")) sfFail("Select an active vehicle first. No command was sent.");
    Map extra = new HashMap(); JSONObject body = null; String path = "";
    if (operation.equals("lock") || operation.equals("unlock")) {
        path = operation.equals("lock") ? "/ac/v2/rcs/rdo/off" : "/ac/v2/rcs/rdo/on";
        body = new JSONObject().put("userName", sfSession.get("email")).put("vin", selected.getString("vin"));
        extra.put("APPCLOUD-VIN", selected.getString("vin"));
    } else if (operation.equals("start") || operation.equals("stop")) {
        // This project targets the confirmed non-EV Santa Fe Hybrid recipe only.
        if (!selected.optString("evStatus").equals("N") || selected.optInt("vehicleGeneration") != 3)
            sfFail("The selected vehicle does not match the confirmed Santa Fe Hybrid generation 3 recipe. No command was sent.");
        path = operation.equals("start") ? "/ac/v2/rcs/rsc/start" : "/ac/v2/rcs/rsc/stop";
        if (operation.equals("start")) {
            int temperature = 72; int duration = 10;
            try {
                if (sfValue("SFDTemperature").length() > 0) temperature = Integer.parseInt(sfValue("SFDTemperature"));
                if (sfValue("SFDDuration").length() > 0) duration = Integer.parseInt(sfValue("SFDDuration"));
            } catch (Exception invalid) { sfFail("Use whole-number climate temperature and duration. No command was sent."); }
            if (temperature < 62 || temperature > 81 || duration < 1 || duration > 10)
                sfFail("Climate temperature must be 62 to 81 F and duration 1 to 10 minutes. No command was sent.");
            String defrost = sfValue("SFDDefrost");
            if (defrost.length() > 0 && !defrost.equals("0") && !defrost.equals("1")) sfFail("Defrost must be 0 or 1. No command was sent.");
            JSONObject seat = new JSONObject().put("drvSeatHeatState", 0).put("astSeatHeatState", 0).put("rlSeatHeatState", 0).put("rrSeatHeatState", 0);
            body = new JSONObject().put("Ims", 0).put("airCtrl", 1).put("airTemp", new JSONObject().put("unit", 1).put("value", temperature))
                .put("defrost", defrost.equals("1")).put("heating1", 0).put("igniOnDuration", duration).put("seatHeaterVentInfo", seat)
                .put("username", sfSession.get("email")).put("vin", selected.getString("regid"));
        }
    } else sfFail("This control is unavailable. No command was sent.");
    return new JSONObject().put("path", path).put("body", body == null ? JSONObject.NULL : body).put("extra", new JSONObject(extra));
}
