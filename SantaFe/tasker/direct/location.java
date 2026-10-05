// Locations stay in Tasker variables. No coordinates are written to exported logs.
import android.location.Location;
import android.location.LocationManager;
import android.os.CancellationSignal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.time.*;
import java.time.format.*;

long sfLocationMillis(String value) {
    if (value == null || value.length() > 60) return 0;
    try { return Instant.parse(value).toEpochMilli(); } catch (Exception ignored) {}
    try { return OffsetDateTime.parse(value).toInstant().toEpochMilli(); } catch (Exception ignored) {}
    // The USA upstream client defines its data timezone as UTC.
    try {
        if (value.matches("[0-9]{14}")) return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("uuuuMMddHHmmss").withResolverStyle(ResolverStyle.STRICT)).toInstant(ZoneOffset.UTC).toEpochMilli();
    } catch (Exception ignored) {}
    return 0;
}
double sfCoordinate(JSONObject coord, String key, double bound) {
    double value = coord.getDouble(key);
    if (Double.isNaN(value) || Double.isInfinite(value) || Math.abs(value) > bound) throw new IOException("Invalid coordinate");
    return value;
}
boolean sfStoreCarLocation(JSONObject location, String source) {
    try {
        if (location == null) return false;
        JSONObject coord = location.getJSONObject("coord");
        double lat = sfCoordinate(coord, "lat", 90), lon = sfCoordinate(coord, "lon", 180);
        long time = sfLocationMillis(location.optString("time"));
        JSONObject selected = (JSONObject)sfSession.get("vehicle");
        tasker.setVariable("SFDCarLat", String.valueOf(lat)); tasker.setVariable("SFDCarLon", String.valueOf(lon));
        tasker.setVariable("SFDCarTime", time > 0 ? Instant.ofEpochMilli(time).toString() : "Unknown");
        tasker.setVariable("SFDCarTimeMillis", String.valueOf(time)); tasker.setVariable("SFDCarReadAt", String.valueOf(sfNow()));
        tasker.setVariable("SFDCarVin", selected.getString("vin")); tasker.setVariable("SFDCarIdentity", sfSession.get("identity"));
        tasker.setVariable("SFDCarSource", source);
        return true;
    } catch (Exception invalid) { return false; }
}
JSONObject sfPhoneLocationValue(Location value) {
    if (value == null) return new JSONObject();
    long age = (android.os.SystemClock.elapsedRealtimeNanos() - value.getElapsedRealtimeNanos()) / 1000000L;
    if (age < 0 || age > 120000) return new JSONObject();
    return new JSONObject().put("lat", value.getLatitude()).put("lon", value.getLongitude())
        .put("time", sfNow() - age).put("accuracy", value.hasAccuracy() ? value.getAccuracy() : -1);
}
JSONObject sfPhoneFix() {
    // One bounded GPS lookup. Permission is granted in Android's Tasker settings.
    LocationManager manager = (LocationManager)context.getSystemService(android.content.Context.LOCATION_SERVICE);
    CancellationSignal cancel = new CancellationSignal();
    ExecutorService executor = Executors.newSingleThreadExecutor();
    final CountDownLatch ready = new CountDownLatch(1);
    final AtomicReference found = new AtomicReference();
    try {
        if (manager == null || !manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) return new JSONObject();
        manager.getCurrentLocation(LocationManager.GPS_PROVIDER, cancel, executor, new java.util.function.Consumer() {
            accept(Object value) { found.set(value); ready.countDown(); }
        });
        ready.await(20, TimeUnit.SECONDS);
        return sfPhoneLocationValue((Location)found.get());
    } catch (Exception unavailable) { return new JSONObject(); }
    finally { cancel.cancel(); executor.shutdownNow(); }
}
double sfDistanceMeters(double lat1, double lon1, double lat2, double lon2) {
    double dlat = Math.toRadians(lat2 - lat1), dlon = Math.toRadians(lon2 - lon1);
    double a = Math.sin(dlat / 2) * Math.sin(dlat / 2) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dlon / 2) * Math.sin(dlon / 2);
    return 6371008.8 * 2 * Math.atan2(Math.sqrt(Math.min(1, a)), Math.sqrt(Math.max(0, 1 - a)));
}
String sfCompareLocation() {
    tasker.setVariable("SFDDistanceMeters", ""); tasker.setVariable("SFDProximity", "Unknown");
    tasker.setVariable("SFDPhoneTime", "Unknown"); tasker.setVariable("SFDPhoneAccuracy", "Unknown");
    String preferred = sfValue("SFDVin");
    String identity = sfIdentity(sfValue("SFDEmail"), sfValue("SFDPassword"), sfValue("SFDPin"));
    if (sfValue("SFDCarVin").length() == 0 || !identity.equals(sfValue("SFDCarIdentity")) || (preferred.length() > 0 && !preferred.equals(sfValue("SFDCarVin"))))
        return "No location for the selected account/car. Use Read car GPS first.";
    JSONObject phone = sfPhoneFix();
    String summary = "Car GPS: " + sfValue("SFDCarLat") + ", " + sfValue("SFDCarLon") + "\nCar timestamp: " + sfValue("SFDCarTime") + "\nSource: " + sfValue("SFDCarSource");
    try {
        double lat = sfCoordinate(phone, "lat", 90), lon = sfCoordinate(phone, "lon", 180);
        long phoneTime = phone.getLong("time"); double accuracy = phone.getDouble("accuracy");
        tasker.setVariable("SFDPhoneTime", Instant.ofEpochMilli(phoneTime).toString()); tasker.setVariable("SFDPhoneAccuracy", String.valueOf(Math.round(accuracy)));
        double carLat = Double.parseDouble(sfValue("SFDCarLat")), carLon = Double.parseDouble(sfValue("SFDCarLon"));
        if (Double.isNaN(carLat) || Double.isInfinite(carLat) || Math.abs(carLat) > 90 || Double.isNaN(carLon) || Double.isInfinite(carLon) || Math.abs(carLon) > 180) throw new IOException("Invalid stored coordinate");
        double distance = sfDistanceMeters(carLat, carLon, lat, lon);
        tasker.setVariable("SFDDistanceMeters", String.valueOf(Math.round(distance)));
        long carTime = Long.parseLong(sfValue("SFDCarTimeMillis")), carAge = sfNow() - carTime, phoneAge = sfNow() - phoneTime;
        int threshold = 150; try { threshold = Integer.parseInt(sfValue("SFDNearMeters")); } catch (Exception ignored) {}
        if (threshold < 25 || threshold > 5000) threshold = 150;
        boolean fresh = carTime > 0 && carAge >= -10000 && carAge <= 900000 && phoneAge >= -10000 && phoneAge <= 120000 && accuracy > 0 && accuracy <= 100;
        String state = fresh ? (distance + accuracy <= threshold ? "Near" : distance - accuracy > threshold ? "Apart" : "Uncertain") : "Unknown";
        tasker.setVariable("SFDProximity", state);
        summary = "Distance to reported car position: " + Math.round(distance) + " m\nComparison: " + state + "\n" + summary
            + "\nPhone timestamp: " + sfValue("SFDPhoneTime") + "\nPhone GPS accuracy: " + Math.round(accuracy) + " m\n" + (fresh ? "Car position has no reported accuracy; comparison is approximate." : "Stale/missing time or phone accuracy. This is not a current-location verdict.");
    } catch (Exception unavailable) { summary += "\nPhone GPS unavailable. Enable precise location for Tasker and try outdoors."; }
    return summary;
}
String sfReadCarLocation() {
    JSONObject data = sfRequest("GET", "/ac/v2/rcs/rfc/findMyCar", null, true, true, null).getJSONObject("data");
    if (!sfStoreCarLocation(data, "Hyundai Find My Car")) sfFail("Hyundai returned no valid car coordinates. The previous location is retained with its original timestamp.");
    tasker.setVariable("SFDLocationVerified", sfValue("SFDCarVin"));
    return sfCompareLocation();
}
void sfLocationReport(String message) {
    tasker.setVariable("SFDLocationResult", message); tasker.setVariable("SFDLocationCheckedAt", Instant.ofEpochMilli(sfNow()).toString());
}
void sfClearLocation() {
    for (String name : new String[]{"SFDCarLat", "SFDCarLon", "SFDCarTime", "SFDCarTimeMillis", "SFDCarReadAt", "SFDCarVin", "SFDCarIdentity", "SFDCarSource", "SFDLocationVerified", "SFDLocationCheckedAt", "SFDPhoneTime", "SFDPhoneAccuracy", "SFDDistanceMeters"}) tasker.setVariable(name, "");
    tasker.setVariable("SFDAutoLocation", "0"); tasker.setVariable("SFDProximity", "Unknown");
    tasker.setVariable("SFDLocationResult", "Location cleared after changing the account/car. Use Read car GPS.");
}
