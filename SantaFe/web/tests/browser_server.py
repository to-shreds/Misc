"""Credential-free HTTP fixture. This file is never imported by the service."""
import sys
from pathlib import Path

from flask import Flask
from app import create_app
from test_app import FakeHyundai
from werkzeug.middleware.dispatcher import DispatcherMiddleware
from werkzeug.serving import make_server, WSGIRequestHandler


class QuietHandler(WSGIRequestHandler):
    def log_request(self, *args, **kwargs):
        pass


app = create_app({"TESTING": True, "SECRET_KEY": "offline-browser-fixture-signing-secret-123456",
                  "WEBSITE_PASSWORD": "offline-browser-password", "REQUIRE_HTTPS": False,
                  "SESSION_COOKIE_SECURE": False}, FakeHyundai)
port = int(sys.argv[1])
source_root = Path(__file__).resolve().parent.parent
frontend = Flask("offline_fixture_frontend", static_folder=str(source_root / "static"), static_url_path="/static")


@frontend.get("/")
def public_frontend():
    # The test-only page points at the local FakeHyundai fixture. It must never
    # contact the baked-in production Render origin with synthetic credentials.
    page = (source_root / "index.html").read_text()
    return page.replace('content="https://santa-fe-emergency.onrender.com"', f'content="http://127.0.0.1:{port}"')


fixture_app = DispatcherMiddleware(app, {"/frontend": frontend})
with make_server("127.0.0.1", port, fixture_app, threaded=True, request_handler=QuietHandler) as server:
    server.serve_forever()
