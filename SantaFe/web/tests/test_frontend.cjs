"use strict";

// Executes the shipped browser code with a real DOM and mocked network only.
// Install jsdom outside the deployment tree and expose it with NODE_PATH.
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { JSDOM } = require("jsdom");

const root = path.resolve(__dirname, "..");
const source = fs.readFileSync(path.join(root, "static/app.js"), "utf8");
const html = fs.readFileSync(path.join(root, "templates/control.html"), "utf8");
const PASSWORD = "sample";
const TOKEN = "synthetic-private-session-token";
const EMAIL = "synthetic@example.test";
const ACCOUNT_PASSWORD = "synthetic-account-password";
const PIN = "7392";
const markerKey = "santa-fe-pending-command";
let checks = 0;
let scenarios = 0;
const equal = (actual, expected, message) => { assert.deepEqual(actual, expected, message); checks += 1; };
const ok = (actual, message) => { assert(actual, message); checks += 1; };

function vehicleState(command = { state: "idle" }, pinAvailable = true) {
  return {
    connected: true,
    vehicle: { vehicle_id: 0, year: 2026, model: "Santa Fe", vin_last4: "1234" },
    vehicles: [],
    status: { locked: true, engine_running: false, fuel_percent: 55, odometer: 101, updated_at: "20261010150000" },
    command,
    saved_account_available: true,
    pin_available: pinAvailable
  };
}

