package com.pathshift.app;

import java.io.IOException;
import java.io.File;
import java.nio.file.LinkOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Executes verified metadata-only operations with collision-free temporary staging. */
public final class BatchCommit {
    private BatchCommit() {}
    public interface Listener { void message(String message); }

    public static final class Result {
        public final int operations;
        public final int affectedPaths;
        public Result(int operations, int affectedPaths) {
            this.operations = operations; this.affectedPaths = affectedPaths;
        }
    }

    private static final class Staged {
        RenameCore.Move move;
        Path temporary;
        boolean placed;
        Staged(RenameCore.Move move, Path temporary) { this.move = move; this.temporary = temporary; }
    }

    public static Result commit(Path root, List<RenameCore.Entry> original,
                                RenameCore.Plan plan, Listener listener) throws IOException {
        if (!plan.okay()) throw new IOException("Resolve the preview errors before saving.");
        if (!Files.isDirectory(root)) throw new IOException("The selected folder is no longer available.");
        ensureNoUnfinishedBatch(root);
        // Re-scan first. Any creation, deletion or modification since the preview
        // invalidates the whole batch. File contents are never read.
        listener.message("Checking for changes since preview…");
        List<RenameCore.Entry> latest = RenameCore.scan(root, new AtomicBoolean(false), null);
        if (latest.size() != original.size()) throw new IOException("The folder changed since the preview. Preview again before saving.");
        Map<String, RenameCore.Entry> fresh = new HashMap<>();
        for (RenameCore.Entry e : latest) fresh.put(e.path, e);
        for (RenameCore.Entry old : original) {
            RenameCore.Entry now = fresh.get(old.path);
            if (now == null || old.directory != now.directory ||
                (!old.directory && (old.size != now.size || old.modified != now.modified)))
                throw new IOException("A file changed since preview: " + old.path + ". Preview again.");
        }

        Path stage = root.resolve(RenameCore.STAGE_PREFIX + UUID.randomUUID().toString());
        List<Staged> staged = new ArrayList<>();
        List<Path> createdDirs = new ArrayList<>();
        boolean begun = false;
        try {
            Files.createDirectory(stage);
            begun = true;
            List<RenameCore.Move> bottomUp = new ArrayList<>(plan.moves);
            Collections.sort(bottomUp, new Comparator<RenameCore.Move>() {
                @Override public int compare(RenameCore.Move a, RenameCore.Move b) {
                    int diff = RenameCore.depth(b.entry.path) - RenameCore.depth(a.entry.path);
                    return diff == 0 ? b.entry.path.compareTo(a.entry.path) : diff;
                }
            });
            int index = 0;
            for (RenameCore.Move move : bottomUp) {
                Path source = resolveInside(root, move.entry.path);
                Path temp = stage.resolve(String.format(Locale.ROOT, "%06d", index++));
                listener.message("Preparing " + move.entry.path);
                safeMove(source, temp);
                staged.add(new Staged(move, temp));
            }

            List<Staged> targetOrder = new ArrayList<>(staged);
            Collections.sort(targetOrder, new Comparator<Staged>() {
                @Override public int compare(Staged a, Staged b) {
                    int diff = RenameCore.depth(a.move.target) - RenameCore.depth(b.move.target);
                    return diff == 0 ? a.move.target.compareTo(b.move.target) : diff;
                }
            });
            for (Staged item : targetOrder) {
                Path dest = resolveInside(root, item.move.target);
                mkdirParents(root, dest.getParent(), createdDirs);
                listener.message("Saving " + item.move.target);
                safeMove(item.temporary, dest);
                item.placed = true;
            }
            Files.delete(stage);
            listener.message("Saved " + staged.size() + " operations.");
            return new Result(staged.size(), plan.changedPaths);
        } catch (Exception failure) {
            if (begun) {
                listener.message("Save failed. Attempting to restore originals…");
                List<String> restoreErrors = new ArrayList<>();
                // Pull completed destinations back to the stage, deepest first.
                List<Staged> placed = new ArrayList<>(staged);
                Collections.sort(placed, new Comparator<Staged>() {
                    @Override public int compare(Staged a, Staged b) {
                        return RenameCore.depth(b.move.target) - RenameCore.depth(a.move.target);
                    }
                });
                for (Staged item : placed) if (item.placed) {
                    try { safeMove(resolveInside(root, item.move.target), item.temporary); item.placed = false; }
                    catch (Exception ex) { restoreErrors.add(item.move.target + ": " + ex.getMessage()); }
                }
                // Restore original parents before their children.
                Collections.sort(staged, new Comparator<Staged>() {
                    @Override public int compare(Staged a, Staged b) {
                        return RenameCore.depth(a.move.entry.path) - RenameCore.depth(b.move.entry.path);
                    }
                });
                for (Staged item : staged) if (!item.placed && Files.exists(item.temporary)) {
                    try {
                        Path back = resolveInside(root, item.move.entry.path);
                        mkdirParents(root, back.getParent(), createdDirs);
                        safeMove(item.temporary, back);
                    } catch (Exception ex) { restoreErrors.add(item.move.entry.path + ": " + ex.getMessage()); }
                }
                if (restoreErrors.isEmpty()) {
                    for (int i = createdDirs.size() - 1; i >= 0; i--) {
                        try { Files.deleteIfExists(createdDirs.get(i)); }
                        catch (Exception ignored) { /* preserve anything nonempty */ }
                    }
                    try { Files.delete(stage); }
                    catch (Exception ex) { restoreErrors.add("Temporary staging cleanup: " + ex.getMessage()); }
                }
                if (!restoreErrors.isEmpty()) {
                    throw new IOException("A save operation failed: " + failure.getMessage()
                        + "\nAutomatic restoration was incomplete. Your items are not deleted. "
                        + "Do not start another batch until recovered. Temporary folder: " + stage
                        + "\nRecovery issues: " + restoreErrors, failure);
                }
            }
            throw new IOException("Nothing was committed. Original filenames were restored. Cause: " + failure.getMessage(), failure);
        }
    }

