# Santa Fe Direct 1.0.0

Standalone Tasker controls for Jon's confirmed Hyundai USA API calls. Tasker contacts Hyundai directly. The API Lab app, Termux, a bridge, plugins and a computer are not required to run this project.

## Install and use

Download [Santa_Fe_Direct.prj.xml](https://to-shreds.github.io/Misc/SantaFe/tasker/Santa_Fe_Direct.prj.xml). In Tasker, long-press a project tab at the bottom, choose **Import Project**, and select the downloaded file. It creates a separate **Santa Fe Direct** project, preserving the earlier **Santa Fe Control Center** project.

1. Run **SFD Setup** and save your email, password and four-digit Bluelink PIN. The VIN is optional when your account has exactly one active vehicle.
2. Run **SFD Connect**. This authenticates, finds the active vehicle and reads its status. It sends no vehicle command. With multiple active vehicles, run **SFD Choose Vehicle** to select the Santa Fe once.
3. Run **SFD Controls**, or use the individual **SFD Lock**, **SFD Unlock**, **SFD Remote Start** and **SFD Remote Stop** tasks. They can also be assigned to Tasker shortcuts or widgets.

**SFD Climate Settings** saves temperature, duration and defrost. Defaults are 72 F, ten minutes and defrost off. Seats and steering-wheel heat remain off. Remote start asks you to confirm the car is outdoors and safe to start.

Tasker Java Code must be available. The export targets Jon's Tasker 6.7.6-beta and follows the privately inspected native action structure. **SFD Verify Actions** is an offline check that the core executes and its libraries load. Actual Tasker 6.6.20 on Android 15 passed import, core execution, masked native forms and saved-account restoration after process restart. Your first manual control still checks Hyundai operation on your installed version.

## Account and command behavior

Credentials are entered once and retained in ordinary Tasker variables, as Jon requested. These values can be included in Tasker backups, so do not share a backup containing your account. Password and PIN are masked in setup and are not prefilled visibly. The downloaded project contains no personal credentials.

The access token and transaction details stay in a Java object in Tasker's memory. A valid login is reused. After expiry or process loss, an individual task signs in with the saved account before proceeding. Login/enrollment failures stop repeated automatic password attempts; check the account and run **SFD Connect** explicitly to try again. A rate-limit response blocks requests for five minutes.

A command's HTTP acceptance is separate from its completion. After one submission, Tasker checks that transaction at a fixed ten-second interval, with no more than ten polls during a roughly two-minute wait. SUCCESS confirms Hyundai reported completion. ERROR reports failure. Pending, missing transaction IDs, connection failures and unknown results remain unresolved. Commands are never automatically resubmitted, including after authentication errors.

While an outcome is unresolved, other vehicle commands are blocked. **SFD Check Command** only polls the existing transaction. **SFD Resolve Unknown** requires you to check the car or MyHyundai, then clears the warning locally without sending a command. The warning survives Tasker process loss. Changing account or vehicle is blocked until the outcome is resolved. Clearing the live session or forgetting the account retains the warning.

Status can be cached, even after **SFD Refresh Status**. The displayed vehicle timestamp controls freshness. The safe result variables are `SFDState`, `SFDResult`, `SFDLastHttp`, `SFDStatus`, `SFDVehicleTime`, `SFDDoorLock`, `SFDEngine` and `SFDClimate` (use the `%` prefix in Tasker). SUCCESS is a command completion result; READ is a status result; PENDING or UNKNOWN requires follow-up. No response body, token or transaction ID is placed in those variables.

## Included tasks

The project contains 17 tasks, each with an executable action: the shared **SFD Core**, **SFD Verify Actions**, **SFD Setup**, **SFD Connect**, **SFD Choose Vehicle**, **SFD Status**, **SFD Refresh Status**, **SFD Lock**, **SFD Unlock**, **SFD Remote Start**, **SFD Remote Stop**, **SFD Check Command**, **SFD Resolve Unknown**, **SFD Climate Settings**, **SFD Controls**, **SFD Clear Session** and **SFD Forget Account**.

No automatic profiles, horn or lights actions are included. Jon confirmed lock/unlock and remote start/stop physically in the tester. Additional exports were not required for his start/stop confirmation. Automated tests use synthetic data and never access his account or operate the car. Final phone checks must establish Tasker behavior on his installed version.

## Rebuild and verify

Run `python3 SantaFe/tools/build_tasker_direct.py` from the repository root. It deterministically embeds `api.java`, `ui.java` and `core.java` into the project. No external Java file has to be copied to the phone.

Run `PYTHONPATH=. python3 -m pytest tests -q` from SantaFe and `bash SantaFe/tests/direct/run-runtime-tests.sh` from the repository root with JDK 17. The latter checks pinned dependency hashes and executes the delivered BeanShell with real OkHttp request construction and intercepted replies. Android dialogs are substituted only in host fixtures; the separate emulator smoke test imports the actual XML and exercises the offline core and native forms in an official Tasker trial.

The working API Lab 0.3.3, earlier APKs, original bridge project, simulator and archived broken export are preserved. Source and observed verification details are maintained in `SantaFe/HANDOFF.md`, `SantaFe/docs/SOURCES.md` and `SantaFe/docs/verification/`.
