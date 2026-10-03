package com.jon.calendarbridge;

import android.Manifest;
import com.jon.calendarbridge.core.MeetingClassifier;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A local setup and preview screen. Calendar operations always use the worker. */
public final class MainActivity extends Activity {
    private static final int PERMISSION_REQUEST = 41;
    private static final int INK = Color.rgb(30, 50, 70);
    private static final int MUTED = Color.rgb(84, 105, 123);
    private static final int BLUE = Color.rgb(55, 98, 135);
    private static final int BACKGROUND = Color.rgb(241, 245, 249);
    private static final int WARNING = Color.rgb(150, 72, 20);

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<BridgeApi.CalendarInfo> sourceChoices = new ArrayList<>();
    private final List<BridgeApi.CalendarInfo> targetChoices = new ArrayList<>();
    private final List<View> formViews = new ArrayList<>();

    private LinearLayout content;
    private LinearLayout permissionCard;
    private LinearLayout previewCard;
    private LinearLayout previewRows;
    private TextView statusText;
    private TextView errorText;
    private TextView previewSummary;
    private TextView calendarHelp;
    private Spinner sourceSpinner;
    private Spinner targetSpinner;
    private EditText domainsInput;
    private EditText pastInput;
    private EditText futureInput;
    private Button permissionButton;
    private Button previewButton;
    private Button startButton;
    private Button syncButton;
    private Button heldButton;
    private Switch autoSwitch;

    private boolean working;
    private boolean loadingForm;
    private boolean formLoaded;
    private boolean confirmed;
    private boolean adjustingSwitch;
    private boolean destroyed;
    private String lastFormSignature = "";
    private String previewSignature;
    private BridgeApi.Config configCache;
    private BridgeApi.Preview lastPreview;
    private int previewLimit = 150;

    private interface UiUpdate { void apply(); }
    private interface Work { UiUpdate run() throws Exception; }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildScreen();
        if (hasCalendarPermission()) {
            loadCalendars();
        } else {
            showPermissionRequired();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (!hasCalendarPermission()) {
            confirmed = false;
            clearPreview();
            showPermissionRequired();
            refreshControls();
        } else if ((!formLoaded || permissionCard.getVisibility() == View.VISIBLE) && !working) {
            loadCalendars();
        } else if (formLoaded && !working) {
            // Do not replace a partially edited form when returning from Settings.
            worker.execute(() -> {
                try {
                    BridgeApi.Config cfg = BridgeApi.load(getApplicationContext());
                    String currentStatus = BridgeApi.status(getApplicationContext());
                    runOnUiThread(() -> {
                        if (!destroyed && !working && confirmed) {
                            configCache = cfg;
                            confirmed = cfg.approved;
                            statusText.setText(currentStatus);
                            setAutoChecked(cfg.enabled && confirmed);
                            refreshControls();
                        }
                    });
                } catch (Exception ignored) {
                    // An explicit action will surface any operational error.
                }
            });
        }
    }

    @Override protected void onDestroy() {
        destroyed = true;
        worker.shutdown();
        super.onDestroy();
    }

    private void buildScreen() {
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(22), dp(18), dp(28));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        setContentView(scroll);

        TextView title = text("Calendar Bridge", 29, INK, true);
        content.addView(title);
        TextView subtitle = text("Know which meetings can fit around your travel.", 15, MUTED, false);
        subtitle.setPadding(0, dp(5), 0, dp(22));
        content.addView(subtitle);

