// Native dialogs. Tasker supplies its Activity and bundled RxJava2 library.
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.WindowManager;
import android.widget.*;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import java.util.function.Consumer;
import io.reactivex.subjects.SingleSubject;

String sfConfirm(String title, String message, String button) {
    signal = SingleSubject.create();
    tasker.doWithActivity(new Consumer() {
        accept(Object object) {
            final Activity activity = (Activity)object;
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            AlertDialog.Builder builder = new AlertDialog.Builder(activity).setTitle(title).setMessage(message).setCancelable(false);
            builder.setPositiveButton(button, new DialogInterface.OnClickListener() {
                onClick(DialogInterface dialog, int which) { signal.onSuccess("yes"); activity.finish(); }
            });
            builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                onClick(DialogInterface dialog, int which) { signal.onSuccess("no"); activity.finish(); }
            });
            builder.show();
        }
    });
    return (String)signal.blockingGet();
}
void sfMessage(String title, String message) {
    signal = SingleSubject.create();
    tasker.doWithActivity(new Consumer() {
        accept(Object object) {
            final Activity activity = (Activity)object;
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            new AlertDialog.Builder(activity).setTitle(title).setMessage(message).setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    onClick(DialogInterface dialog, int which) { signal.onSuccess("ok"); activity.finish(); }
                }).show();
        }
    });
    signal.blockingGet();
}
String sfChoose(String title, String[] choices, String[] values) {
    signal = SingleSubject.create();
    tasker.doWithActivity(new Consumer() {
        accept(Object object) {
            final Activity activity = (Activity)object;
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            AlertDialog.Builder builder = new AlertDialog.Builder(activity).setTitle(title).setCancelable(false);
            builder.setItems(choices, new DialogInterface.OnClickListener() {
                onClick(DialogInterface dialog, int which) { signal.onSuccess(values[which]); activity.finish(); }
            });
            builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                onClick(DialogInterface dialog, int which) { signal.onSuccess("cancel"); activity.finish(); }
            });
            builder.show();
        }
    });
    return (String)signal.blockingGet();
}
JSONObject sfAccountForm() {
    signal = SingleSubject.create();
    tasker.doWithActivity(new Consumer() {
        accept(Object object) {
            final Activity activity = (Activity)object;
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            LinearLayout layout = new LinearLayout(activity); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(24, 12, 24, 12);
            TextView explanation = new TextView(activity); explanation.setText("Enter once. Blank password or PIN keeps the saved value. Leave VIN blank to select the only active vehicle."); layout.addView(explanation);
            EditText email = new EditText(activity); email.setHint("MyHyundai email"); email.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS); email.setSingleLine(true); email.setText(sfValue("SFDEmail")); layout.addView(email);
            EditText password = new EditText(activity); password.setHint("Password"); password.setSingleLine(true); password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); password.setTransformationMethod(PasswordTransformationMethod.getInstance()); layout.addView(password);
            EditText pin = new EditText(activity); pin.setHint("Four-digit Bluelink PIN"); pin.setSingleLine(true); pin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD); pin.setTransformationMethod(PasswordTransformationMethod.getInstance()); layout.addView(pin);
            EditText vin = new EditText(activity); vin.setHint("VIN (optional)"); vin.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS); vin.setSingleLine(true); vin.setText(sfValue("SFDVin")); layout.addView(vin);
            ScrollView scroll = new ScrollView(activity); scroll.addView(layout);
            AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Santa Fe account").setView(scroll).setCancelable(false).setPositiveButton("Save", null)
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    onClick(DialogInterface dialog, int which) { signal.onSuccess(new JSONObject().put("cancel", true)); activity.finish(); }
                }).create();
            dialog.show();
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new android.view.View.OnClickListener() {
                onClick(android.view.View view) {
                    String enteredEmail = email.getText().toString().trim(); String enteredPassword = password.getText().toString(); String enteredPin = pin.getText().toString().trim();
                    String enteredVin = vin.getText().toString().trim().toUpperCase(Locale.US);
                    if (enteredPassword.length() == 0) enteredPassword = sfValue("SFDPassword");
                    if (enteredPin.length() == 0) enteredPin = sfValue("SFDPin");
                    if (enteredEmail.length() == 0 || enteredEmail.length() > 320 || enteredEmail.indexOf('\n') >= 0 || enteredEmail.indexOf('\r') >= 0) { email.setError("Enter your email"); return; }
                    if (enteredPassword.length() == 0 || enteredPassword.length() > 4096) { password.setError("Enter your password"); return; }
                    if (!enteredPin.matches("[0-9]{4}")) { pin.setError("Use four digits"); return; }
                    if (enteredVin.length() > 0 && !enteredVin.matches("[A-HJ-NPR-Z0-9]{17}")) { vin.setError("Use a 17-character VIN, or leave blank"); return; }
                    signal.onSuccess(new JSONObject().put("email", enteredEmail).put("password", enteredPassword).put("pin", enteredPin).put("vin", enteredVin));
                    password.setText(""); pin.setText(""); dialog.dismiss(); activity.finish();
                }
            });
        }
    });
    return (JSONObject)signal.blockingGet();
}
JSONObject sfClimateForm(String prefix, String title, int defaultTemperature, boolean defaultDefrost) {
    signal = SingleSubject.create();
    tasker.doWithActivity(new Consumer() {
        accept(Object object) {
            final Activity activity = (Activity)object;
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            LinearLayout layout = new LinearLayout(activity); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(24, 12, 24, 12);
            TextView explanation = new TextView(activity); explanation.setText("Temperature: 62 to 81 F. Duration: 1 to 10 minutes. Seats and steering-wheel heat remain off."); layout.addView(explanation);
            EditText temperature = new EditText(activity); temperature.setHint("Temperature (F)"); temperature.setInputType(InputType.TYPE_CLASS_NUMBER); temperature.setText(sfValue(prefix + "Temperature").length() == 0 ? String.valueOf(defaultTemperature) : sfValue(prefix + "Temperature")); layout.addView(temperature);
            EditText duration = new EditText(activity); duration.setHint("Minutes"); duration.setInputType(InputType.TYPE_CLASS_NUMBER); duration.setText(sfValue(prefix + "Duration").length() == 0 ? "10" : sfValue(prefix + "Duration")); layout.addView(duration);
            CheckBox defrost = new CheckBox(activity); defrost.setText("Defrost"); defrost.setChecked(sfValue(prefix + "Defrost").length() == 0 ? defaultDefrost : sfValue(prefix + "Defrost").equals("1")); layout.addView(defrost);
            AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(title).setView(layout).setCancelable(false).setPositiveButton("Save", null)
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    onClick(DialogInterface dialog, int which) { signal.onSuccess(new JSONObject().put("cancel", true)); activity.finish(); }
                }).create();
            dialog.show();
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new android.view.View.OnClickListener() {
                onClick(android.view.View view) {
                    int temp = 0; int minutes = 0;
                    try { temp = Integer.parseInt(temperature.getText().toString()); } catch (Exception ignored) {}
                    try { minutes = Integer.parseInt(duration.getText().toString()); } catch (Exception ignored) {}
                    if (temp < 62 || temp > 81) { temperature.setError("Use 62 to 81"); return; }
                    if (minutes < 1 || minutes > 10) { duration.setError("Use 1 to 10"); return; }
                    signal.onSuccess(new JSONObject().put("temperature", temp).put("duration", minutes).put("defrost", defrost.isChecked()));
                    dialog.dismiss(); activity.finish();
                }
            });
        }
    });
    return (JSONObject)signal.blockingGet();
}

