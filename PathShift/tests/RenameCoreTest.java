import com.pathshift.app.RenameCore;
import com.pathshift.app.BatchCommit;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.io.IOException;

public class RenameCoreTest {
    private static int tests = 0;
    private static void yes(boolean ok, String label) { tests++; if (!ok) throw new AssertionError(label); }
    private static void eq(Object expect, Object actual, String msg) { yes(Objects.equals(expect, actual), msg + ": expected " + expect + " got " + actual); }
    private static List<RenameCore.Entry> fixture(String... paths) {
        ArrayList<RenameCore.Entry> a = new ArrayList<>();
        for (String name:paths) a.add(new RenameCore.Entry(name, !name.contains("."), 0, 1));
        return a;
    }
    private static RenameCore.Options opts(boolean full) { RenameCore.Options o = new RenameCore.Options();o.fullPath=full;o.includeFolders=true;return o; }
    public static void main(String[] args) throws Exception {
        yes(RenameCore.validateRelative("A/B/C.jpg") == null, "normal path");
        for(String x:new String[]{"../escape","/outside","a//b","a/./b","a/../b","bad\\slash","foo|bar","abc/",".pathshift-stage-xx/a"}) yes(RenameCore.validateRelative(x) != null, "reject "+x);
        List<RenameCore.Entry> es=fixture("A","A/IMG_03.jpg","B","B/a.jpg");
        RenameCore.Options o=opts(false);
        List<RenameCore.Candidate> p=RenameCore.preview(es,"IMG_(\\d+)\\.jpg","Holiday_$1.jpg",o);
        eq(1,p.size(),"regex candidates");
        eq("A/Holiday_03.jpg",p.get(0).requestedTarget,"group replacement");
        RenameCore.Plan plan=RenameCore.plan(es,p);
        yes(plan.okay(),"rename plan okay");
        eq(1,plan.moves.size(),"one move");
        o.fullPath=true;
        p=RenameCore.preview(es,"^A","C",o);
        eq(2,p.size(),"path prefix changes two paths");
        plan=RenameCore.plan(es,p);
        yes(plan.okay(),"parent and child inheritance");
        eq("C",plan.destinations.get("A"),"parent rename");
        eq("C/IMG_03.jpg",plan.destinations.get("A/IMG_03.jpg"),"child inherits");
        eq(1,plan.moves.size(),"child does not move twice");
        RenameCore.Options justFolders=opts(false);
        justFolders.includeFiles=false;
        List<RenameCore.Candidate> folderOnly=RenameCore.preview(es,"^A$","C",justFolders);
        eq(1,folderOnly.size(),"only the folder matches");
        RenameCore.Plan inherited=RenameCore.plan(es,folderOnly);
        eq(2,inherited.changedPaths,"all affected paths include inherited child paths");
        eq("C/IMG_03.jpg",inherited.destinations.get("A/IMG_03.jpg"),"inherited child path is visible in plan");
        p=RenameCore.preview(es,"^A","B",o);
        plan=RenameCore.plan(es,p);
        yes(!plan.okay(),"collision with untouched B");
        yes(!plan.errors.isEmpty(),"collision explained");
        p=RenameCore.preview(es,"^A","C",o);
        p.get(1).requestedTarget="A/original.jpg";
        plan=RenameCore.plan(es,p);
        eq("A/original.jpg",plan.destinations.get("A/IMG_03.jpg"),"manual override is literal relative path");
        yes(plan.okay(),"manual override + parent move valid");
        p=RenameCore.preview(es,"\\.jpg$",".png",o);
        eq(2,p.size(),"replace across files");
        p.get(0).requestedTarget="../bad";
        plan=RenameCore.plan(es,p);
        yes(!plan.okay(),"traversal rejected");
        o.ignoreCase=true;
        p=RenameCore.preview(es,"img_(\\d+)","photo_$1",o);
        eq(1,p.size(),"case insensitive");
        // Verify real moves and rollback without reading a file's contents in the engine.
        Path root=Files.createTempDirectory("pathshift-test-");
        try {
            Files.createDirectories(root.resolve("A")); Files.createDirectories(root.resolve("B"));
            Files.write(root.resolve("A/file1.txt"),new byte[]{1,2,3});
            Files.write(root.resolve("B/file2.txt"),new byte[]{4,5,6});
            List<RenameCore.Entry> scanned=RenameCore.scan(root,new AtomicBoolean(false),null);
            RenameCore.Options full=opts(true); full.includeFolders=false;
            List<RenameCore.Candidate> cand=RenameCore.preview(scanned,"^(A/file1|B/file2)\\.txt$","$1.swap.txt",full);
            eq(2,cand.size(),"two file changes");
            cand.get(0).requestedTarget="B/file2.txt";
            cand.get(1).requestedTarget="A/file1.txt";
            RenameCore.Plan swap=RenameCore.plan(scanned,cand);
            yes(swap.okay(),"swap plan validates");
            BatchCommit.commit(root,scanned,swap,new BatchCommit.Listener(){public void message(String x){}});
            yes(Arrays.equals(Files.readAllBytes(root.resolve("A/file1.txt")),new byte[]{4,5,6}),"file swap A");
            yes(Arrays.equals(Files.readAllBytes(root.resolve("B/file2.txt")),new byte[]{1,2,3}),"file swap B");
            scanned=RenameCore.scan(root,new AtomicBoolean(false),null);
            full.includeFolders=true;
            cand=RenameCore.preview(scanned,"^A","C",full);
            RenameCore.Plan folder=RenameCore.plan(scanned,cand);
            yes(folder.okay(),"folder move validates");
            BatchCommit.commit(root,scanned,folder,new BatchCommit.Listener(){public void message(String x){}});
            yes(Files.exists(root.resolve("C/file1.txt")),"folder renamed, child follows");
            yes(!Files.exists(root.resolve("A")),"old folder removed");
            // stale preview must be refused
            scanned=RenameCore.scan(root,new AtomicBoolean(false),null);
            cand=RenameCore.preview(scanned,"file1","newfile",full);
            RenameCore.Plan stale=RenameCore.plan(scanned,cand);
            Files.write(root.resolve("C/file1.txt"),new byte[]{9});
            boolean rejected=false;
            try { BatchCommit.commit(root,scanned,stale,new BatchCommit.Listener(){public void message(String x){}}); }
            catch(IOException expected){rejected=true;}
            yes(rejected,"stale snapshot refused");
            yes(Files.exists(root.resolve("C/file1.txt")),"preflight did not touch files");
        } finally {
            Files.walk(root).sorted(Comparator.reverseOrder()).forEach(x->{try{Files.deleteIfExists(x);}catch(IOException ignored){}});
        }
        System.out.println("PASS: "+tests+" assertions");
    }
}
