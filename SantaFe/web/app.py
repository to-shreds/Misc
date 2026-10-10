"""Password-gated emergency controls. One owner, one process, explicit commands."""
from __future__ import annotations

from collections import deque
from datetime import datetime, timezone
import hmac
import os
import secrets
import threading
import time

from flask import Flask, abort, g, jsonify, redirect, render_template, request, session
from itsdangerous import BadSignature, URLSafeSerializer
from werkzeug.exceptions import HTTPException
from werkzeug.middleware.proxy_fix import ProxyFix

from hyundai import HyundaiClient, HyundaiError

ACTIONS = {"lock", "unlock", "start_regular", "start_cold", "start_hot", "stop"}
TERMINAL = {"success", "failure"}
JOURNAL_COOKIE = "sf_command_guard"


def utc_now():
    return datetime.now(timezone.utc).isoformat()


class Controller:
    def __init__(self, factory):
        self.lock = threading.RLock()
        self.factory = factory
        self.epoch = secrets.token_urlsafe(24)
        self.client = None
        self.vehicles = []
        self.vehicle = None
        self.status = None
        self.pin_available = False
        self.command = {"state": "unknown", "message": "The server has restarted. Confirm no earlier command is unresolved before sending another."}
        self.seen = {}
        self.login_attempts = deque()
        self.read_at = 0.0

    def state(self, saved_available=False):
        public_command = {k: v for k, v in self.command.items()
                          if k in {"state", "action", "request_id", "submitted_at", "message"}}
        return {"connected": self.client is not None,
                "vehicle": self.vehicle, "vehicles": self.vehicles,
                "status": self.status, "command": public_command,
                "saved_account_available": saved_available, "pin_available": self.pin_available}


