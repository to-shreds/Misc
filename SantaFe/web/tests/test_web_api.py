"""Public GitHub frontend protocol tests using a fake Hyundai client only."""
import time

import pytest
from itsdangerous import URLSafeSerializer

from app import create_app

ORIGIN = "https://to-shreds.github.io"
PASSWORD = "sample-password"
SECRET = "synthetic-signing-secret-at-least-thirty-two-characters"


class FakeHyundai:
    instances = []

    def __init__(self, email, password, pin):
        self.commands = []
        self.email, self.password, self.pin = email, password, pin
        self.acknowledgments = 0
        type(self).instances.append(self)

    def connect(self):
        return {"vehicles": [{"id": "0", "label": "Santa Fe", "vin_suffix": "1234", "selected": True}]}

    def cached_status(self):
        return {"door_locked": True, "engine_running": False, "vehicle_timestamp": "20261010120000"}

    def command(self, action, pin=None):
        self.commands.append((action, pin))
        return "private-synthetic-transaction"

    def poll(self, tid):
        return {"state": "success", "message": "Checked."}

    def acknowledge_unknown(self):
        self.acknowledgments += 1


@pytest.fixture
def web():
    FakeHyundai.instances = []
    settings = {"TESTING": True, "SECRET_KEY": SECRET, "WEBSITE_PASSWORD": PASSWORD,
                "REQUIRE_HTTPS": False, "SESSION_COOKIE_SECURE": False,
                "HYUNDAI_EMAIL": "saved@example.invalid", "HYUNDAI_PASSWORD": "private-account-secret",
                "HYUNDAI_PIN": "1234"}
    app = create_app(settings, FakeHyundai)
    return app, app.test_client()


def sign_in(client, password=PASSWORD, origin=ORIGIN):
    return client.post("/api/web/session", json={"password": password}, headers={"Origin": origin})


def post(client, path, token, body=None, *, password=None, guard=None, origin=ORIGIN):
    headers = {"Origin": origin, "X-Web-Session": token}
    if password is not None:
        headers["X-Command-Password"] = password
    if guard is not None:
        headers["X-Command-Guard"] = guard
    return client.post("/api/web/" + path, json=body or {}, headers=headers)


def ready(client):
    response = sign_in(client)
    assert response.status_code == 200
    token = response.json["session_token"]
    assert post(client, "connect", token, {"saved": True}).status_code == 200
    assert post(client, "resolve", token, {"acknowledged": True}, password=PASSWORD).status_code == 200
    return token


def prepare(client, token, action="unlock", **kwargs):
    return post(client, "prepare", token, {"action": action, "confirmed": True}, password=PASSWORD, **kwargs)


def execute(client, token, prepared, *, password=PASSWORD, guard=True):
    return post(client, "command", token, {"request_id": prepared["request_id"]}, password=password,
                guard=prepared["command_guard"] if guard is True else guard)


def test_public_info_minimal_without_account_disclosure(web):
    app, client = web
    response = client.get("/api/web/info")
    assert response.status_code == 200
    assert response.json == {"configured": True, "saved_account_available": True}
    assert "Set-Cookie" not in response.headers
    assert FakeHyundai.instances == []
    incomplete = create_app({"TESTING": True, "SECRET_KEY": SECRET, "WEBSITE_PASSWORD": "", "REQUIRE_HTTPS": False}, FakeHyundai)
    assert incomplete.test_client().get("/api/web/info").json["configured"] is False


def test_web_session_has_exact_contract_and_no_cookie_or_credentials(web):
    app, client = web
    response = sign_in(client)
    assert response.status_code == 200
    assert set(response.json) == {"session_token", "expires_in", "state"}
    assert response.json["expires_in"] == 1800
    marker = URLSafeSerializer(SECRET, salt="santa-fe-web-session-v1").loads(response.json["session_token"])
    assert set(marker) == {"owner", "epoch", "issued_at"}
    assert "Set-Cookie" not in response.headers
    assert response.headers["Access-Control-Allow-Origin"] == ORIGIN
    assert "Access-Control-Allow-Credentials" not in response.headers
    text = response.get_data(as_text=True)
    for private in ["saved@example.invalid", "private-account-secret", "1234", PASSWORD]:
        assert private not in text


def test_session_requires_allowed_origin_and_password(web):
    app, client = web
    assert sign_in(client, "incorrect").status_code == 401
    for origin in ["https://evil.example.invalid", "null", ORIGIN + ".evil.example.invalid", ORIGIN + "/path", ""]:
        response = sign_in(client, origin=origin)
        assert response.status_code == 403
        assert "Access-Control-Allow-Origin" not in response.headers
    assert client.post("/api/web/session", json={"password": PASSWORD}).status_code == 403
    assert sign_in(client, origin="http://localhost").status_code == 200


