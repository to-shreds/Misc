#!/usr/bin/env python3
"""Build ../knots.html from the four readable catalogs, local CSS/JS and media IDs.
No network, API keys, downloaded images or third-party runtime libraries required.
"""
from __future__ import annotations
import argparse, json, pathlib, re, sys
ROOT=pathlib.Path(__file__).resolve().parents[2]
SRC=ROOT/'knots'
FIELDS={'=':'summary','+':'materials','!':'caution','?':'check','x':'mistake'}
ACTIVITIES={'Everyday','Camping','Boating','Fishing','Decorative','Neckties','Specialist'}
ESSENTIALS={'overhand','figure-eight','square','sheet-bend','bowline','round-turn-two-half-hitches','clove-hitch','truckers-hitch'}

def parse_catalog(text: str, label: str='catalog') -> list[dict]:
    records=[]; current=None
    for number,raw in enumerate(text.splitlines(),1):
        line=raw.strip()
        if not line or line.startswith('#'):continue
        if line.startswith('@'):
            cols=line[1:].split('|')
            if len(cols)!=7:raise ValueError(f'{label}:{number}: expected 7 header columns')
            ident,name,slug,kind,level,activities,aliases=cols
            current=dict(id=ident,name=name,slug=slug,type=kind,level=int(level),activities=[x for x in activities.split(',') if x],aliases=[x for x in aliases.split(',') if x],steps=[])
            records.append(current)
        elif current is None:raise ValueError(f'{label}:{number}: content before header')
        elif line[0] in FIELDS:
            key=FIELDS[line[0]]
            if key in current:raise ValueError(f'{label}:{number}: duplicate {key}')
            current[key]=line[1:]
        elif line.startswith('>'):current['steps'].append(line[1:])
        else:raise ValueError(f'{label}:{number}: unknown line prefix')
    return records

def serialize(records: list[dict]) -> str:
    out=['# Knotbook original companion instructions. Publisher reference: header slug.\n']
    for k in records:
        out.append('@'+'|'.join([k['id'],k['name'],k['slug'],k['type'],str(k['level']),','.join(k['activities']),','.join(k['aliases'])]))
        for prefix,key in FIELDS.items():out.append(prefix+k[key])
        out.extend('>'+s for s in k['steps']);out.append('')
    return '\n'.join(out)+'\n'

def load() -> list[dict]:
    knots=[]
    for name in ['catalog-a.txt','catalog-b.txt','catalog-c.txt','catalog-d.txt']:
        knots.extend(parse_catalog((SRC/name).read_text(encoding='utf-8'),name))
    media=json.loads((SRC/'media.json').read_text(encoding='utf-8'))
    assert len(knots)==100,f'Expected 100 tutorials; found {len(knots)}'
    assert len({k['id'] for k in knots})==100,'Duplicate knot IDs'
    assert len({k['name'] for k in knots})==100,'Duplicate knot names'
    assert ESSENTIALS <= {k['id'] for k in knots},'Beginner path references missing tutorials'
    for k in knots:
        assert re.fullmatch(r'[a-z0-9]+(?:-[a-z0-9]+)*',k['id']),k['id']
        assert re.fullmatch(r'[a-z0-9-]+',k['slug']),k['slug']
        assert k['level'] in [1,2,3],k['id']
        assert k['activities'] and set(k['activities'])<=ACTIVITIES,k['id']
        assert len(k['steps'])>=4,k['id']
        for key in FIELDS.values():assert isinstance(k.get(key),str) and len(k[key])>=(10 if key=='materials' else 20),(k['id'],key)
        for s in k['steps']:
            assert '::' in s and len(s.split('::',1)[1])>25,(k['id'],s)
        assert k['id'] in media,f'Missing verified demonstration: {k["id"]}'
        k['video']=media[k['id']]
        assert re.fullmatch(r'[A-Za-z0-9_-]{11}',k['video']),k['id']
        k['source']='https://www.animatedknots.com/'+k.pop('slug')
    assert len(set(k['video'] for k in knots))==100,'Each tutorial must have its own matching demonstration'
    assert set(media)==set(k['id'] for k in knots),'Unused or missing media entries'
    return knots

def build(check: bool=False) -> None:
    knots=load()
    data={'version':'1.1.0','publisher':'Animated Knots by Grog','knots':knots}
    encoded=json.dumps(data,ensure_ascii=False,separators=(',',':')).replace('<','\\u003c').replace('\u2028','\\u2028').replace('\u2029','\\u2029')
    html=(SRC/'template.html').read_text(encoding='utf-8')
    for marker,content in {'/*__STYLE__*/':(SRC/'style.css').read_text(encoding='utf-8')+'\n'+(SRC/'originals.css').read_text(encoding='utf-8'),'/*__DATA__*/':encoded,'/*__APP__*/':(SRC/'originals.js').read_text(encoding='utf-8')+'\n'+(SRC/'app.js').read_text(encoding='utf-8')}.items():
        assert html.count(marker)==1,f'Missing or duplicated build marker {marker}'
        html=html.replace(marker,content)
    assert '/*__' not in html,'Unresolved build markers'
    destination=ROOT/'knots.html'
    if check:
        assert destination.exists() and destination.read_text(encoding='utf-8')==html,'knots.html is stale. Run python knots/tools/build.py.'
    else:destination.write_text(html,encoding='utf-8')
    print(f'Knotbook: {len(knots)} tutorials, {sum(len(k["steps"]) for k in knots)} written steps, {len(set(k["video"] for k in knots))} distinct demonstrations, {len(html.encode())} bytes')

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    try:build(args.check)
    except (AssertionError,ValueError,OSError,KeyError,json.JSONDecodeError) as exc:
        print(f'Build failed: {exc}',file=sys.stderr);sys.exit(1)
