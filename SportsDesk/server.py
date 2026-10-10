"""Sports Desk: bounded public HTML discovery. Never proxy player or media bytes."""
from __future__ import annotations

import http.client
import hashlib
import ipaddress
import json
import os
import re
import socket
import ssl
import threading
import time
from collections import OrderedDict, defaultdict, deque
from datetime import datetime, timezone
from html.parser import HTMLParser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urljoin, urlsplit, urlunsplit

VERSION = "0.1.0"
MAX_HTML = 1_000_000
TIMEOUT = 12
SPORTS = {"nfl": "NFL", "cfb": "College football", "nhl": "Hockey", "nba": "Basketball", "mlb": "Baseball", "soccer": "Soccer", "football": "Soccer", "mma": "MMA", "ufc": "MMA", "boxing": "Boxing", "wwe": "Wrestling", "cfl": "CFL", "f1": "Formula 1", "tennis": "Tennis", "rugby": "Rugby", "cricket": "Cricket"}
EVENT = re.compile(r"^/([a-z0-9-]+)/[^/]+/([0-9]+)/?$")
NAMED_EVENT = re.compile(r"^/title-game/([a-z0-9-]+)/([a-z0-9-]+)/?$")


def sport_name(slug: str):
    return SPORTS.get(slug) or next((name for key, name in SPORTS.items() if slug.startswith(key + "-")), None)


def event_info(path: str):
    numbered = EVENT.match(path)
    named = NAMED_EVENT.match(path)
    match = numbered or named
    if not match or not sport_name(match[1]):
        return None
    identity = match[2] if numbered else hashlib.sha256(path.encode()).hexdigest()[:16]
    label = "" if numbered else re.sub(r"-live-streams?-links?$", "", match[2]).replace("-", " ").title()
    label = re.sub(r"\b(Ufc|Wwe|Nfl|Mma)\b", lambda m: m[0].upper(), label)
    return {"id": match[1] + ":" + identity, "sport": sport_name(match[1]), "fallback": label}


class DiscoveryError(Exception):
    pass


def clean_url(value: str, *, add_scheme: bool = False) -> str:
    if not isinstance(value, str) or len(value) > 2048:
        raise DiscoveryError("Enter a valid HTTPS website address.")
    value = value.strip()
    if add_scheme and "://" not in value:
        value = "https://" + value
    if re.search(r"[\x00-\x20\\]", value):
        raise DiscoveryError("The website address contains invalid characters.")
    try:
        p = urlsplit(value)
        host = (p.hostname or "").encode("idna").decode("ascii").lower().rstrip(".")
        if p.scheme != "https" or p.username is not None or p.password is not None or p.port not in (None, 443):
            raise ValueError()
        if not host or "." not in host or host.endswith((".local", ".localhost", ".internal", ".test")):
            raise ValueError()
        try:
            address = ipaddress.ip_address(host)
            if not address.is_global:
                raise ValueError()
        except ValueError:
            if re.fullmatch(r"[0-9.:]+", host) or ":" in host:
                raise ValueError()
        if not re.fullmatch(r"[a-z0-9.-]+", host) or any(not part or len(part) > 63 for part in host.split(".")):
            raise ValueError()
        return urlunsplit(("https", host, p.path or "/", p.query, ""))
    except (ValueError, UnicodeError):
        raise DiscoveryError("Use a public HTTPS website without a login or custom port.") from None


def public_addresses(host: str) -> list[str]:
    addresses = list(dict.fromkeys(item[4][0] for item in socket.getaddrinfo(host, 443, type=socket.SOCK_STREAM)))
    if not addresses or any(not ipaddress.ip_address(ip).is_global for ip in addresses):
        raise DiscoveryError("That address does not resolve exclusively to public servers.")
    return addresses


