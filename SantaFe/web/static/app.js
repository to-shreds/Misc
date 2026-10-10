"use strict";

(() => {
  const byId = (id) => document.getElementById(id);
  const csrf = document.querySelector('meta[name="csrf-token"]').content;
  const markerKey = "santa-fe-pending-command";
  const pendingStates = new Set(["pending", "accepted", "submitted", "queued", "requested", "waiting", "processing", "running", "in_progress"]);
  const successStates = new Set(["succeeded", "completed", "complete", "success", "done"]);
  const failedStates = new Set(["failure", "failed", "rejected", "cancelled", "canceled", "error"]);
  const unknownStates = new Set(["unknown", "indeterminate", "interrupted", "uncertain", "unconfirmed"]);
  const actions = {
    lock: { label: "Lock doors", detail: "Send a request to lock the car's doors.", success: "Doors locked", pending: "Lock request sent" },
    unlock: { label: "Unlock doors", detail: "Send a request to unlock the car's doors.", success: "Doors unlocked", pending: "Unlock request sent" },
    start_regular: { label: "Start the car", detail: "Start the car with the cabin set to 72°F.", success: "Remote start completed", pending: "Start request sent" },
    start_cold: { label: "Start and cool the cabin", detail: "Start the car with the cabin set to 62°F.", success: "Remote start completed", pending: "Cool-cabin start request sent" },
    start_hot: { label: "Start and warm the cabin", detail: "Start the car with the cabin set to 81°F and defrost enabled.", success: "Remote start completed", pending: "Warm-cabin start request sent" },
    stop: { label: "Stop remote start", detail: "Stop a running remote-start session.", success: "Remote stop completed", pending: "Stop request sent" }
  };

  let state = null;
  let busy = "";
  let pollTimer = null;
  let pollFailures = 0;
  let chosenAction = null;
  let localUncertain = false;
  let marker = readMarker();

  function readMarker() {
    try {
      const value = JSON.parse(sessionStorage.getItem(markerKey));
      return value && actions[value.action] ? value : null;
    } catch (_) { return null; }
  }

  function saveMarker(value) {
    marker = value;
    try {
      if (value) sessionStorage.setItem(markerKey, JSON.stringify(value));
      else sessionStorage.removeItem(markerKey);
    } catch (_) { /* Memory remains the fallback when browser storage is unavailable. */ }
  }

  function commandInfo() {
    const command = state && typeof state.command === "object" && state.command ? state.command : {};
    const status = String(command.state || command.status || (state && state.command_state) || "idle").toLowerCase();
    const id = command.request_id || command.id;
    const terminal = successStates.has(status) || failedStates.has(status);
    const matchedTerminal = terminal && (!marker || (marker.request_id && id === marker.request_id));
    if (matchedTerminal && marker) {
      saveMarker(null);
      localUncertain = false;
    }
    const unresolved = localUncertain || unknownStates.has(status) || (status === "prepared" && busy !== "command") || Boolean(state && state.requires_resolution) || Boolean(marker && busy !== "command" && !pendingStates.has(status) && !matchedTerminal);
    return { command, status, pending: pendingStates.has(status), unresolved, terminal };
  }

  function showMessage(message) {
    byId("message-text").textContent = message;
    byId("message").hidden = !message;
  }

  function errorText(data, fallback) {
    if (typeof data.error === "string") return data.error;
    if (data.error && typeof data.error.message === "string") return data.error.message;
    if (typeof data.message === "string") return data.message;
    return fallback;
  }

  async function api(path, method = "POST", body = {}) {
    const abort = new AbortController();
    const timeout = setTimeout(() => abort.abort(), 90000);
    const options = {
      method,
      credentials: "same-origin",
      cache: "no-store",
      signal: abort.signal,
      headers: { "Accept": "application/json", "X-CSRF-Token": csrf }
    };
    if (method !== "GET") {
      options.headers["Content-Type"] = "application/json";
      options.body = JSON.stringify(body);
    }
    try {
      const response = await fetch(path, options);
      if (response.status === 401) {
        location.assign("/");
        throw new Error("Your website session has ended. Sign in again.");
      }
      let data;
      try { data = await response.json(); }
      catch (error) {
        if (error.name === "AbortError") throw error;
        throw new Error("The service did not return a result. Please check your connection.");
      }
      if (!response.ok || data.ok === false) {
        const error = new Error(errorText(data, "The request could not be completed."));
        error.data = data;
        error.status = response.status;
        throw error;
      }
      return data;
    } catch (error) {
      if (error.name === "AbortError") throw new Error("The request took too long. Its result is unavailable.");
      throw error;
    } finally {
      clearTimeout(timeout);
    }
  }

  function acceptState(data) {
    const candidate = data && typeof data.state === "object" ? data.state : data;
    if (candidate && (Object.hasOwn(candidate, "connected") || Object.hasOwn(candidate, "command") || Object.hasOwn(candidate, "requires_resolution"))) {
      state = candidate;
      render();
      schedulePoll();
      return true;
    }
    return false;
  }

  async function reloadState() { acceptState(await api("/api/state", "GET")); }

  function firstValue(object, keys) {
    for (const key of keys) {
      if (object && object[key] !== null && object[key] !== undefined) return object[key];
    }
    return null;
  }

  function boolText(value, yes, no) {
    if (value === true || value === 1 || value === "true" || value === "1") return yes;
    if (value === false || value === 0 || value === "false" || value === "0") return no;
    if (typeof value === "string" && value) return value;
    return "Unknown";
  }

  function numberText(value, suffix = "") {
    if (value === null || value === undefined || value === "" || !Number.isFinite(Number(value))) return "Unknown";
    return `${Number(value).toLocaleString(undefined, { maximumFractionDigits: 1 })}${suffix}`;
  }

  function formattedTime(value) {
    if (!value) return "No status time available";
    if (/^\d{14}$/.test(String(value))) {
      const text = String(value);
      return `Hyundai timestamp: ${text.slice(0, 4)}-${text.slice(4, 6)}-${text.slice(6, 8)} ${text.slice(8, 10)}:${text.slice(10, 12)}:${text.slice(12, 14)}`;
    }
    if (typeof value === "string" && !/(?:Z|[+-]\d{2}:?\d{2})$/i.test(value)) return `Hyundai timestamp: ${value}`;
    const date = new Date(typeof value === "number" && value < 1e12 ? value * 1000 : value);
    if (Number.isNaN(date.getTime())) return "No status time available";
    const today = new Date();
    const sameDay = date.toDateString() === today.toDateString();
    return `As of ${date.toLocaleString(undefined, sameDay ? { hour: "numeric", minute: "2-digit" } : { month: "short", day: "numeric", hour: "numeric", minute: "2-digit" })}`;
  }

  function render() {
    if (!state) return;
    const accountConnected = Boolean(state.connected);
    const connected = accountConnected && Boolean(state.vehicle);
    const info = commandInfo();
    const vehicle = state.vehicle || {};
    const status = state.status || {};
    const vehicleName = vehicle.name || [vehicle.year, vehicle.model].filter(Boolean).join(" ") || "Santa Fe";
    byId("vehicle-name").textContent = vehicleName;
    byId("connection-label").textContent = connected ? "Connected" : (accountConnected ? "Choose vehicle" : "Disconnected");
    byId("connection-pill").classList.toggle("connected", connected);
    byId("vehicle-detail").textContent = connected ? (vehicle.vin_last4 ? `VIN ending ${vehicle.vin_last4}` : "Bluelink account connected") : (accountConnected ? "Select a vehicle below" : "Connect your account to reach the car");
    byId("status-grid").hidden = !connected;
    byId("status-footer").hidden = !connected;
    byId("status-lock").textContent = boolText(firstValue(status, ["locked", "door_locked", "doorLock"]), "Locked", "Unlocked");
    byId("status-engine").textContent = boolText(firstValue(status, ["engine_running", "engine_on", "engine"]), "Running", "Off");
    byId("status-fuel").textContent = numberText(firstValue(status, ["fuel_percent", "fuel_level", "fuelLevel"]), "%");
    const unit = status.odometer_unit ? ` ${String(status.odometer_unit)}` : "";
    byId("status-odometer").textContent = numberText(firstValue(status, ["odometer", "odometer_value"]), unit);
    byId("status-time").textContent = formattedTime(firstValue(status, ["updated_at", "last_updated", "timestamp"]));
    byId("connection-panel").hidden = connected;
    byId("controls-panel").hidden = !connected;
    byId("connect-saved").hidden = accountConnected || !state.saved_account_available;
    byId("saved-divider").hidden = accountConnected || !state.saved_account_available;
    const vehicles = Array.isArray(state.vehicles) ? state.vehicles : [];
    const chooseVehicle = accountConnected && !connected && vehicles.length > 0;
    byId("connection-title").textContent = chooseVehicle ? "Choose your vehicle" : "Connect your Bluelink account";
    byId("connection-description").textContent = chooseVehicle ? "Select the car you want to control." : "Reconnect here if your account session has cleared.";
    byId("vehicle-select-form").hidden = !chooseVehicle;
    byId("connect-form").hidden = chooseVehicle;
    if (chooseVehicle) {
      const select = byId("vehicle-select");
      const previous = select.value;
      select.replaceChildren();
      for (const car of vehicles) {
        const option = document.createElement("option");
        option.value = String(car.vehicle_id ?? car.id);
        option.textContent = car.label || car.name || [car.year, car.model].filter(Boolean).join(" ") || `Vehicle ${option.value}`;
        select.append(option);
      }
      if ([...select.options].some((option) => option.value === previous)) select.value = previous;
    }
    document.querySelectorAll("[data-action]").forEach((button) => { button.disabled = Boolean(busy || info.pending || info.unresolved || !connected); });
    ["refresh-status", "connect-button", "connect-saved"].forEach((id) => { byId(id).disabled = Boolean(busy); });
    byId("disconnect-button").disabled = Boolean(busy || info.pending);
    byId("resolve-command").disabled = Boolean(busy);
    byId("connect-button").textContent = busy === "connect" ? "Connecting…" : "Connect to car";
    byId("connect-saved").textContent = busy === "saved" ? "Connecting…" : "Use saved Bluelink account";
    byId("refresh-status").lastChild.textContent = busy === "status" ? "Checking…" : "Refresh status";
    renderCommand(info);
  }

  function renderCommand(info) {
    const notice = byId("command-notice");
    const action = info.command.action || (marker && marker.action);
    const details = actions[action] || {};
    notice.hidden = !info.pending && !info.unresolved && !info.terminal && busy !== "command";
    notice.classList.toggle("unknown", info.unresolved);
    notice.classList.toggle("failed", failedStates.has(info.status) && !info.unresolved);
    notice.classList.toggle("success", successStates.has(info.status) && !info.unresolved);
    byId("command-spinner").hidden = !(info.pending || busy === "command") || info.unresolved;
    byId("command-result-icon").hidden = !byId("command-spinner").hidden;
    byId("resolve-command").hidden = !info.unresolved;
    if (info.unresolved) {
      byId("command-title").textContent = "Check the car before continuing";
      byId("command-description").textContent = info.command.message || "The previous command's result is unavailable. Check the doors and engine before sending another command.";
      byId("command-icon-use").setAttribute("href", "#icon-alert");
    } else if (info.pending || busy === "command") {
      byId("command-title").textContent = busy === "command" && !info.pending ? "Sending request…" : (details.pending || "Request sent");
      byId("command-description").textContent = info.command.message || "Waiting for Hyundai to confirm the result. Keep this page open.";
    } else if (successStates.has(info.status)) {
      byId("command-title").textContent = details.success || "Command completed";
      byId("command-description").textContent = info.command.message || "Hyundai confirmed the command completed.";
      byId("command-icon-use").setAttribute("href", "#icon-check");
    } else if (failedStates.has(info.status)) {
      byId("command-title").textContent = "Command did not complete";
      byId("command-description").textContent = info.command.message || "Hyundai reported that the request failed. Check the car before trying again.";
      byId("command-icon-use").setAttribute("href", "#icon-alert");
    }
  }

  function schedulePoll() {
    clearTimeout(pollTimer);
    pollTimer = null;
    if (!state) return;
    const info = commandInfo();
    if (info.pending && !info.unresolved && busy !== "command") pollTimer = setTimeout(pollResult, 5000);
  }

  async function pollResult() {
    if (!state || !commandInfo().pending || commandInfo().unresolved) return;
    try {
      const result = await api("/api/poll");
      pollFailures = 0;
      if (!acceptState(result)) await reloadState();
    } catch (error) {
      if (error.data && acceptState(error.data)) return;
      pollFailures += 1;
      if (pollFailures < 3) {
        pollTimer = setTimeout(pollResult, 8000);
      } else {
        localUncertain = true;
        showMessage("The command result could not be checked. Check the car before sending another command.");
        render();
      }
    }
  }

  async function ordinaryRequest(name, path, body) {
    if (busy) return;
    busy = name;
    showMessage("");
    render();
    try {
      const result = await api(path, "POST", body);
      if (!acceptState(result)) await reloadState();
    } catch (error) {
      if (error.data) acceptState(error.data);
      showMessage(error.message);
    } finally {
      busy = "";
      render();
      schedulePoll();
    }
  }

  byId("connect-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    if (!form.reportValidity() || busy) return;
    const credentials = { email: byId("account-email").value.trim(), password: byId("account-password").value, pin: byId("account-pin").value };
    byId("account-password").value = "";
    byId("account-pin").value = "";
    await ordinaryRequest("connect", "/api/connect", credentials);
    credentials.password = "";
    credentials.pin = "";
  });
  byId("connect-saved").addEventListener("click", () => ordinaryRequest("saved", "/api/connect", { saved: true }));
  byId("vehicle-select-form").addEventListener("submit", (event) => {
    event.preventDefault();
    const id = Number(byId("vehicle-select").value);
    if (Number.isInteger(id)) ordinaryRequest("select", "/api/select", { vehicle_id: id });
  });
  byId("refresh-status").addEventListener("click", () => ordinaryRequest("status", "/api/status", {}));
  byId("disconnect-button").addEventListener("click", () => {
    if (state && !commandInfo().pending) ordinaryRequest("disconnect", "/api/disconnect", {});
  });
  byId("dismiss-message").addEventListener("click", () => showMessage(""));

  document.querySelectorAll("[data-action]").forEach((button) => {
    button.addEventListener("click", () => {
      if (busy || !state || !state.connected || commandInfo().pending || commandInfo().unresolved) return;
      chosenAction = button.dataset.action;
      const action = actions[chosenAction];
      byId("confirm-title").textContent = `${action.label}?`;
      byId("confirm-description").textContent = action.detail;
      byId("submit-command").textContent = action.label;
      byId("command-pin").value = "";
      byId("command-pin").required = state.pin_available === false;
      byId("command-pin-optional").textContent = state.pin_available === false ? "required" : "if not already entered";
      const start = chosenAction.startsWith("start_");
      byId("outdoors-row").hidden = !start;
      byId("outdoors-confirmation").checked = false;
      byId("outdoors-confirmation").required = start;
      byId("confirm-dialog").showModal();
    });
  });
  function closeCommandDialog() {
    byId("command-pin").value = "";
    byId("outdoors-confirmation").checked = false;
    byId("confirm-dialog").close();
    chosenAction = null;
  }
  byId("cancel-command").addEventListener("click", closeCommandDialog);
  byId("confirm-dialog").addEventListener("cancel", () => {
    byId("command-pin").value = "";
    byId("outdoors-confirmation").checked = false;
    chosenAction = null;
  });

  byId("command-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!event.currentTarget.reportValidity() || busy || !chosenAction) return;
    const action = chosenAction;
    const pin = byId("command-pin").value;
    const outdoors = byId("outdoors-confirmation").checked;
    closeCommandDialog();
    if (!state || !state.connected || commandInfo().pending || commandInfo().unresolved) return;
    busy = "command";
    localUncertain = false;
    showMessage("");
    saveMarker({ action, submitted_at: new Date().toISOString() });
    render();
    let prepared = false;
    try {
      const preparation = await api("/api/prepare", "POST", { action, confirmed: true, outdoors });
      if (!preparation.request_id) throw new Error("The service could not prepare the command.");
      prepared = true;
      saveMarker({ ...marker, request_id: preparation.request_id });
      const result = await api("/api/command", "POST", { request_id: preparation.request_id, ...(pin ? { pin } : {}) });
      if (!acceptState(result)) await reloadState();
    } catch (error) {
      if (!prepared) {
        saveMarker(null);
        showMessage(`${error.message} No command was sent to Hyundai.`);
        if (error.data) acceptState(error.data);
        // Preparation can leave a server guard even if its reply is lost.
        // Read that state so the page offers recovery without repeating a control.
        try { await reloadState(); } catch (_) { localUncertain = true; }
      } else {
        const gotState = error.data && acceptState(error.data);
        if (!gotState || !commandInfo().terminal) localUncertain = true;
        showMessage(error.message || "The request's result is unavailable. Check the car before continuing.");
      }
    } finally {
      busy = "";
      pollFailures = 0;
      render();
      schedulePoll();
    }
  });

  byId("resolve-command").addEventListener("click", () => {
    byId("checked-car-confirmation").checked = false;
    byId("resolve-dialog").showModal();
  });
  byId("cancel-resolve").addEventListener("click", () => byId("resolve-dialog").close());
  byId("resolve-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!event.currentTarget.reportValidity() || busy) return;
    byId("resolve-dialog").close();
    busy = "resolve";
    render();
    try {
      if (commandInfo().pending) {
        const checked = await api("/api/poll");
        acceptState(checked);
        if (commandInfo().pending) {
          localUncertain = false;
          pollFailures = 0;
          showMessage("Hyundai is still processing the request. Waiting for its result.");
          return;
        }
      }
      const result = await api("/api/resolve", "POST", { acknowledged: true });
      saveMarker(null);
      localUncertain = false;
      showMessage("");
      if (!acceptState(result)) await reloadState();
    } catch (error) { showMessage(error.message); }
    finally { busy = ""; render(); schedulePoll(); }
  });

  reloadState().catch((error) => {
    showMessage(error.message);
    byId("connection-label").textContent = "Unavailable";
    byId("vehicle-detail").textContent = "Reload the page to try connecting again.";
  });
})();
