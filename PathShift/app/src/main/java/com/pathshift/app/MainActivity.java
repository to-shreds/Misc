package com.pathshift.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Native UI. No analytics, network calls, thumbnails, or file content reads. */
public class MainActivity extends Activity {
    private static final int BG = Color.rgb(13, 17, 23);
    private static final int PANEL = Color.rgb(24, 30, 39);
    private static final int BORDER = Color.rgb(48, 57, 70);
    private static final int FG = Color.rgb(236, 241, 246);
    private static final int MUTED = Color.rgb(154, 167, 180);
    private static final int ACCENT = Color.rgb(109, 225, 201);
    private static final int DANGER = Color.rgb(255, 165, 145);

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancelScan = new AtomicBoolean(false);
    private boolean busy = false;
    private enum Mode { FIND, RENAME, PRESETS }
    private Mode mode = Mode.RENAME;
    private boolean viewingPreview = false;
    private boolean viewingFindResults = false;
    private final RenameCore.Options findOptions = new RenameCore.Options();
    private String searchValue = "";
    private boolean searchRegex = true;
    private List<RenameCore.Entry> matches = new ArrayList<>();
    private List<RenameCore.Entry> visibleMatches = new ArrayList<>();
    private int pathsScanned = 0;
    private long filterRevision = 0;
    private MatchAdapter matchAdapter;
    private TextView matchCount;
    private Switch switchRegex;
    private File folder;
    private RenameCore.Options options = new RenameCore.Options();
    private PresetCore.Kind presetKind = null; // null means recipe chooser
    private PresetCore.Settings presetSettings = new PresetCore.Settings();
    private boolean viewingPresetPreview = false;
    private List<RenameCore.Entry> presetSnapshot = new ArrayList<>();
    private List<PresetCore.Proposal> presetProposals = new ArrayList<>();
    private PresetCore.Plan presetPlan;
    private PresetAdapter presetAdapter;
    private Button presetSaveButton, presetFixButton, presetCollisionsButton;
    private TextView presetSummary, presetProblem, presetDestinationLabel;
    private EditText presetFolderInput, presetFilterInput, presetTextInput, presetReplacementInput,
        presetStartInput, presetDigitsInput;
    private Switch presetRecursiveSwitch, presetPreserveSwitch, presetIgnoreSwitch,
        presetRegexSwitch, presetPathSwitch;
    private List<RenameCore.Entry> snapshot = new ArrayList<>();
    private List<RenameCore.Candidate> candidates = new ArrayList<>();
    private RenameCore.Plan currentPlan;
    private long planRevision = 0;

    private EditText findInput, replaceInput;
    private RadioGroup scopeGroup, replacementModeGroup;
    private Switch switchFolders, switchFiles, switchRecursive, switchCase, switchAll;
    private LinearLayout rootView;
    private TextView summary, issue, folderStatus;
    private Button saveButton, previewButton, allPathsButton, collisionButton, autoNumberButton;
    private ListView resultsList;
    private CandidateAdapter adapter;
    private String findValue = "";
    private String replaceValue = "";