def test_exact_cors_preflight_only_on_web_routes(web):
    app, client = web
    headers = {"Origin": ORIGIN, "Access-Control-Request-Method": "POST",
               "Access-Control-Request-Headers": "content-type,x-web-session,x-command-password,x-command-guard"}
    response = client.options("/api/web/command", headers=headers)
    assert response.status_code == 204
    assert response.headers["Access-Control-Allow-Origin"] == ORIGIN
    assert response.headers["Access-Control-Allow-Methods"] == "GET, POST, OPTIONS"
    assert "X-Command-Password" in response.headers["Access-Control-Allow-Headers"]
    assert "Access-Control-Allow-Credentials" not in response.headers
    assert "Origin" in response.headers["Vary"]
    assert "Set-Cookie" not in response.headers
    for changes in [{"Origin": "https://evil.example.invalid"}, {"Access-Control-Request-Method": "DELETE"},
                    {"Access-Control-Request-Headers": "X-Whatever"}]:
        assert client.options("/api/web/command", headers={**headers, **changes}).status_code == 403
    assert "Access-Control-Allow-Origin" not in client.options("/api/command", headers=headers).headers


def test_web_state_requires_current_valid_token_origin_and_no_cookie_fallback(web):
    app, client = web
    token = sign_in(client).json["session_token"]
    assert client.get("/api/web/state", headers={"Origin": ORIGIN, "X-Web-Session": token}).status_code == 200
    assert client.get("/api/web/state", headers={"Origin": ORIGIN}).status_code == 401
    assert client.get("/api/web/state", headers={"Origin": ORIGIN, "X-Web-Session": token + "tampered"}).status_code == 401
    assert client.get("/api/web/state", headers={"X-Web-Session": token}).status_code == 403
    assert client.get("/api/web/state", headers={"Origin": "https://evil.example.invalid", "X-Web-Session": token}).status_code == 403
    serializer = URLSafeSerializer(SECRET, salt="santa-fe-web-session-v1")
    decoded = serializer.loads(token)
    for changes in [{"epoch": "stale-epoch"}, {"issued_at": time.time() - 1801}, {"issued_at": time.time() + 60},
                    {"issued_at": True}, {"owner": "short"}]:
        altered = serializer.dumps({**decoded, **changes})
        assert client.get("/api/web/state", headers={"Origin": ORIGIN, "X-Web-Session": altered}).status_code == 401


def test_prepare_requires_fresh_password_not_just_session(web):
    app, client = web
    token = ready(client)
    body = {"action": "unlock", "confirmed": True}
    for password in [None, "wrong"]:
        response = post(client, "prepare", token, body, password=password)
        assert response.status_code == 401
        assert response.json["code"] == "command_password_required"
        assert FakeHyundai.instances[-1].commands == []
    response = prepare(client, token)
    assert response.status_code == 200
    assert set(response.json) == {"request_id", "command_guard"}
    assert "Set-Cookie" not in response.headers
    assert FakeHyundai.instances[-1].commands == []


def test_execute_requires_fresh_password_again_and_second_command_cannot_bypass(web):
    app, client = web
    token = ready(client)
    first = prepare(client, token).json
    for password in [None, "wrong"]:
        assert execute(client, token, first, password=password).status_code == 401
        assert FakeHyundai.instances[-1].commands == []
    assert execute(client, token, first).json["command"]["state"] == "pending"
    assert post(client, "poll", token, guard=first["command_guard"]).json["command"]["state"] == "success"
    second = prepare(client, token, "lock", guard=first["command_guard"]).json
    for password in [None, "wrong"]:
        assert execute(client, token, second, password=password).status_code == 401
        assert FakeHyundai.instances[-1].commands == [("unlock", None)]
    assert execute(client, token, second).status_code == 200
    assert FakeHyundai.instances[-1].commands == [("unlock", None), ("lock", None)]


def test_resolve_requires_fresh_password_and_exact_ack(web):
    app, client = web
    token = sign_in(client).json["session_token"]
    for password in [None, "wrong"]:
        assert post(client, "resolve", token, {"acknowledged": True}, password=password).status_code == 401
    assert post(client, "resolve", token, {"acknowledged": "true"}, password=PASSWORD).status_code == 400
    assert post(client, "resolve", token, {"acknowledged": True}, password=PASSWORD).json["command"]["state"] == "idle"


