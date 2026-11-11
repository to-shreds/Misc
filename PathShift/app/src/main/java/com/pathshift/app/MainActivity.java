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
    private enum Mode { FIND, RENAME }
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
    private List<RenameCore.Entry> snapshot = new ArrayList<>();
    private List<RenameCore.Candidate> candidates = new ArrayList<>();
    private RenameCore.Plan currentPlan;
    private long planRevision = 0;

    private EditText findInput, replaceInput;
    private RadioGroup scopeGroup;
    private Switch switchFolders, switchFiles, switchRecursive, switchCase, switchAll;
    private LinearLayout rootView;
    private TextView summary, issue, folderStatus;
    private Button saveButton, previewButton, allPathsButton;
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
        return copy;
    }

    private void chooseMode(Mode next) {
        if (busy || mode == next) return;
        rememberEditor();
        mode = next;
        showEditor();
    }

    private void showEditor() {
        viewingPreview = false;
        viewingFindResults = false;
        rootView = vertical();
        rootView.setBackgroundColor(BG);
        setContentView(rootView);
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
        LinearLayout modes = horizontal();
        Button findTab = smallButton("Find", mode == Mode.FIND);
        Button renameTab = smallButton("Rename", mode == Mode.RENAME);
        modes.addView(findTab, new LinearLayout.LayoutParams(0, dp(45), 1));
        LinearLayout.LayoutParams renameTabParams = new LinearLayout.LayoutParams(0, dp(45), 1);
        renameTabParams.leftMargin = dp(9);
        modes.addView(renameTab, renameTabParams);
        findTab.setOnClickListener(v -> chooseMode(Mode.FIND));
        renameTab.setOnClickListener(v -> chooseMode(Mode.RENAME));
        body.addView(modes, spacing(dp(13), dp(12)));

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
            body.addView(replaceInput, spacing(dp(6), dp(14)));
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
            : "Full paths use /. Use $1, $2, etc. for regex capture groups.", 12, MUTED, false));

        LinearLayout toggles = card();
        body.addView(toggles, spacing(dp(16), dp(6)));
        switchFiles = createToggle(toggles, "Include files", active.includeFiles);
        switchFolders = createToggle(toggles, "Include folders", active.includeFolders);
        switchRecursive = createToggle(toggles, "Search subfolders", active.recursive);
        switchCase = createToggle(toggles, "Ignore case", active.ignoreCase);
        if (finding) {
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
        rootView.setPadding(dp(12), dp(8), dp(12), dp(10));
        setContentView(rootView);

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
        rootView.setPadding(dp(12), dp(8), dp(12), dp(10));
        setContentView(rootView);

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
            issue.setText(plan.errors.size() + " problem(s): " + plan.errors.get(0) + "\nUncheck or edit affected items to proceed.");
        } else if (plan.selectedMatches == 0) {
            issue.setText(candidates.isEmpty() ? "No paths matched the pattern. Go back to edit it." : "Select at least one change to save.");
        } else if (plan.moves.isEmpty()) {
            issue.setText("All selected changes are already covered by their parent folder changes.");
        } else issue.setText("");
        saveButton.setEnabled(!busy && plan.okay());
        if (allPathsButton != null) {
            allPathsButton.setText("View all " + plan.changedPaths + " affected paths");
            allPathsButton.setEnabled(!busy && plan.changedPaths > 0);
        }
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
        if (!hasFileAccess()) { requestAccess(); return; }
        File base = starting != null && starting.isDirectory() ? starting : Environment.getExternalStorageDirectory();
        showFolderDialog(base);
    }

    private void showFolderDialog(final File here) {
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
                if (canUp && which == 0) { showFolderDialog(here.getParentFile()); return; }
                int index = which - (canUp ? 1 : 0);
                if (index >= 0 && index < dirs.size()) showFolderDialog(dirs.get(index));
            })
            .setNeutralButton("Storage", (d, w) -> showVolumeDialog())
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Use this folder", (d, w) -> {
                folder = here;
                getPreferences(MODE_PRIVATE).edit().putString("folder", here.getAbsolutePath()).apply();
                snapshot = new ArrayList<>(); candidates = new ArrayList<>(); currentPlan = null;
                updateFolderStatus();
            }).create();
        dialog.show();
    }

    private boolean withinExternalStorage(File dir) {
        String path = dir.getAbsolutePath();
        return path.equals("/storage") || path.startsWith("/storage/");
    }

    private void showVolumeDialog() {
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
            .setItems(labels.toArray(new String[0]), (d, which) -> showFolderDialog(volumes.get(which)))
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
