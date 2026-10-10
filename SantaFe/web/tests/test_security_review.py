"""Independent adversarial checks using a fake Hyundai client, never a vehicle."""
from concurrent.futures import ThreadPoolExecutor
import json
import re
import threading
import time

import pytest

from app import JOURNAL_COOKIE, create_app
from hyundai import HyundaiError


ORIGIN = "https://sf.example.test"
HOST = "sf.example.test"
PASSWORD = "site-test-password-12345"
SETTINGS = {
    "TESTING": True,
    "SECRET_KEY": "a-fake-signing-key-with-at-least-32-characters",
    "WEBSITE_PASSWORD": PASSWORD,
    "HYUNDAI_EMAIL": "fake-owner@example.test",
    "HYUNDAI_PASSWORD": "fake-private-hyundai-password",
    "HYUNDAI_PIN": "",
}


class FakeHyundai:
    def __init__(self, email, password, pin):
        self.calls = []
        self.raise_command = False
        self.poll_state = "pending"

    def connect(self):
        self.calls.append(("connect",))
        return {"vehicles": [{"id": "0", "label": "Santa Fe", "vin_suffix": "0001", "selected": True}]}

    def cached_status(self):
        self.calls.append(("status",))
        return {"door_locked": True, "engine_running": False, "cached": True}

    def command(self, action, pin=None):
        self.calls.append(("command", action, pin))
        if self.raise_command:
            raise HyundaiError("The Hyundai connection timed out.", uncertain=True)
        return "private-upstream-transaction-id"

    def poll(self, tid):
        self.calls.append(("poll", tid))
        return {"state": self.poll_state, "message": "A fake polling result."}

    def acknowledge_unknown(self):
        self.calls.append(("ack",))


@pytest.fixture
def app():
    return create_app(SETTINGS, client_factory=FakeHyundai)


def signin(app, client=None):
    client = client or app.test_client()
    first = client.get("/", base_url=ORIGIN)
    login_csrf = re.search(r'name="csrf" value="([^\"]+)"', first.get_data(as_text=True)).group(1)
    response = client.post("/login", base_url=ORIGIN, headers={"Origin": ORIGIN},
                           data={"password": PASSWORD, "csrf": login_csrf})
    assert response.status_code == 303
    page = client.get("/", base_url=ORIGIN)
    assert page.status_code == 200
    # The GitHub-compatible UI uses a web token, so its template need not expose
    # the legacy API's CSRF value. The signed cookie remains readable by its owner.
    cookie = client.get_cookie(app.config["SESSION_COOKIE_NAME"], domain=HOST)
    csrf = app.session_interface.get_signing_serializer(app).loads(cookie.value)["csrf"]
    return client, csrf


def post(client, csrf, path, body=None, **headers):
    defaults = {"Origin": ORIGIN, "X-CSRF-Token": csrf}
    if path in {"/api/prepare", "/api/command", "/api/resolve"}:
        defaults["X-Command-Password"] = PASSWORD
    return client.post(path, json=body or {}, base_url=ORIGIN,
                       headers={**defaults, **headers})


def ready(app):
    client, csrf = signin(app)
    assert post(client, csrf, "/api/resolve", {"acknowledged": True}).status_code == 200
    assert post(client, csrf, "/api/connect", {"saved": True}).status_code == 200
    return client, csrf, app.extensions["controller"].client


def prepare(client, csrf):
    response = post(client, csrf, "/api/prepare", {"action": "lock", "confirmed": True})
    assert response.status_code == 200
    return response.json["request_id"]


def command_calls(fake):
    return [c for c in fake.calls if c[0] == "command"]


def test_unauthenticated_gate_hides_ui_assets_and_apis(app):
    client = app.test_client()
    response = client.get("/", base_url=ORIGIN)
    text = response.get_data(as_text=True)
    assert 'id="controls-panel"' not in text
    assert "/static/app.js" not in text
    assert "/static/style.css" not in text
    for path in ("/static/app.js", "/static/style.css", "/api/state", "/api/command", "/api/prepare"):
        assert client.get(path, base_url=ORIGIN).status_code == 401
    for path in ("/api/connect", "/api/prepare", "/api/command", "/api/resolve"):
        assert client.post(path, json={}, base_url=ORIGIN).status_code == 401
    assert app.extensions["controller"].client is None


