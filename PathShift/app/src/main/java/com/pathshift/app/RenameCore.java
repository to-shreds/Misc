package com.pathshift.app;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure Java model: metadata-only scan, regex preview, dependency-aware path planning. */
public final class RenameCore {
    private RenameCore() {}
    public static final int MAX_ENTRIES = 250000;
    public static final String STAGE_PREFIX = ".pathshift-stage-";

    public static final class Entry {
        public final String path;
        public final boolean directory;
        public final long size;
        public final long modified;
        public Entry(String path, boolean directory, long size, long modified) {
            this.path = path; this.directory = directory;
            this.size = size; this.modified = modified;
        }
    }

    public static final class Candidate {
        public final Entry entry;
        public final String regexTarget;
        public String requestedTarget;
        public boolean selected = true;
        public Candidate(Entry entry, String proposedTarget) {
            this.entry = entry;
            this.regexTarget = proposedTarget;
            this.requestedTarget = proposedTarget;
        }
        public boolean overridden() { return !regexTarget.equals(requestedTarget); }
    }

    public static final class Options {
        public boolean fullPath = false;
        public boolean includeFolders = false;
        public boolean includeFiles = true;
        public boolean recursive = true;
        public boolean ignoreCase = false;
        public boolean replaceAll = true;
    }

    public static final class Move {
        public final Entry entry;
        public final String target;
        public Move(Entry entry, String target) { this.entry = entry; this.target = target; }
    }

    public static final class Plan {
        public final Map<String, String> destinations = new LinkedHashMap<>();
        public final List<Move> moves = new ArrayList<>();
        public final List<String> errors = new ArrayList<>();
        public final Map<String, String> itemErrors = new HashMap<>();
        public int changedPaths = 0;
        public int selectedMatches = 0;
        public int movedFolders = 0;
        public boolean okay() { return errors.isEmpty() && !moves.isEmpty(); }
        private void fail(String path, String issue) {
            if (errors.size() < 40) errors.add(path + ": " + issue);
            if (!itemErrors.containsKey(path)) itemErrors.put(path, issue);
        }
    }

    public interface Progress { void onItem(int count); }