def create_app(settings=None, client_factory=HyundaiClient):
    app = Flask(__name__, static_url_path="/static")
    app.config.update(
        SECRET_KEY=os.environ.get("SESSION_SECRET", ""),
        WEBSITE_PASSWORD=os.environ.get("WEBSITE_PASSWORD", ""),
        HYUNDAI_EMAIL=os.environ.get("HYUNDAI_EMAIL", ""),
        HYUNDAI_PASSWORD=os.environ.get("HYUNDAI_PASSWORD", ""),
        HYUNDAI_PIN=os.environ.get("HYUNDAI_PIN", ""),
        SESSION_COOKIE_NAME="sf_access", SESSION_COOKIE_HTTPONLY=True,
        SESSION_COOKIE_SECURE=True, SESSION_COOKIE_SAMESITE="Strict",
        PERMANENT_SESSION_LIFETIME=1800, MAX_CONTENT_LENGTH=8192,
        MAX_FORM_MEMORY_SIZE=8192, MAX_FORM_PARTS=8,
        REQUIRE_HTTPS=True, LOGIN_LIMIT=8, LOGIN_WINDOW=300,
    )
    if settings:
        app.config.update(settings)
    # Never start an unprotected controller when setup is incomplete.
    configured = len(app.config["SECRET_KEY"]) >= 32 and len(app.config["WEBSITE_PASSWORD"]) >= 12
    if not app.config["SECRET_KEY"]:
        app.config["SECRET_KEY"] = secrets.token_urlsafe(48)
    host = os.environ.get("RENDER_EXTERNAL_HOSTNAME")
    if host and not app.config.get("TESTING"):
        app.config["TRUSTED_HOSTS"] = [host]
    app.wsgi_app = ProxyFix(app.wsgi_app, x_proto=1)
    controller = Controller(client_factory)
    app.extensions["controller"] = controller
    signer = URLSafeSerializer(app.config["SECRET_KEY"], salt="santa-fe-command-guard-v1")

    def authenticated():
        return (session.get("authenticated") is True
                and session.get("epoch") == controller.epoch
                and time.time() - session.get("login_at", 0) < 1800)

    def json_body():
        if request.mimetype != "application/json":
            abort(415)
        body = request.get_json(silent=True)
        if not isinstance(body, dict):
            abort(400)
        return body

    def safe_vehicle(client, result):
        vehicles = result.get("vehicles", [])
        controller.vehicles = [{"id": int(v["id"]), "label": v.get("label", "Santa Fe"),
                                "vin_last4": v.get("vin_suffix", ""), "selected": bool(v.get("selected"))}
                               for v in vehicles]
        selected = next((v for v in controller.vehicles if v["selected"]), None)
        controller.vehicle = ({"name": selected["label"], "vin_last4": selected["vin_last4"]}
                              if selected else None)

    def normalize_status(data):
        return {"locked": data.get("door_locked"), "engine_running": data.get("engine_running"),
                "climate_on": data.get("climate_on"), "fuel_percent": data.get("fuel_percent"),
                "battery_percent": data.get("battery_percent"), "range": data.get("range"),
                "odometer": data.get("odometer"), "updated_at": data.get("vehicle_timestamp"),
                "read_at": data.get("read_at"), "cached": True}

    def saved_available():
        return bool(app.config["HYUNDAI_EMAIL"] and app.config["HYUNDAI_PASSWORD"])

    def restore_guard():
        value = request.cookies.get(JOURNAL_COOKIE)
        if not value:
            return
        try:
            marker = signer.loads(value)
            if not isinstance(marker, dict):
                raise BadSignature("Invalid marker")
        except BadSignature:
            marker = {"request_id": "invalid"}
        with controller.lock:
            key = marker.get("request_id")
            if key == controller.command.get("request_id") or key in controller.seen:
                return
            if controller.command.get("state") not in {"prepared", "pending", "unknown"}:
                controller.command = {"state": "unknown", "message": "An earlier command could not be verified. Check the car before sending another."}

    @app.before_request
    def gate():
        g.nonce = secrets.token_urlsafe(24)
        if request.path == "/healthz":
            return None
        if app.config["REQUIRE_HTTPS"] and not request.is_secure:
            abort(400, description="Use the HTTPS address.")
        if not configured:
            return "Service setup is incomplete.", 503
        is_auth = authenticated()
        if request.path not in {"/", "/login"} and not is_auth:
            if request.path.startswith("/api/") or request.path.startswith("/static/"):
                return jsonify(error="Sign in to continue."), 401
            return redirect("/", code=303)
        if request.method not in {"GET", "HEAD", "OPTIONS"}:
            origin = request.headers.get("Origin")
            expected = request.host_url.rstrip("/")
            if (origin and origin != expected) or request.headers.get("Sec-Fetch-Site") == "cross-site":
                abort(403)
            token = request.headers.get("X-CSRF-Token") if request.path.startswith("/api/") else request.form.get("csrf", "")
            expected_token = session.get("csrf") if is_auth else session.get("login_csrf")
            if not isinstance(token, str) or not expected_token or not hmac.compare_digest(token, expected_token):
                abort(403)
        if is_auth:
            restore_guard()

    @app.after_request
    def headers(response):
        response.headers.update({
            "Cache-Control": "no-store, private", "Pragma": "no-cache",
            "X-Content-Type-Options": "nosniff", "X-Frame-Options": "DENY",
            "Referrer-Policy": "no-referrer", "Permissions-Policy": "geolocation=(), camera=(), microphone=()",
            "Content-Security-Policy": f"default-src 'none'; script-src 'self'; style-src 'self' 'nonce-{g.get('nonce', '')}'; img-src 'self' data:; connect-src 'self'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'",
        })
        if request.is_secure:
            response.headers["Strict-Transport-Security"] = "max-age=31536000"
        return response

    @app.errorhandler(HTTPException)
    def http_error(error):
        messages = {400: "The request could not be processed.", 403: "The request could not be verified. Reload the page.",
                    404: "Page not found.", 413: "The request is too large.", 415: "Send a JSON request.", 405: "Method not allowed."}
        return jsonify(error=messages.get(error.code, "The request could not be processed.")), error.code

    @app.errorhandler(Exception)
    def unexpected_error(error):
        # Deliberately omit request bodies, upstream objects and exception strings.
        with controller.lock:
            if controller.command.get("state") == "pending":
                controller.command.update(state="unknown", message="The outcome could not be verified. Check the car.")
        return jsonify(error="The request could not be completed. Check the command status before trying again."), 500

    @app.get("/healthz")
    def health():
        return jsonify(status="ok", configured=configured)

    @app.get("/")
    def index():
        if authenticated():
            return render_template("control.html", csrf=session["csrf"])
        session.clear()
        session["login_csrf"] = secrets.token_urlsafe(32)
        return render_template("login.html", login_csrf=session["login_csrf"], nonce=g.nonce)

    @app.post("/login")
    def login():
        now = time.monotonic()
        with controller.lock:
            attempts = controller.login_attempts
            while attempts and attempts[0] < now - app.config["LOGIN_WINDOW"]:
                attempts.popleft()
            if len(attempts) >= app.config["LOGIN_LIMIT"]:
                return render_template("login.html", login_csrf=session.get("login_csrf"), nonce=g.nonce,
                                       error="Too many attempts. Wait five minutes and try again."), 429
            attempts.append(now)
        candidate = request.form.get("password", "")
        if not hmac.compare_digest(candidate.encode(), app.config["WEBSITE_PASSWORD"].encode()):
            return render_template("login.html", login_csrf=session.get("login_csrf"), nonce=g.nonce,
                                   error="Incorrect password."), 401
        session.clear()
        session.update(authenticated=True, epoch=controller.epoch, login_at=time.time(),
                       csrf=secrets.token_urlsafe(32), owner=secrets.token_urlsafe(24))
        session.permanent = True
        return redirect("/", code=303)

    @app.post("/logout")
    def logout():
        session.clear()
        # The independent unresolved-command cookie survives signing out.
        return redirect("/", code=303)

    @app.get("/api/state")
    def state():
        with controller.lock:
            return jsonify(controller.state(saved_available()))

    @app.post("/api/connect")
    def connect():
        body = json_body()
        with controller.lock:
            if controller.command.get("state") in {"pending", "prepared"}:
                return jsonify(error="Check the current command before changing accounts."), 409
            if body.get("saved") is True:
                email, password, pin = (app.config[k] for k in ("HYUNDAI_EMAIL", "HYUNDAI_PASSWORD", "HYUNDAI_PIN"))
            else:
                email, password, pin = (body.get(k, "") for k in ("email", "password", "pin"))
            if not all(isinstance(v, str) for v in (email, password, pin)) or not email or not password:
                return jsonify(error="Enter your Hyundai email and password."), 400
            if len(email) > 320 or len(password) > 512 or (pin and (len(pin) != 4 or not pin.isascii() or not pin.isdigit())):
                return jsonify(error="Check the account details and four-digit PIN."), 400
            # Clear the previous account before changing to another one.
            controller.client = None
            controller.pin_available = False
            controller.status = controller.vehicle = None
            controller.vehicles = []
            try:
                client = controller.factory(email, password, pin)
                info = client.connect()
                safe_vehicle(client, info)
                controller.client = client
                controller.pin_available = bool(pin)
                if controller.vehicle:
                    controller.status = normalize_status(client.cached_status())
                    controller.read_at = time.monotonic()
            except HyundaiError as error:
                return jsonify(error=error.message), 502
            return jsonify(controller.state(saved_available()))

    @app.post("/api/select")
    def select():
        body = json_body()
        with controller.lock:
            if not controller.client:
                return jsonify(error="Connect to Hyundai first."), 409
            if controller.command.get("state") in {"pending", "prepared", "unknown"}:
                return jsonify(error="Resolve the current command before selecting another vehicle."), 409
            vehicle_id = body.get("vehicle_id")
            if isinstance(vehicle_id, bool) or not isinstance(vehicle_id, int):
                abort(400)
            try:
                info = controller.client.select_vehicle(vehicle_id)
                # Clients may return the whole enrollment or only update selection.
                if isinstance(info, dict) and "vehicles" in info:
                    safe_vehicle(controller.client, info)
                else:
                    selected = next((v for v in controller.vehicles if v["id"] == vehicle_id), None)
                    if not selected:
                        abort(400)
                    for vehicle in controller.vehicles:
                        vehicle["selected"] = vehicle["id"] == vehicle_id
                    controller.vehicle = {"name": selected["label"], "vin_last4": selected["vin_last4"]}
                controller.status = normalize_status(controller.client.cached_status())
            except HyundaiError as error:
                return jsonify(error=error.message), 502
            return jsonify(controller.state(saved_available()))

    @app.post("/api/status")
    def status():
        json_body()
        with controller.lock:
            if not controller.client or not controller.vehicle:
                return jsonify(error="Connect and select your vehicle first."), 409
            if time.monotonic() - controller.read_at < 5:
                return jsonify(controller.state(saved_available()))
            try:
                controller.status = normalize_status(controller.client.cached_status())
                controller.read_at = time.monotonic()
            except HyundaiError as error:
                return jsonify(error=error.message), 502
            return jsonify(controller.state(saved_available()))

    @app.post("/api/prepare")
    def prepare():
        body = json_body()
        with controller.lock:
            if not controller.client or not controller.vehicle:
                return jsonify(error="Connect and select your vehicle first."), 409
            if controller.command.get("state") in {"unknown", "prepared", "pending"}:
                return jsonify(error="Resolve the current command before sending another."), 409
            action = body.get("action")
            if not isinstance(action, str) or action not in ACTIONS or body.get("confirmed") is not True:
                return jsonify(error="Confirm a valid command."), 400
            if action.startswith("start_") and body.get("outdoors") is not True:
                return jsonify(error="Confirm that the car is outdoors before starting it."), 400
            request_id = secrets.token_urlsafe(32)
            controller.command = {"state": "prepared", "action": action, "request_id": request_id,
                                  "owner": session["owner"], "prepared_at": time.monotonic(),
                                  "submitted_at": utc_now(), "message": "Ready to send."}
            response = jsonify(request_id=request_id)
            response.set_cookie(JOURNAL_COOKIE, signer.dumps({"request_id": request_id, "action": action, "epoch": controller.epoch}),
                                secure=app.config["SESSION_COOKIE_SECURE"], httponly=True, samesite="Strict", max_age=2592000)
            return response

    @app.post("/api/command")
    def command():
        body = json_body()
        with controller.lock:
            cmd = controller.command
            request_id = body.get("request_id")
            if not isinstance(request_id, str):
                abort(400)
            if request_id in controller.seen:
                return jsonify(controller.state(saved_available()))
            if request_id != cmd.get("request_id") or cmd.get("owner") != session["owner"]:
                return jsonify(error="This command was not prepared in this session."), 409
            if cmd.get("state") != "prepared":
                return jsonify(controller.state(saved_available()))
            try:
                marker = signer.loads(request.cookies.get(JOURNAL_COOKIE, ""))
            except BadSignature:
                marker = {}
            if marker.get("request_id") != request_id or marker.get("epoch") != controller.epoch:
                return jsonify(error="The command guard is missing. Check the car before continuing."), 409
            if time.monotonic() - cmd["prepared_at"] > 120:
                cmd.update(state="failure", message="The confirmation expired. Nothing was sent.")
                controller.seen[request_id] = "failure"
                return jsonify(controller.state(saved_available())), 409
            pin = body.get("pin") or None
            if pin is not None and (not isinstance(pin, str) or len(pin) != 4 or not pin.isascii() or not pin.isdigit()):
                return jsonify(error="Enter a four-digit PIN."), 400
            cmd.update(state="pending", message="Sending to Hyundai. Do not send again.", last_poll_at=0.0)
            controller.seen[request_id] = "pending"
            if len(controller.seen) > 256:
                controller.seen.pop(next(iter(controller.seen)))
            try:
                cmd["transaction_id"] = controller.client.command(cmd["action"], pin=pin)
                cmd["message"] = "Hyundai accepted the request. Checking whether it completed."
            except HyundaiError as error:
                cmd.update(state="unknown" if error.uncertain else "failure", message=error.message)
                controller.seen[request_id] = cmd["state"]
            return jsonify(controller.state(saved_available()))

    @app.post("/api/poll")
    def poll():
        json_body()
        with controller.lock:
            cmd = controller.command
            if cmd.get("state") != "pending" or not controller.client:
                return jsonify(controller.state(saved_available()))
            if time.monotonic() - cmd.get("last_poll_at", 0) < 5:
                return jsonify(controller.state(saved_available()))
            cmd["last_poll_at"] = time.monotonic()
            tid = cmd.get("transaction_id")
            if not tid:
                cmd.update(state="unknown", message="The command outcome could not be verified. Check the car.")
            else:
                try:
                    result = controller.client.poll(tid)
                    result_state = result.get("state")
                    cmd.update(state=result_state if result_state in {"pending", "success", "failure", "unknown"} else "unknown",
                               message=result.get("message", "Check the car before sending another command."))
                except HyundaiError as error:
                    cmd.update(state="unknown", message=error.message)
                if time.monotonic() - cmd["prepared_at"] > 180 and cmd["state"] == "pending":
                    cmd.update(state="unknown", message="The command is taking too long to verify. Check the car.")
                controller.seen[cmd["request_id"]] = cmd["state"]
            return jsonify(controller.state(saved_available()))

    @app.post("/api/resolve")
    def resolve():
        body = json_body()
        if body.get("acknowledged") is not True:
            return jsonify(error="Confirm that you checked the car."), 400
        with controller.lock:
            if controller.command.get("state") == "pending":
                return jsonify(error="Wait for the current check to finish."), 409
            if controller.client:
                controller.client.acknowledge_unknown()
            key = controller.command.get("request_id")
            if key:
                controller.seen[key] = "resolved"
            controller.command = {"state": "idle", "message": "Ready."}
            response = jsonify(controller.state(saved_available()))
            response.delete_cookie(JOURNAL_COOKIE, secure=app.config["SESSION_COOKIE_SECURE"], httponly=True, samesite="Strict")
            return response

    @app.post("/api/disconnect")
    def disconnect():
        json_body()
        with controller.lock:
            if controller.command.get("state") in {"pending", "prepared"}:
                return jsonify(error="Check the current command before disconnecting."), 409
            controller.client = None
            controller.pin_available = False
            controller.status = controller.vehicle = None
            controller.vehicles = []
            return jsonify(controller.state(saved_available()))

    return app


app = create_app()