def test_login_inline_css_matches_csp_nonce(app):
    response = app.test_client().get("/", base_url=ORIGIN)
    nonce = re.search(r"style-src[^;]*'nonce-([^']+)'", response.headers["Content-Security-Policy"]).group(1)
    assert f'<style nonce="{nonce}">' in response.get_data(as_text=True)


def test_cookies_are_secure_and_contain_no_hyundai_credentials(app):
    client, csrf, fake = ready(app)
    session_cookie = client.get_cookie(app.config["SESSION_COOKIE_NAME"], domain=HOST)
    assert session_cookie.secure and session_cookie.http_only and session_cookie.same_site == "Strict"
    payload = app.session_interface.get_signing_serializer(app).loads(session_cookie.value)
    assert SETTINGS["HYUNDAI_EMAIL"] not in json.dumps(payload)
    assert SETTINGS["HYUNDAI_PASSWORD"] not in json.dumps(payload)
    assert PASSWORD not in json.dumps(payload)
    prepare(client, csrf)
    journal = client.get_cookie(JOURNAL_COOKIE, domain=HOST)
    assert journal.secure and journal.http_only and journal.same_site == "Strict"
    assert command_calls(fake) == []


@pytest.mark.parametrize("headers", [{"Origin": "https://evil.example"}, {"Origin": "null"},
                                     {"Sec-Fetch-Site": "cross-site"}, {"X-CSRF-Token": "wrong"}])
def test_mutations_reject_wrong_origin_and_csrf(app, headers):
    client, csrf, fake = ready(app)
    response = post(client, csrf, "/api/prepare", {"action": "unlock", "confirmed": True}, **headers)
    assert response.status_code == 403
    assert command_calls(fake) == []


def test_global_login_limit_cannot_be_evaded_with_forwarded_ip(app):
    for index in range(app.config["LOGIN_LIMIT"]):
        client = app.test_client()
        page = client.get("/", base_url=ORIGIN)
        token = re.search(r'name="csrf" value="([^\"]+)"', page.get_data(as_text=True)).group(1)
        response = client.post("/login", base_url=ORIGIN,
                               data={"password": "wrong", "csrf": token},
                               headers={"Origin": ORIGIN, "X-Forwarded-For": f"198.51.100.{index}"})
        assert response.status_code == 401
    client = app.test_client()
    page = client.get("/", base_url=ORIGIN)
    token = re.search(r'name="csrf" value="([^\"]+)"', page.get_data(as_text=True)).group(1)
    response = client.post("/login", base_url=ORIGIN, data={"password": PASSWORD, "csrf": token},
                           headers={"Origin": ORIGIN, "X-Forwarded-For": "203.0.113.200"})
    assert response.status_code == 429


def test_new_process_requires_ack_and_reads_send_no_commands(app):
    client, csrf = signin(app)
    assert post(client, csrf, "/api/connect", {"saved": True}).status_code == 200
    assert post(client, csrf, "/api/status").status_code == 200
    fake = app.extensions["controller"].client
    assert post(client, csrf, "/api/prepare", {"action": "lock", "confirmed": True}).status_code == 409
    assert command_calls(fake) == []


def test_prepare_cookie_must_arrive_before_execute(app):
    client, csrf, fake = ready(app)
    request_id = prepare(client, csrf)
    client.delete_cookie(JOURNAL_COOKIE, domain=HOST)
    response = post(client, csrf, "/api/command", {"request_id": request_id, "pin": "1234"})
    assert response.status_code == 409
    assert command_calls(fake) == []


def test_submission_replay_is_idempotent_and_secrets_stay_server_side(app):
    client, csrf, fake = ready(app)
    request_id = prepare(client, csrf)
    body = {"request_id": request_id, "pin": "1234"}
    first = post(client, csrf, "/api/command", body)
    assert first.status_code == 200
    # Replay lookup must not bypass the fresh password requirement.
    rejected = client.post("/api/command", json=body, base_url=ORIGIN,
                           headers={"Origin": ORIGIN, "X-CSRF-Token": csrf})
    assert rejected.status_code == 401
    second = post(client, csrf, "/api/command", body)
    assert second.status_code == 200
    assert len(command_calls(fake)) == 1
    for text in (first.get_data(as_text=True), second.get_data(as_text=True)):
        assert "private-upstream-transaction-id" not in text
        assert '"owner"' not in text
        assert '"prepared_at"' not in text
        assert SETTINGS["HYUNDAI_PASSWORD"] not in text
        assert '"pin"' not in text