    public static List<Entry> scan(final Path root, final AtomicBoolean canceled, final Progress progress) throws IOException {
        if (!Files.isDirectory(root)) throw new IOException("The selected folder is not accessible.");
        final ArrayList<Entry> entries = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (canceled.get()) throw new IOException("Scan canceled.");
                if (!root.equals(dir)) {
                    if (dir.getFileName().toString().startsWith(STAGE_PREFIX)) return FileVisitResult.SKIP_SUBTREE;
                    add(dir, attrs);
                }
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (canceled.get()) throw new IOException("Scan canceled.");
                add(file, attrs);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFileFailed(Path file, IOException cause) throws IOException {
                throw new IOException("Cannot read " + root.relativize(file) + ": " + cause.getMessage(), cause);
            }
            private void add(Path file, BasicFileAttributes attrs) throws IOException {
                if (entries.size() >= MAX_ENTRIES)
                    throw new IOException("More than " + MAX_ENTRIES + " items. Choose a smaller folder.");
                String path = root.relativize(file).toString().replace(File.separatorChar, '/');
                entries.add(new Entry(path, attrs.isDirectory(), attrs.size(), attrs.lastModifiedTime().toMillis()));
                if (progress != null && entries.size() % 300 == 0) progress.onItem(entries.size());
            }
        });
        Collections.sort(entries, new Comparator<Entry>() {
            @Override public int compare(Entry a, Entry b) { return a.path.compareTo(b.path); }
        });
        return entries;
    }

    public static List<Candidate> preview(List<Entry> entries, String find, String replacement, Options opts) {
        if (find == null || find.isEmpty()) throw new IllegalArgumentException("Enter a regular expression to find.");
        int flags = opts.ignoreCase ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0;
        Pattern pattern = Pattern.compile(find, flags);
        ArrayList<Candidate> result = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.directory ? !opts.includeFolders : !opts.includeFiles) continue;
            if (!opts.recursive && entry.path.indexOf('/') >= 0) continue;
            String old = opts.fullPath ? entry.path : name(entry.path);
            Matcher matcher = pattern.matcher(old);
            if (!matcher.find()) continue;
            String updated;
            try { updated = opts.replaceAll ? matcher.replaceAll(replacement) : matcher.replaceFirst(replacement); }
            catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Invalid replacement: " + ex.getMessage(), ex); }
            catch (IndexOutOfBoundsException ex) { throw new IllegalArgumentException("Invalid $ group reference: " + ex.getMessage(), ex); }
            if (old.equals(updated)) continue;
            String target = opts.fullPath ? updated : join(parent(entry.path), updated);
            result.add(new Candidate(entry, target));
        }
        return result;
    }

    public static Plan plan(List<Entry> entries, List<Candidate> proposed) {
        Plan plan = new Plan();
        HashMap<String, Candidate> selected = new HashMap<>();
        for (Candidate c : proposed) if (c.selected) {
            selected.put(c.entry.path, c);
            plan.selectedMatches++;
        }
        ArrayList<Entry> topDown = new ArrayList<>(entries);
        Collections.sort(topDown, new Comparator<Entry>() {
            @Override public int compare(Entry a, Entry b) {
                int d = depth(a.path) - depth(b.path);
                return d != 0 ? d : a.path.compareTo(b.path);
            }
        });
        HashMap<String, Entry> original = new HashMap<>();
        for (Entry e : entries) original.put(e.path, e);
        for (Entry e : topDown) {
            String oldParent = parent(e.path);
            String newParent = oldParent.isEmpty() ? "" : plan.destinations.get(oldParent);
            if (newParent == null) {
                plan.fail(e.path, "Parent folder was not scanned.");
                newParent = oldParent;
            }
            String inherited = join(newParent, name(e.path));
            Candidate candidate = selected.get(e.path);
            String destination = inherited;
            if (candidate != null) {
                String request = candidate.requestedTarget;
                // Inherit a renamed ancestor unless the replacement explicitly points elsewhere.
                if (!candidate.overridden() && !oldParent.isEmpty() &&
                    (request.equals(oldParent) || request.startsWith(oldParent + "/"))) {
                    destination = newParent + request.substring(oldParent.length());
                    if (destination.startsWith("/")) destination = destination.substring(1);
                } else destination = request;
            }
            plan.destinations.put(e.path, destination);
            String invalid = validateRelative(destination);
            if (invalid != null) plan.fail(e.path, invalid);
            if (!e.path.equals(destination)) plan.changedPaths++;
            if (candidate != null && !inherited.equals(destination)) {
                plan.moves.add(new Move(e, destination));
                if (e.directory) plan.movedFolders++;
            }
        }
        // All scanned items must have distinct destinations. This includes untouched items
        // and unchanged descendants that travel with a renamed directory.
        Map<String, String> seen = new HashMap<>();
        Map<String, Entry> byDest = new HashMap<>();
        for (Entry e : topDown) {
            String dest = plan.destinations.get(e.path);
            if (validateRelative(dest) != null) continue;
            String normalized = dest.toLowerCase(Locale.ROOT);
            String prior = seen.put(normalized, e.path);
            if (prior != null && !prior.equals(e.path)) {
                plan.fail(e.path, "Destination already used by " + prior);
                plan.fail(prior, "Destination also used by " + e.path);
            }
            byDest.put(normalized, e);
        }
        for (Entry e : topDown) {
            String dest = plan.destinations.get(e.path);
            if (validateRelative(dest) != null) continue;
            String parent = parent(dest).toLowerCase(Locale.ROOT);
            while (!parent.isEmpty()) {
                Entry blocking = byDest.get(parent);
                if (blocking != null && !blocking.directory) {
                    plan.fail(e.path, "Destination has a file where a folder is required: " + parent);
                    break;
                }
                parent = parent(parent);
            }
        }
        // No planned target can be beneath a protected staging directory.
        return plan;
    }

    public static String validateRelative(String relative) {
        if (relative == null || relative.isEmpty()) return "Destination cannot be empty.";
        if (relative.startsWith("/") || relative.endsWith("/") || relative.contains("//")) return "Destination must be a relative path inside the selected folder.";
        if (relative.indexOf('\\') >= 0) return "Use / between folders, not backslashes.";
        String[] segments = relative.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) return "Invalid folder segment: " + segment;
            if (segment.startsWith(STAGE_PREFIX)) return "Reserved temporary folder name.";
            if (segment.endsWith(".") || segment.endsWith(" ")) return "Folder and file names cannot end with a dot or space.";
            if (segment.matches(".*[\\x00-\\x1F:*?\"<>|].*")) return "Name contains unsupported filename characters.";
            try { if (segment.getBytes("UTF-8").length > 255) return "A filename exceeds 255 bytes."; }
            catch (java.io.UnsupportedEncodingException impossible) { return "Invalid filename encoding."; }
        }
        return null;
    }

    public static int depth(String value) {
        if (value == null || value.isEmpty()) return 0;
        int d = 1;
        for (int i = 0; i < value.length(); i++) if (value.charAt(i) == '/') d++;
        return d;
    }
    public static String parent(String path) {
        int i = path.lastIndexOf('/');
        return i < 0 ? "" : path.substring(0, i);
    }
    public static String name(String path) {
        int i = path.lastIndexOf('/');
        return path.substring(i + 1);
    }
    public static String join(String dir, String name) { return dir.isEmpty() ? name : dir + "/" + name; }
}
