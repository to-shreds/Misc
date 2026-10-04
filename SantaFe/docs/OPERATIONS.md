# Operations and recovery

## Where the files live

Installed code: `~/.local/lib/santa-fe-control-center/0.1.0/`.

Private state: `~/.local/share/santa-fe-control-center/`, including configuration, the previous configuration, client hashes, cooldowns, and an instance lock. Optional dependencies are isolated under `runtime-venv/` in this directory. The launcher is `$PREFIX/bin/sf-start` on Termux, or `~/.local/bin/sf-start` in the Linux test environment.

Run `sf-start`. Stop with Ctrl+C in that Termux session. A second process using the same data folder or occupied port exits without replacing the existing process. Do not kill unrelated processes to free the port. The shipped Tasker project uses fixed port 8293; changing only the bridge port breaks its connection.

If a pairing code expires or is consumed in a different browser, restart the bridge to obtain a fresh owner code. Existing client tokens normally remain valid. Owner browser cookies are session cookies and may be lost when a WebView/browser session is destroyed. Tasker's separate private token persists. Re-pairing a dashboard does not erase presets.

## Configuration backups

Use the dashboard's Settings page to export configuration JSON. Save it separately before changing installations. This contains no Hyundai credentials but may contain personal task names and Wi-Fi names. Restoring validates the schema, disables all routines, disarms live commands, and clears pending actions.

The installer refuses to replace an existing same-version code directory whose contents differ. Keep that directory as a rollback copy before deliberately installing a revised build. Do not delete the private state folder just to replace code. Importing the same project again is an Android check, not a substitute for making a Tasker backup first.

Unreadable private JSON files cause a startup error rather than being silently reset. Preserve the files before investigating. Client tokens should not be sent to anyone. Diagnostics do not need a Hyundai password, PIN, or complete raw response.

## Android permissions

Tasker needs whatever Android permissions are required by the particular profiles you enable, including notifications and Bluetooth/Wi-Fi state access. Location permission may be needed for Android to disclose a Wi-Fi SSID. Unknown SSIDs are not treated as evidence that the phone is away from home. Tasker/Termux background and battery settings must be tested on the actual phone; no root or Shizuku dependency is added.

The exact car Bluetooth and home Wi-Fi names are entered under Settings, then copied to profile variables by SF Sync. Home detection is a Wi-Fi proxy. It is not a physical-location guarantee.

## Optional startup at boot

Termux:Boot is an optional separate app, not Termux:Tasker. Use a build compatible with the installed Termux signature, and launch Termux:Boot once before expecting boot scripts to run. Its official instructions use `~/.termux/boot/`.

After manual setup works, a deliberately installed script there can run the private `Start.sh`, redirecting output into Termux private storage. This package does not create that script, acquire a permanent wake lock, or change your Android battery settings. Avoid logging the short-lived owner pairing code into shared storage. In this build a boot-started bridge still starts in simulation and does not retain a live login. A live unattended boot path remains future work.

A wake lock can reduce suspension but consumes power and does not make the process immune to Android termination. Check screen-off behavior before relying on scheduled routines.

## Troubleshooting

**Offline Scene:** run sf-start in Termux, then retry SF Open. Native SF Quick Controls is available offline but cannot send vehicle requests without the bridge.

**Invalid pairing:** owner dashboard code and Tasker transport code are different. Generate the latter in Settings, then run SF Pair Tasker.

**No notification:** run SF Sync manually, check Tasker's notification permission/channel, check the routines master switch and per-rule switch, then inspect recent in-app notices. Android deliveries are at most once. The heartbeat is a two-minute local check, not an immediate push channel.

**A rule does not fire:** inspect its scope, weekday/time window, cooldown, and input freshness. Use its dry run. A missing value is unknown, even for not-equal conditions. Enabling a rule does not create a missing phone event provider.

**Hyundai says pending or the outcome is unknown:** do not repeatedly tap the command. The project will not retry an uncertain command automatically. Check the physical car or official app, then obtain current status.

**Live dependency installation fails:** simulation remains available. Preserve the exact installation error for troubleshooting, with no account secrets. Installing the library is not proof of successful Hyundai login or car compatibility.

## Primary documentation

Tasker Java Code action: https://tasker.joaoapps.com/userguide/en/help/ah_java_code.html

Tasker Web element: https://tasker.joaoapps.com/userguide/en/element_web.html

Termux:Boot instructions: https://github.com/termux/termux-boot/blob/master/README.md