    { findOptions.includeFolders = true; findOptions.ignoreCase = true; }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        String previous = getPreferences(MODE_PRIVATE).getString("folder", "");
        folder = previous.isEmpty() ? Environment.getExternalStorageDirectory() : new File(previous);
        if (!folder.isDirectory()) folder = Environment.getExternalStorageDirectory();
        showEditor();
    }

    @Override protected void onResume() {
        super.onResume();
        if (folderStatus != null) updateFolderStatus();
    }

    @Override public void onBackPressed() {
        if (busy) { Toast.makeText(this, "Finish the current operation first.", Toast.LENGTH_SHORT).show(); return; }
        if (viewingPresetPreview) { showPresetEditor(); return; }
        if (mode == Mode.PRESETS && presetKind != null) {
            rememberPresetForm(); presetKind = null; showPresetsHome(); return;
        }
        if (viewingPreview || viewingFindResults) { showEditor(); return; }
        super.onBackPressed();
    }

    private boolean hasFileAccess() {
        return Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager();
    }

    private void requestAccess() {
        if (hasFileAccess()) { updateFolderStatus(); return; }
        new AlertDialog.Builder(this)
            .setTitle("Allow file management")
            .setMessage("PathShift needs Android's All files access permission to rename files and move folders throughout the location you choose. It works entirely on your device. It does not open file contents or use the internet. Some protected Android folders remain unavailable.")
            .setNegativeButton("Not now", null)
            .setPositiveButton("Open settings", (d, w) -> {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception ex) {
                    startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                }
            }).show();
    }

    /** Keep text and search options when the user switches modes. */
    private void rememberEditor() {
        if (mode == Mode.PRESETS) { rememberPresetForm(); return; }
        if (findInput == null || scopeGroup == null) return;
        RenameCore.Options active = mode == Mode.FIND ? findOptions : options;
        if (mode == Mode.FIND) searchValue = findInput.getText().toString();
        else {
            findValue = findInput.getText().toString();
            if (replaceInput != null) replaceValue = replaceInput.getText().toString();
        }
        active.fullPath = scopeGroup.getCheckedRadioButtonId() == scopeGroup.getChildAt(1).getId();
        active.includeFiles = switchFiles.isChecked();
        active.includeFolders = switchFolders.isChecked();
        active.recursive = switchRecursive.isChecked();
        active.ignoreCase = switchCase.isChecked();
        if (mode == Mode.RENAME && replacementModeGroup != null)
            active.destinationTemplate = replacementModeGroup.getCheckedRadioButtonId()
                == replacementModeGroup.getChildAt(1).getId();
        if (mode == Mode.FIND && switchRegex != null) searchRegex = switchRegex.isChecked();
        if (mode == Mode.RENAME && switchAll != null) active.replaceAll = switchAll.isChecked();
    }

    private RenameCore.Options copyOptions(RenameCore.Options from) {
        RenameCore.Options copy = new RenameCore.Options();
        copy.fullPath = from.fullPath;
        copy.includeFiles = from.includeFiles;
        copy.includeFolders = from.includeFolders;
        copy.recursive = from.recursive;
        copy.ignoreCase = from.ignoreCase;
        copy.replaceAll = from.replaceAll;
        copy.destinationTemplate = from.destinationTemplate;
        return copy;
    }

    private void chooseMode(Mode next) {
        if (busy || mode == next) return;
        rememberEditor();
        mode = next;
        showEditor();
    }

    /** Android 15 draws apps behind status/navigation bars unless the content handles insets. */
    private void displayRoot(int left, int top, int right, int bottom) {
        if (Build.VERSION.SDK_INT >= 35) {
            rootView.setOnApplyWindowInsetsListener((view, windowInsets) -> {
                android.graphics.Insets safe = windowInsets.getInsets(
                    android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.ime());
                view.setPadding(left, top + safe.top, right, bottom + safe.bottom);
                return windowInsets;
            });
        } else {
            rootView.setPadding(left, top, right, bottom);
        }
        setContentView(rootView);
    }

    private void showEditor() {
        if (mode == Mode.PRESETS) {
            if (presetKind == null) showPresetsHome(); else showPresetEditor();
            return;
        }
        viewingPresetPreview = false;
        presetFolderInput = null;
        presetTextInput = null;
        viewingPreview = false;
        viewingFindResults = false;
        rootView = vertical();
        rootView.setBackgroundColor(BG);
        displayRoot(0, 0, 0, 0);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        rootView.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout body = vertical();
        body.setPadding(dp(20), dp(20), dp(20), dp(22));
        scroll.addView(body);

        TextView brand = label("PATHSHIFT", 13, ACCENT, true);
        brand.setLetterSpacing(.22f);
        body.addView(brand);
        addModeTabs(body);

        TextView title = label(mode == Mode.FIND ? "Find files & folders" : "Rename by regex", 27, FG, true);
        pad(title, 0, dp(4), 0, dp(2));
        body.addView(title);
        body.addView(label(mode == Mode.FIND
            ? "Search names or entire paths. Find mode is read-only."
            : "Rewrite filenames or entire paths. Review everything before saving.", 13, MUTED, false));

        LinearLayout folderCard = card();
        LinearLayout.LayoutParams cp = spacing(dp(18), dp(12));
        body.addView(folderCard, cp);
        LinearLayout top = horizontal();
        TextView loc = label("WORKING FOLDER", 12, MUTED, true);
        top.addView(loc, new LinearLayout.LayoutParams(0, -2, 1));
        Button change = smallButton("Browse", false);
        top.addView(change);
        change.setOnClickListener(v -> { if (hasFileAccess()) browseFolders(folder); else requestAccess(); });
        folderCard.addView(top);
        folderStatus = label("", 13, FG, false);
        folderStatus.setTextIsSelectable(true);
        pad(folderStatus, 0, dp(4), 0, 0);
        folderCard.addView(folderStatus);
        updateFolderStatus();

        boolean finding = mode == Mode.FIND;
        RenameCore.Options active = finding ? findOptions : options;
        TextView findLabel = label(finding ? "SEARCH FOR" : "FIND  ·  REGULAR EXPRESSION", 12, MUTED, true);
        body.addView(findLabel);
        findInput = edit(finding ? searchValue : findValue,
            finding ? "e.g. \\.jpg$ or vacation" : "e.g. ^IMG_(\\d+)\\.jpg$");
        findInput.setImeOptions(finding ? EditorInfo.IME_ACTION_SEARCH : EditorInfo.IME_ACTION_NEXT);
        body.addView(findInput, spacing(dp(6), dp(13)));
        if (finding) {
            replaceInput = null;
        } else {
            body.addView(label("REPLACE WITH", 12, MUTED, true));
            replaceInput = edit(replaceValue, "e.g. Photo_$1.jpg");
            replaceInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
            body.addView(replaceInput, spacing(dp(6), dp(10)));
            body.addView(label("HOW TO USE THE REPLACEMENT", 12, MUTED, true));
            replacementModeGroup = new RadioGroup(this);
            replacementModeGroup.setOrientation(LinearLayout.VERTICAL);
            RadioButton replacePart = optionRadio("Replace only the matched text");
            replacePart.setId(View.generateViewId());
            RadioButton setDest = optionRadio("Set the complete resulting name/path");
            setDest.setId(View.generateViewId());
            replacementModeGroup.addView(replacePart);
            replacementModeGroup.addView(setDest);
            replacementModeGroup.check(options.destinationTemplate ? setDest.getId() : replacePart.getId());
            body.addView(replacementModeGroup, spacing(dp(6), dp(5)));
            body.addView(label("Use the second option to move matching files while preserving their original names.", 12, MUTED, false), spacing(0, dp(8)));
            body.addView(label("INSERT ORIGINAL NAME / PATH", 12, MUTED, true));
            addTemplateHelpers(body);
        }

        body.addView(label("APPLY PATTERN TO", 12, MUTED, true));
        scopeGroup = new RadioGroup(this);
        scopeGroup.setOrientation(LinearLayout.HORIZONTAL);
        RadioButton names = optionRadio("Filename");
        names.setId(View.generateViewId());
        RadioButton paths = optionRadio("Full relative path");
        paths.setId(View.generateViewId());
        scopeGroup.addView(names);
        scopeGroup.addView(paths);
        scopeGroup.check(active.fullPath ? paths.getId() : names.getId());
        body.addView(scopeGroup, spacing(dp(4), dp(8)));
        body.addView(label(finding
            ? "Full paths use / separators and are relative to the selected folder."
            : "Filename scope builds the destination beside the file's current folder. Full relative path scope starts at the working folder. $0 is the regex match, not the filename.", 12, MUTED, false));

        LinearLayout toggles = card();
        body.addView(toggles, spacing(dp(16), dp(6)));
        switchFiles = createToggle(toggles, "Include files", active.includeFiles);
        switchFolders = createToggle(toggles, "Include folders", active.includeFolders);
        switchRecursive = createToggle(toggles, "Search subfolders", active.recursive);
        switchCase = createToggle(toggles, "Ignore case", active.ignoreCase);
        if (finding) {
            replacementModeGroup = null;
            switchRegex = createToggle(toggles, "Use regular expression", searchRegex);
            switchAll = null;
        } else {
            switchRegex = null;
            switchAll = createToggle(toggles, "Replace every match per path", active.replaceAll);
        }

        LinearLayout bottom = horizontal();
        bottom.setPadding(dp(20), dp(10), dp(20), dp(18));
        Button access = smallButton("File access", false);
        bottom.addView(access);
        access.setOnClickListener(v -> requestAccess());
        previewButton = smallButton(finding ? "Find matches" : "Preview changes", true);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(51), 1);
        bp.leftMargin = dp(10);
        bottom.addView(previewButton, bp);
        previewButton.setOnClickListener(v -> preview());
        rootView.addView(bottom);
    }

    private void addModeTabs(LinearLayout parent) {
        LinearLayout tabs = horizontal();
        Mode[] modes = new Mode[]{Mode.FIND, Mode.RENAME, Mode.PRESETS};
        String[] titles = new String[]{"Find", "Rename", "Presets"};
        for (int i = 0; i < modes.length; i++) {
            final Mode next = modes[i];
            Button tab = smallButton(titles[i], mode == next);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(45), 1);
            if (i > 0) lp.leftMargin = dp(7);
            tabs.addView(tab, lp);
            tab.setOnClickListener(v -> chooseMode(next));
        }
        parent.addView(tabs, spacing(dp(13), dp(12)));
    }

    private void addTemplateHelpers(LinearLayout body) {
        LinearLayout first = horizontal();
        String[][] pairs = new String[][]{{"Filename", "${filename}"}, {"Stem", "${basename}"}, {"Ext", "${ext}"}};
        for (int i=0;i<pairs.length;i++) {
            String name=pairs[i][0], token=pairs[i][1];
            Button button=smallButton(name,false);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(42),1);
            if(i>0)lp.leftMargin=dp(7);
            first.addView(button,lp);
            button.setOnClickListener(v->insertReplacementToken(token));
        }
        body.addView(first, spacing(dp(6),dp(6)));
        LinearLayout second=horizontal();
        String[][] more=new String[][]{{"Folder", "${folder}"},{"Relative path", "${path}"},{"Regex match", "$0"}};
        for (int i=0;i<more.length;i++) {
            String name=more[i][0],token=more[i][1];
            Button button=smallButton(name,false);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(42),1);
            if(i>0)lp.leftMargin=dp(7);
            second.addView(button,lp);
            button.setOnClickListener(v->insertReplacementToken(token));
        }
        body.addView(second,spacing(0,dp(9)));
        body.addView(label("${filename} = entire filename; ${basename} = name before extension; ${ext} = extension; ${folder} = original relative parent; ${path} = complete relative path. $1, $2 = capture groups.",11,MUTED,false));
    }

    private void insertReplacementToken(String token) {
        if (replaceInput == null) return;
        int cursor = Math.max(0, replaceInput.getSelectionStart());
        replaceInput.getText().insert(cursor, token);
        replaceInput.requestFocus();
    }

    private String presetTitle(PresetCore.Kind kind) {
        switch(kind) {
            case MOVE_NEW: return "Move into a new folder";
            case SORT_EXTENSION: return "Sort by file extension";
            case PREFIX: return "Add a filename prefix";
            case SUFFIX: return "Add a filename suffix";
            case NUMBER: return "Number filenames";
            case REPLACE_TEXT: return "Find and replace text";
            case LOWERCASE: return "Lowercase filenames";
            case CLEAN_SPACES: return "Change spaces to underscores";
            default: return "Preset";
        }
    }
    private String presetDescription(PresetCore.Kind kind) {
        switch(kind) {
            case MOVE_NEW: return "Choose where to create a new folder. Move all files, or just matching names, while keeping the original filenames.";
            case SORT_EXTENSION: return "Choose a destination and create JPG, PNG, PDF, and other extension folders inside a new parent folder.";
            case PREFIX: return "Add text to the beginning of each filename.";
            case SUFFIX: return "Add text before the extension without changing the extension.";
            case NUMBER: return "Give files stable, sequential numbers, such as 001_photo.jpg.";
            case REPLACE_TEXT: return "Use plain text, not regex, to rename matching parts of filenames.";
            case LOWERCASE: return "Make filenames lowercase, including extensions.";
            case CLEAN_SPACES: return "Replace runs of whitespace in filenames with underscores.";
            default: return "";
        }
    }
    private void showPresetsHome() {
        viewingPresetPreview = false;
        viewingPreview = false;
        viewingFindResults = false;
        findInput = null; scopeGroup = null; replacementModeGroup = null;
        presetFolderInput = null; presetTextInput = null;
        rootView = vertical();rootView.setBackgroundColor(BG);
        displayRoot(0,0,0,0);
        ScrollView scroll=new ScrollView(this);
        rootView.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout body=vertical();body.setPadding(dp(20),dp(20),dp(20),dp(25));
        scroll.addView(body);
        body.addView(label("PATHSHIFT",13,ACCENT,true));
        addModeTabs(body);
        body.addView(label("Quick actions",27,FG,true));
        body.addView(label("Choose an action, select the files and destination, then review everything before saving. No preset changes files until Save.",13,MUTED,false),spacing(dp(4),dp(14)));
        PresetCore.Kind[] kinds=new PresetCore.Kind[]{PresetCore.Kind.MOVE_NEW,
            PresetCore.Kind.SORT_EXTENSION,PresetCore.Kind.PREFIX,PresetCore.Kind.SUFFIX,
            PresetCore.Kind.NUMBER,PresetCore.Kind.REPLACE_TEXT,
            PresetCore.Kind.LOWERCASE,PresetCore.Kind.CLEAN_SPACES};
        for(PresetCore.Kind kind:kinds) {
            LinearLayout block=card();
            body.addView(block,spacing(dp(6),dp(6)));
            LinearLayout top=horizontal();
            LinearLayout copy=vertical();
            copy.addView(label(presetTitle(kind),16,FG,true));
            copy.addView(label(presetDescription(kind),12,MUTED,false),spacing(dp(3),0));
            top.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
            Button use=smallButton("Choose",false);
            top.addView(use);
            block.addView(top);
            use.setOnClickListener(v->openPreset(kind));
            block.setOnClickListener(v->openPreset(kind));
        }
        body.addView(label("Advanced regex and whole-path folder renaming remain available in Rename.",12,MUTED,false),spacing(dp(15),0));
    }
    private void openPreset(PresetCore.Kind kind) {
        presetKind=kind;
        presetSettings=new PresetCore.Settings();
        presetSettings.kind=kind;
        presetSettings.destinationParent=folder == null ? Environment.getExternalStorageDirectory().toPath() : folder.toPath();
        if(kind==PresetCore.Kind.MOVE_NEW) presetSettings.newFolder="New folder";
        if(kind==PresetCore.Kind.SORT_EXTENSION) presetSettings.newFolder="Sorted by type";
        if(kind==PresetCore.Kind.NUMBER) {presetSettings.startNumber=1;presetSettings.numberDigits=3;}
        showPresetEditor();
    }
    private void rememberPresetForm() {
        if (presetKind==null) return;
        if (presetFolderInput!=null) presetSettings.newFolder=presetFolderInput.getText().toString().trim();
        if (presetFilterInput!=null) presetSettings.filter=presetFilterInput.getText().toString();
        if (presetTextInput!=null) presetSettings.text=presetTextInput.getText().toString();
        if (presetReplacementInput!=null) presetSettings.replacement=presetReplacementInput.getText().toString();
        if (presetStartInput!=null) try {presetSettings.startNumber=Integer.parseInt(presetStartInput.getText().toString());}
            catch(Exception ignored){presetSettings.startNumber=-1;}
        if (presetDigitsInput!=null) try {presetSettings.numberDigits=Integer.parseInt(presetDigitsInput.getText().toString());}
            catch(Exception ignored){presetSettings.numberDigits=-1;}
        if (presetRecursiveSwitch!=null) presetSettings.recursive=presetRecursiveSwitch.isChecked();
        if (presetPreserveSwitch!=null) presetSettings.preserveSubfolders=presetPreserveSwitch.isChecked();
        if (presetIgnoreSwitch!=null) presetSettings.ignoreCase=presetIgnoreSwitch.isChecked();
        if (presetRegexSwitch!=null) presetSettings.filterRegex=presetRegexSwitch.isChecked();
        if (presetPathSwitch!=null) presetSettings.filterFullPath=presetPathSwitch.isChecked();
    }
    private PresetCore.Settings copyPresetSettings() {
        PresetCore.Settings s=new PresetCore.Settings();
        s.kind=presetSettings.kind;s.destinationParent=presetSettings.destinationParent;
        s.newFolder=presetSettings.newFolder;s.recursive=presetSettings.recursive;
        s.preserveSubfolders=presetSettings.preserveSubfolders;
        s.ignoreCase=presetSettings.ignoreCase;s.filterRegex=presetSettings.filterRegex;
        s.filterFullPath=presetSettings.filterFullPath;
        s.filter=presetSettings.filter;s.text=presetSettings.text;
        s.replacement=presetSettings.replacement;s.startNumber=presetSettings.startNumber;
        s.numberDigits=presetSettings.numberDigits;
        return s;
    }
    private void showPresetEditor() {
        if(presetKind==null) {showPresetsHome();return;}
        viewingPresetPreview=false;viewingPreview=false;viewingFindResults=false;
        findInput=null;scopeGroup=null;replacementModeGroup=null;
        presetFolderInput=null;presetFilterInput=null;presetTextInput=null;
        presetReplacementInput=null;presetStartInput=null;presetDigitsInput=null;
        presetRecursiveSwitch=null;presetPreserveSwitch=null;presetIgnoreSwitch=null;
        presetRegexSwitch=null;presetPathSwitch=null;
        rootView=vertical();rootView.setBackgroundColor(BG);
        displayRoot(0,0,0,0);
        ScrollView scroll=new ScrollView(this);
        rootView.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout body=vertical();body.setPadding(dp(20),dp(16),dp(20),dp(24));
        scroll.addView(body);
        body.addView(label("PATHSHIFT",13,ACCENT,true));
        addModeTabs(body);
        Button back=smallButton("‹ All presets",false);
        back.setOnClickListener(v->{rememberPresetForm();presetKind=null;showPresetsHome();});
        body.addView(back,spacing(0,dp(8)));
        body.addView(label(presetTitle(presetKind),24,FG,true));
        body.addView(label(presetDescription(presetKind),12,MUTED,false),spacing(dp(5),dp(15)));
        LinearLayout source=card();body.addView(source,spacing(0,dp(15)));
        LinearLayout top=horizontal();
        top.addView(label("FILES TO PROCESS",12,MUTED,true),new LinearLayout.LayoutParams(0,-2,1));
        Button sourceBrowse=smallButton("Browse",false);
        top.addView(sourceBrowse);
        source.addView(top);
        folderStatus=label("",13,FG,false);folderStatus.setTextIsSelectable(true);
        source.addView(folderStatus,spacing(dp(7),0));updateFolderStatus();
        sourceBrowse.setOnClickListener(v->browseFolders(folder,selected->{
            folder=selected;getPreferences(MODE_PRIVATE).edit().putString("folder",folder.getAbsolutePath()).apply();
            presetSnapshot=new ArrayList<>();presetProposals=new ArrayList<>();presetPlan=null;
            updateFolderStatus();
        }));
        if(presetKind==PresetCore.Kind.MOVE_NEW || presetKind==PresetCore.Kind.SORT_EXTENSION) {
            LinearLayout destination=card();body.addView(destination,spacing(0,dp(13)));
            LinearLayout pick=horizontal();
            pick.addView(label("CREATE NEW FOLDER INSIDE",12,MUTED,true),new LinearLayout.LayoutParams(0,-2,1));
            Button choose=smallButton("Choose folder",false);
            pick.addView(choose);destination.addView(pick);
            presetDestinationLabel=label(presetSettings.destinationParent.toString(),13,FG,false);
            presetDestinationLabel.setTextIsSelectable(true);
            destination.addView(presetDestinationLabel,spacing(dp(7),dp(10)));
            choose.setOnClickListener(v->browseFolders(presetSettings.destinationParent.toFile(),selected->{
                presetSettings.destinationParent=selected.toPath();
                presetDestinationLabel.setText(selected.getAbsolutePath());
            }));
            destination.addView(label("NEW FOLDER NAME",12,MUTED,true));
            presetFolderInput=edit(presetSettings.newFolder,"e.g. Mag or Collected");
            destination.addView(presetFolderInput,spacing(dp(5),dp(5)));
            destination.addView(label("The new folder is created only when Save succeeds. Its name must not already exist at the destination.",11,MUTED,false));
            body.addView(label("OPTIONAL: ONLY INCLUDE FILES MATCHING",12,MUTED,true),spacing(dp(11),0));
            presetFilterInput=edit(presetSettings.filter,"Leave empty to include every file");
            body.addView(presetFilterInput,spacing(dp(5),dp(7)));
            LinearLayout filterToggles=card();body.addView(filterToggles);
            presetRegexSwitch=createToggle(filterToggles,"Use regex for the filter",presetSettings.filterRegex);
            presetIgnoreSwitch=createToggle(filterToggles,"Ignore case",presetSettings.ignoreCase);
            presetPathSwitch=createToggle(filterToggles,"Match full relative path",presetSettings.filterFullPath);
        } else if(presetKind==PresetCore.Kind.PREFIX || presetKind==PresetCore.Kind.SUFFIX) {
            body.addView(label(presetKind==PresetCore.Kind.PREFIX ? "TEXT TO ADD BEFORE NAME" : "TEXT TO ADD BEFORE EXTENSION",12,MUTED,true));
            presetTextInput=edit(presetSettings.text,"e.g. Edited_");
            body.addView(presetTextInput,spacing(dp(6),dp(9)));
        } else if(presetKind==PresetCore.Kind.REPLACE_TEXT) {
            body.addView(label("FIND THIS TEXT",12,MUTED,true));
            presetTextInput=edit(presetSettings.text,"Text in a filename");
            body.addView(presetTextInput,spacing(dp(6),dp(9)));
            body.addView(label("REPLACE WITH",12,MUTED,true));
            presetReplacementInput=edit(presetSettings.replacement,"Leave empty to remove the text");
            body.addView(presetReplacementInput,spacing(dp(6),dp(9)));
            LinearLayout toggles=card();body.addView(toggles);
            presetIgnoreSwitch=createToggle(toggles,"Ignore case",presetSettings.ignoreCase);
        } else if(presetKind==PresetCore.Kind.NUMBER) {
            body.addView(label("START NUMBER",12,MUTED,true));
            presetStartInput=edit(Integer.toString(presetSettings.startNumber),"1");
            presetStartInput.setInputType(InputType.TYPE_CLASS_NUMBER);
            body.addView(presetStartInput,spacing(dp(5),dp(9)));
            body.addView(label("MINIMUM NUMBER OF DIGITS",12,MUTED,true));
            presetDigitsInput=edit(Integer.toString(presetSettings.numberDigits),"3");
            presetDigitsInput.setInputType(InputType.TYPE_CLASS_NUMBER);
            body.addView(presetDigitsInput,spacing(dp(5),dp(8)));
            body.addView(label("Examples: 001_photo.jpg, 002_photo.jpg. Files are numbered alphabetically by their relative paths.",11,MUTED,false));
        }
        LinearLayout flags=card();body.addView(flags,spacing(dp(14),dp(10)));
        presetRecursiveSwitch=createToggle(flags,"Include files in subfolders",presetSettings.recursive);
        if(presetKind==PresetCore.Kind.MOVE_NEW || presetKind==PresetCore.Kind.SORT_EXTENSION) {
            presetPreserveSwitch=createToggle(flags,"Keep original subfolder structure",presetSettings.preserveSubfolders);
            flags.addView(label("If you flatten files from subfolders, same-named files may collide. You can number duplicates in the preview.",11,MUTED,false));
        }
        body.addView(label("Presets only change files, not entire folder names. Use Rename for changing folders themselves.",12,MUTED,false));
        LinearLayout footer=horizontal();footer.setPadding(dp(20),dp(6),dp(20),dp(14));
        Button access=smallButton("File access",false);footer.addView(access);access.setOnClickListener(v->requestAccess());
        Button next=smallButton("Preview changes",true);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(53),1);lp.leftMargin=dp(10);
        footer.addView(next,lp);next.setOnClickListener(v->previewPreset());
        rootView.addView(footer);
    }
    private void previewPreset() {
        if(busy)return;
        if(!hasFileAccess()){requestAccess();return;}
        if(folder==null || !folder.isDirectory()){toast("Choose a working folder.");return;}
        rememberPresetForm();
        PresetCore.Settings settings=copyPresetSettings();
        final Path sourceRoot=folder.toPath().toAbsolutePath().normalize();
        cancelScan.set(false);busy=true;
        TextView message=label("Checking filenames and folders…",15,FG,false);
        message.setPadding(dp(18),dp(20),dp(18),dp(20));
        AlertDialog progress=new AlertDialog.Builder(this).setTitle("Building preset preview")
            .setView(message).setNegativeButton("Cancel",(d,w)->cancelScan.set(true)).create();
        progress.setCanceledOnTouchOutside(false);progress.show();
        worker.execute(()->{
            try {
                BatchCommit.ensureNoUnfinishedBatch(sourceRoot);
                List<RenameCore.Entry> entries=RenameCore.scan(sourceRoot,cancelScan,
                    count->main.post(()->message.setText("Scanned " + count + " paths…")));
                if(cancelScan.get())throw new IOException("Scan canceled.");
                List<PresetCore.Proposal> proposals=PresetCore.preview(sourceRoot,entries,settings);
                PresetCore.Plan plan=PresetCore.plan(sourceRoot,entries,proposals);
                if(cancelScan.get())throw new IOException("Scan canceled.");
                main.post(()->{
                    busy=false;progress.dismiss();presetSnapshot=entries;
                    presetProposals=proposals;presetPlan=plan;
                    showPresetPreview();
                });
            }catch(Exception ex){main.post(()->{
                busy=false;progress.dismiss();
                if(!cancelScan.get())errorDialog("Cannot preview preset",ex.getMessage());
            });}
        });
    }
    private void showPresetPreview() {
        viewingPresetPreview=true;viewingFindResults=false;viewingPreview=false;
        rootView=vertical();rootView.setBackgroundColor(BG);
        displayRoot(dp(12),dp(8),dp(12),dp(9));
        LinearLayout toolbar=horizontal();
        Button back=smallButton("‹ Adjust preset",false);
        toolbar.addView(back);back.setOnClickListener(v->showPresetEditor());
        TextView note=label("PREVIEW · NOTHING SAVED",12,ACCENT,true);
        note.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);
        toolbar.addView(note,new LinearLayout.LayoutParams(0,dp(45),1));
        rootView.addView(toolbar);
        rootView.addView(label("Review preset changes",23,FG,true),spacing(dp(9),dp(4)));
        presetSummary=label("",13,MUTED,false);rootView.addView(presetSummary);
        presetProblem=label("",12,DANGER,false);rootView.addView(presetProblem,spacing(dp(7),dp(6)));
        LinearLayout actions=horizontal();
        presetCollisionsButton=smallButton("Review conflicts",false);
        presetFixButton=smallButton("Number duplicates",false);
        actions.addView(presetCollisionsButton,new LinearLayout.LayoutParams(0,dp(43),1));
        LinearLayout.LayoutParams rlp=new LinearLayout.LayoutParams(0,dp(43),1);
        rlp.leftMargin=dp(9);actions.addView(presetFixButton,rlp);
        rootView.addView(actions,spacing(dp(3),dp(7)));
        presetCollisionsButton.setOnClickListener(v->showPresetCollisions());
        presetFixButton.setOnClickListener(v->{
            int fixed=PresetCore.numberConflicts(folder.toPath(),presetSnapshot,presetProposals);
            if(fixed==0){toast("No duplicate filenames could be numbered. Review any remaining errors.");return;}
            presetAdapter.notifyDataSetChanged();replanPreset();
        });
        LinearLayout selects=horizontal();
        Button all=smallButton("Select all",false),none=smallButton("Select none",false);
        selects.addView(all);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-2,dp(42));np.leftMargin=dp(8);
        selects.addView(none,np);rootView.addView(selects,spacing(dp(3),dp(7)));
        all.setOnClickListener(v->{for(PresetCore.Proposal p:presetProposals)p.selected=true;presetAdapter.notifyDataSetChanged();replanPreset();});
        none.setOnClickListener(v->{for(PresetCore.Proposal p:presetProposals)p.selected=false;presetAdapter.notifyDataSetChanged();replanPreset();});
        ListView results=new ListView(this);results.setDivider(null);results.setDividerHeight(dp(7));
        results.setFastScrollEnabled(true);results.setClipToPadding(false);
        presetAdapter=new PresetAdapter();results.setAdapter(presetAdapter);
        rootView.addView(results,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout footer=horizontal();
        Button adjust=smallButton("Edit settings",false);footer.addView(adjust);
        adjust.setOnClickListener(v->showPresetEditor());
        presetSaveButton=smallButton("Save changes",true);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(52),1);lp.leftMargin=dp(8);
        footer.addView(presetSaveButton,lp);
        presetSaveButton.setOnClickListener(v->confirmPresetSave());
        rootView.addView(footer,spacing(dp(8),0));
        displayPresetPlan();
    }
    private void replanPreset() {
        if(!viewingPresetPreview || busy)return;
        // The plan is metadata-only and recomputes collision groups immediately.
        presetPlan=PresetCore.plan(folder.toPath(),presetSnapshot,presetProposals);
        displayPresetPlan();
        if(presetAdapter!=null)presetAdapter.notifyDataSetChanged();
    }
    private void displayPresetPlan() {
        if(presetPlan==null || presetSummary==null)return;
        int selected=presetPlan.moves.size();
        presetSummary.setText(presetSnapshot.size()+" paths checked · " + presetProposals.size()
            +" proposed · "+selected+" selected");
        int groups=presetPlan.conflicts.size();
        if(presetPlan.totalErrors>0) {
            presetProblem.setTextColor(DANGER);
            presetProblem.setText((groups>0 ? groups+" duplicate destination group(s). " : "")
                +presetPlan.totalErrors+" blocked item(s). No files will be overwritten. "
                +"Edit, deselect or number conflicts before saving.\n"
                +(presetPlan.issues.isEmpty()?"":presetPlan.issues.get(0)));
        }else if(selected==0){presetProblem.setTextColor(MUTED);presetProblem.setText("No changes selected. Adjust settings or choose files.");}
        else {presetProblem.setTextColor(ACCENT);presetProblem.setText("All selected destinations are available. Files will move only after Save.");}
        presetCollisionsButton.setVisibility(groups>0?View.VISIBLE:View.GONE);
        presetFixButton.setVisibility(presetPlan.totalErrors>0?View.VISIBLE:View.GONE);
        presetSaveButton.setEnabled(!busy && presetPlan.okay());
    }
    private void showPresetCollisions() {
        if(presetPlan==null)return;
        StringBuilder msg=new StringBuilder();int i=0;
        for(java.util.Map.Entry<String,List<String>> item:presetPlan.conflicts.entrySet()) {
            if(i++>=18){msg.append("\nAdditional groups are shown in the preview.");break;}
            msg.append("\n\nDESTINATION\n").append(item.getKey()).append("\n");
            for(String source:item.getValue())msg.append("  • ").append(source).append("\n");
        }
        new AlertDialog.Builder(this).setTitle("Duplicate filenames ("+presetPlan.conflicts.size()+" groups)")
            .setMessage(msg.toString().trim())
            .setNegativeButton("Back",null)
            .setPositiveButton("Number duplicates",(d,w)->{
                int n=PresetCore.numberConflicts(folder.toPath(),presetSnapshot,presetProposals);
                if(n==0)toast("No duplicates could be numbered.");
                replanPreset();
            }).show();
    }
    private class PresetAdapter extends BaseAdapter {
        @Override public int getCount(){return presetProposals.size();}
        @Override public Object getItem(int p){return presetProposals.get(p);}
        @Override public long getItemId(int p){return p;}
        @Override public View getView(int pos,View recycled,ViewGroup parent){
            PresetCore.Proposal p=presetProposals.get(pos);
            LinearLayout row=horizontal();row.setBackground(round(PANEL,BORDER,dp(11)));
            row.setPadding(dp(6),dp(12),dp(8),dp(12));
            CheckBox selected=new CheckBox(MainActivity.this);
            selected.setButtonTintList(android.content.res.ColorStateList.valueOf(ACCENT));
            selected.setChecked(p.selected);row.addView(selected);
            selected.setOnCheckedChangeListener((view,checked)->{p.selected=checked;replanPreset();});
            LinearLayout names=vertical();
            names.addView(label(p.entry.path,12,MUTED,false));
            TextView dest=label("→ " + p.target,13,p.selected?ACCENT:MUTED,true);
            dest.setSingleLine(false);names.addView(dest,spacing(dp(4),0));
            String issue=presetPlan==null?null:presetPlan.itemErrors.get(p.entry.path);
            if(issue!=null)names.addView(label("! "+issue,12,DANGER,false),spacing(dp(4),0));
            row.addView(names,new LinearLayout.LayoutParams(0,-2,1));
            Button edit=smallButton("Edit",false);row.addView(edit);
            edit.setOnClickListener(v->editPresetTarget(p));
            names.setOnClickListener(v->editPresetTarget(p));
            return row;
        }
    }
    private void editPresetTarget(PresetCore.Proposal item) {
        EditText name=edit(item.target.getFileName().toString(),"New filename");
        LinearLayout panel=vertical();panel.setPadding(dp(18),dp(8),dp(18),0);
        panel.addView(label("ORIGINAL\n"+item.entry.path+"\n\nDESTINATION FOLDER\n"+item.target.getParent()
            +"\n\nNEW FILENAME",12,MUTED,false));
        panel.addView(name,spacing(dp(8),dp(5)));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Change destination filename")
            .setView(panel).setNegativeButton("Cancel",null)
            .setNeutralButton("Reset",null).setPositiveButton("Update preview",null).create();
        dialog.setOnShowListener(d->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                String proposed=name.getText().toString().trim();
                if(proposed.indexOf('/')>=0 || proposed.indexOf('\\')>=0 ||
                    RenameCore.validateRelative(proposed)!=null) {
                    name.setError("Enter a valid filename only. Folders are chosen separately.");return;
                }
                item.target=item.target.resolveSibling(proposed);item.selected=true;
                dialog.dismiss();replanPreset();
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{
                item.target=item.originalTarget;item.selected=true;dialog.dismiss();replanPreset();
            });
        });
        dialog.show();
    }
    private void confirmPresetSave() {
        if(busy || presetPlan==null || !presetPlan.okay())return;
        PresetCore.Plan committing=presetPlan;
        new AlertDialog.Builder(this).setTitle("Save " + committing.moves.size() + " file changes?")
            .setMessage("This will rename/move the selected files and create destination folders as needed. "
                +"No files will be overwritten.\n\nThis is the FIRST step that modifies storage. "
                +"A batch cannot be guaranteed atomic if Android shuts down during Save.")
            .setNegativeButton("Keep reviewing",null)
            .setPositiveButton("Save now",(d,w)->savePreset(committing)).show();
    }
    private void savePreset(PresetCore.Plan committing) {
        busy=true;presetSaveButton.setEnabled(false);
        TextView message=label("Checking files…",14,FG,false);
        message.setPadding(dp(20),dp(20),dp(20),dp(20));
        AlertDialog progress=new AlertDialog.Builder(this).setTitle("Saving preset")
            .setMessage("Do not close this app while saving.").setView(message).setCancelable(false).create();
        progress.show();
        Path selectedRoot=folder.toPath();List<RenameCore.Entry> original=presetSnapshot;
        worker.execute(()->{
            try {
                PresetCommit.Result result=PresetCommit.commit(selectedRoot,original,committing,
                    text->main.post(()->message.setText(text)));
                main.post(()->{
                    busy=false;progress.dismiss();presetPlan=null;prettyResetPresetResults();
                    showPresetEditor();
                    new AlertDialog.Builder(this).setTitle("Saved")
                        .setMessage(result.changed+" file(s) moved or renamed successfully.")
                        .setPositiveButton("Done",null).show();
                });
            } catch(Exception ex) {
                main.post(()->{
                    busy=false;progress.dismiss();presetSaveButton.setEnabled(false);
                    errorDialog("Unable to save preset",ex.getMessage()+"\n\nReview the files and preview again before continuing.");
                });
            }
        });
    }
    private void prettyResetPresetResults() {
        presetSnapshot=new ArrayList<>();presetProposals=new ArrayList<>();
        presetPlan=null;
    }

    private void updateFolderStatus() {
        if (folderStatus == null) return;
        folderStatus.setText((folder == null ? "No folder" : folder.getAbsolutePath())
            + (hasFileAccess() ? "" : "\nFile permission required"));
        folderStatus.setTextColor(hasFileAccess() ? FG : DANGER);
    }

    private void preview() {
        if (busy) return;
        if (!hasFileAccess()) { requestAccess(); return; }
        if (folder == null || !folder.isDirectory()) { toast("Choose an accessible folder."); return; }
        rememberEditor();
        final boolean finding = mode == Mode.FIND;
        final RenameCore.Options captured = copyOptions(finding ? findOptions : options);
        final String find = finding ? searchValue : findValue;
        final String replace = replaceValue;
        final boolean regex = searchRegex;
        if (find.trim().isEmpty()) { findInput.setError(finding ? "Enter a name or pattern." : "Enter a regex pattern."); return; }
        if (!captured.includeFiles && !captured.includeFolders) { toast("Enable files, folders, or both."); return; }
        final Path selectedRoot = folder.toPath().toAbsolutePath().normalize();
        busy = true;
        cancelScan.set(false);
        previewButton.setEnabled(true);
        previewButton.setText("Cancel scan");
        previewButton.setOnClickListener(v -> cancelScan.set(true));
        final TextView dialogText = label("Scanning filenames and folders…", 15, FG, false);
        dialogText.setPadding(dp(22), dp(25), dp(22), dp(25));
        final AlertDialog progress = new AlertDialog.Builder(this).setTitle(finding ? "Finding matches" : "Creating preview")
            .setView(dialogText).setNegativeButton("Cancel", (d, w) -> cancelScan.set(true)).create();
        progress.setCanceledOnTouchOutside(false);
        progress.show();
        worker.execute(() -> {
            try {
                if (!finding) BatchCommit.ensureNoUnfinishedBatch(selectedRoot);
                List<RenameCore.Entry> entries = RenameCore.scan(selectedRoot, cancelScan,
                    count -> main.post(() -> dialogText.setText("Scanned " + count + " paths…")));
                if (cancelScan.get()) throw new IOException("Scan canceled.");
                if (finding) {
                    List<RenameCore.Entry> found = RenameCore.find(entries, find, captured, regex);
                    if (cancelScan.get()) throw new IOException("Scan canceled.");
                    main.post(() -> {
                        busy = false;
                        progress.dismiss();
                        pathsScanned = entries.size();
                        matches = found;
                        visibleMatches = new ArrayList<>(found);
                        showFindResults();
                    });
                } else {
                    List<RenameCore.Candidate> found = RenameCore.preview(entries, find, replace, captured);
                    RenameCore.Plan plan = RenameCore.plan(entries, found);
                    main.post(() -> {
                        busy = false;
                        progress.dismiss();
                        snapshot = entries;
                        candidates = found;
                        currentPlan = plan;
                        planRevision++;
                        showPreview();
                    });
                }
            } catch (Exception error) {
                main.post(() -> {
                    busy = false;
                    progress.dismiss();
                    previewButton.setEnabled(true);
                    previewButton.setText(finding ? "Find matches" : "Preview changes");
                    previewButton.setOnClickListener(v -> preview());
                    if (!cancelScan.get()) errorDialog("Could not preview", error.getMessage());
                });
            }
        });
    }

    /** Read-only results; there is deliberately no Save or file-operation action on this screen. */
    private void showFindResults() {
        viewingFindResults = true;
        viewingPreview = false;
        filterRevision++;
        rootView = vertical();
        rootView.setBackgroundColor(BG);
        displayRoot(dp(12), dp(8), dp(12), dp(10));

        LinearLayout toolbar = horizontal();
        Button back = smallButton("‹ Search", false);
        back.setOnClickListener(v -> showEditor());
        toolbar.addView(back);
        TextView note = label("FIND  ·  READ-ONLY", 12, ACCENT, true);
        note.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        toolbar.addView(note, new LinearLayout.LayoutParams(0, dp(44), 1));
        rootView.addView(toolbar);

        TextView title = label("Search results", 23, FG, true);
        rootView.addView(title, spacing(dp(8), dp(3)));
        matchCount = label("", 13, MUTED, false);
        rootView.addView(matchCount, spacing(dp(4), dp(8)));
        TextView message = label("No files or folders have been changed.", 12, ACCENT, false);
        rootView.addView(message, spacing(0, dp(10)));

        EditText refine = edit("", "Filter results by text");
        refine.setSingleLine(true);
        rootView.addView(refine, spacing(0, dp(10)));
        refine.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterResults(s.toString());
            }
            @Override public void afterTextChanged(Editable editable) {}
        });

        TextView empty = label("No matching paths. Try another pattern or clear the result filter.", 14, MUTED, false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(14), dp(22), dp(14), dp(22));
        rootView.addView(empty);

        ListView results = new ListView(this);
        results.setCacheColorHint(Color.TRANSPARENT);
        results.setDivider(null);
        results.setDividerHeight(dp(7));
        results.setFastScrollEnabled(true);
        results.setClipToPadding(false);
        results.setPadding(dp(2), dp(5), dp(2), dp(5));
        matchAdapter = new MatchAdapter();
        results.setAdapter(matchAdapter);
        rootView.addView(results, new LinearLayout.LayoutParams(-1, 0, 1));
        results.setEmptyView(empty);
        updateMatchCount();

        LinearLayout footer = horizontal();
        footer.setPadding(0, dp(9), 0, 0);
        Button again = smallButton("New search", false);
        again.setOnClickListener(v -> showEditor());
        footer.addView(again);
        Button use = smallButton("Use in Rename", true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(52), 1);
        lp.leftMargin = dp(10);
        footer.addView(use, lp);
        use.setOnClickListener(v -> {
            findValue = searchRegex ? searchValue : java.util.regex.Pattern.quote(searchValue);
            options = copyOptions(findOptions);
            mode = Mode.RENAME;
            showEditor();
        });
        rootView.addView(footer);
    }

    /** Refines already found paths in memory, without another filesystem traversal. */
    private void filterResults(String text) {
        final long token = ++filterRevision;
        final String query = text.toLowerCase(Locale.ROOT);
        final List<RenameCore.Entry> source = matches;
        worker.execute(() -> {
            List<RenameCore.Entry> subset = new ArrayList<>();
            for (RenameCore.Entry entry : source) {
                if (query.isEmpty() || entry.path.toLowerCase(Locale.ROOT).contains(query))
                    subset.add(entry);
            }
            main.post(() -> {
                if (!viewingFindResults || token != filterRevision) return;
                visibleMatches = subset;
                if (matchAdapter != null) matchAdapter.notifyDataSetChanged();
                updateMatchCount();
            });
        });
    }

    private void updateMatchCount() {
        if (matchCount == null) return;
        int total = matches.size(), shown = visibleMatches.size();
        matchCount.setText((total == shown ? total + " matches" : shown + " of " + total + " matches")
            + "  ·  " + pathsScanned + " paths scanned");
    }

    private final class MatchAdapter extends BaseAdapter {
        @Override public int getCount() { return visibleMatches.size(); }
        @Override public Object getItem(int position) { return visibleMatches.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View recycled, ViewGroup parent) {
            final RenameCore.Entry entry = visibleMatches.get(position);
            LinearLayout block;
            MatchHolder holder;
            if (recycled instanceof LinearLayout && recycled.getTag() instanceof MatchHolder) {
                block = (LinearLayout)recycled;
                holder = (MatchHolder)recycled.getTag();
            } else {
                block = card();
                block.setPadding(dp(14), dp(11), dp(14), dp(11));
                holder = new MatchHolder();
                holder.type = label("", 11, ACCENT, true);
                holder.path = label("", 14, FG, false);
                holder.path.setSingleLine(false);
                block.addView(holder.type);
                block.addView(holder.path, spacing(dp(4), 0));
                block.setTag(holder);
            }
            holder.type.setText(entry.directory ? "FOLDER" : "FILE");
            holder.path.setText(entry.path);
            block.setOnClickListener(v -> showMatchDetail(entry));
            return block;
        }
    }

    private static final class MatchHolder {
        TextView type;
        TextView path;
    }

    private void showMatchDetail(RenameCore.Entry entry) {
        File absolute = new File(folder, entry.path);
        TextView path = label((entry.directory ? "Folder" : "File") + "\n" + absolute.getAbsolutePath(), 15, FG, false);
        path.setTextIsSelectable(true);
        path.setPadding(dp(16), dp(14), dp(16), dp(14));
        new AlertDialog.Builder(this)
            .setTitle("Found path")
            .setView(path)
            .setNegativeButton("Close", null)
            .setNeutralButton("Copy path", (d, w) -> {
                ClipboardManager clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("File path", absolute.getAbsolutePath()));
                    toast("Path copied.");
                }
            })
            .setPositiveButton("Work in folder", (d, w) -> {
                File next = entry.directory ? absolute : absolute.getParentFile();
                if (next == null || !next.isDirectory()) { toast("That folder is no longer available."); return; }
                folder = next;
                getPreferences(MODE_PRIVATE).edit().putString("folder", next.getAbsolutePath()).apply();
                showEditor();
            })
            .show();
    }

    private void showPreview() {
        viewingPreview = true;
        rootView = vertical();
        rootView.setBackgroundColor(BG);
        displayRoot(dp(12), dp(8), dp(12), dp(10));

        LinearLayout toolbar = horizontal();
        Button back = smallButton("‹ Edit", false);
        toolbar.addView(back);
        back.setOnClickListener(v -> showEditor());
        TextView label = label("PREVIEW  ·  NOTHING SAVED", 12, ACCENT, true);
        label.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        toolbar.addView(label, new LinearLayout.LayoutParams(0, dp(44), 1));
        rootView.addView(toolbar);
        TextView title = label("Review changes", 23, FG, true);
        pad(title, dp(6), dp(5), 0, dp(2));
        rootView.addView(title);
        summary = label("", 13, MUTED, false);
        pad(summary, dp(6), 0, 0, dp(6));
        rootView.addView(summary);
        issue = label("", 12, DANGER, false);
        pad(issue, dp(6), 0, 0, dp(6));
        rootView.addView(issue);
        LinearLayout duplicateRow = horizontal();
        collisionButton = smallButton("Review duplicates", false);
        autoNumberButton = smallButton("Number duplicates", false);
        duplicateRow.addView(collisionButton, new LinearLayout.LayoutParams(0, dp(43), 1));
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(0, dp(43), 1);
        dl.leftMargin = dp(9);
        duplicateRow.addView(autoNumberButton, dl);
        rootView.addView(duplicateRow, spacing(dp(3), dp(8)));
        collisionButton.setOnClickListener(v -> showRenameConflicts());
        autoNumberButton.setOnClickListener(v -> {
            if (busy) return;
            int adjusted = RenameCore.numberDuplicateFiles(snapshot, candidates);
            if (adjusted == 0) toast("There are no duplicate file destinations that can be numbered automatically.");
            else {adapter.notifyDataSetChanged();replan();}
        });
        allPathsButton = smallButton("View all affected paths", false);
        rootView.addView(allPathsButton, spacing(dp(2), dp(3)));
        allPathsButton.setOnClickListener(v -> showAllAffectedPaths());

        LinearLayout actionRow = horizontal();
        Button all = smallButton("Select all", false);
        Button none = smallButton("Select none", false);
        TextView hint = label("Tap Edit to customize", 11, MUTED, false);
        hint.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        actionRow.addView(all);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-2, dp(41));np.leftMargin=dp(8);
        actionRow.addView(none, np);
        actionRow.addView(hint, new LinearLayout.LayoutParams(0, dp(41), 1));
        rootView.addView(actionRow, spacing(dp(5), dp(5)));
        all.setOnClickListener(v -> { for(RenameCore.Candidate c:candidates) c.selected=true; adapter.notifyDataSetChanged(); replan(); });
        none.setOnClickListener(v -> { for(RenameCore.Candidate c:candidates) c.selected=false; adapter.notifyDataSetChanged(); replan(); });

        resultsList = new ListView(this);
        resultsList.setCacheColorHint(Color.TRANSPARENT);
        resultsList.setDivider(null);
        resultsList.setDividerHeight(dp(7));
        resultsList.setClipToPadding(false);
        resultsList.setPadding(dp(2), dp(5), dp(2), dp(5));
        adapter = new CandidateAdapter();
        resultsList.setAdapter(adapter);
        rootView.addView(resultsList, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout footer = horizontal();
        footer.setPadding(0, dp(8), 0, 0);
        Button refresh = smallButton("Recheck", false);
        footer.addView(refresh);
        refresh.setOnClickListener(v -> replan());
        saveButton = smallButton("Save changes", true);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, dp(52), 1);sp.leftMargin = dp(10);
        footer.addView(saveButton, sp);
        saveButton.setOnClickListener(v -> confirmSave());
        rootView.addView(footer);
        displayPlan();
    }

    private void replan() {
        if (!viewingPreview || busy) return;
        long serial = ++planRevision;
        saveButton.setEnabled(false);
        issue.setText("Checking paths and collisions…");
        // Copy user selections before the worker reads them. UI updates can continue.
        List<RenameCore.Candidate> copy = new ArrayList<>();
        for (RenameCore.Candidate c : candidates) {
            RenameCore.Candidate clone = new RenameCore.Candidate(c.entry, c.requestedTarget);
            clone.selected = c.selected;
            copy.add(clone);
        }
        worker.execute(() -> {
            RenameCore.Plan plan = RenameCore.plan(snapshot, copy);
            main.post(() -> {
                if (serial != planRevision || !viewingPreview || busy) return;
                currentPlan = plan;
                displayPlan();
                adapter.notifyDataSetChanged();
            });
        });
    }

    private void displayPlan() {
        if (summary == null || currentPlan == null) return;
        RenameCore.Plan plan = currentPlan;
        String noun = plan.selectedMatches == 1 ? "match" : "matches";
        summary.setText(snapshot.size() + " paths scanned   ·   " + plan.selectedMatches + " selected " + noun
            + "   ·   " + plan.changedPaths + " affected paths");
        if (!plan.errors.isEmpty()) {
            if (!plan.duplicateDestinations.isEmpty()) {
                issue.setText(plan.duplicateDestinations.size() + " duplicate destination name(s). "
                    + "Multiple source files would get the same new filename. Save is blocked so nothing is overwritten. "
                    + "Review duplicates, number them automatically, or edit individual results.");
            } else issue.setText(plan.problemCount + " invalid destination(s). Example: "
                + plan.errors.get(0) + "\nEdit or deselect the affected file before saving.");
        } else if (plan.selectedMatches == 0) {
            issue.setText(candidates.isEmpty() ? "No paths matched the pattern. Go back to edit it." : "Select at least one change to save.");
        } else if (plan.moves.isEmpty()) {
            issue.setText("All selected changes are already covered by their parent folder changes.");
        } else issue.setText("");
        issue.setTextColor(plan.errors.isEmpty() ? MUTED : DANGER);
        saveButton.setEnabled(!busy && plan.okay());
        if (collisionButton != null) collisionButton.setVisibility(plan.duplicateDestinations.isEmpty() ? View.GONE : View.VISIBLE);
        if (autoNumberButton != null) autoNumberButton.setVisibility(plan.duplicateDestinations.isEmpty() ? View.GONE : View.VISIBLE);
        if (allPathsButton != null) {
            allPathsButton.setText("View all " + plan.changedPaths + " affected paths");
            allPathsButton.setEnabled(!busy && plan.changedPaths > 0);
        }
    }

    private void showRenameConflicts() {
        if (currentPlan == null || currentPlan.duplicateDestinations.isEmpty()) return;
        StringBuilder detail = new StringBuilder();
        int count = 0;
        for (java.util.Map.Entry<String, List<String>> group : currentPlan.duplicateDestinations.entrySet()) {
            if (count++ >= 22) {detail.append("\nMore groups are shown in preview.");break;}
            detail.append("\n\nDESTINATION\n").append(group.getKey()).append("\n");
            for (String source : group.getValue()) detail.append("  • ").append(source).append("\n");
        }
        new AlertDialog.Builder(this)
            .setTitle(currentPlan.duplicateDestinations.size() + " duplicate destination(s)")
            .setMessage(detail.toString().trim())
            .setNegativeButton("Back", null)
            .setPositiveButton("Number duplicates", (d, w) -> {
                int n = RenameCore.numberDuplicateFiles(snapshot, candidates);
                if (n == 0) toast("No duplicate file destinations could be numbered.");
                if (adapter != null) adapter.notifyDataSetChanged();
                replan();
            }).show();
    }

    /** Read-only full plan, including files whose paths change through a renamed parent. */
    private void showAllAffectedPaths() {
        if (busy || currentPlan == null) return;
        final RenameCore.Plan viewPlan = currentPlan;
        final List<RenameCore.Entry> affected = new ArrayList<>();
        for (RenameCore.Entry entry : snapshot) {
            String target = viewPlan.destinations.get(entry.path);
            if (target != null && !target.equals(entry.path)) affected.add(entry);
        }
        ListView list = new ListView(this);
        list.setBackgroundColor(BG);
        list.setDivider(null);
        list.setDividerHeight(dp(6));
        list.setFastScrollEnabled(true);
        list.setPadding(dp(9), dp(7), dp(9), dp(7));
        list.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return affected.size(); }
            @Override public Object getItem(int position) { return affected.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position, View recycled, ViewGroup container) {
                RenameCore.Entry entry = affected.get(position);
                LinearLayout block = card();
                block.addView(label((entry.directory ? "FOLDER  ·  " : "") + entry.path,
                    12, MUTED, false));
                block.addView(label("→  " + viewPlan.destinations.get(entry.path),
                    13, ACCENT, true), spacing(dp(4), 0));
                return block;
            }
        });
        new AlertDialog.Builder(this)
            .setTitle("All affected paths (" + affected.size() + ")")
            .setView(list)
            .setPositiveButton("Back to review", null)
            .show();
    }

    private void editCandidate(RenameCore.Candidate c) {
        EditText edit = edit(c.requestedTarget, "Relative destination path");
        edit.setSingleLine(false);
        edit.setMinLines(2);
        LinearLayout panel = vertical();
        panel.setPadding(dp(19), dp(8), dp(19), 0);
        TextView old = label("FROM\n" + c.entry.path + "\n\nTO (relative to working folder)", 13, MUTED, false);
        panel.addView(old);
        panel.addView(edit, spacing(dp(7), dp(5)));
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Edit destination")
            .setView(panel)
            .setNeutralButton("Reset regex", null)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Update preview", null).create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                c.requestedTarget = edit.getText().toString().trim();
                c.selected = true;
                dialog.dismiss();
                adapter.notifyDataSetChanged();
                replan();
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                c.requestedTarget = c.regexTarget;
                c.selected = true;
                dialog.dismiss();
                adapter.notifyDataSetChanged();
                replan();
            });
        });
        dialog.show();
    }

    private void confirmSave() {
        if (busy || currentPlan == null || !currentPlan.okay()) return;
        final RenameCore.Plan committing = currentPlan;
        new AlertDialog.Builder(this)
            .setTitle("Apply these changes?")
            .setMessage("This will apply " + committing.moves.size() + " direct file/folder operations, affecting "
                + committing.changedPaths + " paths. Existing files will not be overwritten.\n\n"
                + "This is the first step that changes anything on disk. Continue?")
            .setNegativeButton("Keep reviewing", null)
            .setPositiveButton("Save now", (d, w) -> save(committing)).show();
    }

    private void save(final RenameCore.Plan committing) {
        busy = true;
        saveButton.setEnabled(false);
        final TextView status = label("Checking metadata…", 14, FG, false);
        status.setPadding(dp(19), dp(19), dp(19), dp(19));
        final AlertDialog progress = new AlertDialog.Builder(this)
            .setTitle("Applying changes")
            .setMessage("Do not force-close the app while this batch is being saved.")
            .setView(status).setCancelable(false).create();
        progress.show();
        final Path path = folder.toPath().toAbsolutePath().normalize();
        final List<RenameCore.Entry> original = snapshot;
        worker.execute(() -> {
            try {
                BatchCommit.Result result = BatchCommit.commit(path, original, committing,
                    message -> main.post(() -> status.setText(message)));
                main.post(() -> {
                    busy = false;
                    progress.dismiss();
                    snapshot = new ArrayList<>();
                    candidates = new ArrayList<>();
                    currentPlan = null;
                    showEditor();
                    new AlertDialog.Builder(this).setTitle("Saved")
                        .setMessage(result.operations + " rename/move operations completed. "
                            + result.affectedPaths + " paths were affected, including items inside renamed folders.")
                        .setPositiveButton("Done", null).show();
                });
            } catch (Exception problem) {
                main.post(() -> {
                    busy = false;
                    progress.dismiss();
                    saveButton.setEnabled(false);
                    errorDialog("Unable to save", problem.getMessage() + "\n\nPreview again before attempting another batch.");
                });
            }
        });
    }

    private void browseFolders(File starting) {
        browseFolders(starting,chosen->{
            folder = chosen;
            getPreferences(MODE_PRIVATE).edit().putString("folder", chosen.getAbsolutePath()).apply();
            snapshot = new ArrayList<>(); candidates = new ArrayList<>(); currentPlan = null;
            updateFolderStatus();
        });
    }
    private void browseFolders(File starting, Consumer<File> onSelect) {
        if (!hasFileAccess()) { requestAccess(); return; }
        File base = starting != null && starting.isDirectory() ? starting : Environment.getExternalStorageDirectory();
        showFolderDialog(base,onSelect);
    }

    private void showFolderDialog(final File here, Consumer<File> onSelect) {
        File[] listing = here.listFiles();
        if (listing == null) { toast("Cannot open this folder."); return; }
        List<File> dirs = new ArrayList<>();
        for (File f : listing) if (f.isDirectory() && !f.getName().startsWith(RenameCore.STAGE_PREFIX)) dirs.add(f);
        Collections.sort(dirs, new Comparator<File>() {
            @Override public int compare(File a, File b) { return a.getName().compareToIgnoreCase(b.getName()); }
        });
        List<String> labels = new ArrayList<>();
        boolean canUp = here.getParentFile() != null && withinExternalStorage(here.getParentFile());
        if (canUp) labels.add("..   Go up");
        for (File dir : dirs) labels.add(dir.getName() + "/");
        if (labels.isEmpty()) labels.add("(No subfolders)");
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Choose folder: " + here.getAbsolutePath())
            .setItems(labels.toArray(new String[0]), (d, which) -> {
                if (canUp && which == 0) { showFolderDialog(here.getParentFile(),onSelect); return; }
                int index = which - (canUp ? 1 : 0);
                if (index >= 0 && index < dirs.size()) showFolderDialog(dirs.get(index),onSelect);
            })
            .setNeutralButton("Storage", (d, w) -> showVolumeDialog(onSelect))
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Use this folder", (d, w) -> onSelect.accept(here)).create();
        dialog.show();
    }

    private boolean withinExternalStorage(File dir) {
        String path = dir.getAbsolutePath();
        return path.equals("/storage") || path.startsWith("/storage/");
    }

    private void showVolumeDialog(Consumer<File> onSelect) {
        List<File> volumes = new ArrayList<>();
        volumes.add(Environment.getExternalStorageDirectory());
        File[] scoped = getExternalFilesDirs(null);
        if (scoped != null) for (File file : scoped) if (file != null) {
            String path = file.getAbsolutePath();
            int i = path.indexOf("/Android/");
            if (i > 0) {
                File volume = new File(path.substring(0, i));
                boolean exists = false;
                for (File prior : volumes) if (prior.equals(volume)) { exists = true; break; }
                if (!exists && volume.isDirectory()) volumes.add(volume);
            }
        }
        List<String> labels = new ArrayList<>();
        for (File v : volumes) labels.add(v.getAbsolutePath());
        new AlertDialog.Builder(this).setTitle("Storage locations")
            .setItems(labels.toArray(new String[0]), (d, which) -> showFolderDialog(volumes.get(which),onSelect))
            .setNegativeButton("Cancel", null).show();
    }

    private final class CandidateAdapter extends BaseAdapter {
        @Override public int getCount() { return candidates.size(); }
        @Override public Object getItem(int pos) { return candidates.get(pos); }
        @Override public long getItemId(int pos) { return pos; }
        @Override public View getView(int pos, View recycled, ViewGroup parent) {
            final RenameCore.Candidate c = candidates.get(pos);
            LinearLayout wrap = horizontal();
            wrap.setGravity(Gravity.CENTER_VERTICAL);
            wrap.setBackground(round(PANEL, BORDER, dp(10)));
            wrap.setPadding(dp(7), dp(11), dp(9), dp(11));
            CheckBox check = new CheckBox(MainActivity.this);
            check.setButtonTintList(android.content.res.ColorStateList.valueOf(ACCENT));
            check.setChecked(c.selected);
            check.setContentDescription("Include " + c.entry.path);
            wrap.addView(check);
            check.setOnCheckedChangeListener((b, selected) -> { c.selected = selected; replan(); });
            LinearLayout names = vertical();
            TextView before = label((c.entry.directory ? "FOLDER  ·  " : "") + c.entry.path, 13, MUTED, false);
            before.setSingleLine(false);
            names.addView(before);
            String target = currentPlan != null ? currentPlan.destinations.get(c.entry.path) : null;
            if (target == null) target = c.requestedTarget;
            TextView after = label("→  " + target, 14, c.selected ? ACCENT : MUTED, true);
            after.setSingleLine(false);
            names.addView(after, spacing(dp(4), 0));
            String error = currentPlan == null ? null : currentPlan.itemErrors.get(c.entry.path);
            if (error != null) {
                TextView e = label("!  " + error, 12, DANGER, false);
                names.addView(e, spacing(dp(5), 0));
            }
            wrap.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
            Button edit = smallButton("Edit", false);
            wrap.addView(edit);
            edit.setOnClickListener(v -> editCandidate(c));
            names.setOnClickListener(v -> editCandidate(c));
            return wrap;
        }
    }

    private void errorDialog(String title, String text) {
        new AlertDialog.Builder(this).setTitle(title)
            .setMessage(text == null ? "Unknown error" : text)
            .setPositiveButton("OK", null).show();
    }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    private int dp(float x) { return (int)(x * getResources().getDisplayMetrics().density + 0.5f); }
    private LinearLayout vertical() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout horizontal() { LinearLayout l = new LinearLayout(this); l.setGravity(Gravity.CENTER_VERTICAL); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private LinearLayout.LayoutParams spacing(int top, int bottom) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);p.topMargin=top;p.bottomMargin=bottom;return p; }
    private void pad(View v, int left, int top, int right, int bottom) { v.setPadding(left, top, right, bottom); }
    private GradientDrawable round(int fill, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill); d.setCornerRadius(radius); d.setStroke(dp(1), stroke);
        return d;
    }
    private LinearLayout card() {
        LinearLayout l = vertical();
        l.setPadding(dp(14), dp(11), dp(14), dp(11));
        l.setBackground(round(PANEL, BORDER, dp(12)));
        return l;
    }
    private TextView label(String text, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text); t.setTextColor(color); t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_SIMPLE);
        return t;
    }
    private EditText edit(String value, String hint) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setTextSize(16);
        e.setTextColor(FG);
        e.setHintTextColor(MUTED);
        e.setSelectAllOnFocus(false);
        e.setHint(hint);
        e.setText(value);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        e.setPadding(dp(12), dp(11), dp(12), dp(11));
        e.setBackground(round(PANEL, BORDER, dp(10)));
        return e;
    }
    private RadioButton optionRadio(String text) {
        RadioButton b = new RadioButton(this);
        b.setText(text); b.setTextSize(13); b.setTextColor(FG);
        b.setButtonTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        b.setPadding(0, 0, dp(15), 0);
        return b;
    }
    private Switch createToggle(LinearLayout container, String text, boolean checked) {
        Switch s = new Switch(this);
        s.setText(text);s.setTextColor(FG);s.setTextSize(14);
        s.setChecked(checked);
        s.setShowText(false);
        s.setThumbTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        LinearLayout.LayoutParams p = spacing(dp(3), dp(3));
        container.addView(s, p);
        return s;
    }
    private Button smallButton(String text, boolean primary) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTextColor(primary ? BG : FG);
        b.setBackground(round(primary ? ACCENT : PANEL, primary ? ACCENT : BORDER, dp(11)));
        b.setPadding(dp(14), dp(4), dp(14), dp(4));
        b.setMinHeight(dp(43));
        b.setMinimumHeight(dp(43));
        return b;
    }
}
