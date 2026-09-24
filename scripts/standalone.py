#!/usr/bin/env python3
"""Rebuild the directly-openable zork.html from canonical zork/ source."""
import base64, json, re
from pathlib import Path
root=Path(__file__).resolve().parents[1]
game=root/'zork'
html=(game/'index.html').read_text()
html=re.sub(r'<script[^>]*>.*?</script>', '', html)
html=html.replace('<link rel="stylesheet" href="style.css">','<style>'+(game/'style.css').read_text()+'</style>')
html=html.replace('href="icon.svg"','href="data:image/svg+xml;base64,'+base64.b64encode((game/'icon.svg').read_bytes()).decode()+'"')
embedded={'story':base64.b64encode((game/'data/zork1.z3').read_bytes()).decode(),'objects':json.loads((game/'data/objects.json').read_text())}
parts=['window.ZORK_EMBEDDED='+json.dumps(embedded,separators=(',',':'))+';']
for name in ['vendor/zvm.js','engine.js','view.js','app.js']:
    code=(game/name).read_text()
    code=code.replace('href="data/LICENSE-zork.txt"','href="https://github.com/historicalsource/zork1/blob/97b7b3d68c075dd9af7da499c3e9690ada3471fd/LICENSE"')
    code=code.replace('href="vendor/LICENSE-ifvms.txt"','href="https://github.com/curiousdannii/ifvms.js/blob/6ac63e6b61144d4353a168df16c4ad5c22526f88/LICENSE"')
    parts.append(code)
licenses='\n\n'.join((game/p).read_text() for p in ['data/LICENSE-zork.txt','vendor/LICENSE-ifvms.txt'])
licenses+='\n\n'+(root/'LICENSE').read_text()
html=html.replace('</body>','<!--\nTHIRD-PARTY AND INTERFACE LICENSES\n'+licenses.replace('--','- -')+'\n-->\n'+''.join('<script>'+p.replace('</script','<\\/script')+'</script>' for p in parts)+'</body>')
(root/'zork.html').write_text(html)
print('Built zork.html:',(root/'zork.html').stat().st_size,'bytes')
