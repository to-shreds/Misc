# Calendar Bridge

An Android app that mirrors a work calendar visible on the phone into a selected Google calendar. It checks attendees locally to distinguish meetings you can generally take from the car from meetings that block your time. Version 1.0.0, Android 8 or newer.

## Install and start

1. Install `CalendarBridge-1.0.0.apk` and open Calendar Bridge.
2. Grant calendar access. Choose the work calendar to read and the Google calendar for copies. A dedicated Google calendar is easiest to manage. It must already be syncing on the phone.
3. Tap **Preview copies**. Check the classifications and tap a meeting to override it if needed.
4. Tap **Start syncing**. The app performs the initial mirror and enables automatic checks.

No Outlook login, Google API project, API key, subscription, or server is needed. Outlook and Google must already be syncing their calendars on the phone. Calendar Bridge writes to Android's calendar provider; the Google calendar sync adapter uploads the copies. A completed local sync does not establish that Google has uploaded them yet. Verify the first copies at calendar.google.com or on another device.

## Meeting rules

- **[CAR]**: complete visible attendee information and organizer emails are within the configured domains. Default: `phiagroup.com`. Manual car-compatible override is available.
- **[BLOCKED]**: external organizer or attendee, incomplete or invalid participant information, manual blocked choice, or a standalone `TENT` title marker.
- **[INFO]**: the source event is explicitly marked Free. It remains Free unless marked TENT or manually classified. This avoids turning informational coworker PTO placeholders into unavailable days.

TENT always blocks, even with a car-compatible override. Domain matching is exact, so `phiagroup.com.other.example` is external. Recurring exceptions with incomplete attendee information remain blocked rather than inheriting a potentially outdated series list. Organizer data can come from the explicit event organizer field or a participant explicitly identified as organizer. It is never guessed from the calendar owner. Overrides apply to the selected occurrence, not the whole recurring series.

CAR meetings remain Busy in Google: they may fit during a drive, but they are not time available to play. This release provides the classified calendar for later tennis or padel planning. It does not calculate playing windows, travel duration, or court availability.

Copies contain the original title and time, classification, a short explanation, and an app ownership marker. Source descriptions, locations, participant addresses, meeting links, invitations, and reminders are not copied. The app does not request Internet permission. Attendee checking stays on the phone. Google uploads the limited copied event data using its existing sync.

## Updates and removals

The app creates, updates, and removes only copies it owns in the selected destination. It never writes to the source. Ownership and calendar checks are included in the actual update/delete selection. A local journal and synced ownership marker recover interrupted inserts without creating another copy.

Positively canceled or declined source events can remove their copies immediately. Other missing events need two successful observations at least 15 minutes apart. Empty source snapshots, large disappearances, hidden calendars, unavailable accounts, and incomplete reads hold removals. The preview lists held copies; **Review held removals** allows explicit removal after checking Outlook is up to date. Review applies only to the displayed preview's missing copies and expires after ten minutes.

The default range is the past 30 days and next 12 calendar months, moving forward daily. You can choose 0 to 365 past days and 1 to 24 future months. Recurrences are flattened to individual occurrences within the range, including moved exceptions. Moving an entire series may temporarily show the old and newly timed copies until the missing-event safety delay completes. Copies that age out of the history range are retained as history and no longer refreshed. Source changes become visible only after Outlook has delivered them to Android; the app can mirror only the date range Outlook exposes locally.

Automatic sync uses Android content-change jobs plus a 15-minute periodic job. Android can delay these checks during sleep, battery restrictions, or force-stop. **Sync now** runs a check while the app is open. Reopen the app after force-stop. On Samsung, remove Calendar Bridge from sleeping apps if updates are delayed. Keep Outlook calendar sync enabled.

Settings changes pause automatic sync and require another preview/start. Changing source or destination leaves previously created copies untouched in the old destination. Removing app data or uninstalling loses its ownership identity; use a fresh destination calendar before starting again to avoid duplicate old copies. Keep the original APK installed when applying signed updates.

Copies appear beside the originals in aCalendar's mixed agenda. Hide the destination there if you only want it for Google-based planning.

## Verification and maintenance

See `TESTING.md` for actual checks performed and outstanding phone verification. Source: `to-shreds/Misc/CalendarBridge`. Read `HANDOFF.md` before changes; readiness is recorded in `to-shreds/ProjectStatus/projects/calendar-bridge/STATUS.md`.

`./test-core.sh` runs independent classifier and deletion-policy regressions. `./build.sh` builds and signs without Gradle dependencies using JDK 17, Android platform 35, and build-tools 35.0.0. Set `ANDROID_SDK_ROOT` if the SDK is elsewhere. Optional Gradle project files support Android Studio. Android emulator instrumentation lives under `tests/android/` when included.

The original private signing identity must be restored before making updates. It is preserved separately and never belongs in the public source repository. Build signing variables are documented in `build.sh`.

Official platform references: [Android Calendar Provider](https://developer.android.com/identity/providers/calendar-provider), [attendee completeness and event fields](https://developer.android.com/reference/android/provider/CalendarContract.EventsColumns), [Android job scheduling](https://developer.android.com/reference/android/app/job/JobInfo.Builder), [Outlook native calendar sync](https://support.microsoft.com/en-us/outlook/how-do-i-sync-my-outlook-calendar-to-the-default-calendar-app-s), and [Google sync troubleshooting](https://support.google.com/calendar/answer/6261951).
