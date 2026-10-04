"""Private, versioned install; never overwrites differing code or the user's configuration."""
from __future__ import annotations
import hashlib
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import sys
import tempfile

VERSION='0.1.0'
MARKER='# Santa Fe Control Center managed launcher'
RUNTIME_DIRS=['bridge','ui','tasker','docs']
RUNTIME_FILES=['Start.sh','Enable-Live.sh','requirements-live.txt','README.md','START-HERE.md','HANDOFF.md']

def hashes(root):
    return {str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest()
            for p in root.rglob('*') if p.is_file() and '__pycache__' not in p.parts and not p.name.endswith('.pyc')}

def install(source,home,prefix=None):
    source=Path(source).resolve();home=Path(home).resolve()
    base=home/'.local/lib/santa-fe-control-center';target=base/VERSION
    bindir=Path(prefix)/'bin' if prefix else home/'.local/bin'
    launcher=bindir/'sf-start'
    # Check collisions before mutating the installation.
    if launcher.exists() and MARKER not in launcher.read_text(errors='replace'):
        raise RuntimeError('An unrelated sf-start command already exists; nothing was overwritten.')
    base.mkdir(mode=0o700,parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='.stage-',dir=base) as temp:
        stage=Path(temp)/VERSION;stage.mkdir(mode=0o700)
        for name in RUNTIME_DIRS:
            shutil.copytree(source/name,stage/name,ignore=shutil.ignore_patterns('__pycache__','*.pyc'))
        for name in RUNTIME_FILES:shutil.copy2(source/name,stage/name)
        if target.exists():
            if hashes(stage)!=hashes(target):
                raise RuntimeError('This version is already installed with different files. It was preserved. Use the existing version or archive/rename its code folder before reinstalling.')
        else:stage.rename(target)
    bindir.mkdir(mode=0o700,parents=True,exist_ok=True)
    bash=shutil.which('bash')
    if not bash:raise RuntimeError('Bash is required for the launcher.')
    text='#!'+bash+'\n'+MARKER+'\nexec '+shlex.quote(bash)+' '+shlex.quote(str(target/'Start.sh'))+' "$@"\n'
    fd,temp=tempfile.mkstemp(prefix='.sf-start-',dir=bindir)
    try:
        with os.fdopen(fd,'w') as f:f.write(text);f.flush();os.fsync(f.fileno())
        os.chmod(temp,0o700);os.replace(temp,launcher)
    finally:
        if os.path.exists(temp):os.unlink(temp)
    return target,launcher

def main():
    if sys.version_info<(3,10):raise SystemExit('Simulation needs Python 3.10 or newer.')
    source=Path(sys.argv[1])
    target,launcher=install(source,Path.home(),os.environ.get('PREFIX'))
    try:
        from zoneinfo import ZoneInfo
        ZoneInfo('America/New_York')
    except Exception:
        # Android may omit a timezone database usable by Python. Isolate any dependency.
        venv=Path.home()/'.local/share/santa-fe-control-center/runtime-venv'
        venv.parent.mkdir(mode=0o700,parents=True,exist_ok=True)
        if not (venv/'bin/python').exists():subprocess.run([sys.executable,'-m','venv',str(venv)],check=True)
        subprocess.run([str(venv/'bin/python'),'-m','pip','install','tzdata'],check=True)
    print('Installed private code at '+str(target))
    print('Existing settings and pairing records were left unchanged.')
    print('Start with: '+str(launcher))
    print('In Termux this is normally simply: sf-start')
    print('Then import the Tasker XML and run SF Open. No Hyundai login is needed for simulation.')

if __name__=='__main__':
    try:main()
    except (RuntimeError,subprocess.CalledProcessError) as e:raise SystemExit(str(e))
