"""Installer checks run on Linux with isolated homes, not on Android/Termux."""
import importlib.util
import json
import os
from pathlib import Path
import signal
import socket
import subprocess
import time
import urllib.request
import pytest
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('sf_install',ROOT/'tools/install.py')
mod=importlib.util.module_from_spec(spec);spec.loader.exec_module(mod)

@pytest.fixture
def installed(tmp_path):
    home=tmp_path/'home';home.mkdir()
    prefix=tmp_path/'prefix'
    target,launcher=mod.install(ROOT,home,prefix)
    return home,prefix,target,launcher

def test_installs_private_runtime_and_preserves_state(tmp_path):
    home=tmp_path/'home';state=home/'.local/share/santa-fe-control-center';state.mkdir(parents=True)
    (state/'sentinel.txt').write_text('preserve me')
    target,launcher=mod.install(ROOT,home,tmp_path/'prefix')
    assert (target/'bridge/server.py').exists() and (target/'ui/app.js').exists()
    assert (state/'sentinel.txt').read_text()=='preserve me'
    assert launcher.stat().st_mode & 0o777==0o700
    assert not list(target.rglob('*.pyc'))

def test_identical_install_is_idempotent(installed):
    home,prefix,target,launcher=installed
    before=mod.hashes(target);mod.install(ROOT,home,prefix)
    assert before==mod.hashes(target)

def test_changed_code_is_not_overwritten(installed):
    home,prefix,target,launcher=installed
    f=target/'ui/app.js';f.write_text(f.read_text()+'\n// Local edit\n')
    before=f.read_bytes()
    with pytest.raises(RuntimeError,match='different files'):mod.install(ROOT,home,prefix)
    assert f.read_bytes()==before

def test_unrelated_launcher_is_not_overwritten(tmp_path):
    prefix=tmp_path/'prefix';(prefix/'bin').mkdir(parents=True)
    existing=prefix/'bin/sf-start';existing.write_text('#!/bin/sh\necho unrelated\n')
    with pytest.raises(RuntimeError,match='unrelated'):mod.install(ROOT,tmp_path/'home',prefix)
    assert 'unrelated' in existing.read_text()
    assert not (tmp_path/'home/.local/lib/santa-fe-control-center/0.1.0').exists()

def test_installed_cli_starts_and_serves_real_http(installed):
    home,prefix,target,launcher=installed
    sock=socket.socket();sock.bind(('127.0.0.1',0));port=sock.getsockname()[1];sock.close()
    env={**os.environ,'HOME':str(home),'PREFIX':str(prefix)}
    process=subprocess.Popen([str(launcher),'--port',str(port)],env=env,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
    try:
        result=None
        for _ in range(50):
            if process.poll() is not None:break
            try:
                with urllib.request.urlopen(f'http://127.0.0.1:{port}/health',timeout=.5) as r:
                    result=json.load(r);break
            except OSError:time.sleep(.1)
        assert result=={'app':'Santa Fe Control Center','version':'0.1.0'}
    finally:
        if process.poll() is None:process.send_signal(signal.SIGINT)
        try:process.communicate(timeout=6)
        except subprocess.TimeoutExpired:process.kill();process.communicate();pytest.fail('CLI did not stop after SIGINT')
    assert process.returncode==0
