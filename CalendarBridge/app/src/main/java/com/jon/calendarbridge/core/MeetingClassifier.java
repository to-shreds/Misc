package com.jon.calendarbridge.core;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Classifies locally visible meetings without retaining or exporting participant addresses. */
public final class MeetingClassifier {
    private static final Pattern TENT_TOKEN = Pattern.compile(
            "(?<![\\p{L}\\p{N}_])TENT(?![\\p{L}\\p{N}_])",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern LOCAL_PART = Pattern.compile(
            "[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+");
    private static final Pattern DOMAIN_LABEL = Pattern.compile(
            "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");

    private MeetingClassifier() {}

    public static final class Result {
        public final boolean carCompatible;
        public final String reason;

        public Result(boolean carCompatible, String reason) {
            this.carCompatible = carCompatible;
            this.reason = reason;
        }
    }

    /**
     * AUTO requires valid participant data and an internal organizer plus another attendee.
     * CAR and BLOCK are explicit user overrides. A standalone TENT title token always blocks,
     * including when CAR was previously selected; the title must change to remove that block.
     */
    public static boolean isTentTitle(String title) { return title != null && TENT_TOKEN.matcher(title).find(); }

    public static Result classify(String title, String organizer, List<String> attendeeEmails,
            boolean hasAttendeeData, Set<String> internalDomains, String override) {
        if (isTentTitle(title)) {
            return blocked("TENT meeting: client confirmation may still be pending");
        }

        String mode = override == null ? "AUTO" : override.trim().toUpperCase(Locale.ROOT);
        if ("BLOCK".equals(mode)) {
            return blocked("Manually marked blocked");
        }
        if ("CAR".equals(mode)) {
            return new Result(true, "Manually marked car-compatible");
        }

        Set<String> domains = normalizeDomains(internalDomains);
        if (domains.isEmpty()) {
            return blocked("No valid internal email domain configured");
        }

        String organizerEmail = normalizeEmail(organizer);
        if (organizerEmail == null) {
            return blocked("Organizer email is missing or invalid");
        }
        if (!domains.contains(domainOf(organizerEmail))) {
            return blocked("Organizer is outside your organization");
        }
        if (!hasAttendeeData || attendeeEmails == null || attendeeEmails.isEmpty()) {
            return blocked("Attendee information is unavailable");
        }

        boolean hasOtherAttendee = false;
        for (String rawEmail : attendeeEmails) {
            String email = normalizeEmail(rawEmail);
            if (email == null) {
                return blocked("An attendee email is missing or invalid");
            }
            if (!domains.contains(domainOf(email))) {
                return blocked("At least one attendee is outside your organization");
            }
            if (!email.equals(organizerEmail)) {
                hasOtherAttendee = true;
            }
        }
        if (!hasOtherAttendee) {
            return blocked("No attendee other than the organizer is visible");
        }
        return new Result(true, "Organizer and all visible attendees are internal");
    }

    private static Result blocked(String reason) {
        return new Result(false, reason);
    }

    private static Set<String> normalizeDomains(Set<String> rawDomains) {
        Set<String> normalized = new HashSet<>();
        if (rawDomains == null) return normalized;
        for (String rawDomain : rawDomains) {
            if (rawDomain == null) continue;
            String domain = rawDomain.trim().toLowerCase(Locale.ROOT);
            if (domain.startsWith("@")) domain = domain.substring(1);
            if (isValidDomain(domain)) normalized.add(domain);
        }
        return normalized;
    }

    private static String normalizeEmail(String rawEmail) {
        if (rawEmail == null) return null;
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        int at = email.indexOf('@');
        if (email.length() > 254 || at < 1 || at != email.lastIndexOf('@')) return null;
        String local = email.substring(0, at);
        if (local.length() > 64 || local.startsWith(".") || local.endsWith(".")
                || local.contains("..") || !LOCAL_PART.matcher(local).matches()) return null;
        return isValidDomain(email.substring(at + 1)) ? email : null;
    }

    private static String domainOf(String email) {
        return email.substring(email.lastIndexOf('@') + 1);
    }

    private static boolean isValidDomain(String domain) {
        if (domain.isEmpty() || domain.length() > 253) return false;
        String[] labels = domain.split("\\.", -1);
        if (labels.length < 2) return false;
        for (String label : labels) {
            if (!DOMAIN_LABEL.matcher(label).matches()) return false;
        }
        return true;
    }
}
