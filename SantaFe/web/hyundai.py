"""Memory-only Hyundai USA client based on the confirmed Santa Fe API recipes.

Authority: to-shreds/Misc, SantaFe/diagnostic.js (API Lab 0.3.3) and
SantaFe/docs/verification/confirmed-api-recipes.json. No request is made on import.
Command acceptance and command completion are separate outcomes.
"""

from __future__ import annotations

import datetime as dt
import http.client
import json
import math
import re
import threading
import time
from urllib.parse import quote
from zoneinfo import ZoneInfo

HOST = "api.telematics.hyundaiusa.com"
BASE = "https://" + HOST
MAX_RESPONSE_BYTES = 1_048_576
REQUEST_TIMEOUT = 25
API = "/ac/v2/"
ACTIONS = frozenset({"lock", "unlock", "start_regular", "start_cold", "start_hot", "stop"})


class HyundaiError(Exception):
    """A safe error suitable for showing to the user, without upstream body text."""

    def __init__(self, message, *, code="hyundai_error", uncertain=False):
        super().__init__(message)
        self.message = message
        self.code = code
        self.uncertain = uncertain


def _https_request(method, path, headers, body):
    """Use one fixed TLS host, no redirect handling, bounded time and response size."""
    if method not in {"GET", "POST"} or not path.startswith("/") or any(c in path for c in "\r\n"):
        raise HyundaiError("Invalid Hyundai request.", code="invalid_request")
    connection = http.client.HTTPSConnection(HOST, timeout=REQUEST_TIMEOUT)
    deadline = time.monotonic() + REQUEST_TIMEOUT
    try:
        connection.request(method, path, body=body, headers=headers)
        if connection.sock is not None:
            connection.sock.settimeout(max(0.1, deadline - time.monotonic()))
        response = connection.getresponse()
        response_headers = {k.lower(): v for k, v in response.getheaders()}
        length = response_headers.get("content-length")
        if length is not None and (not length.isdigit() or int(length) > MAX_RESPONSE_BYTES):
            raise HyundaiError("Hyundai returned an oversized response.", code="invalid_response")
        result = bytearray()
        while True:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise TimeoutError()
            if connection.sock is not None:
                connection.sock.settimeout(remaining)
            chunk = response.read1(min(65536, MAX_RESPONSE_BYTES + 1 - len(result)))
            if not chunk:
                break
            result.extend(chunk)
            if len(result) > MAX_RESPONSE_BYTES:
                raise HyundaiError("Hyundai returned an oversized response.", code="invalid_response")
        # A 3xx response is returned to the caller and rejected, never followed.
        return response.status, response_headers, bytes(result)
    except HyundaiError:
        raise
    except (OSError, TimeoutError, http.client.HTTPException, ValueError):
        raise HyundaiError("The Hyundai connection failed or timed out. No automatic retry was sent.", code="connection_failed") from None
    finally:
        connection.close()


def _number(value):
    return value if type(value) in (int, float) and math.isfinite(value) else None


def _bool(value):
    return value if type(value) is bool else None


def _safe_timestamp(value):
    # Hyundai commonly returns YYYYMMDDhhmmss or an ISO-style dateTime. Do not
    # assume a timezone or call an old observation fresh merely because it was read.
    if isinstance(value, str) and len(value) <= 40 and re.fullmatch(r"[0-9TtZz:.+ /-]+", value):
        return value
    return None


