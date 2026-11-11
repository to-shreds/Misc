package com.pathshift.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** File-name-only recipes; no file contents are inspected or modified. */
public final class PresetCore {
    private PresetCore() {}
    public enum Kind {
        MOVE_NEW, SORT_EXTENSION, PREFIX, SUFFIX, NUMBER, REPLACE_TEXT, LOWERCASE, CLEAN_SPACES
    }
    public static class Settings {
        public Kind kind = Kind.MOVE_NEW;
        public Path destinationParent;
        public String newFolder = "Collected";
        public boolean recursive = false;
        public boolean preserveSubfolders = true;
        public boolean ignoreCase = true;
        public boolean filterRegex = false;
        public boolean filterFullPath = false;
        public String filter = "";
        public String text = "";
        public String replacement = "";
        public int startNumber = 1;
        public int numberDigits = 3;
    }
    public static class Proposal {
        public final RenameCore.Entry entry;
        public final Path originalTarget;
        public Path target;
        public boolean selected = true;
        public Proposal(RenameCore.Entry entry, Path target) {
            this.entry = entry;
            this.originalTarget = target;
            this.target = target;
        }
    }
    public static class Plan {
        public final List<Proposal> moves = new ArrayList<>();
        public final Map<String, String> itemErrors = new HashMap<>();
        public final Map<String, List<String>> conflicts = new LinkedHashMap<>();
        public final List<String> issues = new ArrayList<>();
        public int totalErrors = 0;
        public boolean okay() { return totalErrors == 0 && !moves.isEmpty(); }
        private void fail(String path, String issue) {
            totalErrors++;
            if (issues.size() < 12) issues.add(path + ": " + issue);
            if (!itemErrors.containsKey(path)) itemErrors.put(path, issue);
        }
    }

    public static List<Proposal> preview(Path root, List<RenameCore.Entry> entries, Settings s) throws IOException {
        if (root == null || !Files.isDirectory(root)) throw new IOException("Choose an accessible working folder.");
        Path destination = null;
        if (s.kind == Kind.MOVE_NEW || s.kind == Kind.SORT_EXTENSION) {
            if (s.destinationParent == null || !Files.isDirectory(s.destinationParent))
                throw new IOException("Choose the folder where new folders should be created.");
            if (s.newFolder.isEmpty() || s.newFolder.indexOf('/') >= 0 || s.newFolder.indexOf('\\') >= 0 ||
                RenameCore.validateRelative(s.newFolder) != null)
                throw new IOException("Enter a valid single new-folder name without / or \\.");
            destination = s.destinationParent.toAbsolutePath().normalize().resolve(s.newFolder);
            if (!Files.getFileStore(root).equals(Files.getFileStore(s.destinationParent)))
                throw new IOException("The destination is on another storage volume. PathShift moves filenames without copying file contents, so choose a folder on the same storage volume.");
            if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("A folder named '" + s.newFolder + "' already exists here. Choose a new name.");
        }
        if (s.kind == Kind.REPLACE_TEXT && s.text.isEmpty())
            throw new IOException("Enter the text to replace.");
        if (s.kind == Kind.NUMBER && (s.startNumber < 0 || s.numberDigits < 1 || s.numberDigits > 8))
            throw new IOException("Numbering requires a nonnegative start and 1 to 8 digits.");
        Pattern filter = null;
        if (!s.filter.isEmpty()) filter = Pattern.compile(s.filterRegex ? s.filter : Pattern.quote(s.filter),
            s.ignoreCase ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0);
        final Path base = root.toAbsolutePath().normalize();
        List<Proposal> results = new ArrayList<>();
        int ordinal = s.startNumber;
        for (RenameCore.Entry entry : entries) {
            if (entry.directory) continue; // presets never move complete directory trees
            if (!s.recursive && entry.path.indexOf('/') >= 0) continue;
            String filename = RenameCore.name(entry.path);
            String matchInput = s.filterFullPath ? entry.path : filename;
            if (filter != null && !filter.matcher(matchInput).find()) continue;
            Path source = base.resolve(entry.path);
            Path target;
            switch (s.kind) {
                case MOVE_NEW:
                    target = destination.resolve(s.recursive && s.preserveSubfolders ? entry.path : filename);
                    break;
                case SORT_EXTENSION:
                    String ext = extension(filename);
                    String subfolder = ext.isEmpty() ? "No extension" : ext.toUpperCase(Locale.ROOT);
                    target = destination.resolve(subfolder)
                        .resolve(s.recursive && s.preserveSubfolders ? entry.path : filename);
                    break;
                case PREFIX:
                    target = source.resolveSibling(s.text + filename);
                    break;
                case SUFFIX:
                    target = source.resolveSibling(stem(filename) + s.text + dotExtension(filename));
                    break;
                case NUMBER:
                    target = source.resolveSibling(String.format(Locale.ROOT,
                        "%0" + s.numberDigits + "d", ordinal++) + "_" + filename);
                    break;
                case REPLACE_TEXT:
                    Pattern find = Pattern.compile(Pattern.quote(s.text),
                        s.ignoreCase ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0);
                    target = source.resolveSibling(find.matcher(filename)
                        .replaceAll(Matcher.quoteReplacement(s.replacement)));
                    break;
                case LOWERCASE:
                    target = source.resolveSibling(filename.toLowerCase(Locale.ROOT));
                    break;
                case CLEAN_SPACES:
                    target = source.resolveSibling(filename.trim().replaceAll("\\s+", "_"));
                    break;
                default: throw new IOException("Unsupported preset.");
            }
            target = target.toAbsolutePath().normalize();
            if (!source.equals(target)) results.add(new Proposal(entry, target));
        }
        return results;
    }

