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
    const page = await context.newPage();
    const errors = [];
    const requests = [];
    page.on("pageerror", (error) => errors.push(error.message));
    page.on("request", (request) => requests.push(new URL(request.url()).pathname));
    await page.goto(base);
    ok(await page.locator('input[name="password"]').isVisible(), "Only login is visible");
    ok(!requests.includes("/static/app.js"), "Control script not loaded before password");
    ok(!requests.includes("/static/style.css"), "Control CSS not loaded before password");
    ok((await context.request.get(base + "/api/state")).status() === 401, "Unauthenticated API blocked");
    await page.locator('input[name="password"]').fill("offline-browser-password");
    await page.locator('button[type="submit"]').click();
    await page.locator("#account-email").waitFor({ state: "visible" });
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
    await page.locator("#checked-car-confirmation").check();
    await page.locator('#resolve-form button[type="submit"]').click();
    await page.waitForFunction(() => !document.querySelector('[data-action="unlock"]').disabled);
    ok(await page.locator("#status-lock").textContent() === "Locked", "Cached car status shown");
    await page.locator('[data-action="start_cold"]').click();
    ok((await page.locator("#confirm-description").textContent()).includes("62"), "Cool cabin matches recipe");
    ok(await page.locator("#outdoors-row").isVisible(), "Outdoor acknowledgment required for start");
    await page.locator("#cancel-command").click();
    ok(!requests.includes("/api/command"), "Cancel sends no command");
    await page.locator('[data-action="unlock"]').click();
    await page.locator("#submit-command").click();
    await page.waitForFunction(() => document.querySelector("#command-title").textContent.includes("Doors unlocked"), { timeout: 12000 });
    ok(requests.filter((p) => p === "/api/prepare").length === 1, "One preparation");
    ok(requests.filter((p) => p === "/api/command").length === 1, "One submission");
    ok(requests.indexOf("/api/prepare") < requests.indexOf("/api/command"), "Guard before command");
    const storage = await page.evaluate(() => ({ local: JSON.stringify(localStorage), session: JSON.stringify(sessionStorage) }));
    ok(!storage.local.includes("synthetic-only") && !storage.session.includes("synthetic-only") && !storage.local.includes("1234"), "No credentials in web storage");
    ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), "Mobile fits width");
    await page.screenshot({ path: path.join(__dirname, "browser-mobile.png"), fullPage: true });
    await page.setViewportSize({ width: 1280, height: 850 });
    ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), "Desktop fits width");
    await page.screenshot({ path: path.join(__dirname, "browser-desktop.png"), fullPage: true });
    await page.locator('form[action="/logout"] button').click();
    await page.locator('input[name="password"]').waitFor({ state: "visible" });
    ok((await context.request.get(base + "/static/app.js")).status() === 401, "Sign out revokes asset access");
    ok(errors.length === 0, "No uncaught browser errors: " + errors.join("; "));
    console.log(JSON.stringify({ checks, unexpectedBrowserErrors: errors, liveHyundaiRequests: 0 }));
  } finally {
    if (browser) await browser.close();
    server.kill("SIGTERM");
  }
})().catch((error) => { console.error(error.stack); process.exitCode = 1; });
