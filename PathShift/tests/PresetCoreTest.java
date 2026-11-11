import com.pathshift.app.RenameCore;
import com.pathshift.app.PresetCore;
import com.pathshift.app.PresetCommit;
import com.pathshift.app.BatchCommit;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.io.IOException;

public final class PresetCoreTest {
    private static int assertions=0;
    static void ok(boolean condition,String label) { assertions++;if(!condition)throw new AssertionError(label); }
    static void eq(Object a,Object b,String label) {ok(Objects.equals(a,b),label+" expected:"+a+" actual:"+b);}
    static RenameCore.Entry file(String path){return new RenameCore.Entry(path,false,4,1);}
    static RenameCore.Entry dir(String path){return new RenameCore.Entry(path,true,0,1);}
    public static void main(String[] args) throws Exception {
        List<RenameCore.Entry> fixture=Arrays.asList(dir("sub"), file("A.jpg"),file("B.PNG"),
            file("sub/A.jpg"),file("noextension"),file("Image One.jpg"),file("Another Mag.jpg"));
        Path virtual = Paths.get(System.getProperty("java.io.tmpdir"));
        // We use real folders for all presets because each preview verifies accessible roots.
        Path sandbox=Files.createTempDirectory("pathshift-presets-");
        ok(PresetCore.sameVolume(sandbox,sandbox),"same filesystem check works on JVM");
        ok(PresetCore.sameVolume(Paths.get("/storage/emulated/0/DCIM"), Paths.get("/storage/emulated/0/Download")),
            "Android shared-storage mount recognized without getFileStore");
        ok(!PresetCore.sameVolume(Paths.get("/storage/emulated/0/DCIM"), Paths.get("/storage/AB12-CD34/DCIM")),
            "Android internal-to-SD move blocked without reading data");
        ok(PresetCore.sameVolume(Paths.get("/storage/AB12-CD34/A"), Paths.get("/storage/AB12-CD34/B")),
            "same SD card paths recognized");
        try {
            Path source=Files.createDirectory(sandbox.resolve("Source"));
            Path destination=Files.createDirectory(sandbox.resolve("Destination"));
            Files.createDirectory(source.resolve("sub"));
            Files.write(source.resolve("A.jpg"), new byte[]{1,2,3});
            Files.write(source.resolve("B.PNG"), new byte[]{4,5,6});
            Files.write(source.resolve("sub/A.jpg"), new byte[]{7,8,9});
            Files.write(source.resolve("noextension"), new byte[]{1});
            Files.write(source.resolve("Image One.jpg"), new byte[]{1});
            Files.write(source.resolve("Another Mag.jpg"), new byte[]{1});
            List<RenameCore.Entry> scan=RenameCore.scan(source,new AtomicBoolean(false),null);
            PresetCore.Settings s=new PresetCore.Settings();
            s.destinationParent=destination;
            s.newFolder="Mag";
            s.filter="Another Mag";
            List<PresetCore.Proposal> matches=PresetCore.preview(source,scan,s);
            eq(1,matches.size(),"literal filter");
            eq("Another Mag.jpg",matches.get(0).target.getFileName().toString(),"keep whole filename");
            ok(!Files.exists(destination.resolve("Mag")),"Preview did not make new folder");
            PresetCore.Plan plan=PresetCore.plan(source,scan,matches);
            ok(plan.okay(),"Move proposal valid");
            eq(1,plan.moves.size(),"one file changed");
            PresetCommit.commit(source,scan,plan,msg->{});
            ok(Files.exists(destination.resolve("Mag/Another Mag.jpg")),"moved outside selected root");
            ok(!Files.exists(source.resolve("Another Mag.jpg")),"old filename removed");
            eq(1,Files.readAllBytes(destination.resolve("Mag/Another Mag.jpg")).length,"file bytes preserved");

            scan=RenameCore.scan(source,new AtomicBoolean(false),null);
            s.filter="";
            s.newFolder="Collection";
            s.recursive=true;
            s.preserveSubfolders=true;
            matches=PresetCore.preview(source,scan,s);
            eq(5,matches.size(),"all remaining files recursively");
            ok(matches.stream().anyMatch(p->p.target.toString().endsWith("Collection/sub/A.jpg")),
                "nested source structure preserved");
            plan=PresetCore.plan(source,scan,matches);
            ok(plan.okay(),"recursive move valid");
            PresetCommit.commit(source,scan,plan,msg->{});
            ok(Files.exists(destination.resolve("Collection/sub/A.jpg")),"nested moved file exists");
            ok(Files.isDirectory(source.resolve("sub")),"original empty subfolder remains");
            ok(!Files.exists(destination.resolve("Collection/sub/sub/A.jpg")),"no duplicated nesting");

            // Reload source fixture for names-only recipes, using tests only to write data.
            Files.write(source.resolve("Foo Bar.JpG"),new byte[]{1});
            Files.write(source.resolve("FILENAME.png"),new byte[]{2});
            Files.write(source.resolve("abc.txt"),new byte[]{3});
            scan=RenameCore.scan(source,new AtomicBoolean(false),null);
            s=new PresetCore.Settings();
            s.kind=PresetCore.Kind.PREFIX;
            s.text="Hi_";
            matches=PresetCore.preview(source,scan,s);
            ok(matches.stream().anyMatch(p->p.target.toString().endsWith("Hi_abc.txt")),"prefix");
            s.kind=PresetCore.Kind.SUFFIX;
            s.text="_done";
            matches=PresetCore.preview(source,scan,s);
            ok(matches.stream().anyMatch(p->p.target.toString().endsWith("abc_done.txt")),"suffix before extension");
            s.kind=PresetCore.Kind.NUMBER;
            s.startNumber=7;
            s.numberDigits=4;
            matches=PresetCore.preview(source,scan,s);
            ok(matches.get(0).target.getFileName().toString().startsWith("0007_"),"sequence prefix");
            s.kind=PresetCore.Kind.REPLACE_TEXT;
            s.text="foo";s.replacement="wow";
            matches=PresetCore.preview(source,scan,s);
            ok(matches.stream().anyMatch(p->p.target.getFileName().toString().equals("wow Bar.JpG")),
                "literal replacement defaults ignore case");
            s.kind=PresetCore.Kind.CLEAN_SPACES;
            matches=PresetCore.preview(source,scan,s);
            ok(matches.stream().anyMatch(p->p.target.toString().endsWith("Foo_Bar.JpG")),"space cleanup");
            s.kind=PresetCore.Kind.LOWERCASE;
            matches=PresetCore.preview(source,scan,s);
            plan=PresetCore.plan(source,scan,matches);
            ok(plan.okay(),"case-only rename is allowed after staging");
            s.kind=PresetCore.Kind.SORT_EXTENSION;
            s.destinationParent=destination;
            s.newFolder="ByType";
            matches=PresetCore.preview(source,scan,s);
            ok(matches.stream().anyMatch(p->p.target.toString().endsWith("ByType/JPG/Foo Bar.JpG")),
                "sort extension, case normalized");
            plan=PresetCore.plan(source,scan,matches);
            ok(plan.okay(),"sort extension valid");
            Files.createDirectories(destination.resolve("ByType"));
            boolean rejected=false;
            try { PresetCore.preview(source,scan,s); }catch(IOException expected){rejected=true;}
            ok(rejected,"existing new output folder rejected");
            Files.delete(destination.resolve("ByType"));

            // Flatten duplicate names from two subfolders and offer auto-numbering.
            Files.createDirectories(source.resolve("other"));
            Files.write(source.resolve("other/A.jpg"),new byte[]{1});
            Files.write(source.resolve("sub/A.jpg"),new byte[]{1});
            scan=RenameCore.scan(source,new AtomicBoolean(false),null);
            s.kind=PresetCore.Kind.MOVE_NEW;s.destinationParent=destination;s.newFolder="Flat";
            s.recursive=true;s.preserveSubfolders=false;
            matches=PresetCore.preview(source,scan,s);
            plan=PresetCore.plan(source,scan,matches);
            ok(!plan.okay(),"flatten duplicate names blocked");
            eq(1,plan.conflicts.size(),"collisions grouped in one destination");
            int repaired=PresetCore.numberConflicts(source,scan,matches);
            eq(1,repaired,"auto-number one duplicate");
            plan=PresetCore.plan(source,scan,matches);
            ok(plan.okay(),"auto-number resolves conflicts");
            ok(matches.stream().anyMatch(p->p.target.toString().endsWith("A (2).jpg")),"numbered suffix before extension");

            // Existing destination on unscanned external folder must block Save.
            Files.createDirectory(destination.resolve("Flat"));
            Files.write(destination.resolve("Flat/abc.txt"),new byte[]{9});
            plan=PresetCore.plan(source,scan,matches);
            ok(!plan.okay(),"existing unscanned destination blocks");
            Files.delete(destination.resolve("Flat/abc.txt"));
            Files.delete(destination.resolve("Flat"));

            // Reject a stale preview before any move is attempted.
            plan=PresetCore.plan(source,scan,matches);
            Files.write(source.resolve("abc.txt"),new byte[]{1,2,3,4,5});
            rejected=false;
            try {PresetCommit.commit(source,scan,plan,msg->{});}catch(IOException expected){rejected=true;}
            ok(rejected,"changed source content metadata invalidates preview");
            ok(Files.exists(source.resolve("abc.txt")),"stale check leaves source in place");
            ok(!Files.exists(destination.resolve("Flat")),"stale check does not create destination");

            // Test template variables and absolute match vs whole file destination semantics.
            List<RenameCore.Entry> e=Arrays.asList(file("FaceBatch/photo_mag_005.jpg"),dir("FaceBatch"));
            RenameCore.Options opt=new RenameCore.Options();
            opt.destinationTemplate=true;
            List<RenameCore.Candidate> c=RenameCore.preview(e,"mag","Mag/${filename}",opt);
            eq(1,c.size(),"destination template matches partial filename");
            eq("FaceBatch/Mag/photo_mag_005.jpg",c.get(0).requestedTarget,"preserves full filename");
            c=RenameCore.preview(e,"(mag)","${basename}_$1.${ext}",opt);
            eq("FaceBatch/photo_mag_005_mag.jpg",c.get(0).requestedTarget,"basename, capture, extension");
            opt.fullPath=true;
            c=RenameCore.preview(e,"mag","Restored/${path}",opt);
            eq("Restored/FaceBatch/photo_mag_005.jpg",c.get(0).requestedTarget,"full relative path token");
            opt.destinationTemplate=false;
            opt.fullPath=false;
            c=RenameCore.preview(e,"mag","xx_${filename}",opt);
            eq("FaceBatch/photo_xx_photo_mag_005.jpg_005.jpg",c.get(0).requestedTarget,
                "standard replace only substitutes the matched portion");
            e=Arrays.asList(file("A.txt"),file("B.txt"));
            c=RenameCore.preview(e,"[AB]","D",opt);
            RenameCore.Plan ren=RenameCore.plan(e,c);
            eq(1,ren.duplicateDestinations.size(),"Rename groups duplicate destinations");
            int num=RenameCore.numberDuplicateFiles(e,c);
            eq(1,num,"Rename auto-number suffix for duplicate");
            ren=RenameCore.plan(e,c);
            ok(ren.okay(),"Rename auto-number leaves valid plan");
        } finally {
            Files.walk(sandbox).sorted(Comparator.reverseOrder()).forEach(path->{try{Files.deleteIfExists(path);}catch(IOException ignored){}});
        }
        System.out.println("PASS: "+assertions+" preset & template assertions");
    }
}