JSONObject sfWatchForm() {
    signal = SingleSubject.create();
    final String existingKey = sfValue("SFDWatchKey");
    final String pairingKey = existingKey.matches("[0-9a-f]{64}") ? existingKey : sfNewWatchKey();
    tasker.doWithActivity(new Consumer() {
        accept(Object object) {
            final Activity activity = (Activity)object;
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            LinearLayout layout = new LinearLayout(activity); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(24, 12, 24, 12);
            TextView explanation = new TextView(activity); explanation.setText("Copy the pairing key into Santa Fe Watch's Zepp settings. Hyundai credentials stay in Tasker. Starts require confirmation on the watch. Test connection sends no car command."); layout.addView(explanation);
            CheckBox enabled = new CheckBox(activity); enabled.setText("Enable signed watch commands"); enabled.setChecked(sfValue("SFDWatchEnabled").equals("1")); layout.addView(enabled);
            Button copy = new Button(activity); copy.setText("Copy pairing key"); layout.addView(copy);
            copy.setOnClickListener(new android.view.View.OnClickListener() {
                onClick(android.view.View view) {
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager)activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Santa Fe watch pairing", pairingKey));
                    tasker.showToast("Pairing key copied. Save these settings before testing the watch.");
                }
            });
            new AlertDialog.Builder(activity).setTitle("Santa Fe watch settings").setView(layout).setCancelable(false)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    onClick(DialogInterface dialog, int which) { signal.onSuccess(new JSONObject().put("enabled", enabled.isChecked()).put("key", pairingKey)); activity.finish(); }
                }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    onClick(DialogInterface dialog, int which) { signal.onSuccess(new JSONObject().put("cancel", true)); activity.finish(); }
                }).show();
        }
    });
    return (JSONObject)signal.blockingGet();
}