def fetch_html(value: str) -> tuple[str, str]:
    """Validate and pin DNS for every HTTPS request and redirect. Ignore ambient proxies."""
    url = clean_url(value)
    deadline = time.monotonic() + 20
    for _ in range(5):
        p = urlsplit(url)
        addresses = public_addresses(p.hostname)
        remaining = min(TIMEOUT, deadline - time.monotonic())
        if remaining <= 0:
            raise DiscoveryError("The source took too long to respond. Try again.")
        connection = http.client.HTTPSConnection(p.hostname, 443, timeout=remaining, context=ssl.create_default_context())
        # TLS still verifies the original hostname. Connections use only the validated IP.
        connection._create_connection = lambda address, timeout, source_address=None: socket.create_connection((addresses[0], 443), timeout, source_address)
        try:
            path = urlunsplit(("", "", p.path or "/", p.query, ""))
            connection.request("GET", path, headers={"User-Agent": "SportsDesk/0.1 (+public event metadata)", "Accept": "text/html", "Accept-Encoding": "identity"})
            response = connection.getresponse()
            if response.status in (301, 302, 303, 307, 308):
                location = response.getheader("Location")
                if not location:
                    raise DiscoveryError("The source returned an incomplete redirect.")
                url = clean_url(urljoin(url, location))
                continue
            if response.status != 200:
                raise DiscoveryError(f"The source returned HTTP {response.status}. The saved list has not changed.")
            content_type = response.getheader("Content-Type", "").split(";", 1)[0].lower().strip()
            if content_type not in ("text/html", "application/xhtml+xml"):
                raise DiscoveryError("The source is not an HTML page. Video and playlists are not accepted.")
            if response.getheader("Content-Encoding", "identity") not in ("identity", ""):
                raise DiscoveryError("The source returned an unsupported compressed page.")
            length = response.getheader("Content-Length")
            if length and int(length) > MAX_HTML:
                raise DiscoveryError("The source page is too large.")
            data = bytearray()
            while len(data) <= MAX_HTML:
                if time.monotonic() > deadline:
                    raise DiscoveryError("The source took too long to respond. Try again.")
                chunk = response.read1(min(32768, MAX_HTML + 1 - len(data)))
                if not chunk:
                    break
                data.extend(chunk)
            if len(data) > MAX_HTML:
                raise DiscoveryError("The source page is too large.")
            return url, data.decode("utf-8", "replace")
        finally:
            connection.close()
    raise DiscoveryError("The source redirects too many times.")


class PageParser(HTMLParser):
    def __init__(self, base: str):
        super().__init__(convert_charrefs=True)
        self.base = base
        self.stack = []
        self.active = None
        self.entries = []
        self.players = []

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if tag == "a":
            if self.active:
                self.finish()
            try:
                url = clean_url(urljoin(self.base, a.get("href", "")))
                info = event_info(urlsplit(url).path)
                if info and urlsplit(url).netloc == urlsplit(self.base).netloc:
                    self.active = {"id": info["id"], "sport": info["sport"], "url": url, "title": a.get("title", "") or info["fallback"], "names": [], "time": [], "live": False}
            except DiscoveryError:
                pass
        classes = set(a.get("class", "").split())
        if tag not in {"area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr"}:
            self.stack.append((tag, classes))
        if tag == "iframe" and a.get("id") in ("cx-iframe", "stream-iframe-embed"):
            try:
                self.players.append(clean_url(urljoin(self.base, a.get("src", ""))))
            except DiscoveryError:
                pass

    def handle_endtag(self, tag):
        if tag == "a" and self.active:
            self.finish()
        for index in range(len(self.stack) - 1, -1, -1):
            if self.stack[index][0] == tag:
                del self.stack[index:]
                break

    def handle_data(self, text):
        if not self.active:
            return
        text = " ".join(text.split())
        if not text or any(t in ("script", "style") for t, _ in self.stack):
            return
        if any("name" in classes for _, classes in self.stack):
            self.active["names"].append(text)
        if any(t == "time" for t, _ in self.stack):
            self.active["time"].append(text)
        if text.lower() == "in progress":
            self.active["live"] = True

    def finish(self):
        entry = self.active
        self.active = None
        # No scores, outcome text, descriptions, logos, or page scripts cross this boundary.
        title = " vs ".join(entry["names"][:2])
        if not title:
            title = re.sub(r"^Buffstreams\s+", "", entry["title"], flags=re.I)
            title = re.sub(r"\s+Live Streams?$", "", title, flags=re.I)
        title = " ".join(title.split())[:180]
        if title:
            self.entries.append({"id": entry["id"], "title": title, "sport": entry["sport"], "eventUrl": entry["url"], "live": entry["live"], "sourceTime": " ".join(entry["time"])[:50]})


def parse_catalog(html: str, url: str) -> dict:
    parser = PageParser(url)
    parser.feed(html)
    unique = {}
    for entry in parser.entries:
        unique.setdefault(entry["id"], entry)
    events = list(unique.values())[:300]
    if not events:
        raise DiscoveryError("No recognizable events were found. Check the address; a changed site layout may need an adapter update.")
    return {"source": url, "fetchedAt": datetime.now(timezone.utc).isoformat(), "events": events, "adapter": "buffstreams-v1"}


