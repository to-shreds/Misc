"""Credential-free HTTP fixture. This file is never imported by the service."""
import sys

from app import create_app
from test_app import FakeHyundai
from werkzeug.serving import make_server, WSGIRequestHandler


class QuietHandler(WSGIRequestHandler):
    def log_request(self, *args, **kwargs):
        pass


app = create_app({"TESTING": True, "SECRET_KEY": "offline-browser-fixture-signing-secret-123456",
                  "WEBSITE_PASSWORD": "offline-browser-password", "REQUIRE_HTTPS": False,
                  "SESSION_COOKIE_SECURE": False}, FakeHyundai)
with make_server("127.0.0.1", int(sys.argv[1]), app, threaded=True, request_handler=QuietHandler) as server:
    server.serve_forever()