JSONObject sfLocationForm() {
    signal = SingleSubject.create();
    tasker.doWithActivity(new Consumer() {
        accept(Object object) {
            final Activity activity = (Activity)object;
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            LinearLayout layout = new LinearLayout(activity); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(24, 12, 24, 12);
            TextView explanation = new TextView(activity); explanation.setText("Read car GPS successfully once before enabling. Checks only compare positions. Hyundai can return cached coordinates. Give Tasker precise/background location permission. Schedule uses an hourly heartbeat; checks may be delayed by Android."); layout.addView(explanation);
            CheckBox enabled = new CheckBox(activity); enabled.setText("Enable periodic location comparison"); enabled.setChecked(sfValue("SFDAutoLocation").equals("1")); layout.addView(enabled);
            EditText hours = new EditText(activity); hours.setHint("Check interval (1 to 24 hours)"); hours.setInputType(InputType.TYPE_CLASS_NUMBER); hours.setText(sfValue("SFDLocationHours").length() == 0 ? "1" : sfValue("SFDLocationHours")); layout.addView(hours);
            EditText meters = new EditText(activity); meters.setHint("Near threshold (25 to 5000 meters)"); meters.setInputType(InputType.TYPE_CLASS_NUMBER); meters.setText(sfValue("SFDNearMeters").length() == 0 ? "150" : sfValue("SFDNearMeters")); layout.addView(meters);
            ScrollView scroll = new ScrollView(activity); scroll.addView(layout);
            AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Santa Fe location settings").setView(scroll).setCancelable(false).setPositiveButton("Save", null)
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    onClick(DialogInterface dialog, int which) { signal.onSuccess(new JSONObject().put("cancel", true)); activity.finish(); }
                }).create();
            dialog.show();
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new android.view.View.OnClickListener() {
                onClick(android.view.View view) {
                    int interval = 0, threshold = 0;
                    try { interval = Integer.parseInt(hours.getText().toString()); } catch (Exception ignored) {}
                    try { threshold = Integer.parseInt(meters.getText().toString()); } catch (Exception ignored) {}
                    if (interval < 1 || interval > 24) { hours.setError("Use 1 to 24 hours"); return; }
                    if (threshold < 25 || threshold > 5000) { meters.setError("Use 25 to 5000 meters"); return; }
                    signal.onSuccess(new JSONObject().put("enabled", enabled.isChecked()).put("hours", interval).put("meters", threshold));
                    dialog.dismiss(); activity.finish();
                }
            });
        }
    });
    return (JSONObject)signal.blockingGet();
}
