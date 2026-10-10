import json
import sys
import threading
import unittest
from pathlib import Path
from unittest.mock import patch
from urllib.request import Request, build_opener, ProxyHandler
from urllib.error import HTTPError

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import server as app

HOME = '''<html><a class="competition" href="/cfb/team-b-team-a/100" title="Buffstreams Team A vs Team B"><span class="name">Team A</span><span class="competition-cell-score">42 17<span>In Progress</span></span><span class="name">Team B</span></a><a href="/nfl/team-d-team-c/200" title="Buffstreams Team C vs Team D"><span class="name">Team C</span><time datetime="2026-10-11 17:00:00">05:00 PM</time><span class="name">Team D</span></a><a href="/cfb/team-b-team-a/100" title="Buffstreams Team A vs Team B">Duplicate</a><script>injectSecrets()</script></html>'''
EVENT = '<html><iframe id="cx-iframe" src="https://player.example.com/new-stream-embed/123"></iframe></html>'


class CoreTests(unittest.TestCase):
    def test_domain_change_resolves_relative_events(self):
        result = app.parse_catalog(HOME, "https://new-source.example.com/home99")
        self.assertEqual(len(result["events"]), 2)
        self.assertTrue(all(x["eventUrl"].startswith("https://new-source.example.com/") for x in result["events"]))

    def test_scores_and_scripts_do_not_cross_catalog_boundary(self):
        events = app.parse_catalog(HOME, "https://source.example.com/")["events"]
        data = json.dumps(events)
        self.assertNotIn("42", data)
        self.assertNotIn("17", data)
        self.assertNotIn("injectSecrets", data)
        self.assertNotIn("score", data)
        self.assertTrue(events[0]["live"])
        self.assertEqual(app.parse_catalog(HOME, "https://source.example.com/")["events"][1]["sourceTime"], "05:00 PM")

    def test_blank_or_changed_layout_fails(self):
        for html in ["", "<html>Page moved</html>", '<a href="https://evil.example.com/nfl/a-b/2" title="Buffstreams A vs B"></a>']:
            with self.assertRaises(app.DiscoveryError):
                app.parse_catalog(html, "https://source.example.com/")

    def test_dangerous_urls_rejected(self):
        for url in ["http://example.com", "file:///etc/passwd", "https://localhost/", "https://127.0.0.1/", "https://169.254.169.254/", "https://[::1]/", "https://10.0.0.1/", "https://user:pass@example.com/", "https://example.com:8443/", "https://example.com\\@127.0.0.1", "https://example.com/\nfoo"]:
            with self.subTest(url=url), self.assertRaises(app.DiscoveryError):
                app.clean_url(url)

    def test_public_domain_normalized(self):
        self.assertEqual(app.clean_url("Example.COM/path#test", add_scheme=True), "https://example.com/path")

    def test_preseason_and_playoff_categories(self):
        html='<a href="/nba-preseason/team-b-team-a/101" title="Buffstreams Team A vs Team B"></a><a href="/mlb-playoffs/team-c-team-d/102" title="Buffstreams Team C vs Team D"></a>'
        result=app.parse_catalog(html,"https://source.example.com/")
        self.assertEqual([e["sport"] for e in result["events"]],["Basketball","Baseball"])

    def test_named_fight_event(self):
        url="https://source.example.com/title-game/ufc/fight-night-team-a-vs-team-b-live-streams-links"
        result=app.parse_catalog(f'<a class="competition" href="{url}">Ignored outcome text</a>',"https://source.example.com/")
        self.assertEqual(result["events"][0]["sport"],"MMA")
        self.assertEqual(result["events"][0]["title"],"Fight Night Team A Vs Team B")
        self.assertEqual(app.player("https://source.example.com/",url,lambda value:(value,EVENT))["playback"],"source-page")

    def test_mixed_private_dns_rejected(self):
        responses=[(2,1,6,"",("93.184.216.34",443)),(2,1,6,"",("127.0.0.1",443))]
        with patch.object(app.socket, "getaddrinfo", return_value=responses):
            with self.assertRaises(app.DiscoveryError):
                app.public_addresses("example.com")

    def test_player_never_fetches_player_or_media(self):
        calls=[]
        def fetch(url):
            calls.append(url)
            return url, EVENT
        result=app.player("https://source.example.com/", "https://source.example.com/cfb/a-b/100", fetch)
        self.assertEqual(calls, ["https://source.example.com/cfb/a-b/100"])
        self.assertEqual(result["playerUrl"], "https://player.example.com/new-stream-embed/123")

    def test_foreign_event_and_non_event_rejected_before_network(self):
        for event in ["https://other.example.com/cfb/a-b/100", "https://source.example.com/video.m3u8"]:
            with self.assertRaises(app.DiscoveryError):
                app.player("https://source.example.com/", event, lambda _: self.fail("Unexpected network request"))

    def test_non_player_iframe_not_adopted(self):
        with self.assertRaises(app.DiscoveryError):
            app.player("https://source.example.com/", "https://source.example.com/nfl/a-b/100", lambda url:(url,'<iframe src="https://ads.example.com/"></iframe>'))

    def test_failure_not_cached(self):
        calls=[]
        def fetch(url):
            calls.append(url)
            if len(calls)==1:
                raise app.DiscoveryError("Temporary outage")
            return url, HOME
        discovery=app.Discovery(fetch)
        body={"source":"https://source.example.com/"}
        with self.assertRaises(app.DiscoveryError):
            discovery.run("catalog",body,"test")
        discovery.run("catalog",body,"test")
        discovery.run("catalog",body,"test")
        self.assertEqual(len(calls),2)

    def test_network_response_type_and_redirect_guard(self):
        class Response:
            status=200
            def getheader(self,key,default=None): return {"Content-Type":"video/mp2t"}.get(key,default)
            def read1(self,*_): raise AssertionError("Video bytes were read")
        class Connection:
            def __init__(self,*args,**kwargs): pass
            def request(self,*args,**kwargs): pass
            def getresponse(self): return Response()
            def close(self): pass
        with patch.object(app,"public_addresses",return_value=["93.184.216.34"]),patch.object(app.http.client,"HTTPSConnection",Connection):
            with self.assertRaises(app.DiscoveryError): app.fetch_html("https://example.com/video")
            Response.status=302
            Response.getheader=lambda self,key,default=None:{"Location":"https://127.0.0.1/"}.get(key,default)
            with self.assertRaises(app.DiscoveryError): app.fetch_html("https://example.com/page")


class HTTPTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.calls=[]
        def fetch(url):
            cls.calls.append(url)
            return url, EVENT if "/cfb/" in url else HOME
        cls.server=app.ThreadingHTTPServer(("127.0.0.1",0),app.handler_for(app.Discovery(fetch)))
        cls.thread=threading.Thread(target=cls.server.serve_forever,daemon=True);cls.thread.start()
        cls.root=f"http://127.0.0.1:{cls.server.server_port}"
        cls.client=build_opener(ProxyHandler({}))

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown();cls.server.server_close();cls.thread.join()

    def test_catalog_then_resolve_via_api(self):
        req=Request(self.root+"/api/catalog",data=json.dumps({"source":"https://source.example.com"}).encode(),headers={"Content-Type":"application/json"})
        with self.client.open(req) as response:
            data=json.load(response);self.assertEqual(response.headers["Access-Control-Allow-Origin"],"*")
        event=data["events"][0]
        req=Request(self.root+"/api/player",data=json.dumps({"source":data["source"],"eventUrl":event["eventUrl"]}).encode(),headers={"Content-Type":"application/json"})
        with self.client.open(req) as response:
            self.assertEqual(json.load(response)["playback"],"source-page")
        self.assertFalse(any("player.example" in url for url in self.calls))

    def test_arbitrary_proxy_route_absent(self):
        with self.assertRaises(HTTPError) as error:
            self.client.open(self.root+"/proxy?url=https://example.com/video.ts")
        self.assertEqual(error.exception.code,404)

    def test_health_has_no_media_relay(self):
        with self.client.open(self.root+"/healthz") as response:
            self.assertFalse(json.load(response)["mediaProxy"])


if __name__ == "__main__":
    unittest.main()
