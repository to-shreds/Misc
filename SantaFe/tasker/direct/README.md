# Santa Fe Direct 1.2.0

The browser sends Join commands. Tasker on your phone performs the working Hyundai USA API calls with its saved account. SFD Open opens the same small HTML interface locally, with vehicle results. Amazfit is a separate future project.

## Install or update

Download **Santa_Fe_Direct_1_2_0_JOIN_HTML.prj.xml**. It contains 65 tasks, 137 executable actions, nine scenes and two opt-in profiles. All original 41 task names/IDs and account variables remain. No external Java files, APK, Termux or server are required. Join is needed only for browser commands.

Back up Tasker before updating. It rejects an existing project name. Long-press only the **Santa Fe Direct** bottom tab, choose **Delete**, then **With Contents**, retaining global variables. Import the distinctly named file. Leave unrelated projects alone. Check the saved account afterward; restore it once if your Tasker version removed the variables.

Run **SFD Verify Actions**. Expect **1.2.0** and **Status parser verified: Locked / Off / Off**. This is an offline fixture, not a real car reading. Run **SFD Open** or **SFD Open Web**. A home-screen Tasker shortcut to SFD Open gives quick access.

Under Settings, open **Hyundai account**, save email/password/four-digit Bluelink PIN once, and Connect. Password/PIN stay masked and blank on revisit; blank retains saved values. With multiple active cars, use Choose car. Credentials remain ordinary Tasker globals by Jon's explicit choice; personal backups can contain them. The distributed project has no personal credentials.

## Browser setup

