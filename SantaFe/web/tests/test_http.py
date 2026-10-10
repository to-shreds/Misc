"""Exercise actual HTTP, cookie delivery and protected assets without a car."""
from contextlib import contextmanager
import http.cookiejar
import json
import re
import threading
import urllib.error
import urllib.request

from werkzeug.serving import make_server, WSGIRequestHandler

from app import create_app
from test_app import FakeHyundai


class QuietHandler(WSGIRequestHandler):
    def log_request(self, *args, **kwargs):
        pass


@contextmanager
def running_app():
    app = create_app({"TESTING": True, "SECRET_KEY": "synthetic-http-signing-secret-32-characters",
                      "WEBSITE_PASSWORD": "synthetic-http-password", "REQUIRE_HTTPS": False,
                      "SESSION_COOKIE_SECURE": False}, FakeHyundai)
    server = make_server("127.0.0.1", 0, app, threaded=True, request_handler=QuietHandler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        yield app, "http://127.0.0.1:" + str(server.server_port)
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=2)


def test_real_http_gate_cookies_assets_and_one_complete_fake_command():
    FakeHyundai.next_error = None
    FakeHyundai.result = "success"
    with running_app() as (app, base):
        jar = http.cookiejar.CookieJar()
        opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
        assert json.load(opener.open(base + "/healthz"))["status"] == "ok"
        html = opener.open(base).read().decode()
        assert "/static/app.js" not in html
        try:
            opener.open(base + "/static/app.js")
            raise AssertionError("Control assets were exposed")
        except urllib.error.HTTPError as error:
            assert error.code == 401
        csrf = re.search(r'name="csrf" value="([^"]+)"', html).group(1)
        login = urllib.request.Request(base + "/login", data=urllib.parse.urlencode({"csrf": csrf, "password": "synthetic-http-password"}).encode())
        protected_html = opener.open(login).read().decode()
        assert "/static/app.js" in protected_html
        session_cookie = next(cookie for cookie in jar if cookie.name == app.config["SESSION_COOKIE_NAME"])
        csrf = app.session_interface.get_signing_serializer(app).loads(session_cookie.value)["csrf"]
        assert opener.open(base + "/static/app.js").status == 200

        def post(path, body, command_password=True):
            headers = {"Content-Type": "application/json", "X-CSRF-Token": csrf, "Origin": base}
            if command_password and path in {"/api/prepare", "/api/command", "/api/resolve"}:
                headers["X-Command-Password"] = "synthetic-http-password"
            request = urllib.request.Request(base + path, data=json.dumps(body).encode(),
                                             headers=headers)
            return json.load(opener.open(request))

        connected = post("/api/connect", {"email": "synthetic@example.test", "password": "synthetic-upstream", "pin": "1234"})
        assert connected["connected"] and connected["command"]["state"] == "unknown"
        post("/api/resolve", {"acknowledged": True})
        prepared = post("/api/prepare", {"action": "lock", "confirmed": True})
        assert any(cookie.name == "sf_command_guard" for cookie in jar)
        assert not FakeHyundai.instances[-1].commands
        try:
            post("/api/command", {"request_id": prepared["request_id"]}, command_password=False)
            raise AssertionError("A login cookie replaced the fresh command password")
        except urllib.error.HTTPError as error:
            assert error.code == 401
        assert not FakeHyundai.instances[-1].commands
        submitted = post("/api/command", {"request_id": prepared["request_id"]})
        assert submitted["command"]["state"] == "pending"
        completed = post("/api/poll", {})
        assert completed["command"]["state"] == "success"
        assert FakeHyundai.instances[-1].commands == [("lock", None)]


def test_actual_http_rejects_cross_origin_control_without_upstream_call():
    with running_app() as (app, base):
        request = urllib.request.Request(base + "/api/command", data=b"{}", headers={"Content-Type": "application/json", "Origin": "https://untrusted.example"})
        try:
            urllib.request.urlopen(request)
            raise AssertionError("An unauthenticated command was accepted")
        except urllib.error.HTTPError as error:
            assert error.code == 401
