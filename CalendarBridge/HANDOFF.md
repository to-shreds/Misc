# Calendar Bridge handoff

## Objective and controlling source

Build a native Android app that reads Jon's locally exposed Outlook work calendar and mirrors limited, classified copies into a chosen Google calendar. The user wants to distinguish genuinely free playing time from meetings that can fit car travel. Canonical source is `to-shreds/Misc/CalendarBridge`. `to-shreds/ProjectStatus/projects/calendar-bridge/STATUS.md` controls readiness and next steps. Version 1.0.0, application ID `com.jon.calendarbridge`, minSdk26, target35.

## Accepted decisions

Default exact internal domain: `phiagroup.com`. External organizers or attendees, incomplete participant data, and standalone TENT title markers block. Manual AUTO/CAR/BLOCK overrides are per occurrence; TENT cannot be overridden to CAR. CAR stays Busy because it is meeting time, not playing time. Source events explicitly marked Free remain informational/free unless TENT or an override blocks them.

Only title, time, classification, generic explanation, and ownership marker are mirrored. Participant checks stay local. No original descriptions, locations, meeting links, invitations, or reminders are copied. No Internet permission, OAuth setup, backend, or paid service. Google uploads local copies through its existing account/calendar sync adapter. Default rolling scan: 30 past days and 12 future calendar months. Old copies outside the moving history range remain as history. Source and destination selection happens on the phone. Initial preview and explicit Start syncing are required.

## Implementation

- `BridgeApi.java`: config, calendar identity checks, stable multi-table source snapshot, organizer from explicit event field or explicit organizer attendee role, complete attendee checks, expanded recurring instances, crash journal, ownership-marked target copies, preview and mirroring.
- `core/MeetingClassifier.java`: pure Java exact-domain classification and TENT precedence.
- `core/DeletionPolicy.java`: pure Java missing-copy safety.
- `MainActivity.java`: native slate/white permissions, selection, settings, preview, pagination, local overrides, status, manual/automatic controls, battery guidance, explicit held-removal review.
- `SyncScheduler.java`, `SyncJobService.java`, `BootReceiver.java`: content-change jobs plus persisted 15-minute periodic fallback. Pause checked inside engine lock; stopped jobs do not rearm.
- `tests/`: independent core tests and native Android CalendarProvider instrumentation with isolated synthetic accounts/events. Never run fixture instrumentation on a real phone.
- `build.sh`: direct SDK/JDK APK compilation/signing. Optional Gradle files for Android Studio.

Source writes are forbidden. Destination updates/deletes use atomic ID, destination-calendar, not-deleted and ownership-marker selections. Pending journal entries precede insertions, so interrupted inserts can be recovered by their markers. Native numeric source IDs identify regular meetings; recurrence keys use series ID plus occurrence anchor. Moved exceptions retain ORIGINAL_INSTANCE_TIME. A whole-series time change can temporarily create newly timed occurrences while old copies wait for missing-event safety, then converge.

Positive cancellation/decline permits immediate removal. Other disappearance requires two healthy observations at least 15 minutes apart. Empty source, major loss, hidden/not-syncing source, identity changes, and failed reads hold removals. Explicit reviewed removal is limited to keys in the displayed preview, the same configuration, a ten-minute lifetime, a fresh source scan, and current ownership. Unrelated destination entries remain untouched. Missing exception attendees do not inherit a potentially incomplete series list.

## Do not break

Keep exact organizer/attendee-domain checking, HAS_ATTENDEE_DATA completeness, TENT precedence over Free and overrides, source read-only behavior, private marked copies, no attendee export, no network permission, atomic mutation guards, missing-event holds, journal-before-insert ordering, and same signing identity. Keep source sync enabled and visible; destination may be hidden in aCalendar to avoid visual duplication. Changing selected calendars leaves old-destination copies alone. Uninstall/clear-data loses the installation identity; start with a fresh destination if doing that.

## Latest verified benchmark

Release 1.0.0 compiled and signed; 53 classifier checks, 25 deletion-policy checks and 38 real Android 35 CalendarProvider fixture checks passed. Native main activity launch passed. Actual Outlook export and Google cloud upload on Jon's phone remain first-use acceptance checks. APK SHA256: `84e8efdb41286a9614158e2e34fa21a31a35cf6a281db0df93fe15d3918ee31d`.

## Continuation and verification

Actual check results and remaining phone acceptance are in `TESTING.md`. Continue from the current files, not chat memory. Run the core tests and compile when code changes; run isolated emulator instrumentation for provider, recurrence, ownership or deletion changes. Do not describe real Outlook export or Google cloud upload as verified until Jon has performed the phone check.

The next action after this release is phone installation, select source/destination, preview classifications, start sync, and verify a copy appears in Google on another device or calendar.google.com. Then verify an internal meeting, an external meeting, a TENT hold, an attendee/time change, and a cancellation/deletion. Afterwards the later planning phase can add tennis/padel playing-window detection and travel buffers; it is outside version1.0.0.

The private update signing identity is preserved separately in `CalendarBridge-update-key.zip`. Retrieve that private archive before signing an update; restore the keystore and password file to a private directory and use CB_SIGNING_DIR. Never commit signing material to Misc. Certificate SHA256: `6D:4B:7D:35:E7:BA:94:64:4B:17:FE:05:FB:3E:A6:A7:8F:76:B8:79:43:FF:9A:28:D7:D0:98:A4:D2:66:F2:AF`.
