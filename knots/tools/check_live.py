#!/usr/bin/env python3
"""Verify the public Pages URL, independently of the local browser fixture."""
import json, re, time, urllib.request
URL='https://to-shreds.github.io/Misc/'
def read(path):
    with urllib.request.urlopen(urllib.request.Request(URL+path,headers={'Cache-Control':'no-cache'}),timeout=25) as response:
        assert response.status==200,(path,response.status)
        return response.read().decode('utf-8')
last=None
for attempt in range(6):
    try:
        html=read('knots.html')
        match=re.search(r'<script id="knot-data" type="application/json">(.*?)</script>',html,re.S)
        assert match,'Published page does not contain its standalone tutorial catalog'
        knots=json.loads(match.group(1))['knots']
        assert len(knots)==100,len(knots)
        assert len({k['video'] for k in knots})==100,'Missing distinct demonstrations'
        assert sum(len(k['steps']) for k in knots)==500,'Missing written steps'
        launcher=read('')
        assert 'href="knots.html"' in launcher,'Launcher does not contain Knotbook'
        assert 'href="zork.html"' in launcher,'Existing Zork launcher link missing'
        print('LIVE HTTP PASS: '+URL+'knots.html')
        print('Published page: 100 tutorials, 500 steps, 100 distinct demonstration IDs.')
        print('Published launcher: Knotbook and existing Zork links present.')
        break
    except Exception as exc:
        last=exc
        if attempt==5:raise
        print(f'Pages still propagating ({attempt+1}/6): {exc}',flush=True)
        time.sleep(12)
