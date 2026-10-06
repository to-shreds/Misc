# Santa Fe Direct 1.2.0

[Download Santa_Fe_Direct_1_2_0_JOIN_HTML.prj.xml](https://to-shreds.github.io/Misc/SantaFe/tasker/Santa_Fe_Direct_1_2_0_JOIN_HTML.prj.xml) and [open the controller](https://to-shreds.github.io/Misc/santafe/).

1. Back up Tasker. Long-press only the old **Santa Fe Direct** project tab, choose **Delete**, then **With Contents**. Retain your saved global variables. Import the downloaded XML as a project. Tasker rejects an import while that project name already exists.
2. Run **SFD Verify Actions**, confirm **1.2.0**, then run **SFD Open**. The verification task is offline. Under **Settings**, check **Hyundai account** and **Connect**. Existing account variables are reused. Blank password and PIN fields retain saved values; restore them only if missing. The phone has compact HTML controls and smaller native fallback screens.
3. Under phone **Settings**, open **Join receiver**, enable it and save. In the website's **Settings**, paste your Join sendPush link or enter the key and phone ID once, then save. Tap **Test connection**. The browser should say **SENT** and the phone should say **PHONE REACHED**. This test operates no car.

Use the phone's results to confirm each operation. **SENT** means Join accepted the push. The local phone interface displays the vehicle result; an ordinary browser has no return channel. There are separate regular, cold and hot presets. Starts require confirmation that the car is outdoors and safe to start.

For GPS, give Tasker precise location permission and use **Location > Read car GPS** first. Actual car GPS and live Join delivery remain checks on your phone. Periodic comparison defaults off and can be enabled after a valid car lookup. It never operates the car. Amazfit remains separate.

Add a Tasker home-screen shortcut to **SFD Open** for daily access. [Full setup, behavior and limits](tasker/direct/README.md).

---

The original bridge instructions below are retained for the older separate project.

# Santa Fe Control Center 0.1.0

Start with the simulated car. You do not need the Santa Fe, a Hyundai account, or Termux:Tasker for this setup.

This is the first test build, not a vehicle-validated release. The bridge, rule engine, HTTP interface, and screen behavior have automated tests. Importing and running the project in your actual Tasker installation is the next check.

## 1. Install the bridge once

Save `SantaFe-Control-Center-v0.1.0.zip` in your phone's Download folder.

In Termux, run:

```sh
termux-setup-storage
```

Grant the requested storage permission. Then run this block:

```sh
pkg install python unzip
unzip -n "$HOME/storage/downloads/SantaFe-Control-Center-v0.1.0.zip" -d "$HOME"
bash "$HOME/SantaFe-Control-Center-v0.1.0/Install.sh"
sf-start
```

Use the ZIP's exact filename, without a duplicate-download suffix such as `(1)`. The installer puts the running code in Termux's private storage and leaves existing configuration alone. It may install Python timezone data in its own environment if the phone lacks it. The Hyundai library is not needed or installed for simulation.

Leave the Termux session running. The last lines show an eight-character **owner pairing code**, valid for five minutes. It is a temporary code for this bridge, not a Hyundai credential. An expired code can be replaced by stopping the bridge with Ctrl+C and running `sf-start` again.

## 2. Import the Tasker project

Save the separately supplied `Santa_Fe_Control_Center.prj.xml` to the phone. The identical file is also in the ZIP under `tasker/`.

Back up your existing Tasker configuration first. In Tasker, long-press a project tab at the bottom, choose **Import Project**, and select the XML. Import this as a new project, not a replacement for your Regex project.

Run the task **SF Open**. Enter the owner pairing code from Termux in the dashboard. Pair here first, rather than consuming that code in a separate browser. You should see a prominent **SIMULATION** label and a fictional car with 72% fuel.

The native **SF Quick Controls** task provides a compact Scene with buttons. **SF Open** provides the full control center. No real vehicle can be operated until you deliberately connect and switch to live mode.

## 3. Pair the background Tasker actions

In the dashboard, open **Settings**, find **Pair Tasker's background tasks**, and tap **Generate Tasker pairing code**.

Close the Scene, run **SF Pair Tasker**, and enter this second code. It is different from the owner code. Then run **SF Sync**. This enables the project's guarded profiles and saves a restricted bridge token in Tasker's private storage, outside the project XML.

Grant Tasker notification access as requested by Android. Check that Tasker itself and the **SF Local Heartbeat** profile are enabled. The heartbeat checks the local bridge every two minutes; it does not poll Hyundai. Native notifications can therefore take roughly two minutes to appear, longer if Android suspends either app. Tapping a notification opens Tasker; use SF Open for the dashboard. Direct notification action buttons are not included in this build.

## 4. Try a complete automation without the car

Open **Automations** and choose the **Low-range reminder** template. Keep its scope set to **Simulation only**, enable the routine, save it, and enable the master routines switch.

Open **Simulator** and choose the low-fuel scenario. The range changes and the routine creates a notice. Run **SF Sync** to deliver it to Android immediately instead of waiting for the heartbeat. Try changing a door to open or a value to unknown, editing a climate preset, and choosing a simulated timeout or rejection.

**Pause All** disables rules and disarms real remote commands. It cannot recall a command already sent to a vehicle.

## Daily use

Run `sf-start` in Termux when the bridge is not running. Run **SF Open** or add that task as a Tasker home-screen shortcut. Settings and presets survive a bridge restart. Every restart starts in simulation, with real controls disarmed and no Hyundai login retained.

Do not uninstall Termux to troubleshoot: that removes its private files. First use **Settings > Back up and restore > Show backup JSON** and keep a separate copy. Restoring a backup disables its routines until you review and re-enable them.

## When the car arrives

Stop the bridge, then run:

```sh
bash "$HOME/.local/lib/santa-fe-control-center/0.1.0/Enable-Live.sh"
sf-start
```

This optional step needs Python 3.12 or newer and Internet access to install the third-party library. It has not been installed or exercised on your phone in this build. A package-install failure is a live-mode setup issue, not a reason to discard the working simulator.

In **Account**, enter your Hyundai credentials locally, select the enrolled vehicle, and inspect its returned status. Switching to **Live** does not arm commands. Arming requires a separate deliberate step. Test status first, then one lock command with the car in view. Only test climate start while the car is parked outdoors, never in a garage or enclosed space.

Your car's trim is not proof that every remote control is supported. Cameras, Digital Key, remote window movement, and steering-wheel heat control are not implemented here. Missing values are unknown, not assumed healthy. See README.md and docs/TEST_REPORT.md for exact scope and verification limits.
