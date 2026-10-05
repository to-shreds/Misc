// Scene display preparation only. No login, network request or car command.
import java.io.File;
String sfGuiValue(String name, String fallback) {
    String value = tasker.getVariable(name);
    return value == null || value.length() == 0 || value.equals("%" + name) ? fallback : value;
}
String door = sfGuiValue("SFDDoorLock", "Unknown");
String engine = sfGuiValue("SFDEngine", "Unknown");
String climate = sfGuiValue("SFDClimate", "Unknown");
String timestamp = sfGuiValue("SFDVehicleTime", "Not read yet");
tasker.setVariable("SFDGuiStatus", "Doors: " + door + "\nEngine: " + engine + "\nClimate: " + climate + "\nVehicle timestamp: " + timestamp);
tasker.setVariable("SFDGuiResult", sfGuiValue("SFDResult", "No operation yet."));
tasker.setVariable("SFDGuiState", sfGuiValue("SFDState", "Not connected"));
tasker.setVariable("SFDGuiHttp", sfGuiValue("SFDLastHttp", "None"));
String email = sfGuiValue("SFDEmail", "");
String password = sfGuiValue("SFDPassword", "");
String pin = sfGuiValue("SFDPin", "");
tasker.setVariable("SFDGuiAccount", email.length() > 0 && password.length() > 0 && pin.length() == 4 ? "Account saved. Edit account to update it." : "Account not saved. Tap Edit account.");
String vin = sfGuiValue("SFDVin", "");
tasker.setVariable("SFDGuiVehicle", vin.length() > 7 ? "Selected vehicle: VIN ending " + vin.substring(vin.length() - 8) : "No vehicle selected. Connect, then choose if asked.");
String sfGuiPreset(String prefix, String name, String temperature, String defrost) {
    return name + ": " + sfGuiValue(prefix + "Temperature", temperature) + " F / " + sfGuiValue(prefix + "Duration", "10") + " min / defrost " + (sfGuiValue(prefix + "Defrost", defrost).equals("1") ? "on" : "off");
}
tasker.setVariable("SFDGuiClimate", sfGuiPreset("SFD", "Regular", "72", "0") + "\n\n" + sfGuiPreset("SFDCold", "Cold", "62", "0") + "\n\n" + sfGuiPreset("SFDHot", "Hot", "81", "1"));
tasker.setVariable("SFDGuiLocation", sfGuiValue("SFDLocationResult", "No comparison yet. Read car GPS, then compare with your phone.") + "\n\nLast comparison: " + sfGuiValue("SFDLocationCheckedAt", "Never"));
tasker.setVariable("SFDGuiLocationSchedule", "Periodic checks: " + (sfGuiValue("SFDAutoLocation", "0").equals("1") ? "on" : "off") + " / every " + sfGuiValue("SFDLocationHours", "1") + " hour(s)\nNear threshold: " + sfGuiValue("SFDNearMeters", "150") + " m");
boolean pending = new File(context.getNoBackupFilesDir(), "santa-fe-direct-pending.json").exists();
tasker.setVariable("SFDGuiPending", pending ? "Outcome unresolved. Check the command or physically check the car before resolving it." : "No unresolved command.");
return "DISPLAY READY";
