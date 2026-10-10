# Santa Fe emergency web controls

GitHub Pages hosts the public controller at [SantaFe/web/](https://to-shreds.github.io/Misc/SantaFe/web/). A Free Render Python service at [santa-fe-emergency.onrender.com](https://santa-fe-emergency.onrender.com/) verifies passwords and sends the confirmed Hyundai USA requests directly, without a phone, Join or Tasker. Existing phone projects and API Lab remain separate.

## Access and command passwords

The page and frontend source may load publicly. Public files contain no account credentials, PIN, website password or Hyundai tokens. Render checks the separately configured website password when authorizing private account/status access, and checks it again for every command preparation, execution and checked-car resolution. A read session never grants command authority. Each command dialog starts with an empty required password field and clears it after submission or cancellation.

The frontend uses a short-lived read session held only in page memory. Cross-domain requests use explicit headers with credentials omitted, so the flow does not depend on third-party cookies. Render permits only the configured GitHub origin and its own origin. Failed password guesses are globally limited to eight in five minutes. The password is configured only in Render settings; missing password or a short signing secret leaves the backend closed.

To build the Hyundai login and PIN into the backend, set HYUNDAI_EMAIL, HYUNDAI_PASSWORD and HYUNDAI_PIN once in the service's protected Render Environment settings. Never put those values in public HTML, JavaScript, render.yaml or git. If they are absent, the authorized page can accept account details for the current running session. Runtime credentials and Hyundai tokens remain in server memory. Inputs are cleared and no password, PIN or read token is written to browser storage. Actual Hyundai account/status reads without a PIN still need account-holder verification.

## Deploy

GitHub Pages already publishes the repository root on main. The public index.html is generated from templates/control.html by build_public.py; the same relative static assets serve both copies. Regenerate the public file after editing the template and use --check to verify it.

Render repository: https://github.com/to-shreds/Misc, branch main, Python runtime, Free compute, Virginia region. Direct-service commands:

```text
Build: pip install -r SantaFe/web/requirements.txt
Start: cd SantaFe/web && gunicorn -c gunicorn.conf.py app:app
```

The equivalent Blueprint is SantaFe/web/render.yaml with rootDir SantaFe/web. Keep manual deployments so unrelated Misc changes do not restart the controller. No database, disk, worker, cron job or paid resource is required.

| Render setting | Purpose |
| --- | --- |
| WEBSITE_PASSWORD | Separate nonempty website/command password, configured outside git |
| SESSION_SECRET | Random signing secret of at least 32 characters |
| WEB_ORIGIN | Exact frontend origin, https://to-shreds.github.io |
| HYUNDAI_EMAIL | Optional saved MyHyundai email |
| HYUNDAI_PASSWORD | Optional saved MyHyundai password |
| HYUNDAI_PIN | Optional saved four-digit Bluelink PIN |

Select Save and deploy after changing saved credentials. [Render's environment-variable instructions](https://render.com/docs/configure-environment-variables) describe these settings. The current service uses one Gunicorn process with four threads, normal TLS and bounded requests. Do not add workers: the account and command lock are process-wide.

## Commands and recovery

Lock, Unlock, Regular start (72 F), Cool cabin (62 F), Warm cabin (81 F with defrost), and Stop use the preserved confirmed recipes. Starts last ten minutes, use no seat/steering heat, and require outdoor confirmation. Cached status displays the upstream observation time; receiving it does not prove freshness. Accepted requests and completed transactions are distinct.

The public page opens independently of Render, but private account access and commands can wait about a minute when the free backend wakes. Loading or waking never operates the car. After a process restart, authorization and a checked-car acknowledgment are required before controls can send a command.

Before execution, the browser prepares a single-use intent and receives a signed command guard, which it saves with a nonsecret action/time/request marker before sending. Render validates the password, session owner, signed guard, epoch and live intent before one control transmission. Pending/prepared/unknown commands block competing controls and account changes as applicable. Lost replies and uncertain outcomes require checking the car; commands are never automatically resent. Result polling is read-only and bounded.

Read tokens stay in memory. Only the signed guard and nonsecret recovery marker enter sessionStorage. Restoring a marker never prepares or executes a command. Free Render does not provide a durable global command ledger across browsers; the startup acknowledgment is a conservative safeguard. An old signed guard cannot revoke a copied older guard. Keep one process.

The earlier same-origin cookie login/API remain for compatibility, and their command endpoints also require the fresh command password. They do not bypass the new command authorization rule.

## Verification and controlling sources

```sh
pip install -r requirements.txt pytest
python -m pytest -q
python build_public.py --check
node --check static/app.js
```

The DOM flow tests execute the actual frontend with synthetic replies. Install their dependency outside the deployment directory:

```sh
npm install --prefix /tmp/santa-fe-test-deps jsdom@26.1.0
NODE_PATH=/tmp/santa-fe-test-deps/node_modules node tests/test_frontend.cjs
```

See TEST_REPORT.md for current results and limits. Automated Hyundai replies are synthetic; no real account or vehicle is exercised by tests. First live acceptance is authorizing, connecting, and verifying enrollment/cached status from Render before an individually requested vehicle control.

Controlling implementation: SantaFe/diagnostic.js, SantaFe/tasker/direct/api.java and SantaFe/docs/verification/confirmed-api-recipes.json. Continuation state: SantaFe/HANDOFF.md. Readiness: to-shreds/ProjectStatus/projects/santa-fe-control-center/STATUS.md. This uses previously confirmed private Hyundai recipes, not an official Hyundai integration.
