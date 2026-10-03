package com.jon.calendarbridge;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Instances;
import com.jon.calendarbridge.core.MeetingClassifier;
import com.jon.calendarbridge.core.DeletionPolicy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.DateFormat;
import java.time.ZonedDateTime;
import java.util.*;

/** Local, one-way mirror. Source is only queried, never modified. */
public final class BridgeApi {
    private static final Object LOCK = new Object();
    private static final String PREFS = "bridge";
    private static final int MAX_EVENTS = 10000;
    private static String reviewedConfig = "";
    private static long reviewedAt;
    private static Set<String> reviewedKeys = new HashSet<>();
    public static class Config {
        public long sourceId = -1, targetId = -1;
        public String sourceIdentity = "", targetIdentity = "", domains = "phiagroup.com";
        public int pastDays = 30, futureMonths = 12;
        public boolean enabled, approved;
    }
    public static final class CalendarInfo {
        public final long id;
        public final String label, account, type, identity;
        public final boolean destinationEligible;
        CalendarInfo(Cursor c) {
            id = c.getLong(0); label = safe(c.getString(1)); account = safe(c.getString(2));
            type = safe(c.getString(3));
            identity = id + "|" + type + "|" + account + "|" + safe(c.getString(7));
            destinationEligible = "com.google".equals(type) && c.getInt(4) >= Calendars.CAL_ACCESS_CONTRIBUTOR && c.getInt(6) != 0;
        }
        @Override public String toString() { return label + " (" + account + ")"; }
    }
    public static final class EventView {
        public final String key, title, reason, override;
        public final long begin, end;
        public final boolean allDay, carCompatible, free;
        EventView(String key, String title, long begin, long end, boolean allDay,
                  MeetingClassifier.Result r, String override, boolean free) {
            this.key = key; this.title = title; this.begin = begin; this.end = end;
            this.allDay = allDay; this.carCompatible = r.carCompatible;
            this.reason = r.reason; this.override = override; this.free = free;
        }
    }
    public static final class Preview {
        public final List<EventView> events;
        public final List<String> heldTitles;
        public final int creates, updates, deletes, held;
        public final String note;
        Preview(Snapshot s, Plan p) {
            events = Collections.unmodifiableList(s.events); heldTitles = Collections.unmodifiableList(p.heldTitles);
            creates = p.create.size(); updates = p.update.size(); deletes = p.remove.size();
            held = p.held.size();
            note = "Car-compatible meetings still occupy meeting time. They may fit a drive, but not playing time. "
                + "Recurring meetings are copied as individual occurrences within the selected date range. "
                + (held > 0 ? "Some removals are waiting for another healthy scan or your review. " : "")
                + "Google uploads the copies using its existing calendar sync on this phone.";
        }
    }
    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    public static Config load(Context context) {
        SharedPreferences p = prefs(context); Config c = new Config();
        c.sourceId = p.getLong("source", -1); c.targetId = p.getLong("target", -1);
        c.sourceIdentity = p.getString("sourceIdentity", ""); c.targetIdentity = p.getString("targetIdentity", "");
        c.domains = p.getString("domains", "phiagroup.com");
        c.pastDays = p.getInt("pastDays", 30); c.futureMonths = p.getInt("futureMonths", 12);
        c.enabled = p.getBoolean("enabled", false); c.approved = p.getBoolean("approved", false);
        return c;
    }
    public static List<CalendarInfo> calendars(Context context) throws Exception {
        requirePermissions(context);
        List<CalendarInfo> list = new ArrayList<>();
        String[] projection = { Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME,
            Calendars.ACCOUNT_TYPE, Calendars.CALENDAR_ACCESS_LEVEL, Calendars.VISIBLE,
            Calendars.SYNC_EVENTS, Calendars.NAME };
        try (Cursor c = query(context, Calendars.CONTENT_URI, projection, null, null, null)) {
            while (c.moveToNext()) list.add(new CalendarInfo(c));
        }
        list.sort(Comparator.comparing(x -> x.label + x.account)); return list;
    }
    public static void saveConfig(Context context, Config c) throws Exception {
        synchronized (LOCK) {
            requirePermissions(context); validate(c);
            Config old = load(context);
            boolean same = old.sourceId == c.sourceId && old.targetId == c.targetId
                && old.sourceIdentity.equals(c.sourceIdentity) && old.targetIdentity.equals(c.targetIdentity)
                && domains(old.domains).equals(domains(c.domains))
                && old.pastDays == c.pastDays && old.futureMonths == c.futureMonths;
            boolean ok = prefs(context).edit().putLong("source", c.sourceId).putLong("target", c.targetId)
                .putString("sourceIdentity", c.sourceIdentity).putString("targetIdentity", c.targetIdentity)
                .putString("domains", c.domains).putInt("pastDays", c.pastDays).putInt("futureMonths", c.futureMonths)
                .putBoolean("approved", same && old.approved).putBoolean("enabled", same && old.enabled).commit();
            if (!ok) throw new Exception("Could not save settings. No calendar was changed.");
            SyncScheduler.schedule(context);
        }
    }
    public static void setEnabled(Context context, boolean enabled) {
        synchronized (LOCK) {
            if (enabled && !load(context).approved) throw new IllegalStateException("Preview and start syncing first.");
            if (!prefs(context).edit().putBoolean("enabled", enabled).commit()) throw new IllegalStateException("Could not save sync setting.");
            SyncScheduler.schedule(context);
        }
    }
    public static void setOverride(Context context, String key, String mode) {
        synchronized (LOCK) {
            if (!Arrays.asList("AUTO", "CAR", "BLOCK").contains(mode)) throw new IllegalArgumentException("Unknown classification.");
            if (!prefs(context).edit().putString("override:" + scope(load(context)) + ":" + key, mode).commit())
                throw new IllegalStateException("Could not save classification.");
        }
    }
    public static Preview preview(Context context) throws Exception {
        synchronized (LOCK) {
            Config c = load(context); Snapshot s = snapshot(context, c);
            try (Journal db = new Journal(context)) {
                Plan p = plan(context, c, s, db);
                reviewedConfig = configFingerprint(c); reviewedAt = System.currentTimeMillis();
                reviewedKeys = new HashSet<>(); for(Track t:p.held) reviewedKeys.add(t.key);
                return new Preview(s, p);
            }
        }
    }
    public static String approveAndSync(Context context) throws Exception {
        synchronized (LOCK) {
            snapshot(context, load(context));
            if (!prefs(context).edit().putBoolean("approved", true).putBoolean("enabled", true).commit())
                throw new Exception("Could not save approval. No calendar was changed.");
            try { return sync(context); } finally { SyncScheduler.schedule(context); }
        }
    }
    static String automaticSync(Context context) throws Exception {
        synchronized(LOCK) {
            Config c=load(context); if(!c.enabled || !c.approved) return "Paused.";
            return sync(context);
        }
    }
    public static String sync(Context context) throws Exception {
        synchronized (LOCK) {
            Config c = load(context);
            if (!c.approved) throw new Exception("Preview your calendars and select Start syncing first.");
            Snapshot s = snapshot(context, c);
            String scope = scope(c);
            try (Journal db = new Journal(context)) {
                Plan p = plan(context, c, s, db);
                // Finish and validate every read before any destination mutation.
                int created = 0, updated = 0, removed = 0;
                for (EventView e : p.create) { checkCancelled();
                    assertCalendars(context, c);
                    db.save(scope, e, -1, 0, 0); // write-ahead recovery for interruption after insert
                    long target = insert(context, c, e);
                    db.save(scope, e, target, 0, 0); created++;
                }
                for (Update u : p.update) { checkCancelled();
                    assertCalendars(context, c);
                    if (!owned(context, c, u.target, u.event.key)) throw new Exception("A copied event changed ownership. Sync paused; preview again.");
                    int count = context.getContentResolver().update(Events.CONTENT_URI, values(context, c, u.event), ownershipWhere(), ownershipArgs(context,c,u.target,u.event.key));
                    if (count != 1) throw new Exception("A Google copy disappeared during sync. Preview and try again.");
                    db.save(scope, u.event, u.target, 0, 0); updated++;
                }
                // Reset missing state for all observed entries, even unchanged copies.
                for (EventView e : s.events) {
                    Mirror m = p.destinations.get(e.key);
                    if (m != null) db.save(scope, e, m.id, 0, 0);
                }
                long now = System.currentTimeMillis();
                for (Track t : p.held) db.missing(scope, t.key, t.missingSince == 0 ? now : t.missingSince, t.missingScans + 1);
                for (Track t : p.remove) { checkCancelled();
                    assertCalendars(context, c);
                    if (!owned(context, c, t.target, t.key)) throw new Exception("A copied event changed ownership. Removals paused.");
                    int count = context.getContentResolver().delete(Events.CONTENT_URI, ownershipWhere(), ownershipArgs(context,c,t.target,t.key));
                    if (count == 1) { db.forget(scope, t.key); removed++; }
                }
                String result = "Synced on this phone: " + created + " added, " + updated + " updated, " + removed + " removed."
                    + (p.held.size() > 0 ? " " + p.held.size() + " removals held for safety." : "")
                    + " Google cloud sync may follow shortly.";
                prefs(context).edit().putString("status", result).putLong("lastSync", now).apply();
                return result;
            }
        }
    }
    /** Explicit review bypasses missing-event delay, but never source identity or ownership checks. */
    public static String reviewedRemovals(Context context) throws Exception {
        synchronized (LOCK) {
            Config c = load(context);
            if (!c.approved) throw new Exception("Start syncing this configuration first.");
            if(!configFingerprint(c).equals(reviewedConfig) || System.currentTimeMillis()-reviewedAt > 10*60_000L)
                throw new Exception("The removal preview expired. Preview again before reviewing removals.");
            Set<String> approvedKeys = new HashSet<>(reviewedKeys);
            Snapshot s = snapshot(context, c);
            try (Journal db = new Journal(context)) {
                Plan p = plan(context, c, s, db); int removed = 0;
                for (Track t : p.held) { if(!approvedKeys.contains(t.key)) continue; checkCancelled(); assertCalendars(context, c);
                    if (owned(context, c, t.target, t.key)) {
                        int count = context.getContentResolver().delete(Events.CONTENT_URI, ownershipWhere(), ownershipArgs(context,c,t.target,t.key));
                        if (count == 1) { db.forget(scope(c), t.key); removed++; }
                    }
                }
                return removed + " reviewed copies removed. Your work calendar was not changed.";
            }
        }
    }
    public static String status(Context context) {
        SharedPreferences p = prefs(context); long when = p.getLong("lastSync", 0);
        return p.getString("status", "No sync yet. Preview your calendars to begin.")
            + (when > 0 ? "\nLast completed sync: " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(when)) : "");
    }
    static void recordFailure(Context context, Exception e) {
        prefs(context).edit().putString("status", "Last automatic check failed: " + safeError(e) + " Automatic checks will retry.").apply();
    }
    private static String safeError(Exception e) {
        if (e instanceof SecurityException) return "Calendar access is unavailable. Open Calendar Bridge and grant calendar permission.";
        // Do not persist raw provider messages that could contain meeting or attendee data.
        if (e.getClass() == Exception.class || e instanceof IllegalArgumentException || e instanceof IllegalStateException)
            return safe(e.getMessage());
        return "Calendar data could not be read or updated. Open the app, preview, and retry.";
    }
    private static void requirePermissions(Context context) throws Exception {
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED
            || context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED)
            throw new Exception("Calendar permission is needed. Grant it in the app or Android app settings.");
    }
    private static void validate(Config c) throws Exception {
        if (c.sourceId < 0 || c.targetId < 0) throw new Exception("Select both a work calendar and a Google destination.");
        if (c.sourceId == c.targetId) throw new Exception("Source and destination must be different calendars.");
        if (c.sourceIdentity.isEmpty() || c.targetIdentity.isEmpty()) throw new Exception("Reselect both calendars.");
        if (c.pastDays < 0 || c.pastDays > 365 || c.futureMonths < 1 || c.futureMonths > 24)
            throw new Exception("Use 0 to 365 history days and 1 to 24 future months.");
        domains(c.domains);
    }
    private static Set<String> domains(String value) throws Exception {
        Set<String> set = new HashSet<>();
        for (String raw : safe(value).split(",")) {
            String d = raw.trim().toLowerCase(Locale.ROOT); if (d.startsWith("@")) d = d.substring(1);
            if (!d.matches("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+"))
                throw new Exception("Enter organization domains such as phiagroup.com, separated by commas.");
            set.add(d);
        }
        if (set.isEmpty()) throw new Exception("Enter at least one organization domain."); return set;
    }
    private static Cursor query(Context c, Uri uri, String[] projection, String selection, String[] args, String sort) throws Exception {
        Cursor cursor = c.getContentResolver().query(uri, projection, selection, args, sort);
        if (cursor == null) throw new Exception("Calendar data is temporarily unavailable. Nothing will be removed."); return cursor;
    }
    private static void assertCalendars(Context context, Config c) throws Exception {
        requirePermissions(context); validate(c);
        boolean source = false, target = false;
        String[] pr = { Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME,
            Calendars.ACCOUNT_TYPE, Calendars.CALENDAR_ACCESS_LEVEL, Calendars.VISIBLE, Calendars.SYNC_EVENTS, Calendars.NAME };
        try (Cursor cursor = query(context, Calendars.CONTENT_URI, pr, Calendars._ID + " IN (?,?)",
                                  new String[]{String.valueOf(c.sourceId), String.valueOf(c.targetId)}, null)) {
            while (cursor.moveToNext()) {
                CalendarInfo info = new CalendarInfo(cursor);
                if (info.id == c.sourceId) {
                    if (!info.identity.equals(c.sourceIdentity)) throw new Exception("The work calendar account changed. Reselect it and preview.");
                    if (cursor.getInt(5) == 0 || cursor.getInt(6) == 0)
                        throw new Exception("The work calendar is hidden or not syncing. Enable it in your calendar app, then preview again.");
                    source = true;
                }
                if (info.id == c.targetId) {
                    if (!info.identity.equals(c.targetIdentity) || !info.destinationEligible)
                        throw new Exception("The Google destination changed or is not writable and syncing. Reselect it.");
                    target = true;
                }
            }
        }
        if (!source || !target) throw new Exception("A selected calendar is unavailable. No copies will be removed. Reselect it if needed.");
    }
    private static class Source {
        long id, originalId, originalTime, start, end; int status, selfStatus, availability; String title, organizer, rule, rdate, syncId, originalSyncId;
        String duration, exdate, exrule, timezone;
        boolean allDay, deleted, attendeeData, ambiguousOrganizer; String participantOrganizer="";
        List<String> attendees = new ArrayList<>(), attendeeRecords = new ArrayList<>();
        String signature() { return hash(id + "|" + title + "|" + organizer + "|" + status + "|" + selfStatus + "|" + deleted + "|" + originalId + "|" + originalTime + "|" + rule + "|" + rdate + "|" + start + "|" + end + "|" + duration + "|" + exdate + "|" + exrule + "|" + allDay + "|" + timezone + "|" + attendeeData + "|" + availability + "|" + syncId + "|" + originalSyncId); }
    }
    private static class Snapshot {
        final List<EventView> events = new ArrayList<>();
        final Map<String,EventView> byKey = new HashMap<>();
        final Set<Long> cancelledIds = new HashSet<>();
        final Set<String> cancelledKeys = new HashSet<>();
        long start, end;
    }
    private static Map<Long,Source> sources(Context context, Config config) throws Exception {
        Map<Long,Source> result = new HashMap<>();
        String[] pr = {Events._ID, Events.TITLE, Events.ORGANIZER, Events.ALL_DAY, Events.STATUS,
            Events.SELF_ATTENDEE_STATUS, Events.DELETED, Events.HAS_ATTENDEE_DATA, Events.RRULE, Events.RDATE,
            Events.ORIGINAL_ID, Events.ORIGINAL_INSTANCE_TIME, Events._SYNC_ID, Events.ORIGINAL_SYNC_ID,
            Events.DTSTART, Events.DTEND, Events.DURATION, Events.EXDATE, Events.EXRULE, Events.EVENT_TIMEZONE, Events.AVAILABILITY};
        try (Cursor c = query(context, Events.CONTENT_URI, pr, Events.CALENDAR_ID + "=?", new String[]{"" + config.sourceId}, null)) {
            while (c.moveToNext()) {
                Source e = new Source(); e.id = c.getLong(0); e.title = safe(c.getString(1)); e.organizer = safe(c.getString(2));
                e.allDay = c.getInt(3) != 0; e.status = c.isNull(4) ? -1 : c.getInt(4);
                e.selfStatus = c.isNull(5) ? -1 : c.getInt(5); e.deleted = c.getInt(6) != 0;
                e.attendeeData = c.getInt(7) != 0; e.rule = safe(c.getString(8)); e.rdate = safe(c.getString(9));
                e.originalId = c.isNull(10) ? -1 : c.getLong(10); e.originalTime = c.isNull(11) ? -1 : c.getLong(11);
                e.syncId = safe(c.getString(12)); e.originalSyncId = safe(c.getString(13));
                e.start=c.getLong(14); e.end=c.isNull(15) ? -1 : c.getLong(15); e.duration=safe(c.getString(16));
                e.exdate=safe(c.getString(17));e.exrule=safe(c.getString(18));e.timezone=safe(c.getString(19));e.availability=c.isNull(20)?Events.AVAILABILITY_BUSY:c.getInt(20);
                result.put(e.id, e);
                if (result.size() > MAX_EVENTS) throw new Exception("This source contains too many events for one safe scan. Choose a smaller calendar.");
            }
        }
        return result;
    }
    private static Snapshot snapshot(Context context, Config config) throws Exception {
        assertCalendars(context, config);
        Snapshot s = new Snapshot(); ZonedDateTime now = ZonedDateTime.now();
        s.start = now.minusDays(config.pastDays).toLocalDate().atStartOfDay(now.getZone()).toInstant().toEpochMilli();
        s.end = now.plusMonths(config.futureMonths).toLocalDate().plusDays(1).atStartOfDay(now.getZone()).toInstant().toEpochMilli();
        Map<Long,Source> records = sources(context, config); Map<String,Long> syncIds = new HashMap<>();
        for (Source e : records.values()) {
            if (!e.syncId.isEmpty()) syncIds.put(e.syncId, e.id);
            if (e.deleted || e.status == Events.STATUS_CANCELED || e.selfStatus == Attendees.ATTENDEE_STATUS_DECLINED) s.cancelledIds.add(e.id);
        }
        for(Source e:records.values()) if(s.cancelledIds.contains(e.id) && e.originalTime>=0) {
            long parentId=e.originalId;
            if(parentId<0 && !e.originalSyncId.isEmpty() && syncIds.containsKey(e.originalSyncId)) parentId=syncIds.get(e.originalSyncId);
            if(parentId>=0)s.cancelledKeys.add("e"+parentId+":"+e.originalTime);
        }
        readAttendees(context, records);
        Uri.Builder ub = Instances.CONTENT_URI.buildUpon(); ContentUris.appendId(ub, s.start); ContentUris.appendId(ub, s.end);
        Set<String> internal = domains(config.domains);
        try (Cursor c = query(context, ub.build(), new String[]{Instances.EVENT_ID, Instances.BEGIN, Instances.END},
                              Instances.CALENDAR_ID + "=?", new String[]{"" + config.sourceId}, Instances.BEGIN + " ASC")) {
            while(c.moveToNext()) {
                Source e = records.get(c.getLong(0)); if(e == null) throw new Exception("The work calendar changed while being read. Try again.");
                if(s.cancelledIds.contains(e.id)) continue;
                long begin = c.getLong(1), end = c.getLong(2);
                if(end < begin) throw new Exception("A work event has an invalid duration. Sync paused until it is corrected.");
                Source parent = e;
                if(e.originalId >= 0) parent = records.get(e.originalId);
                else if (!e.originalSyncId.isEmpty()) { Long pid = syncIds.get(e.originalSyncId); parent = pid == null ? null : records.get(pid); }
                if(parent == null) throw new Exception("A recurring meeting exception is missing its series. Let Outlook sync, then retry.");
                if(s.cancelledIds.contains(parent.id)) continue;
                boolean recurring = !parent.rule.isEmpty() || !parent.rdate.isEmpty() || e.originalTime >= 0;
                long anchor = e.originalTime >= 0 ? e.originalTime : begin;
                String key = "e" + parent.id + (recurring ? ":" + anchor : "");
                if(s.byKey.containsKey(key)) throw new Exception("Outlook exposed duplicate meeting occurrences. Let it finish syncing and retry.");
                // Missing exception participants remain unknown; a series list may omit an added client.
                Source attendeeSource = e;
                String organizer = e.organizer.isEmpty() ? (e.ambiguousOrganizer ? "" : e.participantOrganizer) : e.organizer;
                String override = prefs(context).getString("override:" + scope(config) + ":" + key, "AUTO");
                MeetingClassifier.Result classification = MeetingClassifier.classify(e.title, organizer,
                    attendeeSource.attendees, attendeeSource.attendeeData, internal, override);
                boolean free = e.availability==Events.AVAILABILITY_FREE && "AUTO".equals(override) && !MeetingClassifier.isTentTitle(e.title);
                if(free) classification=new MeetingClassifier.Result(false,"Marked free in your work calendar; informational only");
                EventView view = new EventView(key, e.title.isEmpty() ? "Work event" : e.title, begin, end, e.allDay, classification, override, free);
                s.events.add(view); s.byKey.put(key,view);
                if(s.events.size() > MAX_EVENTS) throw new Exception("Too many occurrences for one safe sync. Reduce the future range.");
            }
        }
        assertCalendars(context, config);
        // Re-read event metadata to detect deletion/time/title races during the multi-table read.
        Map<Long,Source> after = sources(context,config);
        if(after.size() != records.size()) throw new Exception("Work events changed during the scan. Try preview again.");
        for(Source e:records.values()) if(!after.containsKey(e.id) || !e.signature().equals(after.get(e.id).signature()))
            throw new Exception("Work events changed during the scan. Try preview again.");
        readAttendees(context, after);
        for(Source e:records.values()) {
            List<String> before = new ArrayList<>(e.attendeeRecords), later = new ArrayList<>(after.get(e.id).attendeeRecords);
            Collections.sort(before); Collections.sort(later);
            if(!before.equals(later)) throw new Exception("Meeting participants changed during the scan. Try preview again.");
        }
        return s;
    }
    private static void readAttendees(Context context, Map<Long,Source> records) throws Exception {
        List<Long> ids = new ArrayList<>(records.keySet());
        for (int offset = 0; offset < ids.size(); offset += 400) {
            int count = Math.min(400, ids.size() - offset); StringBuilder marks = new StringBuilder(); String[] args = new String[count];
            for (int i=0; i<count; i++) { if(i>0) marks.append(','); marks.append('?'); args[i] = "" + ids.get(offset+i); }
            try (Cursor c = query(context, Attendees.CONTENT_URI, new String[]{Attendees.EVENT_ID,Attendees.ATTENDEE_EMAIL,Attendees.ATTENDEE_RELATIONSHIP},
                                  Attendees.EVENT_ID + " IN (" + marks + ")", args, null)) {
                while(c.moveToNext()) {
                    Source e = records.get(c.getLong(0)); if(e==null)continue;
                    String email=safe(c.getString(1)).trim(); int relationship=c.getInt(2);
                    e.attendees.add(email); e.attendeeRecords.add(email+"\u0000"+relationship);
                    if(relationship==Attendees.RELATIONSHIP_ORGANIZER) {
                        if(e.participantOrganizer.isEmpty()) e.participantOrganizer=email;
                        else if(!e.participantOrganizer.equalsIgnoreCase(email))e.ambiguousOrganizer=true;
                    }
                }
            }
        }
    }
    private static String safe(String s) { return s == null ? "" : s; }
    private static void checkCancelled() throws InterruptedException { if(Thread.currentThread().isInterrupted()) throw new InterruptedException("Sync interrupted."); }
    private static String hash(String s) {
        try { byte[] bytes=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)); char[] hex=new char[bytes.length*2]; char[] digits="0123456789abcdef".toCharArray();
            for(int i=0;i<bytes.length;i++){int b=bytes[i]&255;hex[i*2]=digits[b>>>4];hex[i*2+1]=digits[b&15];} return new String(hex);
        } catch(Exception e) { throw new IllegalStateException("Hashing unavailable",e); }
    }
    private static String configFingerprint(Config c) { return hash(scope(c) + "|" + c.domains + "|" + c.pastDays + "|" + c.futureMonths); }
    private static String scope(Config c) { return hash(c.sourceIdentity + "\n" + c.targetIdentity); }
    private static String installation(Context context) {
        SharedPreferences p=prefs(context); String id=p.getString("installation",null);
        if(id==null) { id=UUID.randomUUID().toString(); if(!p.edit().putString("installation",id).commit()) throw new IllegalStateException("Could not initialize local sync data."); }
        return id;
    }
    private static String prefix(Context context, Config c) { return "[CalendarBridge:" + installation(context) + ":" + scope(c) + ":"; }
    private static String marker(Context context,Config c,String key) { return prefix(context,c) + hash(key) + "]"; }
    private static ContentValues values(Context context,Config c,EventView e) {
        ContentValues v=new ContentValues(); v.put(Events.CALENDAR_ID,c.targetId);
        v.put(Events.TITLE,(e.free ? "[INFO] " : e.carCompatible ? "[CAR] " : "[BLOCKED] ") + e.title);
        v.put(Events.DTSTART,e.begin); v.put(Events.DTEND,e.end); v.put(Events.ALL_DAY,e.allDay ? 1 : 0);
        v.put(Events.EVENT_TIMEZONE,e.allDay ? "UTC" : TimeZone.getDefault().getID());
        v.put(Events.EVENT_END_TIMEZONE,e.allDay ? "UTC" : TimeZone.getDefault().getID());
        v.putNull(Events.RRULE); v.putNull(Events.RDATE); v.putNull(Events.EXRULE); v.putNull(Events.EXDATE); v.putNull(Events.DURATION);
        v.put(Events.DESCRIPTION,(e.free ? "Informational event. Does not block availability." : e.carCompatible ? "Car-compatible meeting. Travel time only; not available for play." : "Blocked work time.")
                + "\n" + e.reason + "\n\n" + marker(context,c,e.key));
        v.put(Events.STATUS,Events.STATUS_CONFIRMED); v.put(Events.AVAILABILITY,e.free ? Events.AVAILABILITY_FREE : Events.AVAILABILITY_BUSY);
        v.put(Events.ACCESS_LEVEL,Events.ACCESS_PRIVATE); v.put(Events.HAS_ALARM,0);
        return v;
    }
    private static long insert(Context context,Config c,EventView e) throws Exception {
        Uri result=context.getContentResolver().insert(Events.CONTENT_URI,values(context,c,e));
        if(result==null) throw new Exception("Google calendar did not accept the copy. Preview and retry."); return ContentUris.parseId(result);
    }
    private static String ownershipWhere() { return Events._ID+"=? AND "+Events.CALENDAR_ID+"=? AND "+Events.DELETED+"=0 AND "+Events.DESCRIPTION+" LIKE ?"; }
    private static String[] ownershipArgs(Context context,Config c,long id,String key) { return new String[]{""+id,""+c.targetId,"%"+marker(context,c,key)}; }
    private static boolean owned(Context context,Config c,long id,String key) throws Exception {
        try(Cursor cursor=query(context,Events.CONTENT_URI,new String[]{Events.CALENDAR_ID,Events.DESCRIPTION,Events.DELETED},
            Events._ID+"=?",new String[]{""+id},null)) {
            return cursor.moveToFirst() && cursor.getLong(0)==c.targetId && cursor.getInt(2)==0
                && safe(cursor.getString(1)).endsWith(marker(context,c,key));
        }
    }
    private static class Mirror {
        long id,begin,end; boolean allDay; String title,description; int availability,access,hasAlarm,status; String rule,rdate;
        boolean matches(Context context,Config c,EventView e) {
            ContentValues v=values(context,c,e);
            return begin==e.begin && end==e.end && allDay==e.allDay && title.equals(v.getAsString(Events.TITLE))
                && description.equals(v.getAsString(Events.DESCRIPTION)) && availability==(e.free ? Events.AVAILABILITY_FREE : Events.AVAILABILITY_BUSY)
                && access==Events.ACCESS_PRIVATE && hasAlarm==0 && status==Events.STATUS_CONFIRMED && rule.isEmpty() && rdate.isEmpty();
        }
    }
    private static Map<String,Mirror> mirrors(Context context,Config c) throws Exception {
        Map<String,Mirror> out=new HashMap<>(); String pref=prefix(context,c);
        try(Cursor cursor=query(context,Events.CONTENT_URI,new String[]{Events._ID,Events.DTSTART,Events.DTEND,Events.ALL_DAY,
            Events.TITLE,Events.DESCRIPTION,Events.AVAILABILITY,Events.ACCESS_LEVEL,Events.HAS_ALARM,Events.STATUS,Events.RRULE,Events.RDATE},
            Events.CALENDAR_ID+"=? AND "+Events.DELETED+"=0",new String[]{""+c.targetId},null)) {
            while(cursor.moveToNext()) {
                String desc=safe(cursor.getString(5)); int at=desc.lastIndexOf(pref);
                if(at<0 || !desc.endsWith("]")) continue;
                String keyHash=desc.substring(at+pref.length(),desc.length()-1); if(!keyHash.matches("[a-f0-9]{64}")) continue;
                Mirror m=new Mirror(); m.id=cursor.getLong(0); m.begin=cursor.getLong(1); m.end=cursor.getLong(2); m.allDay=cursor.getInt(3)!=0;
                m.title=safe(cursor.getString(4));m.description=desc;m.availability=cursor.getInt(6);m.access=cursor.getInt(7);m.hasAlarm=cursor.getInt(8);m.status=cursor.getInt(9);m.rule=safe(cursor.getString(10));m.rdate=safe(cursor.getString(11));
                if(out.put(keyHash,m)!=null) throw new Exception("Duplicate app-created copies found. Sync paused to protect your calendar.");
            }
        }
        return out;
    }
    private static class Track { String key; long target,begin,end,missingSince; int missingScans; }
    private static class Update { EventView event;long target; Update(EventView e,long id){event=e;target=id;} }
    private static class Plan {
        List<EventView> create=new ArrayList<>();List<Update> update=new ArrayList<>();List<Track> remove=new ArrayList<>(),held=new ArrayList<>();
        List<String> heldTitles=new ArrayList<>();Map<String,Mirror> destinations=new HashMap<>();
    }
    private static Plan plan(Context context,Config c,Snapshot s,Journal db) throws Exception {
        Plan p=new Plan();Map<String,Mirror> mirror=mirrors(context,c);
        Map<String,Track> tracks=db.tracks(scope(c));
        for(EventView e:s.events) {
            Mirror m=mirror.get(hash(e.key));
            if(m==null) {
                Track old=tracks.get(e.key);
                if(old!=null && old.target>=0) {
                    try(Cursor row=query(context,Events.CONTENT_URI,new String[]{Events.CALENDAR_ID,Events.DELETED},Events._ID+"=?",new String[]{""+old.target},null)) {
                        if(row.moveToFirst() && row.getLong(0)==c.targetId && row.getInt(1)==0)
                            throw new Exception("A tracked Google copy was edited or moved. Remove that copy and preview again to recreate it.");
                    }
                }
                p.create.add(e);
            } else { p.destinations.put(e.key,m);if(!m.matches(context,c,e))p.update.add(new Update(e,m.id)); }
        }
        List<Track> absent=new ArrayList<>(); int trackedInWindow=0;
        for(Track t:tracks.values()) {
            if(t.end<=s.start || t.begin>=s.end) continue; // old history stays intact; rolling range does not erase history
            trackedInWindow++;
            if(s.byKey.containsKey(t.key)) continue;
            Mirror m=mirror.get(hash(t.key)); if(m==null)continue; // never act on an unmarked event or a reused numeric ID
            t.target=m.id; absent.add(t);
        }
        long now=System.currentTimeMillis();
        for(Track t:absent) {
            long sid=-1; try { sid=Long.parseLong(t.key.substring(1).split(":")[0]); }catch(RuntimeException ignored){}
            boolean cancellation=s.cancelledIds.contains(sid) || s.cancelledKeys.contains(t.key);
            DeletionPolicy.Result result=DeletionPolicy.evaluate(s.events.size(),trackedInWindow,absent.size(),t.missingSince,t.missingScans+1,now,true,cancellation);
            if(result.mayDelete)p.remove.add(t);else {p.held.add(t);p.heldTitles.add(mirror.get(hash(t.key)).title+" ("+DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(t.begin))+")");}
        }
        return p;
    }
    private static final class Journal extends SQLiteOpenHelper implements AutoCloseable {
        Journal(Context context) { super(context,"mirror.db",null,1); }
        @Override public void onCreate(SQLiteDatabase db) { db.execSQL("CREATE TABLE copies(scope TEXT NOT NULL, key TEXT NOT NULL, target INTEGER NOT NULL, begin INTEGER NOT NULL, end INTEGER NOT NULL, missing_since INTEGER NOT NULL DEFAULT 0, missing_scans INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(scope,key))"); }
        @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) { throw new IllegalStateException("Unsupported sync data version."); }
        Map<String,Track> tracks(String scope) {
            Map<String,Track> result=new HashMap<>();
            try(Cursor c=getReadableDatabase().query("copies",new String[]{"key","target","begin","end","missing_since","missing_scans"},"scope=?",new String[]{scope},null,null,null)) {
                while(c.moveToNext()){Track t=new Track();t.key=c.getString(0);t.target=c.getLong(1);t.begin=c.getLong(2);t.end=c.getLong(3);t.missingSince=c.getLong(4);t.missingScans=c.getInt(5);result.put(t.key,t);}
            }return result;
        }
        void save(String scope,EventView e,long target,long since,int scans) {
            ContentValues v=new ContentValues();v.put("scope",scope);v.put("key",e.key);v.put("target",target);v.put("begin",e.begin);v.put("end",e.end);v.put("missing_since",since);v.put("missing_scans",scans);
            if(getWritableDatabase().insertWithOnConflict("copies",null,v,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new IllegalStateException("Could not save sync tracking. Sync stopped.");
        }
        void missing(String scope,String key,long since,int scans){ContentValues v=new ContentValues();v.put("missing_since",since);v.put("missing_scans",scans);getWritableDatabase().update("copies",v,"scope=? AND key=?",new String[]{scope,key});}
        void forget(String scope,String key){getWritableDatabase().delete("copies","scope=? AND key=?",new String[]{scope,key});}
    }
}
