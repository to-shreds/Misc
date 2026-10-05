# Santa Fe Direct 1.1.0

A standalone Tasker interface for the confirmed Hyundai USA calls. Jon has now confirmed login, vehicle selection, locking and unlocking from Tasker on his own phone. Tasker contacts Hyundai directly using the saved account.

## Install or update

Download **Santa_Fe_Direct_1_1_0_SCENES.prj.xml**. This named copy has 41 tasks and seven scenes; it is byte-identical to the verified 1.1.0 project. Select that exact filename in Tasker's **Import Project** picker.

For an update, back up your Tasker configuration, then remove the old **Santa Fe Direct** project with its contents. Direct XML import rejects an existing project name with **a project with that name already exists**; it does not offer the replacement assumed in the earlier instructions. Long-press the old project's bottom tab, select **Delete**, then **With Contents**. Remove only that project and its tasks/scenes, keeping your saved global variables. Then import the distinctly named new file. Credentials are ordinary Tasker global variables. The removal/import account-retention check is still queued during a GitHub Actions runner incident; that transfer has not yet been independently verified. Check **Account** after import; if Tasker removed the saved values, use **Edit account** once to restore them. The earlier bridge project is separate. Version 1.1.0 keeps the original API task IDs and account variable names.

Run **SFD Verify Actions** once. It should identify version 1.1.0 and show **Status parser verified: Locked / Off / Off** from its offline fixture. That check sends no network request and does not describe the actual car.

Run **SFD Open** for the GUI. Assign that task to a home-screen shortcut or widget if desired. For first use or if account values are absent after replacement, open **Account**, tap **Edit account**, save email/password/four-digit PIN, then tap **Connect**. With multiple active cars, use **Choose vehicle**. The VIN is optional when the account has one active vehicle.

## Scene interface

- **Home:** last returned doors/engine/climate status, vehicle timestamp, current result and navigation.
- **Controls:** lock, unlock, remote start and remote stop, plus access to climate settings and command follow-up.
- **Status:** read or request refresh, reconnect, select a vehicle and inspect the returned timestamp.
- **Account:** masked account editor, Connect, Choose vehicle, Clear session and confirmed Forget account.
- **Climate:** saved temperature, duration and defrost, with editor and start/stop buttons. Defaults are 72 F, ten minutes, defrost off; seats and steering-wheel heat stay off.
- **Command:** latest operation state/result, last HTTP code, unresolved-outcome warning, Check command and Resolve unknown. This is the latest result, not a history of physical car states.
- **Help:** first-use and daily-use instructions.

Each page has Home/Close navigation. Navigation is offline. Account and climate edits use the existing native settings forms, then return to their scene. Each operation closes the page, calls the existing guarded core once and opens the updated status/result page. A control can take up to roughly two minutes to finish checking its transaction; do not run another control while waiting. Remote start retains its outdoor/safe-to-start confirmation.

The original individual tasks and the compact **SFD Controls** menu remain available. No bridge, APK, Termux, plugin or computer is needed. Horn, lights and automatic profiles are excluded.

## Status display correction

Version 1.0.0 showed Unknown for doors, engine and climate on Jon's Tasker even while receiving a valid timestamp. The prior tester log already contains real true/false fields. Version 1.1.0 reads the JSON Boolean directly instead of requiring a boxed Boolean type check, and explicitly tests both states, string booleans, missing/null values and malformed fields. Unknown is retained for values that cannot be recognized. The exact 6.7.6-beta runtime cause was not reproduced in the older host interpreter; the updated native reader and phone display must be distinguished from host-only behavior.

## Account and command behavior

Credentials are entered once and retained in ordinary Tasker variables, as Jon requested. These values can be included in Tasker backups, so do not share a backup containing your account. Password and PIN are masked in setup and are not prefilled visibly. The downloaded project contains no personal credentials.

The access token and transaction details stay in a Java object in Tasker's memory. A valid login is reused. After expiry or process loss, an individual task signs in with the saved account before proceeding. Login/enrollment failures stop repeated automatic password attempts; check the account and run **SFD Connect** explicitly to try again. A rate-limit response blocks requests for five minutes.

A command's HTTP acceptance is separate from its completion. After one submission, Tasker checks that transaction at a fixed ten-second interval, with no more than ten polls during a roughly two-minute wait. SUCCESS confirms Hyundai reported completion. ERROR reports failure. Pending, missing transaction IDs, connection failures and unknown results remain unresolved. Commands are never automatically resubmitted, including after authentication errors.

While an outcome is unresolved, other vehicle commands are blocked. **SFD Check Command** only polls the existing transaction. **SFD Resolve Unknown** requires you to check the car or MyHyundai, then clears the warning locally without sending a command. The warning survives Tasker process loss. Changing account or vehicle is blocked until the outcome is resolved. Clearing the live session or forgetting the account retains the warning.

Status can be cached, even after **SFD Refresh Status**. The displayed vehicle timestamp controls freshness. The safe result variables are `SFDState`, `SFDResult`, `SFDLastHttp`, `SFDStatus`, `SFDVehicleTime`, `SFDDoorLock`, `SFDEngine` and `SFDClimate` (use the `%` prefix in Tasker). SUCCESS is a command completion result; READ is a status result; PENDING or UNKNOWN requires follow-up. No response body, token or transaction ID is placed in those variables.

## Included tasks

The project contains 41 tasks, 89 executable actions and seven native scenes. Its original 17 API/settings tasks remain: the shared **SFD Core**, **SFD Verify Actions**, **SFD Setup**, **SFD Connect**, **SFD Choose Vehicle**, **SFD Status**, **SFD Refresh Status**, **SFD Lock**, **SFD Unlock**, **SFD Remote Start**, **SFD Remote Stop**, **SFD Check Command**, **SFD Resolve Unknown**, **SFD Climate Settings**, **SFD Controls**, **SFD Clear Session** and **SFD Forget Account**.

No automatic profiles, horn or lights actions are included. Jon confirmed lock/unlock and remote start/stop physically in the tester. Additional exports were not required for his start/stop confirmation. Automated tests use synthetic data and never access his account or operate the car. Login, vehicle selection, lock and unlock are physically confirmed from Tasker 1.0.0 on Jon's installed version. Updated scene/status behavior and remote start/stop from Tasker remain phone acceptance checks; start/stop were already confirmed in the tester.

## Rebuild and verify

Run `python3 SantaFe/tools/build_tasker_direct.py` from the repository root. It deterministically embeds `api.java`, `ui.java`, `core.java` and the offline `gui.java` display preparation into the project. No external Java file has to be copied to the phone.

Run `PYTHONPATH=. python3 -m pytest tests -q` from SantaFe and `bash SantaFe/tests/direct/run-runtime-tests.sh` from the repository root with JDK 17. The latter checks pinned dependency hashes and executes the delivered BeanShell with real OkHttp request construction and intercepted replies. Android dialogs are substituted only in host fixtures; the separate emulator smoke test imports the actual XML and exercises the offline core and native forms in an official Tasker trial.

The working API Lab 0.3.3, earlier APKs, original bridge project, simulator and archived broken export are preserved. Source and observed verification details are maintained in `SantaFe/HANDOFF.md`, `SantaFe/docs/SOURCES.md` and `SantaFe/docs/verification/`.
