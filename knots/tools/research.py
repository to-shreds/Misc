#!/usr/bin/env python3
"""Verify publisher URLs and public video IDs. Do not redistribute publisher art.
Research excerpts are kept only in a one-day developer artifact, never in the app.
"""
import concurrent.futures, json, pathlib, re, time
from urllib.parse import urlparse, urljoin
import requests
from bs4 import BeautifulSoup
ROOT=pathlib.Path(__file__).resolve().parents[2]
OUT=ROOT/'knots'/'research'; OUT.mkdir(parents=True,exist_ok=True)
BASE='https://www.animatedknots.com/'
def get(url):
    r=requests.get(url,timeout=35); r.raise_for_status(); return r.text
html=get(BASE+'complete-knot-list')
soup=BeautifulSoup(html,'html.parser')
print('INDEX',len(html),soup.title,flush=True)
print('ANCHOR SAMPLE',[(a.get('href'),a.get_text(' ',strip=True)[:60]) for a in soup.select('a[href]')[:30]],flush=True)
(OUT/'index-debug.html').write_text(html)
links={}
for a in soup.select('a[href]'):
    href=urljoin(BASE,a['href']).split('#')[0].split('?')[0].rstrip('/')
    p=urlparse(href)
    if p.hostname not in ['animatedknots.com','www.animatedknots.com']:continue
    href=BASE+p.path.strip('/')
    img=a.find('img')
    if img and p.path.strip('/') and '/' not in p.path.strip('/'):
        links[href]=img.get('alt') or a.get_text(' ',strip=True)
print('LINKS',len(links),flush=True)
assert len(links)>=100,'Publisher inventory unavailable or markup changed; inspect debug artifact.'
def inspect(item):
    url,alt=item
    try:
        h=get(url); s=BeautifulSoup(h,'html.parser')
        videos=re.findall(r'(?:youtube(?:-nocookie)?\.com/(?:embed/|watch\?v=)|youtu\.be/)([a-zA-Z0-9_-]{11})',h)
        title=s.h1.get_text(' ',strip=True) if s.h1 else alt
        text=s.get_text('\n',strip=True); brief=''
        if 'To Step use Arrow Keys' in text:
            after=text.split('To Step use Arrow Keys',1)[1]
            after=re.sub(r'^.*?Set Speed using 1\s*[–-]\s*5\.?','',after,flags=re.S)
            brief=' '.join(after.split('Details',1)[0].strip().split()[:170])
        return {'name':title,'source':url,'slug':url.rsplit('/',1)[-1],'video':videos[0] if videos else None,'brief':brief}
    except Exception as e:return {'source':url,'error':str(e)}
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:records=list(pool.map(inspect,links.items()))
records.sort(key=lambda r:r.get('name',r['source']))
(OUT/'references.json').write_text(json.dumps(records,indent=2),encoding='utf-8')
print(json.dumps(records,indent=2))
print('PUBLISHER REFERENCES',len(records),'WITH VIDEO',sum(bool(r.get('video')) for r in records))
