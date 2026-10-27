package com.localfolderslideshow.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class MainActivity extends Activity {
    private static final int REQUEST_FOLDER = 1001;
    private static final String PREFS = "slideshow_settings";

    private Uri selectedFolder;
    private TextView folderLabel;
    private Spinner modeSpinner;
    private LinearLayout regexPanel;
    private EditText regexInput;
    private Spinner regexTargetSpinner;
    private Switch caseSensitiveSwitch;
    private Spinner intervalSpinner;
    private Spinner transitionSpinner;
    private Spinner durationSpinner;
    private Spinner fitSpinner;
    private Spinner sortSpinner;
    private Switch shuffleSwitch;
    private Switch loopSwitch;
    private Switch recursiveSwitch;

    private final String[] modeLabels = {"All images in selected folder", "Regex search"};
    private final String[] regexTargetLabels = {"File name only", "Relative path under search root"};
    private final String[] intervalLabels = {"1 second", "2 seconds", "3 seconds", "5 seconds", "8 seconds", "10 seconds", "15 seconds", "20 seconds", "30 seconds", "60 seconds"};
    private final long[] intervalValues = {1000, 2000, 3000, 5000, 8000, 10000, 15000, 20000, 30000, 60000};
    private final String[] transitionLabels = {"Fade", "Slide", "Zoom", "None"};
    private final String[] durationLabels = {"150 ms", "300 ms", "500 ms", "750 ms", "1 second", "1.5 seconds"};
    private final long[] durationValues = {150, 300, 500, 750, 1000, 1500};
    private final String[] fitLabels = {"Fit entire image", "Fill screen (crop)"};
    private final String[] sortLabels = {"Name A-Z", "Newest first", "Oldest first"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(18, 18, 18));
        getWindow().setNavigationBarColor(Color.rgb(18, 18, 18));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(18, 18, 18));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(28));
        scrollView.addView(root, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("Folder Slideshow");
        title.setTextSize(30);
        title.setTextColor(Color.WHITE);
        title.setPadding(0, 0, 0, dp(6));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Choose a local folder for this run, then show every image in it or search that folder with a regular expression. The selected folder and regex are not remembered after the app closes.");
        subtitle.setTextSize(15);
        subtitle.setTextColor(Color.rgb(190, 190, 190));
        subtitle.setPadding(0, 0, 0, dp(18));
        root.addView(subtitle);

        modeSpinner = addSpinnerSetting(root, "Slideshow source", modeLabels);

        Button chooseFolder = new Button(this);
        chooseFolder.setText("Choose folder / search root");
        chooseFolder.setOnClickListener(v -> chooseFolder());
        LinearLayout.LayoutParams folderButtonParams = fullWidth();
        folderButtonParams.topMargin = dp(14);
        root.addView(chooseFolder, folderButtonParams);

        folderLabel = new TextView(this);
        folderLabel.setText("No folder selected");
        folderLabel.setTextColor(Color.rgb(170, 170, 170));
        folderLabel.setTextSize(14);
        folderLabel.setPadding(dp(4), dp(8), dp(4), dp(4));
        root.addView(folderLabel);

        regexPanel = new LinearLayout(this);
        regexPanel.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams regexPanelParams = fullWidth();
        regexPanelParams.topMargin = dp(8);
        root.addView(regexPanel, regexPanelParams);

        TextView regexLabel = new TextView(this);
        regexLabel.setText("Regular expression");
        regexLabel.setTextColor(Color.WHITE);
        regexLabel.setTextSize(15);
        regexPanel.addView(regexLabel, fullWidth());

        regexInput = new EditText(this);
        regexInput.setHint("e.g. vacation.*2026|IMG_\\d+");
        regexInput.setHintTextColor(Color.rgb(125, 125, 125));
        regexInput.setTextColor(Color.WHITE);
        regexInput.setTextSize(15);
        regexInput.setSingleLine(true);
        regexInput.setTypeface(Typeface.MONOSPACE);
        regexInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        regexPanel.addView(regexInput, fullWidth());

        regexTargetSpinner = addSpinnerSetting(regexPanel, "Apply regex to", regexTargetLabels);
        caseSensitiveSwitch = addSwitch(regexPanel, "Case-sensitive regex", "Off means letters match regardless of case.");

        TextView regexHelp = new TextView(this);
        regexHelp.setText("The chosen folder is the search root. Regex uses search semantics, so an unanchored pattern may match anywhere in the filename or relative path. Add ^ and $ when you want a whole-string match.");
        regexHelp.setTextColor(Color.rgb(150, 150, 150));
        regexHelp.setTextSize(12);
        regexHelp.setPadding(dp(4), dp(8), dp(4), dp(4));
        regexPanel.addView(regexHelp, fullWidth());

        recursiveSwitch = addSwitch(root, "Include subfolders", "Also scan image files inside nested folders. For regex search, this controls how deeply the selected search root is scanned.");
        intervalSpinner = addSpinnerSetting(root, "Time per photo", intervalLabels);
        transitionSpinner = addSpinnerSetting(root, "Transition", transitionLabels);
        durationSpinner = addSpinnerSetting(root, "Transition duration", durationLabels);
        fitSpinner = addSpinnerSetting(root, "Image sizing", fitLabels);
        sortSpinner = addSpinnerSetting(root, "Base order", sortLabels);

        shuffleSwitch = addSwitch(root, "Shuffle / random order", "Randomizes the matched photos before playback and again on each loop.");
        loopSwitch = addSwitch(root, "Loop continuously", "Restart at the end instead of stopping.");

        Button start = new Button(this);
        start.setText("Start slideshow");
        LinearLayout.LayoutParams startParams = fullWidth();
        startParams.topMargin = dp(18);
        root.addView(start, startParams);
        start.setOnClickListener(v -> startSlideshow());

        TextView note = new TextView(this);
        note.setText("Supports image files exposed by Android's folder picker, including common JPEG, PNG, WebP, GIF, HEIC and other image formats supported by the device. Search stays within the folder you explicitly choose.");
        note.setTextColor(Color.rgb(145, 145, 145));
        note.setTextSize(12);
        note.setPadding(dp(4), dp(14), dp(4), 0);
        root.addView(note);

        setContentView(scrollView);
        restoreSettings();
        updateSearchUi();

        modeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateSearchUi();
                saveSettings();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        regexTargetSpinner.setOnItemSelectedListener(saveListener);
        intervalSpinner.setOnItemSelectedListener(saveListener);
        transitionSpinner.setOnItemSelectedListener(saveListener);
        durationSpinner.setOnItemSelectedListener(saveListener);
        fitSpinner.setOnItemSelectedListener(saveListener);
        sortSpinner.setOnItemSelectedListener(saveListener);
        caseSensitiveSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> saveSettings());
        shuffleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> saveSettings());
        loopSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> saveSettings());
        recursiveSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> saveSettings());
    }

    private final AdapterView.OnItemSelectedListener saveListener = new AdapterView.OnItemSelectedListener() {
        @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { saveSettings(); }
        @Override public void onNothingSelected(AdapterView<?> parent) { }
    };

    private void updateSearchUi() {
        if (regexPanel == null || modeSpinner == null) return;
        regexPanel.setVisibility(modeSpinner.getSelectedItemPosition() == 1 ? View.VISIBLE : View.GONE);
    }

    private void chooseFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_FOLDER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_FOLDER && resultCode == RESULT_OK && data != null && data.getData() != null) {
            selectedFolder = data.getData();
            String name = selectedFolder.getLastPathSegment();
            try {
                String docId = DocumentsContract.getTreeDocumentId(selectedFolder);
                int colon = docId.lastIndexOf(':');
                name = colon >= 0 && colon < docId.length() - 1 ? docId.substring(colon + 1) : docId;
                if (name == null || name.trim().isEmpty()) name = "Selected folder";
            } catch (Exception ignored) { }
            folderLabel.setText(name == null ? "Selected folder" : name);
            folderLabel.setTextColor(Color.rgb(110, 210, 140));
        }
    }

    private void startSlideshow() {
        if (selectedFolder == null) {
            Toast.makeText(this, "Choose a folder or search root first.", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean regexMode = modeSpinner.getSelectedItemPosition() == 1;
        String regex = regexInput.getText().toString();
        if (regexMode) {
            if (regex.trim().isEmpty()) {
                Toast.makeText(this, "Enter a regular expression first.", Toast.LENGTH_SHORT).show();
                regexInput.requestFocus();
                return;
            }
            try {
                int flags = caseSensitiveSwitch.isChecked() ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
                Pattern.compile(regex, flags);
            } catch (PatternSyntaxException e) {
                String detail = e.getDescription();
                Toast.makeText(this, "Invalid regex" + (detail == null ? "." : ": " + detail), Toast.LENGTH_LONG).show();
                regexInput.requestFocus();
                return;
            }
        }

        saveSettings();
        Intent intent = new Intent(this, SlideshowActivity.class);
        intent.putExtra("root_uri", selectedFolder.toString());
        intent.putExtra("search_mode", regexMode);
        intent.putExtra("regex", regex);
        intent.putExtra("regex_target", regexTargetSpinner.getSelectedItemPosition());
        intent.putExtra("regex_case_sensitive", caseSensitiveSwitch.isChecked());
        intent.putExtra("interval_ms", intervalValues[intervalSpinner.getSelectedItemPosition()]);
        intent.putExtra("transition", transitionLabels[transitionSpinner.getSelectedItemPosition()].toLowerCase());
        intent.putExtra("transition_ms", durationValues[durationSpinner.getSelectedItemPosition()]);
        intent.putExtra("fit_mode", fitSpinner.getSelectedItemPosition());
        intent.putExtra("sort_mode", sortSpinner.getSelectedItemPosition());
        intent.putExtra("shuffle", shuffleSwitch.isChecked());
        intent.putExtra("loop", loopSwitch.isChecked());
        intent.putExtra("recursive", recursiveSwitch.isChecked());
        startActivity(intent);
    }

    private Spinner addSpinnerSetting(LinearLayout root, String label, String[] choices) {
        TextView text = new TextView(this);
        text.setText(label);
        text.setTextColor(Color.WHITE);
        text.setTextSize(15);
        LinearLayout.LayoutParams textParams = fullWidth();
        textParams.topMargin = dp(12);
        root.addView(text, textParams);

        Spinner spinner = new Spinner(this, Spinner.MODE_DROPDOWN);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, choices) {
            @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                if (v instanceof TextView) ((TextView) v).setTextColor(Color.WHITE);
                return v;
            }
        };
        spinner.setAdapter(adapter);
        root.addView(spinner, fullWidth());
        return spinner;
    }

    private Switch addSwitch(LinearLayout root, String title, String detail) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams blockParams = fullWidth();
        blockParams.topMargin = dp(14);
        root.addView(block, blockParams);

        Switch sw = new Switch(this);
        sw.setText(title);
        sw.setTextColor(Color.WHITE);
        sw.setTextSize(15);
        block.addView(sw, fullWidth());

        TextView help = new TextView(this);
        help.setText(detail);
        help.setTextColor(Color.rgb(150, 150, 150));
        help.setTextSize(12);
        help.setPadding(dp(4), 0, dp(4), 0);
        block.addView(help, fullWidth());
        return sw;
    }

    private void restoreSettings() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        modeSpinner.setSelection(p.getInt("mode", 0));
        regexTargetSpinner.setSelection(p.getInt("regex_target", 0));
        caseSensitiveSwitch.setChecked(p.getBoolean("regex_case_sensitive", false));
        intervalSpinner.setSelection(p.getInt("interval", 3));
        transitionSpinner.setSelection(p.getInt("transition", 0));
        durationSpinner.setSelection(p.getInt("duration", 2));
        fitSpinner.setSelection(p.getInt("fit", 0));
        sortSpinner.setSelection(p.getInt("sort", 0));
        shuffleSwitch.setChecked(p.getBoolean("shuffle", true));
        loopSwitch.setChecked(p.getBoolean("loop", true));
        recursiveSwitch.setChecked(p.getBoolean("recursive", false));
    }

    private void saveSettings() {
        if (intervalSpinner == null || modeSpinner == null) return;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putInt("mode", modeSpinner.getSelectedItemPosition())
                .putInt("regex_target", regexTargetSpinner.getSelectedItemPosition())
                .putBoolean("regex_case_sensitive", caseSensitiveSwitch.isChecked())
                .putInt("interval", intervalSpinner.getSelectedItemPosition())
                .putInt("transition", transitionSpinner.getSelectedItemPosition())
                .putInt("duration", durationSpinner.getSelectedItemPosition())
                .putInt("fit", fitSpinner.getSelectedItemPosition())
                .putInt("sort", sortSpinner.getSelectedItemPosition())
                .putBoolean("shuffle", shuffleSwitch.isChecked())
                .putBoolean("loop", loopSwitch.isChecked())
                .putBoolean("recursive", recursiveSwitch.isChecked())
                .apply();
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