1. Install/enable Join on the same phone as Tasker. Enable **SFD Join Settings** and Save. The imported **SFD Join Commands** profile already has the native Join Received Push event from Jon's example, filtered on `hyundai=:=`, routing `%joincomm` into SFD Join Receive. No manual profile construction is needed.
2. Open [the controller](https://to-shreds.github.io/Misc/santafe/). Under Settings, enter only the Join API key and save it. By explicit request, the external controller is hard-coded to the current phone device ID. The API key is not in the public source; remembered keys use local storage on that browser/device and are not encrypted. Blank key fields retain the saved key. Forget removes it.
3. Tap **Test connection**. The browser reports SENT; the phone should report PHONE REACHED. This test makes no Hyundai request and operates no car. Allow Tasker notifications to see results while its interface is closed. Join delivery can be delayed.
4. Try Lock and Unlock, checking the phone result. For a remote start, the website sends only the bare start command and Tasker asks the outdoors/safe-to-start question on the phone before contacting Hyundai.

GitHub serves files; your browser sends the Join request. The website contacts only Join, once per action, with URL-encoded parameters, no cookies, no redirects and no retries. A network/unreadable response or HTTP 408/5xx is delivery unknown, not proof that nothing happened. The warning survives reload until you check the phone/car and acknowledge it.

## Results and WebView

SENT means Join accepted a push, not that Tasker received it or Hyundai completed the action. An ordinary browser has no Tasker return channel. Its status/GPS buttons request a read on the phone; view the results there. The page does not invent vehicle completion or status.

The **SFD Web** scene bundles the exact same `control/` HTML/CSS/JavaScript source locally. Buttons call SFD Web Receive with the shared command contract; only safe SFDWebState/SFDWebLog snapshots are read. It displays the correlated Hyundai result, returned status, presets and GPS. It renders offline and needs no Join key. The account editor remains native and masked.

This local copy keeps Tasker's powerful JavaScript interface off mutable websites. There is one UI source: regenerate the XML to include edits. Website deployment updates the browser interface; updating the local phone copy requires a new import. SFD Open Home retains eight native fallback pages with smaller type. The earlier API Lab page/APK remain available.

Phone activity is private and capped at 60 results; browser activity at 100. Logs include times, names, outcomes and HTTP codes. They omit keys, credentials, tokens, transaction IDs, full VINs and coordinates. Coordinates appear in the private phone display only. Export downloads a sanitized JSON file; nothing is uploaded automatically. Clear browser log leaves phone history and saved settings intact.

## Presets

Regular uses existing climate settings, defaulting to 72 F / 10 minutes / defrost off. Cold defaults to 62 F / 10 minutes / defrost off; hot to 81 F / 10 minutes / defrost on. Edit each independently from phone Settings or native Climate. Temperature must be a whole number from 62 to 81 F; duration 1 to 10 minutes. Seats and steering-wheel heat remain off. These are temperature/defrost presets, not copies of MyHyundai's HI/LO or seat ventilation presets.

All use the confirmed `/ac/v2/rcs/rsc/start` recipe. Stop, lock and unlock retain their confirmed recipes. Horn and lights are absent.

## GPS comparison

Give Tasker precise location access. Open Location and **Read car GPS**. It uses the upstream USA `GET /ac/v2/rcs/rfc/findMyCar` endpoint and compares the car coordinates with **the Tasker phone's** GPS. This new endpoint needs its first real-car check; prior logs redacted the whole location object. No extra log is required before testing it.

The display keeps the car timestamp and phone accuracy. Compare phone GPS uses the saved car position without contacting Hyundai. Near/Apart requires car data within 15 minutes, a phone fix within two minutes and phone accuracy at most 100 m. Otherwise the verdict is Unknown; an accuracy boundary overlap is Uncertain. Car accuracy is not provided, so the distance/verdict are approximate. ISO timestamps and the upstream compact UTC format are recognized; other times stay Unknown.

After a successful car-coordinate lookup, Location checks can opt into comparison every 1 to 24 hours, with a near threshold from 25 to 5,000 m. Default is off. Android must allow precise/background location and normal Tasker background execution. Checks never start, stop, lock or unlock. An unresolved command skips a check; a lookup failure pauses the schedule. Changing the account/car clears the location cache and consent. Unavailable phone GPS yields Unknown.

## Join contract and safeguards

The simple examples work: `hyundai=:=lock`, `unlock`, `ignition_on`, `ignition_on_cold`, `ignition_on_hot`, `ignition_off`. Bare starts keep the phone confirmation. Bare commands have no request identity and must not be automatically retried.

The external controller sends only `hyundai=:=COMMAND`, using the same prefix and `%joincomm`. Tasker interprets the command and performs the existing Hyundai request locally. Bare starts keep the phone confirmation. The receiver still accepts the older structured `COMMAND|REQUEST_ID|ISSUED_MS|SAFE` form for compatibility, but the external website no longer generates it.

| Command | Phone operation |
| --- | --- |
| ignition_on / ignition_on_cold / ignition_on_hot | Regular / cold / hot start |
| ignition_off | Remote stop |
| lock / unlock | Door control |
| ping | Offline phone test |
| status / refresh | Read / request refreshed status |
| location / compare_location | Car lookup / offline phone comparison |
| poll | Follow the existing transaction |

Unsupported/malformed commands never contact Hyundai. Join cannot change accounts/vehicles or resolve unknown outcomes. Both receivers share the existing lock, durable unresolved-command guard, one submission and bounded polling. No automatic resubmission occurs. Check command polls the original transaction. Resolve unknown remains manual after checking the car. Cached status keeps its own timestamp and is not changed speculatively after a successful command.

## Rebuild and verify

From the repo root:

```sh
python3 SantaFe/tools/build_tasker_direct.py
PYTHONPATH=SantaFe python3 -m pytest SantaFe/tests -q
bash SantaFe/tests/direct/run-runtime-tests.sh
node --test SantaFe/tests/test_control.cjs
node SantaFe/tests/control_browser.cjs
```

Browser tests require Playwright/Chromium and intercept Join responses. BeanShell tests intercept Hyundai responses using real OkHttp request construction. Native CI imports the exact XML into official Tasker on Android 15, runs offline forms/WebView, and checks replacement from 1.1.0. No automated test uses an account, sends a live Join push or operates a car.
