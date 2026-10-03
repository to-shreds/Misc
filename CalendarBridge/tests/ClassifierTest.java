import com.jon.calendarbridge.core.MeetingClassifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Standalone regression tests; run with javac/java without Android or JUnit. */
public final class ClassifierTest {
    private static final Set<String> INTERNAL = new HashSet<>(
            Collections.singletonList("phiagroup.com"));
    private static int checks;

    public static void main(String[] args) {
        List<String> internal = Arrays.asList("jon.jablon@phiagroup.com", "latrisha@phiagroup.com");
        expect(true, "Internal meeting", "jon.jablon@phiagroup.com", internal, true, INTERNAL, "AUTO");
        expect(true, "Internal meeting", " Jon.Jablon@PHIAGROUP.COM ",
                Arrays.asList("JON.JABLON@phiagroup.com", " LaTrisha@PhiaGroup.Com "),
                true, new HashSet<>(Collections.singletonList(" @PHIAGROUP.COM ")), "AUTO");
        expect(true, "Internal meeting", "jon@phiagroup.com",
                Collections.singletonList("colleague+meeting@phiagroup.com"), true, INTERNAL, "AUTO");
        expect(false, "External organizer", "client@example.com", internal, true, INTERNAL, "AUTO");
        expect(false, "External attendee", "jon@phiagroup.com",
                Arrays.asList("jon@phiagroup.com", "client@example.com"), true, INTERNAL, "AUTO");
        expect(false, "Domain suffix attack", "jon@phiagroup.com",
                Collections.singletonList("client@phiagroup.com.evil.example"), true, INTERNAL, "AUTO");
        expect(false, "Domain prefix attack", "jon@phiagroup.com",
                Collections.singletonList("client@evilphiagroup.com"), true, INTERNAL, "AUTO");
        expect(false, "Unconfigured subdomain", "jon@phiagroup.com",
                Collections.singletonList("client@team.phiagroup.com"), true, INTERNAL, "AUTO");
        expect(false, "Malformed attendee", "jon@phiagroup.com",
                Collections.singletonList("client@@phiagroup.com"), true, INTERNAL, "AUTO");
        expect(false, "Whitespace inside attendee", "jon@phiagroup.com",
                Collections.singletonList("client @phiagroup.com"), true, INTERNAL, "AUTO");
        expect(false, "Trailing domain dot", "jon@phiagroup.com",
                Collections.singletonList("client@phiagroup.com."), true, INTERNAL, "AUTO");
        expect(false, "Missing organizer", null, internal, true, INTERNAL, "AUTO");
        expect(false, "Invalid organizer", "Jon Jablon", internal, true, INTERNAL, "AUTO");
        expect(false, "Display-form organizer", "Jon <jon@phiagroup.com>", internal, true, INTERNAL, "AUTO");
        expect(false, "Blank attendee", "jon@phiagroup.com",
                Arrays.asList("colleague@phiagroup.com", " "), true, INTERNAL, "AUTO");
        expect(false, "Null attendee", "jon@phiagroup.com",
                Arrays.asList("colleague@phiagroup.com", null), true, INTERNAL, "AUTO");
        expect(false, "No attendee data", "jon.jablon@phiagroup.com", internal, false, INTERNAL, "AUTO");
        expect(false, "No attendees", "jon@phiagroup.com", Collections.<String>emptyList(), true, INTERNAL, "AUTO");
        expect(false, "Null attendees", "jon@phiagroup.com", null, true, INTERNAL, "AUTO");
        expect(false, "Organizer only", "jon@phiagroup.com",
                Collections.singletonList("JON@phiagroup.com"), true, INTERNAL, "AUTO");
        expect(false, "Duplicated organizer only", "jon@phiagroup.com",
                Arrays.asList("jon@phiagroup.com", " Jon@PHIAGROUP.COM "), true, INTERNAL, "AUTO");
        expect(false, "No domains", "jon.jablon@phiagroup.com", internal, true, null, "AUTO");
        expect(false, "Invalid domains", "jon.jablon@phiagroup.com", internal, true,
                new HashSet<>(Arrays.asList(" ", null, "phiagroup..com", "*.phiagroup.com")), "AUTO");

        String[] tentativeTitles = {"TENT", "TENT meeting", "[TENT] Client call", "tent: Client call",
                "Client call (TeNt)", "TENT-Client call", "Client call / TENT"};
        for (String title : tentativeTitles) {
            expect(false, title, "jon.jablon@phiagroup.com", internal, true, INTERNAL, "AUTO");
            expect(false, title, "jon.jablon@phiagroup.com", internal, true, INTERNAL, "CAR");
        }
        String[] ordinaryTitles = {"Content planning", "Attention to detail", "Tentative discussion",
                "TENTATIVE", "tentacle", "attentive", "PATENT", "TENT_123"};
        for (String title : ordinaryTitles) {
            expect(true, title, "jon.jablon@phiagroup.com", internal, true, INTERNAL, "AUTO");
        }
        expect(true, null, "jon.jablon@phiagroup.com", internal, true, INTERNAL, "AUTO");
        expect(false, "Internal meeting", "jon.jablon@phiagroup.com", internal, true, INTERNAL, "BLOCK");
        expect(false, "Internal meeting", "jon.jablon@phiagroup.com", internal, true, INTERNAL, " block ");
        expect(true, "External manually flexible", "client@example.com",
                Collections.singletonList("other@example.com"), true, INTERNAL, "CAR");
        expect(true, "Manual classification without metadata", null, null, false, null, " car ");
        expect(true, "Internal meeting", "jon.jablon@phiagroup.com", internal, true, INTERNAL, null);
        expect(false, "Unknown override does not grant access", null, null, false, INTERNAL, "UNEXPECTED");

        MeetingClassifier.Result tentative = MeetingClassifier.classify("[TENT] Client call",
                "jon.jablon@phiagroup.com", internal, true, INTERNAL, "CAR");
        if (!tentative.reason.contains("TENT")) throw new AssertionError("TENT reason must identify the rule");
        checks++;
        System.out.println("ClassifierTest: " + checks + " checks passed");
    }

    private static void expect(boolean expected, String title, String organizer, List<String> attendees,
            boolean hasAttendeeData, Set<String> domains, String override) {
        MeetingClassifier.Result result = MeetingClassifier.classify(
                title, organizer, attendees, hasAttendeeData, domains, override);
        if (result.carCompatible != expected) {
            throw new AssertionError("Unexpected classification for " + title + ", override " + override
                    + ": " + result.reason);
        }
        if (result.reason == null || result.reason.trim().isEmpty()) {
            throw new AssertionError("Every result must explain its classification");
        }
        checks++;
    }
}