def test_many_correct_password_checks_do_not_consume_failure_budget(web):
    app, client = web
    token = ready(client)
    for _ in range(16):
        assert sign_in(client).status_code == 200
        assert post(client, "resolve", token, {"acknowledged": True}, password=PASSWORD).status_code == 200
    assert len(app.extensions["controller"].login_attempts) == 0


def test_password_failure_lockout_is_global_and_rejects_correct_commands(web):
    app, client = web
    token = ready(client)
    first = prepare(client, token).json
    for _ in range(8):
        assert execute(client, token, first, password="wrong").status_code == 401
    locked_response = execute(client, token, first)
    assert locked_response.status_code == 429
    assert locked_response.json["code"] == "password_rate_limited"
    assert sign_in(app.test_client()).status_code == 429
    assert FakeHyundai.instances[-1].commands == []
    assert len(app.extensions["controller"].login_attempts) == 8
    app.extensions["controller"].login_attempts = type(app.extensions["controller"].login_attempts)([time.monotonic() - 301] * 8)
    assert execute(client, token, first).status_code == 200


def test_signed_guard_required_and_duplicate_never_resends(web):
    app, client = web
    token = ready(client)
    first = prepare(client, token).json
    for guard in [None, "forged", first["command_guard"] + "tampered"]:
        assert execute(client, token, first, guard=guard).status_code == 409
        assert FakeHyundai.instances[-1].commands == []
    assert execute(client, token, first).status_code == 200
    assert execute(client, token, first).status_code == 200
    assert execute(client, token, first, password=None).status_code == 401
    assert FakeHyundai.instances[-1].commands == [("unlock", None)]
    assert post(client, "poll", token, guard=first["command_guard"]).json["command"]["state"] == "success"
    assert execute(client, token, first).status_code == 200
    assert FakeHyundai.instances[-1].commands == [("unlock", None)]


def test_guard_binds_request_epoch_and_owner(web):
    app, client = web
    token = ready(client)
    first = prepare(client, token).json
    signer = URLSafeSerializer(SECRET, salt="santa-fe-command-guard-v1")
    marker = signer.loads(first["command_guard"])
    for changes in [{"request_id": "different-request"}, {"epoch": "stale-epoch"}, {"owner": "different-owner"}]:
        guard = signer.dumps({**marker, **changes})
        assert execute(client, token, first, guard=guard).status_code == 409
    other_token = sign_in(app.test_client()).json["session_token"]
    assert execute(client, other_token, first).status_code == 409
    assert FakeHyundai.instances[-1].commands == []


def test_prepared_guard_restores_unknown_after_restart_and_new_session(web):
    app, client = web
    token = ready(client)
    first = prepare(client, token).json
    restarted = create_app(dict(app.config), FakeHyundai)
    restarted_client = restarted.test_client()
    assert restarted_client.get("/api/web/state", headers={"Origin": ORIGIN, "X-Web-Session": token}).status_code == 401
    new_token = sign_in(restarted_client).json["session_token"]
    assert post(restarted_client, "connect", new_token, {"saved": True}).status_code == 200
    assert prepare(restarted_client, new_token, guard=first["command_guard"]).status_code == 409
    assert post(restarted_client, "resolve", new_token, {"acknowledged": True}, password=PASSWORD,
                guard=first["command_guard"]).status_code == 200
    assert prepare(restarted_client, new_token).status_code == 200


def test_expired_preparation_cannot_transmit(web):
    app, client = web
    token = ready(client)
    first = prepare(client, token).json
    app.extensions["controller"].command["prepared_at"] -= 121
    response = execute(client, token, first)
    assert response.status_code == 409
    assert response.json["command"]["state"] == "failure"
    assert FakeHyundai.instances[-1].commands == []


def test_public_web_aliases_preserve_business_logic_and_state_privacy(web):
    app, client = web
    token = ready(client)
    assert post(client, "status", token).status_code == 200
    assert post(client, "select", token, {"vehicle_id": "0"}).status_code == 400
    first = prepare(client, token).json
    response = execute(client, token, first)
    text = response.get_data(as_text=True)
    for private in ["private-synthetic-transaction", "saved@example.invalid", "private-account-secret", PASSWORD]:
        assert private not in text
    assert "owner" not in response.json["command"]
    assert post(client, "disconnect", token, guard=first["command_guard"]).status_code == 409
    assert post(client, "poll", token, guard=first["command_guard"]).status_code == 200
    assert post(client, "disconnect", token, guard=first["command_guard"]).status_code == 200
