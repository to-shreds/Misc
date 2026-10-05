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
tasker.setVariable("SFDGuiClimate", "Temperature: " + sfGuiValue("SFDTemperature", "72") + " F\nDuration: " + sfGuiValue("SFDDuration", "10") + " minutes\nDefrost: " + (sfGuiValue("SFDDefrost", "0").equals("1") ? "On" : "Off") + "\nSeat and steering-wheel heat: off");
boolean pending = new File(context.getNoBackupFilesDir(), "santa-fe-direct-pending.json").exists();
tasker.setVariable("SFDGuiPending", pending ? "Outcome unresolved. Check the command or physically check the car before resolving it." : "No unresolved command.");
return "DISPLAY READY";
