# Santa Fe

**[Open the Join controller](https://to-shreds.github.io/Misc/santafe/).** The website sends commands through Join; Tasker on your phone makes the working Hyundai API calls. Enter the Join API key once. Your Hyundai account remains in Tasker.

**Santa Fe Direct 1.3.0** adds regular/cold/hot start, stop, lock/unlock, small HTML phone controls, correlated results, private activity and opt-in car/phone GPS comparison. Download [Santa_Fe_Direct_1_3_0_BARE_JOIN.prj.xml](tasker/Santa_Fe_Direct_1_3_0_BARE_JOIN.prj.xml), follow [setup](tasker/direct/README.md), then run **SFD Open**. There are 65 tasks, 137 actions, nine scenes and two guarded profiles. All 41 original task IDs and account variables remain. Amazfit is deferred.

The phone HTML is generated from the same UI source and bundled locally. Native fallback pages remain under SFD Open Home. Join acceptance and Hyundai completion stay separate. The upstream USA GPS lookup needs its first real-car check; periodic comparison defaults off and never controls the car. See [HANDOFF.md](HANDOFF.md) for current verification and acceptance limits.

The API Lab tester below is preserved for further diagnostics.

Install **[Santa Fe API Lab for Android](https://to-shreds.github.io/Misc/SantaFe/android/dist/SantaFe-API-Lab-0.3.3.apk)** and open it. The [GitHub page](https://to-shreds.github.io/Misc/SantaFe/) links the app; the lowercase `/Misc/santafe/` address opens the new controller.

The HTML tester is bundled inside the APK and talks directly to Hyundai using a restricted native HTTPS component. No Tampermonkey, cloud relay, computer, Termux or server setup is required. The app takes your MyHyundai email, password, and four-digit Bluelink service PIN. Connect reads enrollment and cached status; **Run read tests** repeats the reads and capability inspection. It records sanitized requests/responses automatically. **Export log** exports JSON using Android's file picker. Open **Account settings**, enter your details once, and leave **Remember account on this phone** enabled. Saved details are encrypted using an Android Keystore key and excluded from backup. Connect reuses them without retyping. Switching apps and exporting now preserve the live session. **Disconnect** clears the live login; **Forget saved account** removes the saved details. Session tokens remain in memory. Sanitized logs remain locally.

## Connection and privacy

Inside the app, check for **Direct Hyundai connection**, connect, and select your vehicle if necessary. Start with read tests. The installed app loads only its bundled page; it does not load live website scripts. The native component permits only the exact Hyundai hostname and known endpoints, retains normal HTTPS validation, rejects redirects, and has no application retry loop. Only Internet permission is requested. See [Android details and source](android/README.md) for the complete trust boundary, restrictions and build procedure.

The earlier userscript and manual browser modes are retained for existing users and regression checks. Those modes handle credentials through the helper extension and are no longer the recommended phone workflow. The web page keeps login disabled unless that legacy helper is detected or Direct diagnostic mode is explicitly selected. An actual credential-free preflight returned HTTP 500 without CORS permission; ordinary HTML could not read that tested endpoint, and `no-cors` would not produce usable results.

The app and its source necessarily handle your account details during authentication. Remembered account details are encrypted in app-private storage, sent only to Hyundai, and never stored in browser storage or exported logs. Browser mode does not offer remembered credentials. Fixture testing is separate from the supplied real-account evidence: login, enrollment and status reads work; Jon reports lock and unlock both worked, and the recorded lock transaction reached SUCCESS. Jon subsequently confirmed remote start and stop physically as well. Horn and lights remain untested. Jon has confirmed login, vehicle selection, lock and unlock in Tasker; the new Join/GPS paths have separate device acceptance checks.

## Vehicle commands

Refresh from car is separate and can wake the vehicle. Lock, unlock, climate, lights, and horn tests require individual confirmation. Climate start also requires outdoor acknowledgement. Submission is not completion: check the transaction result. A timeout, unreadable transaction ID, or empty response never triggers a retry. Reported equipment fields do not by themselves confirm remote support.

## Preserved bridge project

The old export created named tasks with zero actions on the phone. The generator now follows action attribute and child ordering in the user's known-good native export. The corrected XML contains 40 tasks and 113 actions. The broken file is archived under `tasker/archive/`.

**[Corrected Tasker import candidate](tasker/Santa_Fe_Control_Center.prj.xml)** still needs an actual phone import. This is not a claim that Hyundai features or Tasker runtime are verified. Use the HTML tester's real-account log to decide which calls/features can be carried into Tasker.

The existing local bridge, simulator, UI, settings, and Tasker action payloads are preserved. They remain a separate local system: `Tasker -> localhost bridge -> Hyundai`. See [START-HERE.md](START-HERE.md), [HANDOFF.md](HANDOFF.md), and [docs/TEST_REPORT.md](docs/TEST_REPORT.md).
