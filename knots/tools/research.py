#!/usr/bin/env python3
"""Research publisher URLs and public video IDs; never download video or artwork."""
import json,pathlib,re,time,subprocess
from urllib.parse import urljoin
import requests
from bs4 import BeautifulSoup
ROOT=pathlib.Path(__file__).resolve().parents[2]
OUT=ROOT/'knots'/'research';OUT.mkdir(parents=True,exist_ok=True)
BASE='https://www.animatedknots.com/'
def get(url):
 r=requests.get(url,timeout=40);r.raise_for_status();return r.text
soup=BeautifulSoup(get(BASE+'complete-knot-list'),'html.parser')
links={urljoin(BASE,a['href']).rstrip('/'):a.get('aria-label','') for a in soup.select('a.w-grid-item-anchor[href]')}
assert len(links)>=100,'Publisher inventory missing.'
(OUT/'links.json').write_text(json.dumps(links,indent=2))
# Retrieve only public titles and embed IDs from the publisher's own channel.
try:
 p=subprocess.run(['yt-dlp','--flat-playlist','--dump-single-json','--skip-download','--socket-timeout','30','--retries','1','https://www.youtube.com/c/AnimatedKnotsbyGrog/videos'],capture_output=True,text=True,timeout=150)
 print('VIDEO METADATA STATUS',p.returncode,p.stderr[-1500:],flush=True)
 if p.returncode==0:
  v=json.loads(p.stdout); entries=[{'id':e.get('id'),'title':e.get('title'),'url':e.get('url')} for e in v.get('entries',[])]
  (OUT/'videos.json').write_text(json.dumps(entries,indent=2));print('PUBLIC VIDEOS',len(entries),flush=True)
except Exception as e:print('Metadata unavailable',e,flush=True)
# Public WordPress content endpoint: ordinary published pages, never authenticated/private content.
try:
 allposts=[]
 for page in [1,2,3]:
  r=requests.get(BASE+f'wp-json/wp/v2/us_portfolio?per_page=100&page={page}',timeout=35)
  print('PUBLIC API',page,r.status_code,r.text[:120],flush=True)
  if not r.ok:break
  posts=r.json()
  if not isinstance(posts,list):break
  allposts.extend(posts)
  if len(posts)<100:break
  time.sleep(2)
 if allposts:(OUT/'public-posts.json').write_text(json.dumps(allposts))
except Exception as e:print('Public API unavailable',e,flush=True)
# Sequential, paced verification. Failed URLs stay explicitly unavailable.
records=[]
for url,title in links.items():
 time.sleep(1.2)
 try:
  html=get(url);s=BeautifulSoup(html,'html.parser')
  videos=re.findall(r'(?:youtube(?:-nocookie)?\.com/(?:embed/|watch\?v=)|youtu\.be/)([a-zA-Z0-9_-]{11})',html)
  text=s.get_text('\n',strip=True);brief=''
  if 'To Step use Arrow Keys' in text:
   after=text.split('To Step use Arrow Keys',1)[1]
   after=re.sub(r'^.*?Set Speed using 1\s*[–-]\s*5\s*\.','',after,flags=re.S)
   brief=' '.join(after.split('Details',1)[0].strip().split()[:160])
  records.append({'name':title,'source':url,'slug':url.rsplit('/',1)[-1],'video':videos[0] if videos else None,'brief':brief})
 except Exception as e:records.append({'name':title,'source':url,'slug':url.rsplit('/',1)[-1],'error':str(e)})
 print(len(records),title,'OK' if 'error' not in records[-1] else 'UNAVAILABLE',flush=True)
 (OUT/'references.json').write_text(json.dumps(records,indent=2),encoding='utf-8')
print('REFERENCES',len(records),'VIDEOS',sum(bool(r.get('video')) for r in records),flush=True)