        LinearLayout statusCard = card(content);
        statusCard.addView(text("Sync status", 17, INK, true));
        statusText = text("Choose your calendars, then preview the copies.", 14, MUTED, false);
        statusText.setPadding(0, dp(8), 0, dp(8));
        statusCard.addView(statusText);
        errorText = text("", 14, WARNING, false);
        errorText.setVisibility(View.GONE);
        statusCard.addView(errorText);
        autoSwitch = new Switch(this);
        autoSwitch.setText("Automatic sync");
        autoSwitch.setTextColor(INK);
        autoSwitch.setTextSize(16);
        autoSwitch.setPadding(0, dp(8), 0, dp(8));
        statusCard.addView(autoSwitch, fullWidth());
        autoSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (adjustingSwitch) return;
            if (working || !confirmed || !hasCalendarPermission()) {
                setAutoChecked(configCache != null && configCache.enabled && confirmed);
                return;
            }
            runWork(checked ? "Turning on automatic sync..." : "Pausing automatic sync...", () -> {
                BridgeApi.setEnabled(getApplicationContext(), checked);
                BridgeApi.Config cfg = BridgeApi.load(getApplicationContext());
                String status = BridgeApi.status(getApplicationContext());
                return () -> {
                    configCache = cfg;
                    statusText.setText(status);
                    setAutoChecked(cfg.enabled);
                };
            });
        });
        syncButton = button("Sync now", false);
        statusCard.addView(syncButton, buttonParams());
        syncButton.setOnClickListener(view -> syncNow());
        statusCard.addView(note("Background checks run when Android allows. Sync now checks immediately."));

        permissionCard = card(content);
        permissionCard.addView(text("Allow calendar access", 17, INK, true));
        permissionCard.addView(note("Read access checks your work schedule. Write access creates and updates copies in the Google calendar you select."));
        permissionButton = button("Grant calendar access", true);
        permissionCard.addView(permissionButton, buttonParams());
        permissionButton.setOnClickListener(view -> {
            if (hasCalendarPermission()) loadCalendars();
            else requestCalendarPermission();
        });

        LinearLayout calendars = card(content);
        calendars.addView(text("Your calendars", 19, INK, true));
        calendars.addView(label("Work calendar to read"));
        sourceSpinner = new Spinner(this);
        calendars.addView(sourceSpinner, spinnerParams());
        calendars.addView(label("Google calendar for copies"));
        targetSpinner = new Spinner(this);
        calendars.addView(targetSpinner, spinnerParams());
        calendarHelp = note("A separate Google calendar makes the copies easy to show or hide.");
        calendars.addView(calendarHelp);
        calendars.addView(note("Your work calendar stays read-only. Only copies created by this app are updated or removed."));
        formViews.add(sourceSpinner);
        formViews.add(targetSpinner);
        AdapterView.OnItemSelectedListener selectionListener = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                formChanged();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { formChanged(); }
        };
        sourceSpinner.setOnItemSelectedListener(selectionListener);
        targetSpinner.setOnItemSelectedListener(selectionListener);

        LinearLayout rules = card(content);
        rules.addView(text("Meeting rules", 19, INK, true));
        rules.addView(label("Internal email domains"));
        domainsInput = input("phiagroup.com", false);
        domainsInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        rules.addView(domainsInput, fullWidth());
        rules.addView(note("Separate additional domains with commas. Everyone attending must be internal. External or unclear meetings are blocked."));
        rules.addView(note("Titles containing TENT are always blocked, even if all current attendees are internal."));
        rules.addView(note("Car-compatible meetings still count as busy. They may fit during travel, but are not time available to play."));
        rules.addView(note("Events marked free in Outlook stay free, unless TENT or your classification choice blocks them."));

        LinearLayout range = new LinearLayout(this);
        range.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout pastColumn = new LinearLayout(this);
        pastColumn.setOrientation(LinearLayout.VERTICAL);
        LinearLayout futureColumn = new LinearLayout(this);
        futureColumn.setOrientation(LinearLayout.VERTICAL);
        pastColumn.addView(label("Past days"));
        pastInput = input("30", true);
        pastColumn.addView(pastInput, fullWidth());
        futureColumn.addView(label("Future months"));
        futureInput = input("12", true);
        futureColumn.addView(futureInput, fullWidth());
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        half.setMargins(0, 0, dp(8), 0);
        range.addView(pastColumn, half);
        LinearLayout.LayoutParams otherHalf = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        otherHalf.setMargins(dp(8), 0, 0, 0);
        range.addView(futureColumn, otherHalf);
        rules.addView(range, fullWidth());
        rules.addView(note("The date range moves forward automatically. Past days: 0 to 365. Future months: 1 to 24."));
        formViews.add(domainsInput);
        formViews.add(pastInput);
        formViews.add(futureInput);
        TextWatcher changes = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { formChanged(); }
            @Override public void afterTextChanged(Editable s) { }
        };
        domainsInput.addTextChangedListener(changes);
        pastInput.addTextChangedListener(changes);
        futureInput.addTextChangedListener(changes);

        previewButton = button("Preview copies", true);
        rules.addView(previewButton, buttonParams());
        previewButton.setOnClickListener(view -> preview());

        previewCard = card(content);
        previewCard.setVisibility(View.GONE);
        previewCard.addView(text("Review before syncing", 19, INK, true));
        previewSummary = text("", 14, MUTED, false);
        previewSummary.setPadding(0, dp(10), 0, dp(10));
        previewCard.addView(previewSummary);
        startButton = button("Start syncing", true);
        previewCard.addView(startButton, buttonParams());
        startButton.setOnClickListener(view -> startSyncing());
        heldButton = button("Review held removals", false);
        heldButton.setVisibility(View.GONE);
        previewCard.addView(heldButton, buttonParams());
        heldButton.setOnClickListener(view -> reviewHeldRemovals());
        previewCard.addView(note("Tap a meeting to change its classification. Copies include its title, time, and classification. Attendee addresses and descriptions stay on your phone."));
        previewRows = new LinearLayout(this);
        previewRows.setOrientation(LinearLayout.VERTICAL);
        previewCard.addView(previewRows, fullWidth());

        LinearLayout practical = card(content);
        practical.addView(text("Keep sync reliable", 17, INK, true));
        practical.addView(note("Keep Outlook's calendar sync on. On Samsung, allow this app to run in the background and keep it out of sleeping apps if updates are delayed."));
        Button battery = button("Battery settings", false);
        practical.addView(battery, buttonParams());
        battery.setOnClickListener(view -> openBatterySettings());
        practical.addView(note("Google copies will also appear in a mixed agenda. Hide the destination calendar in aCalendar if you want to avoid seeing each work meeting twice."));
        practical.addView(note("Changing either calendar leaves copies in the previous destination untouched. A dedicated destination calendar makes those older copies easy to manage."));
        refreshControls();
    }

    private void loadCalendars() {
        runWork("Reading calendars on this phone...", () -> {
            BridgeApi.Config cfg = BridgeApi.load(getApplicationContext());
            List<BridgeApi.CalendarInfo> available = BridgeApi.calendars(getApplicationContext());
            String status = BridgeApi.status(getApplicationContext());
            return () -> {
                loadingForm = true;
                configCache = cfg;
                sourceChoices.clear();
                sourceChoices.addAll(available);
                targetChoices.clear();
                for (BridgeApi.CalendarInfo calendar : available) {
                    if (calendar.destinationEligible) targetChoices.add(calendar);
                }
                configureSpinner(sourceSpinner, sourceChoices, "Choose work calendar", cfg.sourceId, cfg.sourceIdentity);
                configureSpinner(targetSpinner, targetChoices, "Choose Google calendar", cfg.targetId, cfg.targetIdentity);
                domainsInput.setText(cfg.domains);
                pastInput.setText(String.valueOf(cfg.pastDays));
                futureInput.setText(String.valueOf(cfg.futureMonths));
                loadingForm = false;
                formLoaded = true;
                lastFormSignature = formSignature();
                confirmed = cfg.approved && selected(sourceSpinner, sourceChoices) != null
                        && selected(targetSpinner, targetChoices) != null;
                setAutoChecked(cfg.enabled && confirmed);
                permissionCard.setVisibility(View.GONE);
                statusText.setText(status);
                if (sourceChoices.isEmpty()) {
                    calendarHelp.setText("No calendars are available. Enable Outlook calendar sync, then reopen this app.");
                } else if (targetChoices.isEmpty()) {
                    calendarHelp.setText("No writable, syncing Google calendar is available. Add your Google account to this phone, enable calendar sync, and reopen this app.");
                } else {
                    calendarHelp.setText("A separate Google calendar makes the copies easy to show or hide.");
                }
                if (cfg.approved && !confirmed) {
                    statusText.setText("A saved calendar is no longer available. Choose both calendars and preview again.");
                    worker.execute(() -> BridgeApi.setEnabled(getApplicationContext(), false));
                }
            };
        });
    }

    private void configureSpinner(Spinner spinner, List<BridgeApi.CalendarInfo> choices,
                                  String placeholder, long savedId, String savedIdentity) {
        List<String> labels = new ArrayList<>();
        labels.add(placeholder);
        int selection = 0;
        for (int i = 0; i < choices.size(); i++) {
            BridgeApi.CalendarInfo c = choices.get(i);
            String account = c.account == null ? "" : c.account;
            labels.add(c.label + (account.isEmpty() ? "" : "\n" + account));
            if (c.id == savedId && c.identity.equals(savedIdentity)) selection = i + 1;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, labels) {
            private View multiline(View view) {
                TextView label = (TextView) view;
                label.setSingleLine(false);
                label.setMaxLines(3);
                label.setTextSize(14);
                label.setTextColor(INK);
                label.setPadding(dp(8), dp(6), dp(8), dp(6));
                return label;
            }
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                return multiline(super.getView(position, convertView, parent));
            }
            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                return multiline(super.getDropDownView(position, convertView, parent));
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(selection);
    }

    private BridgeApi.CalendarInfo selected(Spinner spinner, List<BridgeApi.CalendarInfo> choices) {
        int index = spinner.getSelectedItemPosition() - 1;
        return index >= 0 && index < choices.size() ? choices.get(index) : null;
    }

    private void formChanged() {
        if (loadingForm || !formLoaded || working) return;
        String current = formSignature();
        if (current.equals(lastFormSignature)) return;
        lastFormSignature = current;
        confirmed = false;
        clearPreview();
        setAutoChecked(false);
        if (configCache != null) {
            configCache.enabled = false;
            configCache.approved = false;
        }
        clearError();
        statusText.setText("Settings changed. Automatic sync is paused. Preview the copies before starting again.");
        BridgeApi.Config draft = null;
        try { draft = readForm(); } catch (IllegalArgumentException ignored) { }
        final BridgeApi.Config snapshot = draft;
        // One ordered queue prevents a settings change racing a later preview or sync.
        worker.execute(() -> {
            try {
                BridgeApi.setEnabled(getApplicationContext(), false);
                if (snapshot != null) BridgeApi.saveConfig(getApplicationContext(), snapshot);
            } catch (Exception failure) {
                runOnUiThread(() -> {
                    if (!destroyed && !working && current.equals(formSignature())) {
                        showError("Could not save settings. " + describe(failure));
                    }
                });
            }
        });
        refreshControls();
    }

    private String formSignature() {
        BridgeApi.CalendarInfo source = selected(sourceSpinner, sourceChoices);
        BridgeApi.CalendarInfo target = selected(targetSpinner, targetChoices);
        return (source == null ? "none" : source.identity) + "\u0000"
                + (target == null ? "none" : target.identity) + "\u0000"
                + domainsInput.getText().toString() + "\u0000"
                + pastInput.getText().toString() + "\u0000" + futureInput.getText().toString();
    }

    private BridgeApi.Config readForm() {
        BridgeApi.CalendarInfo source = selected(sourceSpinner, sourceChoices);
        BridgeApi.CalendarInfo target = selected(targetSpinner, targetChoices);
        if (source == null) throw new IllegalArgumentException("Choose the work calendar to read.");
        if (target == null) throw new IllegalArgumentException("Choose a writable Google calendar for the copies.");
        if (source.id == target.id) throw new IllegalArgumentException("Choose different source and destination calendars.");
        String domains = domainsInput.getText().toString().trim();
        if (domains.isEmpty()) throw new IllegalArgumentException("Enter at least one internal email domain, such as phiagroup.com.");
        int past = number(pastInput, 0, 365, "Past days");
        int future = number(futureInput, 1, 24, "Future months");
        BridgeApi.Config cfg = new BridgeApi.Config();
        cfg.sourceId = source.id;
        cfg.targetId = target.id;
        cfg.sourceIdentity = source.identity;
        cfg.targetIdentity = target.identity;
        cfg.domains = domains;
        cfg.pastDays = past;
        cfg.futureMonths = future;
        cfg.enabled = false;
        cfg.approved = false;
        return cfg;
    }

    private int number(EditText field, int min, int max, String name) {
        try {
            int value = Integer.parseInt(field.getText().toString().trim());
            if (value >= min && value <= max) return value;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException(name + " must be a whole number from " + min + " to " + max + ".");
    }

    private void preview() {
        if (!hasCalendarPermission()) { showPermissionRequired(); return; }
        final BridgeApi.Config draft;
        try { draft = readForm(); } catch (IllegalArgumentException failure) {
            showError(failure.getMessage());
            return;
        }
        final String signature = formSignature();
        confirmed = false;
        clearPreview();
        setAutoChecked(false);
        runWork("Checking meetings and preparing your preview...", () -> {
            BridgeApi.setEnabled(getApplicationContext(), false);
            BridgeApi.saveConfig(getApplicationContext(), draft);
            BridgeApi.Preview result = BridgeApi.preview(getApplicationContext());
            BridgeApi.Config cfg = BridgeApi.load(getApplicationContext());
            return () -> {
                configCache = cfg;
                if (!signature.equals(formSignature())) {
                    statusText.setText("Settings changed. Preview again before syncing.");
                    return;
                }
                previewSignature = signature;
                lastPreview = result;
                renderPreview();
                statusText.setText("Preview ready. No calendar copies were changed. Review it, then tap Start syncing.");
            };
        });
    }

    private void startSyncing() {
        if (!previewIsCurrent()) {
            showError("Preview the current settings before starting sync.");
            return;
        }
        if (!hasCalendarPermission()) { showPermissionRequired(); return; }
        final String signature = formSignature();
        runWork("Creating and updating your Google copies...", () -> {
            String message = BridgeApi.approveAndSync(getApplicationContext());
            BridgeApi.Config cfg = BridgeApi.load(getApplicationContext());
            BridgeApi.Preview fresh = BridgeApi.preview(getApplicationContext());
            return () -> {
                configCache = cfg;
                confirmed = cfg.approved && signature.equals(formSignature());
                setAutoChecked(cfg.enabled && confirmed);
                statusText.setText(message);
                lastPreview = fresh;
                previewSignature = signature;
                renderPreview();
            };
        });
    }

    private void syncNow() {
        if (!confirmed || !hasCalendarPermission()) return;
        final String signature = formSignature();
        runWork("Checking and syncing your calendar copies...", () -> {
            String message = BridgeApi.sync(getApplicationContext());
            BridgeApi.Config cfg = BridgeApi.load(getApplicationContext());
            BridgeApi.Preview fresh = BridgeApi.preview(getApplicationContext());
            return () -> {
                configCache = cfg;
                confirmed = cfg.approved && signature.equals(formSignature());
                setAutoChecked(cfg.enabled && confirmed);
                statusText.setText(message);
                lastPreview = fresh;
                previewSignature = signature;
                renderPreview();
            };
        });
    }

    private boolean previewIsCurrent() {
        return lastPreview != null && previewSignature != null
                && previewSignature.equals(formSignature());
    }

    private void clearPreview() {
        lastPreview = null;
        previewLimit = 150;
        previewSignature = null;
        if (previewCard != null) previewCard.setVisibility(View.GONE);
        if (previewRows != null) previewRows.removeAllViews();
    }

    private void renderPreview() {
        if (lastPreview == null) return;
        previewCard.setVisibility(View.VISIBLE);
        int count = lastPreview.events.size();
        int shown = Math.min(count, previewLimit);
        String summary = count + (count == 1 ? " meeting" : " meetings") + " in the date range.\n"
                + lastPreview.creates + " to add, " + lastPreview.updates + " to update, "
                + lastPreview.deletes + " to remove.\n"
                + lastPreview.held + " removals held for review.";
        if (lastPreview.note != null && !lastPreview.note.isEmpty()) summary += "\n\n" + lastPreview.note;
        if (count > shown) summary += "\n\nShowing the first " + shown + " of " + count + " meetings. Sync covers all " + count + ".";
        previewSummary.setText(summary);
        startButton.setVisibility(confirmed ? View.GONE : View.VISIBLE);
        heldButton.setVisibility(lastPreview.held > 0 && configCache != null && configCache.approved
                ? View.VISIBLE : View.GONE);
        previewRows.removeAllViews();
        if (count == 0) {
            previewRows.addView(note("No meetings were found in this date range. If Outlook is still syncing, wait and preview again."));
        }
        for (int i = 0; i < shown; i++) {
            BridgeApi.EventView event = lastPreview.events.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(13), dp(12), dp(13), dp(12));
            row.setBackground(rounded(event.free ? Color.rgb(239, 247, 235)
                    : event.carCompatible ? Color.rgb(236, 246, 247) : Color.rgb(246, 247, 250), 10));
            LinearLayout.LayoutParams params = fullWidth();
            params.setMargins(0, dp(9), 0, 0);
            previewRows.addView(row, params);
            row.addView(text(event.title == null || event.title.trim().isEmpty() ? "Untitled meeting" : event.title, 16, INK, true));
            TextView when = text(eventTime(event), 13, MUTED, false);
            when.setPadding(0, dp(4), 0, dp(7));
            row.addView(when);
            row.addView(text(event.free ? "INFORMATIONAL / FREE" : event.carCompatible ? "CAR-COMPATIBLE" : "BLOCKED", 12,
                    event.free ? Color.rgb(69, 109, 50) : event.carCompatible ? Color.rgb(23, 104, 111) : BLUE, true));
            if (event.reason != null && !event.reason.isEmpty()) row.addView(text(event.reason, 13, MUTED, false));
            if (event.override != null && !"AUTO".equals(event.override) && !event.override.isEmpty()) {
                row.addView(text("Your choice: " + ("CAR".equals(event.override) ? "car-compatible" : "blocked"), 12, MUTED, false));
            }
            row.setContentDescription((event.title == null ? "Untitled meeting" : event.title)
                    + ", " + eventTime(event) + ", " + (event.free ? "informational, free"
                    : event.carCompatible ? "car-compatible" : "blocked") + ". Tap to change classification.");
            row.setFocusable(true);
            row.setClickable(true);
            row.setOnClickListener(view -> { if (!working) chooseOverride(event); });
        }
        if(shown < count) {
            Button more = button("Show next " + Math.min(150,count-shown) + " meetings",false);
            previewRows.addView(more,buttonParams());
            more.setOnClickListener(view -> { if(!working) { previewLimit += 150; renderPreview(); } });
        }
    }

    private void chooseOverride(BridgeApi.EventView event) {
        boolean tent = MeetingClassifier.isTentTitle(event.title);
        final String[] modes = tent ? new String[]{"AUTO", "BLOCK"} : new String[]{"AUTO", "CAR", "BLOCK"};
        String[] labels = tent
                ? new String[]{"Automatic: TENT is always blocked", "Always blocked"}
                : new String[]{"Automatic classification", "Car-compatible", "Blocked"};
        new AlertDialog.Builder(this)
                .setTitle(tent ? "TENT meetings stay blocked" : "Meeting classification")
                .setItems(labels, (dialog, which) -> applyOverride(event.key, modes[which]))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void applyOverride(String key, String mode) {
        if (!previewIsCurrent()) { showError("Preview again before changing a meeting's classification."); return; }
        final String signature = formSignature();
        confirmed = false;
        setAutoChecked(false);
        clearPreview();
        runWork("Applying your choice and refreshing the preview...", () -> {
            BridgeApi.setEnabled(getApplicationContext(), false);
            BridgeApi.setOverride(getApplicationContext(), key, mode);
            BridgeApi.Preview fresh = BridgeApi.preview(getApplicationContext());
            BridgeApi.Config cfg = BridgeApi.load(getApplicationContext());
            return () -> {
                configCache = cfg;
                lastPreview = fresh;
                previewSignature = signature;
                renderPreview();
                statusText.setText("Classification changed. Review the updated preview, then tap Start syncing.");
            };
        });
    }

    private void reviewHeldRemovals() {
        if (!previewIsCurrent() || configCache == null || !configCache.approved || lastPreview.held == 0) return;
        StringBuilder message = new StringBuilder("These app-created copies are absent from the current work calendar snapshot. Outlook may not have finished syncing.\n\n");
        List<String> titles = lastPreview.heldTitles;
        if (titles != null && !titles.isEmpty()) {
            int shown = titles.size();
            for (int i = 0; i < shown; i++) message.append("• ").append(titles.get(i)).append('\n');
            if (titles.size() > shown) message.append("And ").append(titles.size() - shown).append(" more copies.\n");
            message.append('\n');
        }
        message.append("Remove the held copies only if you are sure the original meetings were deleted. The app will check the source again before removing them.");
        new AlertDialog.Builder(this)
                .setTitle("Review " + lastPreview.held + " held removals")
                .setMessage(message.toString())
                .setNegativeButton("Keep copies", null)
                .setPositiveButton("Remove held copies", (dialog, which) -> {
                    if (!previewIsCurrent() || configCache == null || !configCache.approved) {
                        showError("Preview again before reviewing removals.");
                        return;
                    }
                    final String signature = formSignature();
                    runWork("Rechecking the source and removing reviewed copies...", () -> {
                        String result = BridgeApi.reviewedRemovals(getApplicationContext());
                        BridgeApi.Preview fresh = BridgeApi.preview(getApplicationContext());
                        return () -> {
                            statusText.setText(result);
                            lastPreview = fresh;
                            previewSignature = signature;
                            renderPreview();
                        };
                    });
                }).show();
    }

    private String eventTime(BridgeApi.EventView event) {
        SimpleDateFormat day = new SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault());
        if (event.allDay) {
            day.setTimeZone(TimeZone.getTimeZone("UTC"));
            String begin = day.format(new Date(event.begin));
            String end = day.format(new Date(Math.max(event.begin, event.end - 1)));
            return begin.equals(end) ? begin + " · All day" : begin + " to " + end + " · All day";
        }
        SimpleDateFormat time = new SimpleDateFormat("h:mm a", Locale.getDefault());
        String beginDay = day.format(new Date(event.begin));
        String endDay = day.format(new Date(event.end));
        return beginDay + " · " + time.format(new Date(event.begin)) + " to "
                + (beginDay.equals(endDay) ? "" : endDay + " ") + time.format(new Date(event.end));
    }

    private void runWork(String busyMessage, Work action) {
        if (working || destroyed) return;
        working = true;
        clearError();
        statusText.setText(busyMessage);
        refreshControls();
        worker.execute(() -> {
            try {
                UiUpdate update = action.run();
                runOnUiThread(() -> {
                    if (destroyed) return;
                    working = false;
                    update.apply();
                    refreshControls();
                });
            } catch (Exception failure) {
                BridgeApi.Config afterFailure = null;
                try {
                    BridgeApi.setEnabled(getApplicationContext(), false);
                    afterFailure = BridgeApi.load(getApplicationContext());
                } catch (Exception ignored) { }
                final BridgeApi.Config failedConfig = afterFailure;
                runOnUiThread(() -> {
                    if (destroyed) return;
                    working = false;
                    configCache = failedConfig;
                    confirmed = false;
                    clearPreview();
                    setAutoChecked(false);
                    statusText.setText("That step did not finish. Sync is paused. Your source calendar was not changed.");
                    showError(describe(failure));
                    if (!formLoaded && hasCalendarPermission()) {
                        permissionCard.setVisibility(View.VISIBLE);
                        permissionButton.setText("Reload calendars");
                    }
                    refreshControls();
                });
            }
        });
    }

    private void refreshControls() {
        boolean ready = hasCalendarPermission() && formLoaded && !working;
        for (View input : formViews) input.setEnabled(ready);
        previewButton.setEnabled(ready && !sourceChoices.isEmpty() && !targetChoices.isEmpty());
        startButton.setEnabled(ready && previewIsCurrent());
        syncButton.setEnabled(ready && confirmed);
        autoSwitch.setEnabled(ready && confirmed);
        permissionButton.setEnabled(!working);
        heldButton.setEnabled(ready && previewIsCurrent() && configCache != null && configCache.approved);
        if (previewRows != null) {
            for (int i = 0; i < previewRows.getChildCount(); i++) previewRows.getChildAt(i).setEnabled(!working);
        }
    }

    private void setAutoChecked(boolean checked) {
        adjustingSwitch = true;
        autoSwitch.setChecked(checked);
        adjustingSwitch = false;
    }

    private boolean hasCalendarPermission() {
        return checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    private void showPermissionRequired() {
        permissionCard.setVisibility(View.VISIBLE);
        permissionButton.setText("Grant calendar access");
        statusText.setText("Calendar access is needed before previewing or syncing.");
        setAutoChecked(false);
    }

    private void requestCalendarPermission() {
        boolean blocked = getPreferences(MODE_PRIVATE).getBoolean("askedCalendar", false)
                && !shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR)
                && !shouldShowRequestPermissionRationale(Manifest.permission.WRITE_CALENDAR);
        if (blocked && !hasCalendarPermission()) {
            new AlertDialog.Builder(this)
                    .setTitle("Enable calendar access")
                    .setMessage("Open app settings, choose Permissions, and allow Calendar access. Then return here.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("App settings", (dialog, which) -> openAppSettings())
                    .show();
            return;
        }
        getPreferences(MODE_PRIVATE).edit().putBoolean("askedCalendar", true).apply();
        requestPermissions(new String[]{Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR}, PERMISSION_REQUEST);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == PERMISSION_REQUEST) {
            if (hasCalendarPermission()) loadCalendars();
            else {
                showPermissionRequired();
                showError("Calendar access was not granted. Use Grant calendar access to try again.");
                refreshControls();
            }
        }
    }

    private void openBatterySettings() {
        try { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }
        catch (ActivityNotFoundException failure) { openAppSettings(); }
    }

    private void openAppSettings() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
    }

    private void clearError() { errorText.setVisibility(View.GONE); }

    private void showError(String message) {
        errorText.setText(message == null || message.trim().isEmpty() ? "Please try again." : message);
        errorText.setVisibility(View.VISIBLE);
        errorText.announceForAccessibility(errorText.getText());
    }

    private String describe(Exception failure) {
        if (failure instanceof SecurityException) return "Calendar access is unavailable. Check this app's Calendar permission, then try again.";
        String message = failure.getMessage();
        if (message == null || message.trim().isEmpty()) return "Could not finish. Check calendar permissions and Outlook and Google calendar sync, then try again.";
        message = message.replace('\u2014', '-');
        return message.length() > 450 ? message.substring(0, 450) + "..." : message;
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(18), dp(18), dp(18));
        box.setBackground(rounded(Color.WHITE, 17));
        box.setElevation(dp(1));
        LinearLayout.LayoutParams params = fullWidth();
        params.setMargins(0, 0, 0, dp(15));
        parent.addView(box, params);
        return box;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(dp(2), 1);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private TextView note(String value) {
        TextView view = text(value, 13, MUTED, false);
        view.setPadding(0, dp(9), 0, dp(2));
        return view;
    }

    private TextView label(String value) {
        TextView view = text(value, 14, INK, true);
        view.setPadding(0, dp(15), 0, dp(7));
        return view;
    }

    private EditText input(String hint, boolean numeric) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setTextColor(INK);
        field.setTextSize(16);
        field.setSingleLine(true);
        field.setPadding(dp(12), dp(10), dp(12), dp(10));
        field.setBackground(rounded(BACKGROUND, 9));
        if (numeric) field.setInputType(InputType.TYPE_CLASS_NUMBER);
        return field;
    }

    private Button button(String value, boolean primary) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(primary ? Color.WHITE : BLUE);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primary ? BLUE : Color.rgb(230, 238, 245)));
        button.setMinHeight(dp(48));
        button.setGravity(Gravity.CENTER);
        return button;
    }

    private GradientDrawable rounded(int fill, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = fullWidth();
        params.setMargins(0, dp(10), 0, 0);
        return params;
    }

    private LinearLayout.LayoutParams spinnerParams() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(65));
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
