# Santa Fe

Start with **[Santa Fe API Lab](https://to-shreds.github.io/Misc/SantaFe/)**. The lowercase `/Misc/santafe/` address redirects there.

The page takes your MyHyundai email, password, and four-digit Bluelink service PIN. It reads enrollment and cached status, inspects returned capability fields, and records sanitized requests/responses automatically. Logs survive reloads in that browser and can be downloaded as JSON. Credentials and session tokens remain only in tab memory.

## Browser setup

On Firefox for Android, install [Tampermonkey](https://addons.mozilla.org/en-US/android/addon/tampermonkey/), then open the page's **Santa Fe network helper** link and choose Install. Reload and check for **Browser helper ready**. Connect, select your vehicle if necessary, and use **Run read tests**. Download the log when finished.

Complete Connection setup above the login form first. In Automatic mode, credentials and Connect stay disabled until the helper is detected. Missing-helper submissions send no login request. Direct browser requests are an explicit diagnostic option, not an automatic fallback.

The helper sends directly from your browser to Hyundai, with a fixed hostname and endpoint allowlist. No hosted proxy, account database, analytics, or Termux setup is required. The ordinary-browser option remains available for diagnostics. An actual credential-free preflight returned HTTP 500 without CORS permission; `no-cors` would not produce readable API results.

## Vehicle commands

Refresh from car is separate and can wake the vehicle. Lock, unlock, climate, lights, and horn tests require individual confirmation. Climate start also requires outdoor acknowledgement. Submission is not completion: check the transaction result. A timeout, unreadable transaction ID, or empty response never triggers a retry. Reported equipment fields do not by themselves confirm remote support.

## Tasker

The old export created named tasks with zero actions on the phone. The generator now follows action attribute and child ordering in the user's known-good native export. The corrected XML contains 40 tasks and 113 actions. The broken file is archived under `tasker/archive/`.

**[Corrected Tasker import candidate](tasker/Santa_Fe_Control_Center.prj.xml)** still needs an actual phone import. This is not a claim that Hyundai features or Tasker runtime are verified. Use the HTML tester's real-account log to decide which calls/features can be carried into Tasker.

The existing local bridge, simulator, UI, settings, and Tasker action payloads are preserved. They remain a separate local system: `Tasker -> localhost bridge -> Hyundai`. See [START-HERE.md](START-HERE.md), [HANDOFF.md](HANDOFF.md), and [docs/TEST_REPORT.md](docs/TEST_REPORT.md).
