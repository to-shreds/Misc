#!/usr/bin/env python3
"""Fetch pinned MIT source and rebuild the unmodified browser interpreter.
Requires Python 3, git, Node/npm, and Internet access. No credentials required.
Normal gameplay and routine tests use the committed files and need no build.
"""
import hashlib, json, shutil, subprocess, tempfile, urllib.request
from pathlib import Path
root=Path(__file__).resolve().parents[1]
lock=json.loads((root/'sources.lock.json').read_text())
for folder in ['zork/data','zork/vendor']:(root/folder).mkdir(parents=True,exist_ok=True)
s=lock['story'];i=lock['interpreter']
def fetch(repository,commit,source,destination):
    url=f'https://raw.githubusercontent.com/{repository}/{commit}/{source}'
    with urllib.request.urlopen(url,timeout=60) as response:
        (root/destination).write_bytes(response.read())
fetch(s['repository'],s['commit'],s['path'],'zork/data/zork1.z3')
actual=hashlib.sha256((root/'zork/data/zork1.z3').read_bytes()).hexdigest()
if actual!=s['sha256']:raise RuntimeError('Original Zork checksum mismatch')
fetch(s['repository'],s['commit'],'LICENSE','zork/data/LICENSE-zork.txt')
fetch(i['repository'],i['commit'],'LICENSE','zork/vendor/LICENSE-ifvms.txt')
with tempfile.TemporaryDirectory() as tmp:
    src=Path(tmp)/'ifvms'
    subprocess.run(['git','clone','--quiet','https://github.com/'+i['repository']+'.git',str(src)],check=True)
    subprocess.run(['git','-C',str(src),'checkout','--quiet',i['commit']],check=True)
    subprocess.run(['npx','--yes',i['bundler'],str(src/i['entry']),'--bundle','--format=iife','--global-name=ZVM','--outfile='+str(root/'zork/vendor/zvm.js')],check=True)
print('Pinned, unmodified upstream game and interpreter ready.')
