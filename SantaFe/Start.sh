#!/usr/bin/env bash
# Santa Fe Control Center managed launcher. Keeps this Termux session in the foreground.
set -euo pipefail
umask 077
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
VENV="$HOME/.local/share/santa-fe-control-center/runtime-venv"
if [[ -x "$VENV/bin/python" ]]; then
  PY="$VENV/bin/python"
else
  PY="$(command -v python3 || command -v python || true)"
fi
if [[ -z "$PY" ]]; then printf '%s\n' 'Python is missing. In Termux: pkg install python'; exit 1; fi
cd -- "$ROOT"
exec "$PY" -m bridge.server "$@"