def catalog(source: str, fetch=fetch_html) -> dict:
    url, html = fetch(clean_url(source, add_scheme=True))
    return parse_catalog(html, url)


def player(source: str, event: str, fetch=fetch_html) -> dict:
    source = clean_url(source, add_scheme=True)
    event = clean_url(event)
    if urlsplit(source).netloc != urlsplit(event).netloc or not event_info(urlsplit(event).path):
        raise DiscoveryError("The event must belong to the selected source website.")
    final, html = fetch(event)
    parser = PageParser(final)
    parser.feed(html)
    if not parser.players:
        raise DiscoveryError("This event does not currently expose a recognized player. Try another event or refresh the list.")
    return {"playerUrl": parser.players[0], "eventUrl": event, "playback": "source-page"}


class Discovery:
    def __init__(self, fetch=fetch_html):
        self.fetch = fetch
        self.cache = OrderedDict()
        self.lock = threading.Lock()
        self.jobs = threading.BoundedSemaphore(4)
        self.calls = defaultdict(deque)

    def run(self, action, body, identity):
        now = time.monotonic()
        with self.lock:
            if len(self.calls) > 1000:
                self.calls = defaultdict(deque, {k: v for k, v in self.calls.items() if v and v[-1] > now - 60})
            times = self.calls[identity]
            while times and times[0] < now - 60:
                times.popleft()
            if len(times) >= 30:
                raise DiscoveryError("Too many requests. Wait a minute and try again.")
            times.append(now)
        source = clean_url(body.get("source", ""), add_scheme=True)
        event = body.get("eventUrl", "") if action == "player" else ""
        key = (action, source, event)
        with self.lock:
            cached = self.cache.get(key)
            if cached and now - cached[0] < (45 if action == "player" else 90):
                self.cache.move_to_end(key)
                return cached[1]
        if not self.jobs.acquire(blocking=False):
            raise DiscoveryError("The helper is busy. Try again shortly.")
        try:
            result = player(source, event, self.fetch) if action == "player" else catalog(source, self.fetch)
            with self.lock:
                self.cache[key] = (time.monotonic(), result)
                while len(self.cache) > 128:
                    self.cache.popitem(last=False)
            return result
        finally:
            self.jobs.release()


def handler_for(discovery):
    class Handler(BaseHTTPRequestHandler):
        def reply(self, status, value):
            data = json.dumps(value, ensure_ascii=False).encode()
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(data)))
            self.send_header("Access-Control-Allow-Origin", "*")
            self.send_header("Access-Control-Allow-Methods", "POST, GET, OPTIONS")
            self.send_header("Access-Control-Allow-Headers", "Content-Type")
            self.send_header("X-Content-Type-Options", "nosniff")
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(data)

        def do_OPTIONS(self):
            self.reply(200, {})

        def do_GET(self):
            self.reply(200, {"ok": True, "version": VERSION, "mode": "html-metadata-only", "mediaProxy": False}) if self.path == "/healthz" else self.reply(404, {"error": "Not found."})

        def do_POST(self):
            if self.path not in ("/api/catalog", "/api/player"):
                return self.reply(404, {"error": "Not found."})
            try:
                size = int(self.headers.get("Content-Length", "0"))
                if not 0 < size <= 8192:
                    return self.reply(413, {"error": "Request must be a small JSON object."})
                self.connection.settimeout(10)
                body = json.loads(self.rfile.read(size))
                if not isinstance(body, dict):
                    raise ValueError()
                result = discovery.run(self.path.rsplit("/", 1)[1], body, self.client_address[0])
                self.reply(200, result)
            except DiscoveryError as error:
                self.reply(422, {"error": str(error)})
            except (ValueError, TypeError, json.JSONDecodeError):
                self.reply(400, {"error": "Invalid request."})
            except (OSError, http.client.HTTPException):
                self.reply(502, {"error": "The source could not be reached. Your saved list is unchanged."})

        def log_message(self, *_):
            pass
    return Handler


if __name__ == "__main__":
    server = ThreadingHTTPServer(("0.0.0.0", int(os.environ.get("PORT", "10000"))), handler_for(Discovery()))
    server.daemon_threads = True
    print(f"Sports Desk metadata helper {VERSION} listening on {server.server_port}", flush=True)
    server.serve_forever()
