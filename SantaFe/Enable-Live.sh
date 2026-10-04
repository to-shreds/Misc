#!/usr/bin/env bash
# Optional: installs the public Hyundai library. Does not connect to an account or send a car command.
set -euo pipefail
umask 077
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PY="$(command -v python3 || command -v python || true)"
[[ -n "$PY" ]] || { echo 'Python is missing. In Termux: pkg install python'; exit 1; }
"$PY" -c 'import sys; assert sys.version_info >= (3,12), "Live mode needs Python 3.12 or newer."'
VENV="$HOME/.local/share/santa-fe-control-center/runtime-venv"
mkdir -p -- "$(dirname -- "$VENV")"
if [[ ! -x "$VENV/bin/python" ]]; then "$PY" -m venv "$VENV"; fi
"$VENV/bin/python" -m pip install -r "$ROOT/requirements-live.txt"
"$VENV/bin/python" -c 'from hyundai_kia_connect_api import VehicleManager; print("Optional library imports successfully. No account was accessed.")'
printf '%s\n' 'Stop the existing bridge with Ctrl+C, then run sf-start again.' 'Account setup stays local in the dashboard. Live operation is not yet verified on your car.'
