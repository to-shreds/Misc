#!/usr/bin/env python3
"""Verify the public Pages page matches this compiled release byte for byte."""
import hashlib, json, pathlib, re, time, urllib.request
URL='https://to-shreds.github.io/Misc/'
EXPECTED=(pathlib.Path(__file__).resolve().parents[2]/'knots.html').read_bytes()
EXPECTED_SHA=hashlib.sha256(EXPECTED).hexdigest()
def read(path):
    with urllib.request.urlopen(urllib.request.Request(URL+path,headers={'Cache-Control':'no-cache'}),timeout=25) as response:
        assert response.status==200,(path,response.status)
        return response.read()
for attempt in range(10):
    try:
        raw=read('knots.html')
        actual=hashlib.sha256(raw).hexdigest()
        assert actual==EXPECTED_SHA,f'Published page is not this build: expected {EXPECTED_SHA}, received {actual}'
        html=raw.decode('utf-8')
        match=re.search(r'<script id="knot-data" type="application/json">(.*?)</script>',html,re.S)
        assert match,'Published page does not contain its standalone tutorial catalog'
        data=json.loads(match.group(1));knots=data['knots']
        assert len(knots)==100,len(knots)
        assert len({k['video'] for k in knots})==100,'Missing distinct reference demonstrations'
        assert sum(len(k['steps']) for k in knots)==500,'Missing written steps'
        assert 'window.KnotOriginals=Object.freeze' in html,'Original animation runtime missing'
        assert 'id="originals-only"' in html,'Original animation filter missing'
        launcher=read('').decode('utf-8')
        assert 'href="knots.html"' in launcher,'Launcher does not contain Knotbook'
        assert 'href="zork.html"' in launcher,'Existing Zork launcher link missing'
        print('LIVE HTTP PASS: '+URL+'knots.html')
        print('Published version: '+data['version'])
        print('Published SHA256: '+actual)
        print('Published page exactly matches this compiled release, including the original animation player.')
        print('Published catalog: 100 tutorials, 500 written steps and 100 retained reference video IDs.')
        print('Published launcher: Knotbook and existing Zork links present.')
        break
    except Exception as exc:
        if attempt==9:raise
        print(f'Pages still propagating ({attempt+1}/10): {exc}',flush=True)
        time.sleep(12)
