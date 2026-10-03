# Verified release 1.0.0

## Completed checks

The classifier suite passed 53 independent expectations and the deletion-policy suite passed 25. These cover exact internal-domain matching, external organizer/attendee handling, absent or invalid participants, standalone TENT markers, overrides, empty sources, large loss, positive cancellations, elapsed-time and consecutive-scan requirements.

Native Android 35 CalendarProvider instrumentation passed all 38 checks using isolated synthetic source and Google-type account fixtures. It verified:

- Expanded recurring meetings and a moved exception produce the intended eight source occurrences.
- Internal, external, TENT, unknown and source-Free classifications produce the correct copies.
- A repeat sync produces no duplicates and preserves destination IDs.
- Title, attendee and single-event time edits update the same destination entry.
- A pending journal row recovers an interrupted insertion without duplicating its marked copy.
- A canceled destination copy is restored, and explicit organizer attendee metadata supports a missing event-organizer field.
- Explicit cancellation and aged, repeatedly missing originals remove their copies.
- Canceling an exception removes only that occurrence, preserving the rest of the series.
- All-day UTC dates and exclusive end dates are retained.
- Hidden or identity-mismatched sources cannot remove copies; an empty healthy source holds removals.
- Unrelated destination entries stay untouched.
- Source events and attendee rows are unchanged; no attendee rows, participant addresses or source alarms are exported.
- Changed configuration requires new approval; unapproved settings cannot sync.
- The native main activity starts without an exception.

The signed APK compiled using Java 17, Android platform 35/build-tools 35.0.0. v2 and v3 signature verification passed. Manifest package `com.jon.calendarbridge`, version 1.0.0, min26/target35, read/write-calendar and boot permissions only. No Internet permission. Test harness and mock authenticator exist only in the separate emulator-test APK, not the user APK.

APK SHA256: `84e8efdb41286a9614158e2e34fa21a31a35cf6a281db0df93fe15d3918ee31d`.

## Limits and phone acceptance

The emulator used real Android calendar storage with synthetic events and a mock Google-type account. It did not connect to Jon's Outlook account or Google's calendar servers. Phone acceptance still needs to confirm the Outlook-exported participant fields and Google's upload of the limited copies. Samsung battery restrictions and Android's scheduling delays were not measured on Jon's phone. These are setup/acceptance checks, not claimed as completed.

Install the APK, select a work calendar and an existing syncing Google destination, review Preview copies, and Start syncing. Confirm an internal meeting, an external meeting and a TENT hold are labeled appropriately, then check the Google destination on calendar.google.com or another device. Change a meeting's title, time or attendees and confirm its copy updates. Cancel/delete a test meeting and confirm its copy disappears, allowing for the 15-minute missing-event guard and native Google upload. A genuinely empty source may require the explicit held-removal review.

The whole-series time-change path is conservative: new occurrence anchors can create newly timed copies while old occurrences await missing-event safety. Native tests verify individual moved exceptions, not same-ID preservation for a complete series shift. Copies outside the rolling history range are retained and not refreshed. Clearing app data or uninstalling loses local ownership identity; use a fresh destination if doing so.

## Reproduce

Run `./test-core.sh` for the policies and `./build.sh` for the APK. Native checks: build the app, run `tests/android/build-test.sh`, then `tests/android/run-test.sh` with an isolated Android 35 emulator. The test runner rejects physical-device serials, and instrumentation requires emulator hardware. It resets this app's local state and creates synthetic fixture calendars, so it must not run on a real phone.

The software emulator in this session had no KVM and initially restarted during boot. An emulator-only watchdog timeout increase allowed the native suite to finish. Test fixtures required a registered mock account plus matched recurrence sync IDs, as the provider correctly removed an unregistered Google account and required complete recurrence linkage. Final test results above are from the corrected fixtures; initial fixture failures are not reported as app passes.
