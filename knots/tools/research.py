#!/usr/bin/env python3
"""Verify publisher URLs and embed IDs; keep research excerpts out of publication."""
import concurrent.futures,json,pathlib,re
from urllib.parse import urljoin
import requests
from bs4 import BeautifulSoup
ROOT=pathlib.Path(__file__).resolve().parents[2]
OUT=ROOT/'knots'/'research';OUT.mkdir(parents=True,exist_ok=True)
BASE='https://www.animatedknots.com/'
def get(url):
 r=requests.get(url,timeout=35);r.raise_for_status();return r.text
soup=BeautifulSoup(get(BASE+'complete-knot-list'),'html.parser')
links={urljoin(BASE,a['href']).rstrip('/'):a.get('aria-label','') for a in soup.select('a.w-grid-item-anchor[href]')}
assert len(links)>=100,'Publisher inventory missing or markup changed.'
def inspect(item):
 url,alt=item
 try:
  html=get(url);s=BeautifulSoup(html,'html.parser')
  videos=re.findall(r'(?:youtube(?:-nocookie)?\.com/(?:embed/|watch\?v=)|youtu\.be/)([a-zA-Z0-9_-]{11})',html)
  title=s.h1.get_text(' ',strip=True) if s.h1 else alt
  text=s.get_text('\n',strip=True);brief=''
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
print('REFERENCES',len(records),'VIDEOS',sum(bool(r.get('video')) for r in records))
