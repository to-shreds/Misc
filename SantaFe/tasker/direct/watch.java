// Signed Join messages. No transport credentials or Hyundai passwords in pushes.
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

byte[] sfWatchBytes(String hex) {
    if (!hex.matches("[0-9a-f]{64}")) sfFail("The watch pairing key is missing or invalid. Open SFD Watch Settings.");
    byte[] bytes = new byte[32];
    for (int i = 0; i < bytes.length; i++) bytes[i] = (byte)Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
    return bytes;
}
String sfWatchHex(byte[] bytes) {
    StringBuilder encoded = new StringBuilder();
    for (int i = 0; i < bytes.length; i++) encoded.append(String.format("%02x", new Object[]{Integer.valueOf(bytes[i] & 255)}));
    return encoded.toString();
}
String sfNewWatchKey() {
    byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes); return sfWatchHex(bytes);
}
String sfWatchSignature(String key, String message) {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(sfWatchBytes(key), "HmacSHA256"));
    return sfWatchHex(mac.doFinal(message.getBytes("US-ASCII")));
}
JSONObject sfAcceptWatch(String message) {
    if (!sfValue("SFDWatchEnabled").equals("1")) sfFail("Watch commands are disabled. Open SFD Watch Settings.");
    if (message == null || message.length() > 320 || !message.matches("sfd1\\|[a-z0-9_-]{12,64}\\|[0-9]{13}\\|(start|start_cold|start_hot|stop|lock|unlock|ping)\\|[01]\\|[0-9a-f]{64}"))
        sfFail("Invalid watch command. No request was sent.");
    String[] fields = message.split("\\|", -1);
    String content = message.substring(0, message.lastIndexOf('|'));
    String expected = sfWatchSignature(sfValue("SFDWatchKey"), content);
    if (!MessageDigest.isEqual(expected.getBytes("US-ASCII"), fields[5].getBytes("US-ASCII")))
        sfFail("Watch command authentication failed. No request was sent.");
    long now = sfNow(); long issued = Long.parseLong(fields[2]);
    if (issued < now - 90000 || issued > now + 10000) sfFail("Watch command expired or the phone clock changed. No request was sent.");
    boolean starts = fields[3].startsWith("start");
    if (starts != fields[4].equals("1")) sfFail("A start needs the watch's outdoor/safe-to-start confirmation. No request was sent.");
    File receipt = new File(context.getNoBackupFilesDir(), "santa-fe-direct-watch-seen.json");
    JSONArray seen = new JSONArray();
    if (receipt.exists()) {
        if (!receipt.isFile() || receipt.length() > 65536) sfFail("Watch receipt storage is invalid. No request was sent.");
        FileInputStream in = new FileInputStream(receipt); ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try { byte[] bytes = new byte[2048]; int count; while ((count = in.read(bytes)) != -1) { if (buffer.size() + count > 65536) sfFail("Watch receipt storage is invalid. No request was sent."); buffer.write(bytes, 0, count); } }
        finally { in.close(); }
        seen = new JSONArray(new String(buffer.toByteArray(), "UTF-8"));
    }
    JSONArray retained = new JSONArray();
    for (int i = 0; i < seen.length(); i++) {
        JSONObject previous = seen.getJSONObject(i);
        if (previous.getString("id").equals(fields[1])) sfFail("Duplicate watch command ignored. No request was sent.");
        if (previous.getLong("expires") >= now) retained.put(previous);
    }
    if (retained.length() >= 128) sfFail("Too many recent watch requests. Wait before trying again.");
    retained.put(new JSONObject().put("id", fields[1]).put("expires", issued + 100000));
    File temporary = new File(receipt.getPath() + ".tmp");
    FileOutputStream out = new FileOutputStream(temporary);
    try { out.write(retained.toString().getBytes("UTF-8")); out.getFD().sync(); } finally { out.close(); }
    if (!temporary.renameTo(receipt) || !receipt.isFile() || receipt.length() == 0) sfFail("Could not save the watch receipt. No request was sent.");
    return new JSONObject().put("command", fields[3]).put("safe", starts);
}