class HyundaiClient:
    def __init__(self, email, password, pin, *, transport=None):
        if not isinstance(email, str) or not email.strip() or any(c in email for c in "\r\n"):
            raise HyundaiError("Configure the MyHyundai email.", code="configuration")
        if not isinstance(password, str) or not password:
            raise HyundaiError("Configure the MyHyundai password.", code="configuration")
        if pin != "":
            self._validate_pin(pin)
        self._email = email.strip()
        self._password = password
        self._pin = pin
        self._transport = transport or _https_request
        self._token = None
        self._expires = 0
        self._vehicles = []
        self._selected = None
        self._status_read = False
        self._transactions = {}
        self._pending = None
        self._uncertain = False
        self._lock = threading.RLock()

    @staticmethod
    def _validate_pin(pin):
        if not isinstance(pin, str) or not re.fullmatch(r"[0-9]{4}", pin):
            raise HyundaiError("Enter the four-digit Bluelink PIN.", code="invalid_pin")

    def _headers(self, *, auth=True, vehicle=False, pin=None):
        offset = dt.datetime.now(ZoneInfo("America/New_York")).utcoffset().total_seconds() / 3600
        headers = {
            "Content-Type": "application/json;charset=UTF-8",
            "Accept": "application/json, text/plain, */*", "from": "SPA", "to": "ISS",
            "language": "0", "offset": str(int(offset)), "refresh": "false",
            "encryptFlag": "false", "brandIndicator": "H",
            "client_id": "m66129Bb-em93-SPAHYN-bZ91-am4540zp19920",
            "clientSecret": "v558o935-6nne-423i-baa8",
            "Origin": BASE, "Referer": BASE + "/login",
        }
        if auth:
            headers.update(username=self._email, accessToken=self._token)
            if pin or self._pin:
                headers["blueLinkServicePin"] = pin or self._pin
        if vehicle:
            self._require_vehicle()
            headers.update(registrationId=self._selected["regid"], vin=self._selected["vin"], gen=str(self._selected.get("vehicleGeneration") or 2))
        return headers

    def _request(self, method, path, body=None, *, auth=True, vehicle=False, extra=None, pin=None, command=False):
        headers = self._headers(auth=auth, vehicle=vehicle, pin=pin)
        headers.update(extra or {})
        encoded = None if body is None else json.dumps(body, separators=(",", ":"), allow_nan=False).encode("utf-8")
        try:
            status, response_headers, raw = self._transport(method, path, headers, encoded)
        except Exception as error:
            # Do not expose exception text, URLs, credentials, or upstream response bodies.
            if isinstance(error, HyundaiError):
                raise HyundaiError(str(error), code=error.code, uncertain=command) from None
            raise HyundaiError("The Hyundai connection failed or timed out. No automatic retry was sent.", code="connection_failed", uncertain=command) from None
        response_headers = {str(k).lower(): v for k, v in response_headers.items()}
        if len(raw) > MAX_RESPONSE_BYTES:
            raise HyundaiError("Hyundai returned an oversized response.", code="invalid_response", uncertain=command)
        data = None
        if raw.strip():
            try:
                data = json.loads(raw)
            except (ValueError, UnicodeDecodeError):
                raise HyundaiError("Hyundai returned an unreadable response. No automatic retry was sent.", code="invalid_response", uncertain=command) from None
            if not isinstance(data, dict):
                raise HyundaiError("Hyundai returned an unexpected response.", code="invalid_response", uncertain=command)
        if status == 429:
            raise HyundaiError("Hyundai rate limited the request. Try later.", code="rate_limited")
        if status in (401, 403):
            self._token = None
            raise HyundaiError("Hyundai rejected the authentication. Check MyHyundai or reconnect.", code="authentication")
        if not 200 <= status < 300:
            raise HyundaiError("Hyundai rejected the request. No automatic retry was sent.", code="upstream_http", uncertain=command)
        if data and ("errorCode" in data or data.get("error")):
            raise HyundaiError("Hyundai returned an API error. No automatic retry was sent.", code="upstream_api", uncertain=command)
        return data, response_headers

    def _login(self):
        self._token = None
        self._expires = 0
        data, _ = self._request("POST", "/v2/ac/oauth/token", {"username": self._email, "password": self._password}, auth=False)
        token = (data or {}).get("access_token")
        if not isinstance(token, str) or not token or any(c in token for c in "\r\n"):
            raise HyundaiError("Hyundai did not return a login token. Check whether MyHyundai requires extra authentication.", code="authentication")
        duration = (data or {}).get("expires_in", 1800)
        try:
            duration = float(duration)
        except (TypeError, ValueError):
            duration = 1800
        if not math.isfinite(duration) or duration <= 0:
            raise HyundaiError("Hyundai returned an expired login token.", code="authentication")
        self._token = token
        self._expires = time.monotonic() + max(0, duration - 10)

    def _ensure_session(self):
        if not self._token or time.monotonic() >= self._expires:
            self._login()

    def _read(self, path, *, vehicle=False, extra=None):
        self._ensure_session()
        try:
            return self._request("GET", path, vehicle=vehicle, extra=extra)
        except HyundaiError as error:
            if error.code != "authentication":
                raise
            # Reads may reauthenticate once. Vehicle commands never retry.
            self._login()
            return self._request("GET", path, vehicle=vehicle, extra=extra)

    def connect(self):
        with self._lock:
            if self._pending is not None or self._uncertain:
                raise HyundaiError("Check the previous command before reconnecting.", code="pending_command")
            previous = (self._selected or {}).get("regid")
            encoded_email = quote(self._email, safe="~()*!.'-").replace("%40", "@")
            data, _ = self._read(API + "enrollment/details/" + encoded_email)
            records = (data or {}).get("enrolledVehicleDetails")
            if not isinstance(records, list):
                raise HyundaiError("Hyundai did not return its expected vehicle list.", code="invalid_response")
            vehicles = []
            for record in records:
                vehicle = record.get("vehicleDetails") if isinstance(record, dict) else None
                if not isinstance(vehicle, dict):
                    continue
                if not all(isinstance(vehicle.get(key), str) and vehicle[key] and not any(c in vehicle[key] for c in "\r\n") for key in ("regid", "vin")):
                    continue
                if vehicle.get("enrollmentStatus") == "ACTIVE":
                    vehicles.append(vehicle)
            self._vehicles = vehicles
            self._selected = next((v for v in vehicles if v["regid"] == previous), None)
            if len(vehicles) == 1:
                self._selected = vehicles[0]
            self._status_read = False
            return self._safe_vehicles()

    def _safe_vehicle(self, vehicle, index):
        # Avoid nickName because it may contain a person's name or other private text.
        model = vehicle.get("modelName") or vehicle.get("modelCode") or "Hyundai"
        if not isinstance(model, str) or not re.fullmatch(r"[A-Za-z0-9 ()+./_-]{1,60}", model):
            model = "Hyundai"
        return {"id": str(index), "label": model, "vin_suffix": vehicle["vin"][-4:], "selected": vehicle is self._selected}

    def _safe_vehicles(self):
        return {"vehicles": [self._safe_vehicle(v, i) for i, v in enumerate(self._vehicles)],
                "selected": next((str(i) for i, v in enumerate(self._vehicles) if v is self._selected), None)}

    def select_vehicle(self, vehicle_id):
        with self._lock:
            if self._pending is not None or self._uncertain:
                raise HyundaiError("Check the previous command before changing vehicles.", code="pending_command")
            try:
                index = int(vehicle_id)
            except (ValueError, TypeError):
                raise HyundaiError("Choose an enrolled vehicle.", code="vehicle_required") from None
            if not 0 <= index < len(self._vehicles) or str(index) != str(vehicle_id):
                raise HyundaiError("Choose an enrolled vehicle.", code="vehicle_required")
            self._selected = self._vehicles[index]
            self._status_read = False
            return self._safe_vehicles()

    def _require_vehicle(self):
        if self._selected is None:
            raise HyundaiError("Connect and choose an active vehicle first.", code="vehicle_required")

    def cached_status(self):
        with self._lock:
            self._require_vehicle()
            data, _ = self._read(API + "rcs/rvs/vehicleStatus", vehicle=True)
            status = (data or {}).get("vehicleStatus")
            if not isinstance(status, dict):
                raise HyundaiError("Hyundai returned no vehicle status.", code="invalid_response")
            self._status_read = True
            index = self._vehicles.index(self._selected)
            range_data = status.get("dte") if isinstance(status.get("dte"), dict) else {}
            battery = status.get("battery") if isinstance(status.get("battery"), dict) else {}
            return {
                "vehicle": self._safe_vehicle(self._selected, index), "cached": True,
                "vehicle_timestamp": _safe_timestamp(status.get("dateTime")),
                "read_at": dt.datetime.now(dt.timezone.utc).isoformat(),
                "freshness": "cached_observation", "door_locked": _bool(status.get("doorLock")),
                "engine_running": _bool(status.get("engine")), "climate_on": _bool(status.get("airCtrlOn")),
                "fuel_percent": _number(status.get("fuelLevel")),
                "range": {"value": _number(range_data.get("value")), "unit": _number(range_data.get("unit"))},
                "odometer": _number(status.get("odometer")), "battery_percent": _number(battery.get("batSoc")),
            }

    def _command_recipe(self, action):
        if action not in ACTIONS:
            raise HyundaiError("This control is unavailable.", code="invalid_action")
        self._require_vehicle()
        vehicle = self._selected
        if action in {"lock", "unlock"}:
            return (API + "rcs/rdo/" + ("off" if action == "lock" else "on"),
                    {"userName": self._email, "vin": vehicle["vin"]}, {"APPCLOUD-VIN": vehicle["vin"]})
        if vehicle.get("evStatus") != "N" or str(vehicle.get("vehicleGeneration")) != "3":
            raise HyundaiError("This vehicle does not match the confirmed Santa Fe Hybrid remote-start recipe.", code="unsupported_vehicle")
        if action == "stop":
            return API + "rcs/rsc/stop", None, {}
        temperature = {"start_regular": 72, "start_cold": 62, "start_hot": 81}[action]
        return API + "rcs/rsc/start", {
            "Ims": 0, "airCtrl": 1, "airTemp": {"unit": 1, "value": temperature},
            "defrost": action == "start_hot", "heating1": 0, "igniOnDuration": 10,
            "seatHeaterVentInfo": {"drvSeatHeatState": 0, "astSeatHeatState": 0, "rlSeatHeatState": 0, "rrSeatHeatState": 0},
            "username": self._email, "vin": vehicle["regid"],
        }, {}

    def command(self, action, pin=None):
        with self._lock:
            command_pin = self._pin if pin is None else pin
            self._validate_pin(command_pin)
            if self._pending is not None or self._uncertain:
                raise HyundaiError("The previous command is unresolved. Check its result or check the car before continuing.", code="pending_command")
            path, body, extra = self._command_recipe(action)
            if not self._status_read:
                raise HyundaiError("Read the vehicle status before sending a command.", code="status_required")
            # Login is permitted before transmission, never after a failed command.
            self._ensure_session()
            self._uncertain = True
            try:
                _, headers = self._request("POST", path, body, vehicle=True, extra=extra, pin=command_pin, command=True)
            except HyundaiError as error:
                self._uncertain = error.uncertain
                raise
            tid = headers.get("tmstid") or headers.get("transactionid") or headers.get("xid")
            if not isinstance(tid, str) or not re.fullmatch(r"[A-Za-z0-9._:-]{1,256}", tid):
                raise HyundaiError("Hyundai accepted the request without a readable transaction ID. Check the car; no retry was sent.", code="unknown_command", uncertain=True)
            self._transactions[tid] = {"action": action, "vehicle": self._selected, "service": "REMOTE_POLL"}
            self._pending = tid
            self._uncertain = False
            return tid

    def poll(self, tid):
        with self._lock:
            transaction = self._transactions.get(tid)
            if transaction is None:
                raise HyundaiError("There is no matching command to check.", code="unknown_transaction")
            if transaction["vehicle"] is not self._selected:
                raise HyundaiError("Return to the vehicle that received the command.", code="vehicle_required")
            data, _ = self._read(API + "rmt/getRunningStatus", vehicle=True,
                                 extra={"tid": tid, "login_id": self._email, "service_type": transaction["service"]})
            status = (data or {}).get("status")
            state = {"SUCCESS": "success", "ERROR": "failure", "PENDING": "pending"}.get(status, "unknown")
            messages = {
                "success": "Hyundai confirmed command completion. Read status separately to check the car's current state.",
                "failure": "Hyundai reported command failure. No retry was sent.",
                "pending": "Hyundai is still processing the command.",
                "unknown": "Hyundai has not confirmed the command outcome. Check the car before sending another command.",
            }
            if state in {"success", "failure"}:
                if self._pending == tid:
                    self._pending = None
                self._transactions.pop(tid, None)
            return {"state": state, "message": messages[state]}

    def acknowledge_unknown(self):
        """Called only after the user acknowledges physically checking the car."""
        with self._lock:
            self._pending = None
            self._uncertain = False
            self._transactions.clear()
