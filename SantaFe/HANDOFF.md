# Santa Fe Control Center handoff

## Current state

IMPORTANT 2026-10-04 PHONE RESULT: the generated `tasker/Santa_Fe_Control_Center.prj.xml` imports into the user's Tasker with the tasks present but every imported task shows zero actions. The XML file itself contains `<Action>` elements, so the prior structural XML/reference checks were insufficient and must not be treated as Android import validation. The Tasker export generator is therefore a known broken deliverable until a corrected XML is imported on the actual phone and actions are visible.

Version 0.1.0 is the first simulation-oriented build for Tasker 6.7.6-beta and a future U.S. 2026 Santa Fe Hybrid Calligraphy. The complete source and generated XML are in this package. No earlier car-control implementation existed in the inspected ProjectStatus tree. Overall project status remains IN PROGRESS until Android import/runtime and the real account/car are checked.

## Controlling materials

This source folder and its versioned delivery ZIP control implementation. `to-shreds/ProjectStatus/projects/santa-fe-control-center/STATUS.md` controls readiness and next steps. `docs/SOURCES.md` records external interface references. The user's Regex_Scene.prj.xml supplied the Tasker 6.7.6-beta format and 1440x3120 reference geometry, but is not included because it contains unrelated credentials.

## Completed

Built the loopback-only standard-library server, isolated simulation/live adapters, typed rule engine, presets, eight-section responsive UI, 40-task/8-profile/10-Scene Tasker export, native private-token transport, installer/start/optional live-dependency scripts, backup/restore, and tests. All defaults are simulation and routines are off. No Termux:Tasker dependency exists.

Security and reliability work includes origin/host checks, role-separated pairing, private token files, unknown/stale data handling, one vehicle worker, idempotency, one-use confirmations, reconnect cancellation, overdue-action dropping, revision conflicts, malformed-field normalization, and protected same-version installation. Browser testing found and fixed an in-flight refresh race, a double-render navigation race, and stale-settings-form overwrite risk.

## Verification

See docs/TEST_REPORT.md and docs/verification/. The final run passed 89 pytest tests and 15 Chromium DOM checks. Five unique embedded BeanShell snippets parsed successfully. Browser DOM checks use Chromium and an explicit in-memory model transport because this environment blocks browser URL navigation. Actual HTTP is tested independently. BeanShell parsing is syntax-only. No Android import, notification, background-survival, real-library install, or Hyundai operation has been tested.

## Do not break

Do not add Termux:Tasker, AutoInput, Shizuku, a cloud host, or account secrets to Tasker variables/XML. Keep simulation and live scopes separate. A timeout is unknown and must not trigger an automatic retry. Do not promote cached vehicle data to a fresh observation. Do not claim every trim feature is remotely available. Preserve user settings and original projects on update. Keep the local server private and never repoint the privileged Web element at an external site. Do not upload the user's sample XML or APK into this source bundle.

## Unfinished or intentionally deferred

Actual phone import and Scene sizing; Android Java-action execution; permission and background behavior; dependency installation and real Hyundai login; VIN-specific feature verification; complete capability discovery; long-term hardware-backed credentials; automatic live-session restart; direct actionable notification buttons; automatic Android Auto/calendar/weather/geofence providers; camera/Digital Key/window/steering-wheel controls. The source and guide state these limits rather than pretending those integrations exist.

## Next action

The immediate blocker is now known: fix the Tasker export generator so imported tasks retain their actions in Tasker 6.7.6-beta. Do not treat XML that merely contains `<Action>` nodes as sufficient. Validate the corrected export on the actual phone before continuing with simulator or vehicle testing.