async function fixture(options = {}) {
  const dom = new JSDOM(html, { url: "https://to-shreds.github.io/Misc/SantaFe/web/", runScripts: "outside-only" });
  const w = dom.window;
  const calls = [];
  const timers = new Map();
  let nextTimer = 0;
  let commandNumber = 0;
  let backendState = options.state || { connected: false, vehicle: null, vehicles: [], status: null, command: { state: "unknown" }, saved_account_available: Boolean(options.savedAccount), pin_available: true };
  let hasSession = false;
  let activeGuard = null;
  const pollResults = [...(options.pollResults || [])];
  const errors = [];

  w.AbortController = AbortController;
  w.setTimeout = (fn, delay) => { const id = ++nextTimer; timers.set(id, { fn, delay }); return id; };
  w.clearTimeout = (id) => timers.delete(id);
  w.HTMLDialogElement.prototype.showModal = function () { this.setAttribute("open", ""); };
  w.HTMLDialogElement.prototype.close = function () { this.removeAttribute("open"); this.dispatchEvent(new w.Event("close")); };
  w.addEventListener("error", (event) => errors.push(event.message));
  if (options.marker) w.sessionStorage.setItem(markerKey, JSON.stringify(options.marker));

  const reply = (data, status = 200) => ({ ok: status >= 200 && status < 300, status, json: async () => structuredClone(data) });
  w.fetch = async (url, request) => {
    const pathname = new URL(url).pathname;
    const body = request.body ? JSON.parse(request.body) : null;
    const call = { path: pathname, method: request.method, body, headers: { ...request.headers }, credentials: request.credentials };
    calls.push(call);
    equal(new URL(url).origin, "https://santa-fe-emergency.onrender.com", "Requests use the configured Render backend");
    equal(request.credentials, "omit", "Cross-site requests omit cookies");
    if (pathname === "/api/web/info") {
      equal(request.method, "GET", "Public information is read only");
      ok(!call.headers["X-Web-Session"] && !call.headers["X-Command-Password"], "Public load sends no credentials");
      return reply({ configured: true, saved_account_available: Boolean(options.savedAccount) });
    }
    if (pathname === "/api/web/session") {
      if (body.password !== PASSWORD) return reply({ ok: false, error: "Incorrect website password" }, 401);
      hasSession = true;
      return reply({ session_token: TOKEN, state: backendState });
    }
    equal(call.headers["X-Web-Session"], TOKEN, "Protected requests carry only the memory session token");
    ok(hasSession, "Protected request occurs after password authorization");
    if (pathname === "/api/web/state") return reply({ state: backendState });
    if (pathname === "/api/web/connect") {
      if (body.saved) equal(body, { saved: true }, "Saved-account connection sends no Hyundai credentials");
      else {
        equal(body, { email: EMAIL, password: ACCOUNT_PASSWORD, pin: PIN }, "Runtime connection sends entered account credentials");
        equal(w.document.getElementById("account-password").value, "", "Hyundai password is cleared before the request");
        equal(w.document.getElementById("account-pin").value, "", "Hyundai PIN is cleared before the request");
      }
      backendState = vehicleState(backendState.command, options.pinAvailable !== false);
      return reply({ state: backendState });
    }
    if (pathname === "/api/web/prepare") {
      if (call.headers["X-Command-Password"] !== PASSWORD) return reply({ ok: false, error: "Incorrect website password", code: "command_password_required" }, 401);
      if (options.rateLimitPrepare) return reply({ ok: false, error: "Too many command-password attempts", code: "command_password_required" }, 429);
      commandNumber += 1;
      activeGuard = { request_id: `request-${commandNumber}`, command_guard: `synthetic-guard-${commandNumber}` };
      backendState = vehicleState({ state: "prepared", action: body.action, request_id: activeGuard.request_id }, options.pinAvailable !== false);
      equal(w.document.getElementById("command-website-password").value, "", "Command password is cleared before preparation");
      equal(w.document.getElementById("command-pin").value, "", "Command PIN is cleared before preparation");
      if (options.losePrepare) throw new Error("Lost preparation response");
      return reply(activeGuard);
    }
    if (pathname === "/api/web/command") {
      equal(call.headers["X-Command-Password"], PASSWORD, "Execution separately sends the entered command password");
      equal(call.headers["X-Command-Guard"], activeGuard.command_guard, "Execution carries the server-issued guard");
      equal(body.request_id, activeGuard.request_id, "Execution uses the prepared request identifier");
      const marker = JSON.parse(w.sessionStorage.getItem(markerKey));
      equal(marker.request_id, activeGuard.request_id, "Request identifier is persisted before execution");
      equal(marker.command_guard, activeGuard.command_guard, "Command guard is persisted before execution");
      equal(marker.action, backendState.command.action, "Persisted marker records the prepared action");
      ok(typeof marker.submitted_at === "string", "Persisted marker records its timestamp");
      ok(!Object.hasOwn(body, "password"), "Website password stays out of the command JSON body");
      backendState = vehicleState({ ...backendState.command, state: options.pending ? "pending" : "succeeded" }, options.pinAvailable !== false);
      if (options.loseCommand) {
        backendState.command.state = "unknown";
        throw new Error("Lost command response");
      }
      return reply({ state: backendState });
    }
    if (pathname === "/api/web/poll") {
      ok(!call.headers["X-Command-Password"], "Polling never retains or sends a command password");
      const result = pollResults.shift();
      if (result === "network_error") throw new Error("Polling network unavailable");
      if (result === "expired_session") return reply({ ok: false, error: "Website access expired" }, 401);
      backendState = vehicleState({ ...backendState.command, state: result || "succeeded" }, options.pinAvailable !== false);
      return reply({ state: backendState });
    }
    if (pathname === "/api/web/resolve") {
      equal(call.headers["X-Command-Password"], PASSWORD, "Resolution requires a freshly entered website password");
      equal(body, { acknowledged: true }, "Resolution includes physical-car acknowledgement");
      backendState = { ...backendState, command: { state: "idle" } };
      return reply({ state: backendState });
    }
    if (pathname === "/api/web/status") return reply({ state: backendState });
    throw new Error(`Unexpected endpoint: ${pathname}`);
  };

  const id = (value) => w.document.getElementById(value);
  const settle = async () => { for (let turn = 0; turn < 8; turn += 1) await new Promise((resolve) => setImmediate(resolve)); };
  const click = async (element) => { (typeof element === "string" ? id(element) : element).click(); await settle(); };
  const submit = async (value) => { id(value).dispatchEvent(new w.Event("submit", { bubbles: true, cancelable: true })); await settle(); };
  const escape = async (value) => { id(value).dispatchEvent(new w.Event("cancel", { cancelable: true })); id(value).removeAttribute("open"); await settle(); };
  const action = (name) => w.document.querySelector(`[data-action="${name}"]`);
  const count = (pathname) => calls.filter((call) => call.path === pathname).length;
  const authorize = async () => {
    await click("authorize-button");
    id("access-password").value = PASSWORD;
    await submit("access-form");
  };
  const connect = async () => {
    await authorize();
    id("account-email").value = EMAIL;
    id("account-password").value = ACCOUNT_PASSWORD;
    id("account-pin").value = PIN;
    await submit("connect-form");
  };
  const resolve = async () => {
    await click("resolve-command");
    id("resolve-password").value = PASSWORD;
    id("checked-car-confirmation").checked = true;
    await submit("resolve-form");
  };
  const ready = async () => { await connect(); await resolve(); };
  const command = async (name, pin = "") => {
    await click(action(name));
    id("command-website-password").value = PASSWORD;
    id("command-pin").value = pin;
    if (name.startsWith("start_")) id("outdoors-confirmation").checked = true;
    await submit("command-form");
  };
  const runTimer = async (delay) => {
    const timer = [...timers.entries()].find(([, entry]) => entry.delay === delay);
    ok(timer, `Expected read-only timer scheduled for ${delay}ms`);
    timers.delete(timer[0]);
    await timer[1].fn();
    await settle();
  };
  const storageSafe = () => {
    const stored = JSON.stringify({ local: { ...w.localStorage }, session: { ...w.sessionStorage } });
    for (const secret of [PASSWORD, TOKEN, ACCOUNT_PASSWORD, EMAIL, PIN]) ok(!stored.includes(secret), "No account, website, PIN or session credential is in browser storage");
    equal(w.localStorage.length, 0, "No persistent browser storage is used");
    if (w.sessionStorage.getItem(markerKey)) {
      const keys = Object.keys(JSON.parse(w.sessionStorage.getItem(markerKey))).sort();
      equal(keys, ["action", "command_guard", "request_id", "submitted_at"], "Only the unresolved-command marker and non-authorizing guard are stored");
    }
  };
  w.eval(source);
  await settle();
  return { w, calls, timers, id, action, count, click, submit, escape, authorize, connect, resolve, ready, command, runTimer, storageSafe, settle,
    finish: () => { equal(errors, [], "Actual frontend throws no DOM errors"); dom.window.close(); scenarios += 1; } };
}