def test_second_browser_cannot_bypass_owner_wide_pending_command(app):
    client, csrf, fake = ready(app)
    request_id = prepare(client, csrf)
    other, other_csrf = signin(app)
    assert post(other, other_csrf, "/api/command", {"request_id": request_id, "pin": "1234"}).status_code == 409
    assert post(other, other_csrf, "/api/prepare", {"action": "unlock", "confirmed": True}).status_code == 409
    assert post(client, csrf, "/api/command", {"request_id": request_id, "pin": "1234"}).status_code == 200
    assert post(other, other_csrf, "/api/connect", {"saved": True}).status_code == 409
    assert post(other, other_csrf, "/api/resolve", {"acknowledged": True}).status_code == 409
    assert len(command_calls(fake)) == 1


def test_lost_upstream_response_stays_unknown_across_logout_and_new_browser(app):
    client, csrf, fake = ready(app)
    fake.raise_command = True
    request_id = prepare(client, csrf)
    result = post(client, csrf, "/api/command", {"request_id": request_id, "pin": "1234"})
    assert result.status_code == 200
    assert result.json["command"]["state"] == "unknown"
    assert len(command_calls(fake)) == 1
    journal_value = client.get_cookie(JOURNAL_COOKIE, domain=HOST).value
    response = client.post("/logout", base_url=ORIGIN, data={"csrf": csrf}, headers={"Origin": ORIGIN})
    assert response.status_code == 303
    assert client.get_cookie(JOURNAL_COOKIE, domain=HOST).value == journal_value
    fresh, fresh_csrf = signin(app)
    assert post(fresh, fresh_csrf, "/api/prepare", {"action": "lock", "confirmed": True}).status_code == 409
    assert len(command_calls(fake)) == 1


def test_restart_invalidates_access_and_requires_ack_even_without_journal(app):
    client, csrf, fake = ready(app)
    request_id = prepare(client, csrf)
    assert post(client, csrf, "/api/command", {"request_id": request_id, "pin": "1234"}).status_code == 200
    copied_session = client.get_cookie(app.config["SESSION_COOKIE_NAME"], domain=HOST).value
    restarted = create_app(SETTINGS, client_factory=FakeHyundai)
    old_browser = restarted.test_client()
    old_browser.set_cookie(restarted.config["SESSION_COOKIE_NAME"], copied_session, domain=HOST)
    assert old_browser.get("/api/state", base_url=ORIGIN).status_code == 401
    fresh, token = signin(restarted)
    assert post(fresh, token, "/api/connect", {"saved": True}).status_code == 200
    assert post(fresh, token, "/api/prepare", {"action": "lock", "confirmed": True}).status_code == 409
    assert command_calls(restarted.extensions["controller"].client) == []


def test_tampered_journal_reblocks_finished_state(app):
    client, csrf, fake = ready(app)
    client.set_cookie(JOURNAL_COOKIE, "tampered", domain=HOST)
    state = client.get("/api/state", base_url=ORIGIN)
    assert state.json["command"]["state"] == "unknown"
    assert post(client, csrf, "/api/prepare", {"action": "lock", "confirmed": True}).status_code == 409
    assert command_calls(fake) == []


def test_malformed_and_oversized_requests_do_not_submit_controls(app):
    client, csrf, fake = ready(app)
    response = client.post("/api/prepare", base_url=ORIGIN, data="[]", content_type="application/json",
                           headers={"Origin": ORIGIN, "X-CSRF-Token": csrf, "X-Command-Password": PASSWORD})
    assert response.status_code == 400
    response = post(client, csrf, "/api/prepare", {"action": "lock", "confirmed": True, "padding": "x" * 9000})
    assert response.status_code == 413
    response = client.get("/api/command", base_url=ORIGIN)
    assert response.status_code == 405
    assert command_calls(fake) == []


def test_public_status_cold_start_never_authorizes_or_resolves_a_command(app):
    client = app.test_client()
    controller = app.extensions["controller"]
    original_command = dict(controller.command)
    result = client.get("/api/web/public-status", base_url=ORIGIN)
    assert result.status_code == 200
    assert result.json["availability"] == "ready"
    assert result.json["vehicle"] == {"name": "Santa Fe"}
    assert "Set-Cookie" not in result.headers
    assert controller.command == original_command
    assert controller.client.calls == [("connect",), ("status",)]
    assert client.get("/api/web/state", base_url=ORIGIN, headers={"Origin": ORIGIN}).status_code == 401
    assert client.get("/api/state", base_url=ORIGIN).status_code == 401
    assert command_calls(controller.client) == []