    public static String stem(String name) {
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? name : name.substring(0, dot);
    }
    public static String dotExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? "" : name.substring(dot);
    }
    public static String extension(String name) {
        String ext = dotExtension(name);
        return ext.isEmpty() ? "" : ext.substring(1);
    }

    /** Validate all chosen destinations and existing files, including files outside working root. */
    public static Plan plan(Path root, List<RenameCore.Entry> scan, List<Proposal> proposals) {
        Path base = root.toAbsolutePath().normalize();
        Plan plan = new Plan();
        Set<String> selectedSources = new HashSet<>();
        Map<String, List<Proposal>> destinations = new LinkedHashMap<>();
        Map<String, String> existingScan = new HashMap<>();
        for (RenameCore.Entry e : scan) existingScan.put(lower(base.resolve(e.path)), e.path);
        for (Proposal p : proposals) if (p.selected) {
            plan.moves.add(p);
            selectedSources.add(lower(base.resolve(p.entry.path)));
            String key = lower(p.target);
            if (!destinations.containsKey(key)) destinations.put(key, new ArrayList<>());
            destinations.get(key).add(p);
        }
        for (Proposal p : plan.moves) {
            String source = p.entry.path;
            Path target = p.target;
            String name = target.getFileName() == null ? "" : target.getFileName().toString();
            String error = RenameCore.validateRelative(name);
            if (error != null) plan.fail(source, error);
            if (!target.isAbsolute()) plan.fail(source, "Target must have an absolute path.");
            if (base.resolve(source).equals(target))
                plan.fail(source, "New filename is unchanged.");
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !selectedSources.contains(lower(target)))
                plan.fail(source, "Destination already exists and will not be overwritten: " + target);
            Path cursor = target.getParent();
            while (cursor != null) {
                if (Files.isSymbolicLink(cursor)) {
                    plan.fail(source, "Destination cannot pass through a linked folder: " + cursor);
                    break;
                }
                if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(cursor)) {
                    plan.fail(source, "A file blocks the new folder: " + cursor);
                    break;
                }
                cursor = cursor.getParent();
            }
        }
        for (Map.Entry<String, List<Proposal>> group : destinations.entrySet()) {
            if (group.getValue().size() <= 1) continue;
            ArrayList<String> sources = new ArrayList<>();
            for (Proposal p : group.getValue()) {
                sources.add(p.entry.path);
                plan.fail(p.entry.path, "Other selected files would also use this destination.");
            }
            plan.conflicts.put(group.getKey(), sources);
        }
        return plan;
    }

    public static int numberConflicts(Path root, List<RenameCore.Entry> scan, List<Proposal> proposals) {
        Plan current = plan(root, scan, proposals);
        Set<String> occupied = new HashSet<>();
        Set<String> selectedSources = new HashSet<>();
        for (Proposal p : proposals) if (p.selected) selectedSources.add(p.entry.path);
        for (RenameCore.Entry e : scan) {
            Path path = root.resolve(e.path).toAbsolutePath().normalize();
            if (!selectedSources.contains(e.path)) occupied.add(lower(path));
        }
        for (Proposal p : proposals) if (p.selected) {
            if (!current.conflicts.containsKey(lower(p.target)) && !Files.exists(p.target))
                occupied.add(lower(p.target));
        }
        int altered = 0;
        for (Proposal p : proposals) {
            if (!p.selected) continue;
            String key = lower(p.target);
            if (!current.conflicts.containsKey(key) && !Files.exists(p.target)) continue;
            if (occupied.add(key)) continue;
            Path parent = p.target.getParent();
            int count = 2;
            Path proposal;
            do {
                proposal = parent.resolve(RenameCore.name(RenameCore.withNumericSuffix(p.target.getFileName().toString(),count++)));
            } while ((occupied.contains(lower(proposal)) || Files.exists(proposal)) && count < 999999);
            if (count >= 999999) continue;
            p.target = proposal;
            occupied.add(lower(proposal));
            altered++;
        }
        return altered;
    }
    private static String lower(Path path) { return path.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT); }
}