    public static void ensureNoUnfinishedBatch(Path root) throws IOException {
        File[] existing = root.toFile().listFiles();
        if (existing == null) throw new IOException("Cannot list the selected working folder.");
        for (File file : existing) if (file.getName().startsWith(RenameCore.STAGE_PREFIX))
            throw new IOException("An unfinished PathShift batch exists: " + file
                + ". Examine and recover those files before starting another batch.");
    }

    static Path resolveInside(Path root, String relative) throws IOException {
        String issue = RenameCore.validateRelative(relative);
        if (issue != null) throw new IOException("Unsafe destination " + relative + ": " + issue);
        Path normalized = root.resolve(relative.replace('/', java.io.File.separatorChar)).normalize();
        if (!normalized.startsWith(root) || normalized.equals(root)) throw new IOException("Path escapes selected folder: " + relative);
        Path cursor = normalized.getParent();
        while (cursor != null && cursor.startsWith(root)) {
            if (Files.isSymbolicLink(cursor)) throw new IOException("Symlinked folder is not supported: " + cursor);
            if (cursor.equals(root)) break;
            cursor = cursor.getParent();
        }
        return normalized;
    }

    private static void mkdirParents(Path root, Path dir, List<Path> created) throws IOException {
        if (dir == null || dir.equals(root)) return;
        ArrayList<Path> missing = new ArrayList<>();
        Path cursor = dir;
        while (cursor != null && !Files.exists(cursor)) {
            if (!cursor.startsWith(root)) throw new IOException("Cannot create folder outside selection.");
            missing.add(cursor);
            cursor = cursor.getParent();
        }
        if (cursor != null && !Files.isDirectory(cursor)) throw new IOException("A required parent is a file: " + cursor);
        for (int i = missing.size() - 1; i >= 0; i--) {
            Files.createDirectory(missing.get(i));
            created.add(missing.get(i));
        }
    }

    private static void safeMove(Path from, Path to) throws IOException {
        if (Files.exists(to, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Destination already exists: " + to);
        // Deliberately do not request REPLACE_EXISTING or ATOMIC_MOVE (whose collision
        // behavior is filesystem-specific). Moving on one volume does not copy bytes.
        Files.move(from, to);
    }
}
