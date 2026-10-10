import re
import threading

import pytest

from app import create_app, JOURNAL_COOKIE
from hyundai import HyundaiError


class FakeHyundai:
    instances = []
    next_error = None
    result = "success"

    def __init__(self, email, password, pin):
        self.commands = []
        self.reads = 0
        self.email = email
        self.password = password
        self.pin = pin
        type(self).instances.append(self)

    def connect(self):
        return {"vehicles": [{"id": "0", "label": "Santa Fe", "vin_suffix": "1234", "selected": True}]}

    def cached_status(self):
        self.reads += 1
        return {"door_locked": True, "engine_running": False, "vehicle_timestamp": "20261010120000"}

    def command(self, action, pin=None):
        self.commands.append((action, pin))
        if type(self).next_error:
            raise type(self).next_error
        return "synthetic-transaction"

    def poll(self, tid):
        return {"state": type(self).result, "message": "Checked."}

    def acknowledge_unknown(self):
        pass


@pytest.fixture
def web():
    FakeHyundai.instances = []
    FakeHyundai.next_error = None
    FakeHyundai.result = "success"
    app = create_app({"TESTING": True, "SECRET_KEY": "test-signing-secret-longer-than-thirty-two",
                      "WEBSITE_PASSWORD": "long-test-passphrase", "REQUIRE_HTTPS": False,
                      "SESSION_COOKIE_SECURE": False}, FakeHyundai)
    return app, app.test_client()


def login(client, password="long-test-passphrase"):
    html = client.get("/").get_data(as_text=True)
    csrf = re.search(r'name="csrf" value="([^"]+)"', html).group(1)
    return client.post("/login", data={"password": password, "csrf": csrf})


def csrf(client):
    with client.session_transaction() as sess:
        return sess["csrf"]


def post(client, path, data=None, **kwargs):
    return client.post(path, json=data or {}, headers={"X-CSRF-Token": csrf(client)}, **kwargs)


def connect(client):
    return post(client, "/api/connect", {"email": "test@example.test", "password": "synthetic-only", "pin": "1234"})


def ready(client):
    assert login(client).status_code == 303
    assert connect(client).status_code == 200
    assert post(client, "/api/resolve", {"acknowledged": True}).status_code == 200


def prepare(client, action="unlock", **extra):
    return post(client, "/api/prepare", {"action": action, "confirmed": True, **extra})


def test_gate_blocks_control_markup_assets_and_every_api(web):
    app, client = web
    text = client.get("/").get_data(as_text=True)
    assert "app.js" not in text
    assert "api/command" not in text
    for path in ["/static/app.js", "/static/style.css", "/api/state"]:
        assert client.get(path).status_code == 401
    for path in ["/api/connect", "/api/status", "/api/prepare", "/api/command", "/api/poll", "/api/resolve", "/api/disconnect", "/api/select"]:
        assert client.post(path, json={}).status_code == 401
    assert FakeHyundai.instances == []


def test_setup_missing_or_short_password_fails_closed():
    app = create_app({"TESTING": True, "SECRET_KEY": "x" * 32, "WEBSITE_PASSWORD": "1234", "REQUIRE_HTTPS": False}, FakeHyundai)
    client = app.test_client()
    assert client.get("/").status_code == 503
    assert client.get("/static/app.js").status_code == 503
    assert client.get("/healthz").json == {"status": "ok", "configured": False}


def test_login_needs_csrf_and_rate_limits_global(web):
    app, client = web
    assert client.post("/login", data={"password": "long-test-passphrase"}).status_code == 403
    for _ in range(8):
        assert login(client, "wrong").status_code == 401
    assert login(client).status_code == 429
    fresh = app.test_client()
    assert login(fresh).status_code == 429


def test_signed_cookie_contains_no_hyundai_credentials(web):
    app, client = web
    ready(client)
    with client.session_transaction() as sess:
        assert not any(k in sess for k in ["email", "password", "pin", "token", "access_token"])
    text = client.get("/api/state").get_data(as_text=True)
    assert "synthetic-only" not in text
    assert "test@example.test" not in text


def test_connect_and_cached_status_are_read_only(web):
    app, client = web
    login(client)
    assert connect(client).status_code == 200
    assert post(client, "/api/status").status_code == 200
    assert FakeHyundai.instances[-1].commands == []
    assert client.get("/api/state").json["command"]["state"] == "unknown"
    assert prepare(client).status_code == 409


def test_mutations_require_csrf_and_same_origin(web):
    app, client = web
    ready(client)
    assert client.post("/api/prepare", json={"action": "unlock", "confirmed": True}).status_code == 403
    for origin in ["https://evil.test", "null"]:
        assert client.post("/api/prepare", json={"action": "unlock", "confirmed": True},
                           headers={"X-CSRF-Token": csrf(client), "Origin": origin}).status_code == 403
    assert not FakeHyundai.instances[-1].commands


