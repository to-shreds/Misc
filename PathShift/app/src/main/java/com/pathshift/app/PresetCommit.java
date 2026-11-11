package com.pathshift.app;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Previewed one-volume file moves using temporary staging and best-effort rollback. */
public final class PresetCommit {
    private PresetCommit() {}
    public interface Listener { void message(String text); }
    public static class Result { public final int changed; Result(int n) { changed=n; } }
    private static class Step {
        final Path original, destination, staged;
        boolean placed;
        Step(Path a, Path b, Path c) { original=a; destination=b; staged=c; }
    }
    public static Result commit(Path root, List<RenameCore.Entry> snapshot, PresetCore.Plan plan,
                                Listener listener) throws IOException {
        if (!plan.okay()) throw new IOException("Resolve the destination conflicts before saving.");
        Path base = root.toAbsolutePath().normalize();
        BatchCommit.ensureNoUnfinishedBatch(base);
        listener.message("Verifying files have not changed since preview…");
        List<RenameCore.Entry> recent = RenameCore.scan(base, new AtomicBoolean(false), null);
        if (recent.size() != snapshot.size()) throw new IOException("Files have changed since preview. Run preview again.");
        Map<String, RenameCore.Entry> current = new HashMap<>();
        for (RenameCore.Entry e : recent) current.put(e.path,e);
        for (RenameCore.Entry e : snapshot) {
            RenameCore.Entry now = current.get(e.path);
            if (now == null || now.directory != e.directory ||
                (!e.directory && (now.size != e.size || now.modified != e.modified)))
                throw new IOException("Files have changed since preview: " + e.path);
        }
        FileStore store = Files.getFileStore(base);
        // Check every destination before staging. Same volume is essential to avoid
        // content-copy/delete behavior for a cross-volume Files.move implementation.
        for (PresetCore.Proposal item : plan.moves) {
            Path dest = item.target;
            Path cursor = dest.getParent();
            while (cursor != null && !Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)) cursor=cursor.getParent();
            if (cursor == null || !Files.isDirectory(cursor)) throw new IOException("Destination folder is unavailable: " + dest);
            if (!store.equals(Files.getFileStore(cursor)))
                throw new IOException("Moving between storage volumes is not supported without copying file contents. Choose a destination on the same volume.");
        }
        PresetCore.Plan fresh = PresetCore.plan(base,recent,plan.moves);
        if (!fresh.okay()) throw new IOException("A destination now conflicts with an existing file. Preview again.");
        Path stage = base.resolve(RenameCore.STAGE_PREFIX + UUID.randomUUID());
        List<Step> steps = new ArrayList<>();
        List<Path> created = new ArrayList<>();
        boolean started = false;
        try {
            Files.createDirectory(stage);
            started=true;
            int n=0;
            for (PresetCore.Proposal move : plan.moves) {
                Path src = base.resolve(move.entry.path).normalize();
                Path temp = stage.resolve(String.format(java.util.Locale.ROOT, "%06d", n++));
                listener.message("Preparing " + move.entry.path);
                safeMove(src,temp);
                steps.add(new Step(src,move.target,temp));
            }
            for (Step step : steps) {
                listener.message("Moving to " + step.destination);
                mkdirsSafe(step.destination.getParent(),created);
                safeMove(step.staged,step.destination);
                step.placed=true;
            }
            Files.delete(stage);
            return new Result(steps.size());
        } catch (Exception err) {
            if (started) {
                listener.message("Save failed. Restoring original files…");
                List<String> failures = new ArrayList<>();
                for (int i=steps.size()-1;i>=0;i--) {
                    Step step=steps.get(i);
                    if (step.placed) {
                        try { safeMove(step.destination,step.staged);step.placed=false; }
                        catch(Exception ex){failures.add(step.destination + ": " + ex.getMessage());}
                    }
                }
                for (int i=steps.size()-1;i>=0;i--) {
                    Step step=steps.get(i);
                    if (!step.placed && Files.exists(step.staged)) {
                        try { safeMove(step.staged,step.original); }
                        catch(Exception ex){failures.add(step.original + ": " + ex.getMessage());}
                    }
                }
                for (int i=created.size()-1;i>=0;i--) {
                    try {Files.deleteIfExists(created.get(i));}
                    catch(Exception ignored) { /* nonempty folders must be preserved */ }
                }
                if (failures.isEmpty()) {
                    try {Files.delete(stage);}
                    catch(Exception ex){failures.add("Temporary stage cleanup: " + ex.getMessage());}
                }
                if (!failures.isEmpty()) throw new IOException("Some files could not be automatically restored. "
                    + "Do not begin another batch. Recovery folder: " + stage + "\n" + failures, err);
            }
            throw new IOException("Save failed; originals were restored. " + err.getMessage(),err);
        }
    }
    static void safeMove(Path from, Path to) throws IOException {
        if (Files.exists(to, LinkOption.NOFOLLOW_LINKS)) throw new IOException("File already exists: " + to);
        Files.move(from,to);
    }
    static void mkdirsSafe(Path folder,List<Path> created) throws IOException {
        List<Path> missing = new ArrayList<>();
        Path p=folder;
        while (p!=null && !Files.exists(p,LinkOption.NOFOLLOW_LINKS)) {missing.add(p);p=p.getParent();}
        if (p==null || !Files.isDirectory(p) || Files.isSymbolicLink(p))
            throw new IOException("Invalid destination parent: " + p);
        // Reject symlinks in any existing ancestor, not merely the first one.
        Path walk=p;
        while (walk!=null) {
            if (Files.isSymbolicLink(walk)) throw new IOException("Linked folder not supported: " + walk);
            walk=walk.getParent();
        }
        for(int i=missing.size()-1;i>=0;i--) {
            Files.createDirectory(missing.get(i));created.add(missing.get(i));
        }
    }
}
