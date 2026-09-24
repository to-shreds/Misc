#!/usr/bin/env python3
"""ARCHIVED ONE-TIME v1.0 -> v1.1 edit. Not part of routine builds.
The resulting app.js/template.html/build.py are authoritative after installation.
Refuse to change a baseline that differs from the inspected source.
"""
from pathlib import Path
import hashlib
r=Path(__file__).resolve().parents[1]
if "version:'1.1.0'" in (r/'app.js').read_text():
    print('Original animation integration is already applied.');raise SystemExit(0)
expected={'app.js':'2c96a73b9013eaa5445734c5a83e8aa286f762a2','template.html':'b7c1d5fd61b4c6e5254caa831a8797cf7078d54b','tools/build.py':'8cf34ce0d15cea5fb656e8a6c8dad9934cfb093d','tools/browser_test.py':'eca7ce31db857d6ec6ebf49357d1b43ce74c5c76'}
for name,sha in expected.items():
    raw=(r/name).read_bytes()
    assert hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()==sha, 'Baseline changed: '+name
updates={}
p=r/'app.js';s=p.read_text()
s=s.replace("const knots = data.knots;", "const knots = data.knots;\nconst originals = window.KnotOriginals;\nlet originalsOnly=false;")
s=s.replace("function picture(k,hero=false){if(!state.images", "function picture(k,hero=false){if(originals.has(k.id))return '<div class=\"original-preview\">'+originals.thumbnail(k.id)+'</div>';if(!state.images")
s=s.replace("(k.video?'▶ TYING DEMONSTRATION':'ILLUSTRATED SOURCE')", "(originals.has(k.id)?'◉ ORIGINAL ANIMATION':k.video?'▶ TYING DEMONSTRATION':'ILLUSTRATED SOURCE')")
s=s.replace("knots.filter(k=>(!savedOnly", "knots.filter(k=>(!originalsOnly||originals.has(k.id))&&(!savedOnly")
s=s.replace("function clearFilters(){category=", "function clearFilters(){originalsOnly=false;$('originals-only').setAttribute('aria-pressed','false');category=")
s=s.replace("if(v!=='detail'){$('media').replaceChildren();", "if(v!=='detail'){originals.stop();$('media').classList.remove('original-media');$('media').replaceChildren();")
s=s.replace("savedOnly=h==='saved';pathOnly=h==='essentials';", "savedOnly=h==='saved';pathOnly=h==='essentials';if(h==='originals'){clearFilters();originalsOnly=true;$('originals-only').setAttribute('aria-pressed','true');}")
s=s.replace("if(savedOnly||pathOnly)window.scrollTo", "if(savedOnly||pathOnly||h==='originals')window.scrollTo")
start=s.index('function renderMedia()');end=s.index('function stepParts',start)
old=s[start:end]
new=old.replace("const k=current;const available=!!k.video;", "const k=current;const available=!!k.video;originals.stop();$('media').classList.remove('original-media');$('source-link').href=k.source;$('youtube-link').hidden=!available;if(available)$('youtube-link').href='https://www.youtube.com/watch?v='+k.video;\nif(originals.has(k.id)){document.querySelector('.media-credit').textContent='Original Knotbook animation · Batch 1 of 5';document.querySelector('.media-help').textContent='The drawing follows the rope route in five moves. Use the controls here or the written step buttons. Reference video and source remain available for comparison.';originals.mount($('media'),k,n=>{step=n;allSteps=false;renderSteps();});originals.sync(k.id,step);return;}\ndocument.querySelector('.media-credit').textContent='Real tying demonstration · Animated Knots by Grog';document.querySelector('.media-help').textContent='Press play, then use the player’s speed menu to slow down. Written steps are a separate read-along, not synchronized video frames.';")
s=s[:start]+new+s[end:]
s=s.replace("function renderSteps(){if(!current)return;", "function renderSteps(){if(!current)return;originals.sync(current.id,step);")
s=s.replace("$('clear').onclick=clearFilters;", "$('originals-only').onclick=()=>{originalsOnly=!originalsOnly;$('originals-only').setAttribute('aria-pressed',String(originalsOnly));renderGrid();};\n$('clear').onclick=clearFilters;")
s=s.replace("if($('settings').open||", "if(document.querySelector('.oa-expanded')){if(e.key==='Escape'){$('oa-expand').click();e.preventDefault();}return;}if($('settings').open||")
s=s.replace("version:'1.0.0'", "version:'1.1.0'")
updates[p]=s
p=r/'tools/build.py';s=p.read_text().replace("'version':'1.0.0'", "'version':'1.1.0'")
s=s.replace("(SRC/'style.css').read_text(encoding='utf-8')", "(SRC/'style.css').read_text(encoding='utf-8')+'\n'+(SRC/'originals.css').read_text(encoding='utf-8')".replace("+'\n'+", "+'\\n'+"))
s=s.replace("(SRC/'app.js').read_text(encoding='utf-8')", "(SRC/'originals.js').read_text(encoding='utf-8')+'\n'+(SRC/'app.js').read_text(encoding='utf-8')".replace("+'\n'+", "+'\\n'+"))
updates[p]=s
p=r/'template.html';s=p.read_text()
s=s.replace('100 knot tutorials with real tying demonstrations','100 knot tutorials with original animations and reference demonstrations')
s=s.replace('<div class="search-row">', '<div class="original-banner"><div><strong>Made here. Move by move.</strong><p>Our first 20 original rope animations are ready to explore. No video player or internet needed once this page is loaded.</p></div><a href="#originals">Explore the first 20 →</a></div>\n <div class="search-row">')
s=s.replace('<div><button id="unlearned"', '<div><button id="originals-only" class="text-btn" aria-pressed="false">Original animations · 20</button><button id="unlearned"')
s=s.replace("Use the video’s speed menu to slow it down.", "Use the animation or video speed control to slow it down.")
s=s.replace('Why do the pictures and videos need an internet connection?', 'Which animations work without an internet connection?')
s=s.replace('Knotbook’s words and interface are local. The real tying demonstrations and their preview pictures are delivered by YouTube and credited to Animated Knots by Grog. They are not copied or re-hosted here. Pressing play loads an external player. The original illustrated source is always linked below it.', 'The first 20 original animations, their pictures, the words and the interface are built into this page. After the page has loaded, they need no further connection. They are expanded rope-path schematics, not simulations of rope tension or tightening. The other 80 tutorials retain YouTube demonstrations and preview pictures credited to Animated Knots by Grog. Those require internet. Reference links remain on every tutorial.')
s=s.replace('This guide deliberately does not mirror specialist diagrams or rewrite left/right instructions automatically.', 'The original animation player can mirror the whole diagram. That does not reverse the sequence or change the knot. Left/right wording in the companion text is not rewritten: use the rope colors and crossing directions to follow the mirrored view.')
s=s.replace('v1.0<br>Original companion instructions. Demonstrations © Animated Knots by Grog. No affiliation.', 'v1.1<br>20 original Knotbook animations. 80 reference videos © Animated Knots by Grog. No affiliation.')
s=s.replace('Loads video preview images from YouTube. Videos only load when you press play.', 'Loads external preview pictures from YouTube. Original drawings always remain available. Videos only load when you press play.')
updates[p]=s
p=r/'tools/browser_test.py';s=p.read_text()
s=s.replace("    page.locator('#play-video').click()", "    expect(page.locator('#oa-stage svg')).to_have_count(1)\n    check(True,'Bowline now uses the original local animation')\n    page.evaluate('location.hash=\"knot/anchor-hitch\"')\n    expect(page.locator('#knot-title')).to_have_text('Anchor Hitch')\n    page.locator('#play-video').click()")
s=s.replace("'https://www.youtube-nocookie.com/embed/YXRnPES0Qec?autoplay=1&playsinline=1&rel=0'", "'https://www.youtube-nocookie.com/embed/'+next(k['video'] for k in knots if k['id']=='anchor-hitch')+'?autoplay=1&playsinline=1&rel=0'")
updates[p]=s
p=r.parent/'.github/workflows/knotbook.yml';s=p.read_text()
s=s.replace('node --check knots/app.js','node --check knots/app.js\n          node --check knots/originals.js')
s=s.replace('run: python knots/tools/browser_test.py --online-pictures','run: |\n          python knots/tools/browser_test.py --online-pictures\n          python knots/tools/originals_test.py')
updates[p]=s
# Confirm the installed source matches the exact local files that passed checks.
verified={'app.js':'aac1c9af01362eb9fb42944fce15dfe7539f901570a3e0afcd00294eddf42c4c','template.html':'ad6d34b2647e02c6c743cb6cbe5f8bdfe1173182153c8890ab9b20eba58a925a','tools/build.py':'7f5968f770be6b45c9c2cd835e26b0e8c49c4c75abc80a47978964261d642d00','tools/browser_test.py':'2e360b2158a9c5b35afb8d57cf08644c454c8e3c09faaad37755487007225652'}
for name,sha in verified.items():
    assert hashlib.sha256(updates[r/name].encode()).hexdigest()==sha,'Unexpected patch output: '+name
for path,text in updates.items():path.write_text(text,encoding='utf-8')
print('Installed original animations into the existing source. Catalogs, media IDs, launcher and saved-data schema were not changed.')
