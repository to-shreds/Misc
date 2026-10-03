package com.jon.calendarbridge.tests;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import com.jon.calendarbridge.BridgeApi;
import java.util.List;
import java.util.TimeZone;

/** Native Android provider integration tests. Run only on an isolated emulator. */
public final class BridgeInstrumentation extends Instrumentation {
    private Context context;
    private long source, target;
    private int checks;
    private StringBuilder report = new StringBuilder();
    private static final String SOURCE_ACCOUNT = "fixtures@phiagroup.com";
    private static final String SOURCE_TYPE = "LOCAL";
    private static final String TARGET_ACCOUNT = "fixtures@example.com";
    private static final String TARGET_TYPE = "com.google";

    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        context = getTargetContext();
        Bundle output = new Bundle();
        try {
            if (!android.os.Build.HARDWARE.contains("ranchu") && !android.os.Build.HARDWARE.contains("goldfish"))
                throw new IllegalStateException("Run fixture tests only on an isolated Android emulator.");
            runSuite();
            output.putString("stream", "\nPASS: " + checks + " provider checks\n" + report);
            finish(Activity.RESULT_OK, output);
        } catch (Throwable failure) {
            output.putString("stream", "\nFAIL after " + checks + " checks\n" + report + android.util.Log.getStackTraceString(failure));
            finish(Activity.RESULT_CANCELED, output);
        }
    }

    private void runSuite() throws Exception {
        // An emulator-only fixture account. No real account credentials or Google API access.
        context.getSharedPreferences("bridge", Context.MODE_PRIVATE).edit().clear().commit();
        context.deleteDatabase("mirror.db");
        android.accounts.AccountManager accounts=android.accounts.AccountManager.get(context);
        android.accounts.Account fixtureAccount=new android.accounts.Account(TARGET_ACCOUNT,TARGET_TYPE);
        boolean hasFixture=false;
        for(android.accounts.Account account:accounts.getAccountsByType(TARGET_TYPE))if(account.equals(fixtureAccount))hasFixture=true;
        if(!hasFixture && !accounts.addAccountExplicitly(fixtureAccount,null,null))
            throw new IllegalStateException("Could not register isolated Google-type fixture account.");
        removeFixtureCalendars(SOURCE_ACCOUNT, SOURCE_TYPE);
        removeFixtureCalendars(TARGET_ACCOUNT, TARGET_TYPE);
        source = calendar("Fixture Work", SOURCE_ACCOUNT, SOURCE_TYPE);
        target = calendar("Fixture Google Mirror", TARGET_ACCOUNT, TARGET_TYPE);
        configure();
        long base = (System.currentTimeMillis() / 86400000L + 1) * 86400000L + 9 * 3600000L;
        long internal = event(source, "Internal planning", base, base + 1800000L, true, false);
        attendees(internal, "jon@phiagroup.com", "colleague@phiagroup.com");
        long external = event(source, "Client meeting", base + 3600000L, base + 5400000L, true, false);
        attendees(external, "jon@phiagroup.com", "client@example.org");
        long tent = event(source, "TENT client hold", base + 7200000L, base + 9000000L, true, false);
        attendees(tent, "jon@phiagroup.com", "colleague@phiagroup.com");
        long missing = event(source, "Focus block", base + 10800000L, base + 12600000L, false, false);
        long free = event(source, "Team notice", base + 14400000L, base + 16200000L, true, true);
        attendees(free, "jon@phiagroup.com", "colleague@phiagroup.com");
        long series = recurring("Daily internal", base + 18000000L);
        attendees(series, "jon@phiagroup.com", "colleague@phiagroup.com");
        long anchor = base + 18000000L + 86400000L;
        long exception = exception(series, "Daily moved", anchor, anchor + 3600000L, false);
        long unrelated = event(target, "Personal destination event", base, base + 600000L, false, false);
        String sourceBefore = sourceFingerprint();
        BridgeApi.Preview preview = BridgeApi.preview(context);
        check(preview.events.size() == 8, "recurrence and moved exception flatten to 8 occurrences; got " + preview.events.size());
        check(preview.creates == 8, "initial preview proposes 8 copies");
        check(eventView(preview, "Internal planning").carCompatible, "internal meeting is car-compatible");
        check(!eventView(preview, "Client meeting").carCompatible, "external participant blocks");
        check(!eventView(preview, "TENT client hold").carCompatible, "TENT blocks");
        check(!eventView(preview, "Focus block").carCompatible, "missing attendee data blocks");
        check(eventView(preview, "Team notice").free, "free source event remains informational");
        check(!eventView(preview, "Daily moved").carCompatible, "moved exception without its own attendee list blocks safely");
        BridgeApi.approveAndSync(context);
        check(countCopies() == 8, "first sync creates 8 owned copies");
        check(sourceBefore.equals(sourceFingerprint()), "sync does not modify work events or attendee rows");
        long copy = targetId("[CAR] Internal planning");
        long tentCopy = targetId("[BLOCKED] TENT client hold");
        check(value(targetId("[INFO] Team notice"), Events.AVAILABILITY) == Events.AVAILABILITY_FREE, "Google information copy stays free");
        check(value(copy, Events.HAS_ALARM) == 0, "copies carry no meeting alarms");
        check(countDestinationAttendees() == 0, "attendee email rows are not exported");
        check(!destinationDescriptions().contains("@phiagroup.com"), "destination descriptions contain no participant emails");
        BridgeApi.sync(context);
        check(countCopies() == 8, "repeat sync does not duplicate");
        check(targetId("[CAR] Internal planning") == copy, "repeat sync preserves target ID");
        try(SQLiteDatabase db=context.openOrCreateDatabase("mirror.db",Context.MODE_PRIVATE,null)) {
            ContentValues pending=new ContentValues();pending.put("target",-1);db.update("copies",pending,"key=?",new String[]{"e"+internal});
        }
        BridgeApi.sync(context);
        check(countCopies()==8 && targetId("[CAR] Internal planning")==copy,"pending journal row recovers existing marker without duplicate");
        ContentValues canceledCopy = new ContentValues(); canceledCopy.put(Events.STATUS, Events.STATUS_CANCELED);
        update(copy, canceledCopy); BridgeApi.sync(context);
        check(value(copy, Events.STATUS) == Events.STATUS_CONFIRMED, "canceled Google copy restores same ID to confirmed");
        ContentValues organizerMissing=new ContentValues();organizerMissing.putNull(Events.ORGANIZER);update(internal,organizerMissing);
        ContentValues organizerRole=new ContentValues();organizerRole.put(Attendees.ATTENDEE_RELATIONSHIP,Attendees.RELATIONSHIP_ORGANIZER);
        context.getContentResolver().update(Attendees.CONTENT_URI,organizerRole,Attendees.EVENT_ID+"=? AND "+Attendees.ATTENDEE_EMAIL+"=?",new String[]{""+internal,"jon@phiagroup.com"});
        BridgeApi.sync(context);
        check(targetId("[CAR] Internal planning")==copy,"explicit organizer attendee row supplies missing organizer email");
        organizerMissing.put(Events.ORGANIZER,"jon@phiagroup.com");update(internal,organizerMissing);

        ContentValues changed = new ContentValues(); changed.put(Events.TITLE, "Renamed internal");
        update(internal, changed);
        context.getContentResolver().delete(Attendees.CONTENT_URI, Attendees.EVENT_ID + "=?", new String[]{""+internal});
        attendees(internal, "jon@phiagroup.com", "external@example.org");
        BridgeApi.sync(context);
        check(targetId("[BLOCKED] Renamed internal") == copy, "title and external attendee change update same copy");
        changed.clear(); changed.put(Events.DTSTART, base + 600000L); changed.put(Events.DTEND, base + 2400000L); update(internal, changed);
        BridgeApi.sync(context);
        check(targetId("[BLOCKED] Renamed internal") == copy && value(copy, Events.DTSTART) == base + 600000L, "single event move keeps same copy");
        BridgeApi.setOverride(context, eventView(BridgeApi.preview(context), "TENT client hold").key, "CAR");
        BridgeApi.sync(context);
        check(targetId("[BLOCKED] TENT client hold") == tentCopy, "TENT overrides a manual CAR classification");

        changed.clear(); changed.put(Events.STATUS, Events.STATUS_CANCELED); update(external, changed);
        BridgeApi.sync(context);
        check(targetIdOptional("[BLOCKED] Client meeting") < 0, "explicit cancellation removes its copy");
        long missingCopy = targetId("[BLOCKED] Focus block");
        hardDelete(missing);
        BridgeApi.sync(context);
        check(exists(missingCopy), "nonexplicit disappearance is held initially");
        ageMissing("e" + missing);
        BridgeApi.sync(context);
        check(!exists(missingCopy), "missing event removed after aged consecutive observations");
        check(exists(unrelated), "unrelated destination event is protected");

        long exceptionCopy = targetId("[BLOCKED] Daily moved");
        changed.clear(); changed.put(Events.STATUS, Events.STATUS_CANCELED); update(exception, changed);
        BridgeApi.sync(context);
        check(!exists(exceptionCopy), "canceled recurrence exception removes only that occurrence");
        check(countTitleContains("Daily internal") == 2, "other series occurrences remain");
        long midnight = base - 9 * 3600000L;
        long allDay = event(source,"All-day work",midnight,midnight+86400000L,false,false);
        changed.clear();changed.put(Events.ALL_DAY,1);update(allDay,changed);BridgeApi.sync(context);
        long allDayCopy=targetId("[BLOCKED] All-day work");
        check(value(allDayCopy,Events.ALL_DAY)==1 && value(allDayCopy,Events.DTSTART)==midnight
            && value(allDayCopy,Events.DTEND)==midnight+86400000L,"all-day dates and exclusive end remain intact");
        int beforeHeld = countCopies();
        ContentValues calendarChange = new ContentValues(); calendarChange.put(Calendars.VISIBLE, 0);
        context.getContentResolver().update(ContentUris.withAppendedId(Calendars.CONTENT_URI, source), calendarChange, null, null);
        expectFailure("hidden source blocks sync", () -> BridgeApi.sync(context));
        check(countCopies() == beforeHeld, "hidden source does not remove copies");
        calendarChange.put(Calendars.VISIBLE, 1);
        context.getContentResolver().update(ContentUris.withAppendedId(Calendars.CONTENT_URI, source), calendarChange, null, null);
        BridgeApi.Config config = BridgeApi.load(context); config.sourceIdentity += "-invalid";
        context.getSharedPreferences("bridge", Context.MODE_PRIVATE).edit().putString("sourceIdentity", config.sourceIdentity).commit();
        expectFailure("calendar fingerprint mismatch blocks", () -> BridgeApi.sync(context));
        check(countCopies() == beforeHeld, "fingerprint mismatch does not remove copies");
        configure(); BridgeApi.preview(context); BridgeApi.approveAndSync(context);

        // A whole empty source is held even when journal missing rows have been aged.
        try (Cursor c = context.getContentResolver().query(Events.CONTENT_URI, new String[]{Events._ID}, Events.CALENDAR_ID+"=?", new String[]{""+source}, null)) {
            java.util.ArrayList<Long> ids = new java.util.ArrayList<>(); while(c.moveToNext()) ids.add(c.getLong(0));
            for(Long id:ids) hardDelete(id);
        }
        BridgeApi.sync(context); ageMissing(null); BridgeApi.sync(context);
        check(countCopies() == beforeHeld, "empty healthy source holds existing copies");
        check(exists(unrelated), "empty source still protects unrelated destination event");
        config = BridgeApi.load(context); config.futureMonths = 11; BridgeApi.saveConfig(context, config);
        check(!BridgeApi.load(context).approved && !BridgeApi.load(context).enabled, "settings change requires a new preview approval");
        expectFailure("unapproved configuration cannot sync", () -> BridgeApi.sync(context));

        // Leave useful synthetic data for the subsequent visual smoke check.
        event(source, "TENT client hold", base, base+1800000L, false, false);
        long demo = event(source, "Internal planning", base+3600000L, base+5400000L, true, false);
        attendees(demo,"jon@phiagroup.com","colleague@phiagroup.com");
        configure(); BridgeApi.preview(context); BridgeApi.approveAndSync(context);
        Activity activity = startActivitySync(new Intent().setClassName(context.getPackageName(), "com.jon.calendarbridge.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitForIdleSync(); check(activity != null, "native main activity starts without an exception");
        report.append("Synthetic source and Google-type fixture destination remain for visual inspection.\n");
    }

    private void configure() throws Exception {
        BridgeApi.Config c = new BridgeApi.Config(); c.sourceId=source; c.targetId=target;
        for(BridgeApi.CalendarInfo info:BridgeApi.calendars(context)) { if(info.id==source)c.sourceIdentity=info.identity; if(info.id==target)c.targetIdentity=info.identity; }
        if(c.sourceIdentity.isEmpty() || c.targetIdentity.isEmpty())throw new IllegalStateException("Fixture calendars disappeared: source="+source+", target="+target+", visible="+BridgeApi.calendars(context));
        BridgeApi.saveConfig(context,c);
    }
    private long calendar(String name,String account,String type) {
        ContentValues v=new ContentValues();v.put(Calendars.ACCOUNT_NAME,account);v.put(Calendars.ACCOUNT_TYPE,type);
        v.put(Calendars.NAME,name);v.put(Calendars.CALENDAR_DISPLAY_NAME,name);v.put(Calendars.OWNER_ACCOUNT,account);
        v.put(Calendars.CALENDAR_COLOR,0xff436c89);v.put(Calendars.CALENDAR_ACCESS_LEVEL,Calendars.CAL_ACCESS_OWNER);
        v.put(Calendars.VISIBLE,1);v.put(Calendars.SYNC_EVENTS,1);v.put(Calendars.CALENDAR_TIME_ZONE,TimeZone.getDefault().getID());
        return ContentUris.parseId(context.getContentResolver().insert(syncUri(Calendars.CONTENT_URI,account,type),v));
    }
    private void removeFixtureCalendars(String account,String type) {
        context.getContentResolver().delete(syncUri(Calendars.CONTENT_URI,account,type), Calendars.ACCOUNT_NAME+"=? AND "+Calendars.ACCOUNT_TYPE+"=?",new String[]{account,type});
    }
    private Uri syncUri(Uri uri,String account,String type) { return uri.buildUpon().appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER,"true").appendQueryParameter(Calendars.ACCOUNT_NAME,account).appendQueryParameter(Calendars.ACCOUNT_TYPE,type).build(); }
    private long event(long calendar,String title,long begin,long end,boolean hasAttendees,boolean free) {
        ContentValues v=new ContentValues();v.put(Events.CALENDAR_ID,calendar);v.put(Events.TITLE,title);v.put(Events.DTSTART,begin);v.put(Events.DTEND,end);
        v.put(Events.EVENT_TIMEZONE,"UTC");v.put(Events.ORGANIZER,"jon@phiagroup.com");v.put(Events.HAS_ATTENDEE_DATA,hasAttendees?1:0);
        v.put(Events.STATUS,Events.STATUS_CONFIRMED);v.put(Events.AVAILABILITY,free?Events.AVAILABILITY_FREE:Events.AVAILABILITY_BUSY);
        return ContentUris.parseId(context.getContentResolver().insert(Events.CONTENT_URI,v));
    }
    private long recurring(String title,long begin) {
        ContentValues v=new ContentValues();v.put(Events.CALENDAR_ID,source);v.put(Events.TITLE,title);v.put(Events.DTSTART,begin);v.put(Events.DURATION,"PT1800S");
        v.put(Events.RRULE,"FREQ=DAILY;COUNT=3");v.put(Events.EVENT_TIMEZONE,"UTC");v.put(Events.ORGANIZER,"jon@phiagroup.com");v.put(Events.HAS_ATTENDEE_DATA,1);v.put(Events.STATUS,Events.STATUS_CONFIRMED);
        v.put(Events._SYNC_ID,"fixture-series-1");
        return ContentUris.parseId(context.getContentResolver().insert(syncUri(Events.CONTENT_URI,SOURCE_ACCOUNT,SOURCE_TYPE),v));
    }
    private long exception(long series,String title,long original,long moved,boolean canceled) {
        ContentValues v=new ContentValues();v.put(Events.CALENDAR_ID,source);v.put(Events.TITLE,title);v.put(Events.ORIGINAL_ID,series);v.put(Events.ORIGINAL_INSTANCE_TIME,original);v.put(Events.ORIGINAL_ALL_DAY,0);
        v.put(Events.DTSTART,moved);v.put(Events.DTEND,moved+1800000L);v.put(Events.EVENT_TIMEZONE,"UTC");v.put(Events.ORGANIZER,"jon@phiagroup.com");v.put(Events.HAS_ATTENDEE_DATA,0);v.put(Events.STATUS,canceled?Events.STATUS_CANCELED:Events.STATUS_CONFIRMED);
        v.put(Events.ORIGINAL_SYNC_ID,"fixture-series-1");
        return ContentUris.parseId(context.getContentResolver().insert(syncUri(Events.CONTENT_URI,SOURCE_ACCOUNT,SOURCE_TYPE),v));
    }
    private void attendees(long event,String... addresses) { for(String address:addresses) { ContentValues v=new ContentValues();v.put(Attendees.EVENT_ID,event);v.put(Attendees.ATTENDEE_EMAIL,address);v.put(Attendees.ATTENDEE_TYPE,Attendees.TYPE_REQUIRED);v.put(Attendees.ATTENDEE_RELATIONSHIP,Attendees.RELATIONSHIP_ATTENDEE);v.put(Attendees.ATTENDEE_STATUS,Attendees.ATTENDEE_STATUS_ACCEPTED);context.getContentResolver().insert(Attendees.CONTENT_URI,v); } }
    private void update(long id,ContentValues values) { context.getContentResolver().update(ContentUris.withAppendedId(Events.CONTENT_URI,id),values,null,null); }
    private void hardDelete(long id) { context.getContentResolver().delete(syncUri(ContentUris.withAppendedId(Events.CONTENT_URI,id),SOURCE_ACCOUNT,SOURCE_TYPE),null,null); }
    private BridgeApi.EventView eventView(BridgeApi.Preview preview,String title) { for(BridgeApi.EventView e:preview.events)if(e.title.equals(title))return e;throw new AssertionError("Missing preview title "+title); }
    private int countCopies() { return count(Events.CONTENT_URI,Events.CALENDAR_ID+"=? AND "+Events.DELETED+"=0 AND "+Events.DESCRIPTION+" LIKE ?",new String[]{""+target,"%[CalendarBridge:%"}); }
    private int countDestinationAttendees() {
        java.util.HashSet<Long> eventIds=new java.util.HashSet<>();
        try(Cursor c=context.getContentResolver().query(Events.CONTENT_URI,new String[]{Events._ID},Events.CALENDAR_ID+"=?",new String[]{""+target},null)){while(c.moveToNext())eventIds.add(c.getLong(0));}
        int count=0;try(Cursor c=context.getContentResolver().query(Attendees.CONTENT_URI,new String[]{Attendees.EVENT_ID},null,null,null)){while(c.moveToNext())if(eventIds.contains(c.getLong(0)))count++;}return count;
    }
    private int countTitleContains(String value) { return count(Events.CONTENT_URI,Events.CALENDAR_ID+"=? AND "+Events.DELETED+"=0 AND "+Events.TITLE+" LIKE ?",new String[]{""+target,"%"+value+"%"}); }
    private int count(Uri uri,String where,String[] args) { try(Cursor c=context.getContentResolver().query(uri,new String[]{"_id"},where,args,null)){return c.getCount();} }
    private long targetId(String title) { long id=targetIdOptional(title);if(id<0)throw new AssertionError("Missing target title "+title);return id; }
    private long targetIdOptional(String title) { try(Cursor c=context.getContentResolver().query(Events.CONTENT_URI,new String[]{Events._ID},Events.CALENDAR_ID+"=? AND "+Events.DELETED+"=0 AND "+Events.TITLE+"=?",new String[]{""+target,title},null)){return c.moveToFirst()?c.getLong(0):-1;} }
    private boolean exists(long id) { return count(Events.CONTENT_URI,Events._ID+"=? AND "+Events.DELETED+"=0",new String[]{""+id})>0; }
    private long value(long id,String column) { try(Cursor c=context.getContentResolver().query(ContentUris.withAppendedId(Events.CONTENT_URI,id),new String[]{column},null,null,null)){if(!c.moveToFirst())throw new AssertionError("Missing event "+id);return c.getLong(0);} }
    private String destinationDescriptions() { StringBuilder s=new StringBuilder();try(Cursor c=context.getContentResolver().query(Events.CONTENT_URI,new String[]{Events.DESCRIPTION},Events.CALENDAR_ID+"=?",new String[]{""+target},null)){while(c.moveToNext())s.append(c.getString(0));}return s.toString(); }
    private String sourceFingerprint() { StringBuilder s=new StringBuilder();try(Cursor c=context.getContentResolver().query(Events.CONTENT_URI,new String[]{Events._ID,Events.TITLE,Events.DTSTART,Events.DTEND,Events.RRULE,Events.ORIGINAL_ID,Events.STATUS},Events.CALENDAR_ID+"=?",new String[]{""+source},Events._ID+" ASC")){while(c.moveToNext())for(int i=0;i<c.getColumnCount();i++)s.append(c.getString(i)).append('|');}try(Cursor c=context.getContentResolver().query(Attendees.CONTENT_URI,new String[]{Attendees.EVENT_ID,Attendees.ATTENDEE_EMAIL},null,null,Attendees.EVENT_ID+" ASC,"+Attendees.ATTENDEE_EMAIL+" ASC")){while(c.moveToNext())s.append(c.getLong(0)).append(':').append(c.getString(1)).append('|');}return s.toString(); }
    private void ageMissing(String key) { try(SQLiteDatabase db=context.openOrCreateDatabase("mirror.db",Context.MODE_PRIVATE,null)){ContentValues v=new ContentValues();v.put("missing_since",System.currentTimeMillis()-20*60000L);v.put("missing_scans",2);db.update("copies",v,key==null?null:"key=?",key==null?null:new String[]{key});} }
    private interface Action { void run() throws Exception; }
    private void expectFailure(String label,Action action) throws Exception {boolean failed=false;try{action.run();}catch(Exception expected){failed=true;}check(failed,label);}
    private void check(boolean condition,String label) {if(!condition)throw new AssertionError(label);checks++;report.append("PASS ").append(label).append('\n');if(checks%5==0){Bundle progress=new Bundle();progress.putString("stream","\nProvider checks completed: "+checks+"\n");sendStatus(0,progress);}}
}