(async () => {
  {
    const f = await fixture();
    equal(f.calls.map((call) => call.path), ["/api/web/info"], "A public page requests only non-sensitive availability");
    ok(!f.id("controls-panel").hidden, "The public control page is visible");
    ok(f.id("connect-form").hidden, "Private account form starts hidden");
    for (const button of f.w.document.querySelectorAll("[data-action]")) ok(button.disabled, "Public commands start disabled");
    await f.click("authorize-button");
    f.id("access-password").value = PASSWORD;
    await f.click("cancel-access");
    equal(f.id("access-password").value, "", "Cancel clears access password");
    await f.click("authorize-button");
    equal(f.id("access-password").value, "", "Opening access never recycles a password");
    await f.submit("access-form");
    equal(f.count("/api/web/session"), 0, "Blank access password is rejected locally");
    f.id("access-password").value = "incorrect";
    await f.submit("access-form");
    equal(f.id("access-password").value, "", "Rejected access password is cleared");
    ok(f.id("connect-form").hidden, "Rejected access does not reveal the private account form");
    equal(f.calls.filter((call) => !["/api/web/info", "/api/web/session"].includes(call.path)), [], "No privileged request occurs before authorization");
    f.storageSafe();
    f.finish();
  }
  {
    const f = await fixture();
    await f.connect();
    equal(f.id("status-lock").textContent, "Locked", "Connection renders safe cached status");
    ok(f.action("unlock").disabled, "Startup uncertainty prevents car commands");
    await f.click("resolve-command");
    await f.submit("resolve-form");
    equal(f.count("/api/web/resolve"), 0, "Blank resolution cannot clear startup guard");
    f.id("resolve-password").value = PASSWORD;
    await f.submit("resolve-form");
    equal(f.count("/api/web/resolve"), 0, "Resolution also requires physical-car acknowledgement");
    f.id("checked-car-confirmation").checked = true;
    await f.submit("resolve-form");
    equal(f.id("resolve-password").value, "", "Resolution clears its password after submission");
    ok(!f.action("unlock").disabled, "Acknowledgement enables connected car controls");
    await f.click(f.action("unlock"));
    f.id("command-website-password").value = PASSWORD;
    f.id("command-pin").value = PIN;
    await f.click("cancel-command");
    equal(f.id("command-website-password").value, "", "Command cancel clears website password");
    equal(f.id("command-pin").value, "", "Command cancel clears Hyundai PIN");
    await f.click(f.action("unlock"));
    equal(f.id("command-website-password").value, "", "Reopening command requires new password input");
    f.id("command-website-password").value = PASSWORD;
    f.id("command-pin").value = PIN;
    await f.escape("confirm-dialog");
    equal(f.id("command-website-password").value, "", "Escape clears website password");
    equal(f.id("command-pin").value, "", "Escape clears Hyundai PIN");
    equal(f.count("/api/web/prepare"), 0, "Cancel and Escape never prepare a command");
    await f.click(f.action("start_cold"));
    ok(f.id("confirm-description").textContent.includes("62"), "Cool-cabin text matches the recipe");
    ok(!f.id("outdoors-row").hidden, "Start exposes outdoor acknowledgement");
    f.id("command-website-password").value = PASSWORD;
    await f.submit("command-form");
    equal(f.count("/api/web/prepare"), 0, "Starting requires outdoor acknowledgement");
    await f.click("cancel-command");
    await f.command("unlock");
    equal(f.count("/api/web/prepare"), 1, "First command is prepared once");
    equal(f.count("/api/web/command"), 1, "First command executes once");
    equal(f.id("command-title").textContent, "Doors unlocked", "Hyundai success state renders the command result");
    equal(f.w.sessionStorage.getItem(markerKey), null, "Confirmed completion clears its guard marker");
    await f.click(f.action("lock"));
    equal(f.id("command-website-password").value, "", "Second command never reuses the first password");
    await f.submit("command-form");
    equal(f.count("/api/web/prepare"), 1, "A memory session cannot authorize an empty command password");
    f.id("command-website-password").value = PASSWORD;
    await f.submit("command-form");
    equal(f.count("/api/web/command"), 2, "Second command executes only after a newly entered password");
    equal(f.count("/api/web/session"), 1, "Read-session authorization never substitutes for each fresh command password");
    equal(f.calls.filter((call) => ["/api/web/prepare", "/api/web/command"].includes(call.path)).map((call) => call.path), ["/api/web/prepare", "/api/web/command", "/api/web/prepare", "/api/web/command"], "Every execution follows its own preparation");
    f.storageSafe();
    await f.click("access-button");
    ok(f.action("lock").disabled && f.id("status-grid").hidden, "Forgetting access hides status and disables commands");
    f.finish();
  }
  {
    const f = await fixture({ savedAccount: true, state: vehicleState({ state: "idle" }) });
    await f.authorize();
    await f.click(f.action("unlock"));
    f.id("command-website-password").value = "incorrect";
    await f.submit("command-form");
    equal(f.count("/api/web/prepare"), 1, "Invalid command password gets one preparation attempt");
    equal(f.count("/api/web/command"), 0, "Invalid command password never executes a command");
    ok(f.id("message-text").textContent.includes("No command was sent"), "Rejected preparation explains that no control was sent");
    equal(f.id("command-website-password").value, "", "Invalid command password is cleared");
    equal(f.count("/api/web/state"), 1, "Invalid command password safely reloads the retained private read session");
    ok(!f.action("lock").disabled, "Invalid command password retains connected read access without a false physical-car acknowledgement");
    ok(f.id("resolve-command").hidden, "Rejected password does not create a false uncertain-command notice");
    f.storageSafe();
    f.finish();
  }
  {
    const f = await fixture({ rateLimitPrepare: true, state: vehicleState({ state: "idle" }) });
    await f.authorize();
    await f.command("unlock");
    equal(f.count("/api/web/command"), 0, "Password rate limiting blocks execution");
    ok(!f.action("lock").disabled && f.id("resolve-command").hidden, "Password rate limiting retains read access and does not invent uncertainty");
    equal(f.id("command-website-password").value, "", "Rate-limited command input is cleared");
    f.storageSafe();
    f.finish();
  }
  {
    const f = await fixture({ savedAccount: true });
    await f.authorize();
    ok(!f.id("connect-saved").hidden, "Authorized page offers a server-configured Hyundai account");
    await f.click("connect-saved");
    await f.resolve();
    await f.command("lock");
    equal(f.count("/api/web/command"), 1, "Configured login and PIN still require a fresh command password");
    f.storageSafe();
    f.finish();
  }
  {
    const f = await fixture({ pending: true, pollResults: ["network_error", "network_error", "network_error", "pending", "succeeded"] });
    await f.ready();
    await f.command("unlock");
    await f.runTimer(5000);
    await f.runTimer(8000);
    await f.runTimer(8000);
    ok(!f.id("resolve-command").hidden, "Failed pending-result checks make manual recovery available");
    await f.click("resolve-command");
    f.id("resolve-password").value = PASSWORD;
    f.id("checked-car-confirmation").checked = true;
    await f.submit("resolve-form");
    equal(f.count("/api/web/resolve"), 1, "Manual recovery cannot resolve a command Hyundai still considers pending");
    equal(f.count("/api/web/poll"), 4, "Manual recovery checks pending Hyundai state once after failed polling");
    equal(f.count("/api/web/command"), 1, "Manual recovery never resubmits pending execution");
    ok(f.action("lock").disabled, "Car controls remain disabled while Hyundai is processing");
    await f.runTimer(5000);
    ok(!f.action("lock").disabled, "Read-only polling resumes after pending recovery check");
    equal(f.id("resolve-password").value, "", "Pending recovery does not retain its password");
    f.storageSafe();
    f.finish();
  }
  {
    const f = await fixture({ pending: true, pollResults: ["expired_session"] });
    await f.ready();
    await f.command("unlock");
    await f.runTimer(5000);
    equal(f.count("/api/web/command"), 1, "Read-session expiry never repeats execution");
    ok(f.action("lock").disabled && f.id("status-grid").hidden, "Read-session expiry removes private status and blocks commands");
    ok(!f.id("resolve-command").hidden, "Expiry during pending execution preserves the physical-car recovery requirement");
    equal(f.id("access-button").textContent, "Connect", "Expired read session must be reauthorized");
    f.storageSafe();
    f.finish();
  }
  {
    const f = await fixture({ pinAvailable: false });
    await f.ready();
    await f.click(f.action("unlock"));
    ok(f.id("command-pin").required, "Missing server PIN requires a command PIN");
    f.id("command-website-password").value = PASSWORD;
    await f.submit("command-form");
    equal(f.count("/api/web/prepare"), 0, "PIN-required command cannot prepare without a PIN");
    f.id("command-pin").value = PIN;
    await f.submit("command-form");
    equal(f.calls.find((call) => call.path === "/api/web/command").body.pin, PIN, "Entered PIN is sent only in execution JSON");
    equal(f.id("command-pin").value, "", "Submitted command PIN is cleared");
    f.storageSafe();
    f.finish();
  }
  {
    const f = await fixture({ loseCommand: true });
    await f.ready();
    await f.command("unlock");
    equal(f.count("/api/web/command"), 1, "Lost execution response is not retried");
    ok(f.action("lock").disabled, "Lost response blocks the next command");
    ok(!f.id("resolve-command").hidden, "Lost response exposes physical-car recovery");
    f.storageSafe();
    ok(![...f.timers.values()].some((timer) => timer.delay === 5000 || timer.delay === 8000), "Unknown execution schedules no automatic resend or polling");
    await f.resolve();
    equal(f.w.sessionStorage.getItem(markerKey), null, "Manual recovery clears the unresolved marker");
    ok(!f.action("lock").disabled, "Password and physical-car acknowledgement permit a later command");
    equal(f.count("/api/web/command"), 1, "Recovery does not resend the uncertain command");
    f.finish();
  }
  {
    const f = await fixture({ losePrepare: true });
    await f.ready();
    await f.command("unlock");
    equal(f.count("/api/web/prepare"), 1, "Lost preparation is never retried");
    equal(f.count("/api/web/command"), 0, "Lost preparation cannot fall through to execution");
    equal(f.count("/api/web/state"), 1, "Lost preparation reads server state to recover its guard");
    ok(f.action("lock").disabled && !f.id("resolve-command").hidden, "Orphaned server preparation blocks controls and offers recovery");
    await f.resolve();
    ok(!f.action("lock").disabled, "Password-protected physical acknowledgement clears orphaned preparation");
    f.storageSafe();
    f.finish();
  }
  {
    const f = await fixture({ pending: true });
    await f.ready();
    await f.command("unlock");
    ok(f.action("lock").disabled, "Pending Hyundai command blocks another execution");
    f.storageSafe();
    await f.runTimer(5000);
    equal(f.count("/api/web/poll"), 1, "Pending command uses read-only polling");
    equal(f.count("/api/web/command"), 1, "Polling never repeats the execution");
    ok(!f.action("lock").disabled, "Confirmed poll completion enables later commands");
    equal(f.id("command-website-password").value, "", "Polling leaves no retained command password");
    f.storageSafe();
    f.finish();
  }
  {
    const f = await fixture({ pending: true, pollResults: ["network_error", "network_error", "network_error"] });
    await f.ready();
    await f.command("unlock");
    await f.runTimer(5000);
    await f.runTimer(8000);
    await f.runTimer(8000);
    equal(f.count("/api/web/poll"), 3, "Read-only polling failure has a bounded retry count");
    equal(f.count("/api/web/command"), 1, "Polling failures never repeat execution");
    ok(f.action("lock").disabled && !f.id("resolve-command").hidden, "Failed result checks require manual physical-car recovery");
    ok(![...f.timers.values()].some((timer) => timer.delay === 5000 || timer.delay === 8000), "Repeated polling failure stops automatic retries");
    await f.click("resolve-command");
    f.id("resolve-password").value = PASSWORD;
    f.id("checked-car-confirmation").checked = true;
    await f.submit("resolve-form");
    equal(f.count("/api/web/poll"), 4, "Recovery checks a still-pending command once before resolving");
    equal(f.count("/api/web/command"), 1, "Recovery polling does not send a car command");
    f.storageSafe();
    f.finish();
  }
  {
    const f = await fixture({ marker: { action: "unlock", submitted_at: "2026-10-10T15:00:00Z", request_id: "old-request", command_guard: "old-guard" } });
    ok(f.action("lock").disabled && !f.id("resolve-command").hidden, "Reloaded unresolved marker blocks public car commands");
    equal(f.calls.map((call) => call.path), ["/api/web/info"], "Reload does not auto-authorize or auto-execute from stored guard");
    f.storageSafe();
    f.finish();
  }
  console.log(JSON.stringify({ scenarios, checks, actualFrontendSourceExecuted: true, mockedNetwork: true, liveHyundaiRequests: 0, browserLayoutVerified: false }));
})().catch((error) => { console.error(error.stack); process.exitCode = 1; });
