#!/usr/bin/env python3
"""Collect canonical publisher links and *embed IDs*, never copy their images/video.
Research excerpts stay in a short-lived CI artifact, not the published guide.
"""
import concurrent.futures, json, pathlib, re, time
from urllib.parse import urlparse
import requests
from bs4 import BeautifulSoup

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / 'knots' / 'research'
OUT.mkdir(parents=True, exist_ok=True)
HEADERS = {'User-Agent': 'Misc-Knotbook/1.0 (reference verification; https://github.com/to-shreds/Misc)'}

def get(url):
    for attempt in range(3):
        try:
            r=requests.get(url, headers=HEADERS, timeout=35)
            r.raise_for_status()
            return r.text
        except requests.RequestException:
            if attempt == 2: raise
            time.sleep(2 + attempt*3)

soup=BeautifulSoup(get('https://www.animatedknots.com/complete-knot-list'), 'html.parser')
links={}
for a in soup.select('a[href]'):
    href=a['href'].split('#')[0].rstrip('/')
    if href.startswith('/'):
        href='https://www.animatedknots.com'+href
    if not href.startswith('https://www.animatedknots.com/'): continue
    img=a.find('img')
    if img and len(href.split('/'))==4 and 'complete-knot-list' not in href:
        links[href] = img.get('alt') or a.get_text(' ',strip=True)

# The authoritative list is also kept as metadata for rebuilding the media manifest.
def inspect(item):
    url,alt=item
    try:
        html=get(url)
        s=BeautifulSoup(html,'html.parser')
        videos=re.findall(r'(?:youtube(?:-nocookie)?\.com/(?:embed/|watch\?v=)|youtu\.be/)([a-zA-Z0-9_-]{11})',html)
        title=s.h1.get_text(' ',strip=True) if s.h1 else alt
        # A short research-only extract of the actual tying paragraph.
        text=s.get_text('\n',strip=True)
        marker='To Step use Arrow Keys'
        brief=''
        if marker in text:
            after=text.split(marker,1)[1]
            after=re.sub(r'^.*?Set Speed using 1\s*[–-]\s*5\.?','',after,flags=re.S)
            brief=after.split('Details',1)[0].strip()
            brief=' '.join(brief.split()[:170])
        return {'name':title,'source':url,'slug':url.rsplit('/',1)[-1], 'video':videos[0] if videos else None,'brief':brief}
    except Exception as e:
        return {'source':url,'error':str(e)}

with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
    records=list(pool.map(inspect,links.items()))
records.sort(key=lambda r:r.get('name',r['source']))
(OUT/'references.json').write_text(json.dumps(records,indent=2),encoding='utf-8')
print(json.dumps(records,indent=2))
print('PUBLISHER REFERENCES',len(records),'WITH VIDEO',sum(bool(r.get('video')) for r in records))