def test_public_status_filters_nested_secrets_invalid_types_and_markup(app):
    client, csrf, fake = ready(app)
    controller = app.extensions["controller"]
    controller.vehicle = {"name": "<script>private-account-secret</script>", "vin_last4": "private-vin", "latitude": "private-location"}
    controller.status = {
        "locked": "private-account-secret", "engine_running": True, "climate_on": False,
        "fuel_percent": True, "battery_percent": float("nan"), "odometer": 42,
        "range": {"value": 88, "unit": float("inf"), "vin": "private-vin", "latitude": "private-location"},
        "updated_at": "private-auth-token", "read_at": "2026-10-10T13:44:00+00:00",
        "email": SETTINGS["HYUNDAI_EMAIL"], "password": SETTINGS["HYUNDAI_PASSWORD"], "pin": "9876",
        "coordinates": {"latitude": "private-location"},
    }
    controller.command.update(transaction_id="private-transaction", owner="private-owner")
    controller.read_at = time.monotonic()
    before = dict(controller.command)
    response = client.get("/api/web/public-status", base_url=ORIGIN)
    assert response.status_code == 200
    assert set(response.json) == {"availability", "vehicle", "status"}
    assert response.json["vehicle"] == {"name": "Hyundai"}
    status = response.json["status"]
    assert set(status) == {"locked", "engine_running", "climate_on", "fuel_percent", "battery_percent",
                           "range", "odometer", "updated_at", "read_at", "cached"}
    assert status["locked"] is None and status["engine_running"] is True
    assert status["fuel_percent"] is None and status["battery_percent"] is None
    assert status["updated_at"] is None
    assert status["range"] == {"value": 88, "unit": None}
    text = response.get_data(as_text=True)
    for private in ("private-account-secret", "private-vin", "private-location", "private-auth-token",
                    "private-transaction", "private-owner", "9876", SETTINGS["HYUNDAI_EMAIL"], SETTINGS["HYUNDAI_PASSWORD"]):
        assert private not in text
    assert controller.command == before
    assert command_calls(fake) == []


@pytest.mark.parametrize("execute_first", [False, True])
def test_public_reads_preserve_prepared_or_pending_guard_without_client_calls(app, execute_first):
    client, csrf, fake = ready(app)
    request_id = prepare(client, csrf)
    if execute_first:
        assert post(client, csrf, "/api/command", {"request_id": request_id, "pin": "1234"}).status_code == 200
    controller = app.extensions["controller"]
    controller.read_at -= 10000
    before_command, before_calls = dict(controller.command), list(fake.calls)
    public = app.test_client()
    response = public.get("/api/web/public-status", base_url=ORIGIN,
                          headers={"X-Command-Guard": "tampered", "X-Command-Password": "wrong"})
    assert response.status_code == 200 and response.json["availability"] == "ready"
    assert controller.command == before_command
    assert fake.calls == before_calls
    assert "command" not in response.json
    assert post(client, csrf, "/api/prepare", {"action": "unlock", "confirmed": True}).status_code == 409


def test_concurrent_anonymous_cold_reads_share_one_read_only_connection():
    entered = threading.Event()
    release = threading.Event()
    instances = []

    class SlowFake(FakeHyundai):
        def __init__(self, email, password, pin):
            super().__init__(email, password, pin)
            instances.append(self)

        def connect(self):
            entered.set()
            assert release.wait(2)
            return super().connect()

    app = create_app(SETTINGS, client_factory=SlowFake)
    original = dict(app.extensions["controller"].command)
    with ThreadPoolExecutor(max_workers=2) as pool:
        first = pool.submit(lambda: app.test_client().get("/api/web/public-status", base_url=ORIGIN))
        try:
            assert entered.wait(1)
            second = pool.submit(lambda: app.test_client().get("/api/web/public-status", base_url=ORIGIN))
        finally:
            release.set()
        assert first.result(timeout=3).json["availability"] == "ready"
        assert second.result(timeout=3).json["availability"] == "ready"
    assert len(instances) == 1
    assert instances[0].calls == [("connect",), ("status",)]
    assert app.extensions["controller"].command == original