def test_prepare_cookie_precedes_one_control_submission_and_replay_is_safe(web):
    app, client = web
    ready(client)
    response = prepare(client)
    assert response.status_code == 200
    assert JOURNAL_COOKIE in response.headers["Set-Cookie"]
    assert "HttpOnly" in response.headers["Set-Cookie"]
    assert FakeHyundai.instances[-1].commands == []
    request_id = response.json["request_id"]
    assert post(client, "/api/command", {"request_id": request_id}).json["command"]["state"] == "pending"
    assert post(client, "/api/command", {"request_id": request_id}).status_code == 200
    assert FakeHyundai.instances[-1].commands == [("unlock", None)]
    assert prepare(client, "lock").status_code == 409
    assert post(client, "/api/poll").json["command"]["state"] == "success"
    assert post(client, "/api/command", {"request_id": request_id}).status_code == 200
    assert len(FakeHyundai.instances[-1].commands) == 1


def test_execute_requires_prepared_cookie_and_owner(web):
    app, client = web
    ready(client)
    request_id = prepare(client).json["request_id"]
    other = app.test_client()
    login(other)
    assert post(other, "/api/command", {"request_id": request_id}).status_code == 409
    client.delete_cookie(JOURNAL_COOKIE)
    assert post(client, "/api/command", {"request_id": request_id}).status_code == 409
    assert FakeHyundai.instances[-1].commands == []


def test_start_requires_outdoors_and_exact_boolean_ack(web):
    app, client = web
    ready(client)
    assert prepare(client, "start_regular").status_code == 400
    assert prepare(client, "start_regular", outdoors="true").status_code == 400
    assert prepare(client, "start_regular", outdoors=True).status_code == 200


def test_unknown_command_blocks_reconnect_replay_and_second_browser_until_ack(web):
    app, client = web
    ready(client)
    FakeHyundai.next_error = HyundaiError("Synthetic lost response.", uncertain=True)
    request_id = prepare(client).json["request_id"]
    response = post(client, "/api/command", {"request_id": request_id})
    assert response.json["command"]["state"] == "unknown"
    other = app.test_client()
    login(other)
    assert prepare(other, "lock").status_code == 409
    assert post(client, "/api/command", {"request_id": request_id}).status_code == 200
    assert len(FakeHyundai.instances[-1].commands) == 1
    assert post(other, "/api/resolve", {"acknowledged": True}).status_code == 200
    assert other.get("/api/state").json["command"]["state"] == "idle"


def test_process_restart_invalidates_old_login_and_requires_ack(web):
    app, client = web
    ready(client)
    prepare(client)
    cookie = client.get_cookie("sf_access")
    guard = client.get_cookie(JOURNAL_COOKIE)
    restarted = create_app(dict(app.config), FakeHyundai).test_client()
    restarted.set_cookie("sf_access", cookie.value)
    restarted.set_cookie(JOURNAL_COOKIE, guard.value)
    assert restarted.get("/api/state").status_code == 401
    login(restarted)
    assert restarted.get("/api/state").json["command"]["state"] == "unknown"
    assert connect(restarted).status_code == 200
    assert prepare(restarted).status_code == 409


def test_logout_keeps_unresolved_guard(web):
    app, client = web
    ready(client)
    prepare(client)
    token = csrf(client)
    assert client.post("/logout", data={"csrf": token}).status_code == 303
    assert client.get_cookie(JOURNAL_COOKIE) is not None
    login(client)
    assert prepare(client).status_code == 409


def test_parallel_preparation_accepts_only_one_owner_request(web):
    app, client = web
    ready(client)
    other = app.test_client()
    login(other)
    results = []
    threads = [threading.Thread(target=lambda c=c: results.append(prepare(c).status_code)) for c in [client, other]]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()
    assert sorted(results) == [200, 409]


def test_cache_headers_security_and_json_bounds(web):
    app, client = web
    ready(client)
    response = client.get("/")
    assert response.headers["Cache-Control"] == "no-store, private"
    assert response.headers["X-Frame-Options"] == "DENY"
    assert "frame-ancestors 'none'" in response.headers["Content-Security-Policy"]
    assert client.post("/api/status", data="x", headers={"X-CSRF-Token": csrf(client)}).status_code == 415
    assert post(client, "/api/prepare", {"action": [], "confirmed": True}).status_code == 400
    assert post(client, "/api/status", {"too_big": "x" * 9000}).status_code == 413
