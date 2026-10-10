"use strict";
const { chromium } = require("playwright");
const { spawn } = require("node:child_process");
const path = require("node:path");
const assert = require("node:assert/strict");

(async () => {
  const port = 18371;
  const base = `http://127.0.0.1:${port}`;
  const server = spawn("python3", [path.join(__dirname, "browser_server.py"), String(port)], { stdio: "pipe" });
  let browser;
  let checks = 0;
  const ok = (condition, message) => { assert(condition, message); checks += 1; };
  try {
    let healthy = false;
    for (let attempt = 0; attempt < 80; attempt++) {
      try { healthy = (await fetch(base + "/healthz")).ok; } catch (_) { }
      if (healthy) break;
      await new Promise((resolve) => setTimeout(resolve, 100));
    }
    ok(healthy, "Fixture started");
    browser = await chromium.launch({ headless: true, args: ["--no-sandbox"] });
    const context = await browser.newContext({ viewport: { width: 390, height: 844 } });
    const externalRequests = [];
    await context.route("**/*", async (route) => {
      if (new URL(route.request().url()).origin !== base) {
        externalRequests.push(route.request().url());
        await route.abort();
      } else await route.continue();
    });
    const page = await context.newPage();
    const errors = [];
    const requests = [];
    page.on("pageerror", (error) => errors.push(error.message));
    page.on("request", (request) => requests.push(new URL(request.url()).pathname));
    await page.goto(base + "/frontend/");
    await page.waitForFunction(() => !document.querySelector("#authorize-button").disabled);
    ok(await page.locator("#controls-panel").isVisible(), "Public controls are visible");
    ok(await page.locator('[data-action="unlock"]').isDisabled(), "Public controls cannot execute commands");
    ok(requests.some((p) => p.endsWith("/static/app.js")), "Public page loads its script");
    ok(!requests.includes("/api/web/state") && !requests.includes("/api/web/connect"), "Public load requests no private state");
    ok((await context.request.get(base + "/api/state")).status() === 401, "Unauthenticated API blocked");
    await page.locator("#authorize-button").click();
    await page.locator("#access-password").fill("offline-browser-password");
    await page.locator("#submit-access").click();
    await page.locator("#account-email").waitFor({ state: "visible" });
    ok(await page.locator("#access-password").inputValue() === "", "Private-access password cleared");
    ok(await page.locator("#resolve-command").isVisible(), "Restart guard shown");
    await page.locator("#account-email").fill("test@example.test");
    await page.locator("#account-password").fill("synthetic-only");
    await page.locator("#account-pin").fill("1234");
    await page.locator("#connect-button").click();
    await page.locator("#controls-panel").waitFor({ state: "visible" });
    ok(await page.locator('[data-action="unlock"]').isDisabled(), "Controls blocked until acknowledgment");
    ok(await page.locator("#account-password").inputValue() === "", "Account password cleared from inputs");
    ok(await page.locator("#account-pin").inputValue() === "", "PIN cleared from inputs");
    await page.locator("#resolve-command").click();
    await page.locator("#resolve-password").fill("offline-browser-password");
    await page.locator("#checked-car-confirmation").check();
    await page.locator('#resolve-form button[type="submit"]').click();
    await page.waitForFunction(() => !document.querySelector('[data-action="unlock"]').disabled);
    ok(await page.locator("#status-lock").textContent() === "Locked", "Cached car status shown");
    await page.locator('[data-action="start_cold"]').click();
    ok((await page.locator("#confirm-description").textContent()).includes("62"), "Cool cabin matches recipe");
    ok(await page.locator("#outdoors-row").isVisible(), "Outdoor acknowledgment required for start");
    await page.locator("#cancel-command").click();
    ok(!requests.includes("/api/web/command"), "Cancel sends no command");
    await page.locator('[data-action="unlock"]').click();
    ok(await page.locator("#command-website-password").inputValue() === "", "Command starts with fresh password input");
    await page.locator("#command-website-password").fill("incorrect");
    await page.locator("#submit-command").click();
    await page.waitForFunction(() => document.querySelector("#message-text").textContent.includes("No command was sent"));
    ok(!requests.includes("/api/web/command"), "Wrong command password sends no control");
    await page.waitForFunction(() => !document.querySelector('[data-action="unlock"]').disabled);
    await page.locator('[data-action="unlock"]').click();
    ok(await page.locator("#command-website-password").inputValue() === "", "Rejected password is not reused");
    await page.locator("#command-website-password").fill("offline-browser-password");
    await page.locator("#submit-command").click();
    await page.waitForFunction(() => document.querySelector("#command-title").textContent.includes("Doors unlocked"), undefined, { timeout: 12000 });
    ok(requests.filter((p) => p === "/api/web/prepare").length === 2, "One rejected password and one preparation");
    ok(requests.filter((p) => p === "/api/web/command").length === 1, "One submission");
    ok(requests.indexOf("/api/web/prepare") < requests.indexOf("/api/web/command"), "Guard before command");
    await page.locator('[data-action="lock"]').click();
    ok(await page.locator("#command-website-password").inputValue() === "", "Second command also requires fresh password input");
    await page.locator("#cancel-command").click();
    const storage = await page.evaluate(() => ({ local: JSON.stringify(localStorage), session: JSON.stringify(sessionStorage) }));
    ok(!storage.local.includes("synthetic-only") && !storage.session.includes("synthetic-only") && !storage.local.includes("1234") && !storage.session.includes("offline-browser-password"), "No credentials in web storage");
    ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), "Mobile fits width");
    await page.screenshot({ path: path.join(__dirname, "browser-mobile.png"), fullPage: true });
    await page.setViewportSize({ width: 1280, height: 850 });
    ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), "Desktop fits width");
    await page.screenshot({ path: path.join(__dirname, "browser-desktop.png"), fullPage: true });
    await page.locator("#access-button").click();
    ok(await page.locator('[data-action="unlock"]').isDisabled(), "Forgetting private access disables commands");
    ok(await page.locator("#status-grid").isHidden(), "Forgetting private access hides status");
    ok((await context.request.get(base + "/static/app.js")).status() === 401, "Legacy protected Render assets remain gated");
    ok(errors.length === 0, "No uncaught browser errors: " + errors.join("; "));
    ok(externalRequests.length === 0, "Synthetic fixture never contacts external hosts");
    console.log(JSON.stringify({ checks, unexpectedBrowserErrors: errors, externalRequests, liveHyundaiRequests: 0 }));
  } finally {
    if (browser) await browser.close();
    server.kill("SIGTERM");
  }
})().catch((error) => { console.error(error.stack); process.exitCode = 1; });
