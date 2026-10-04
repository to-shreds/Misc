#!/usr/bin/env bash
set -euo pipefail
umask 077
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PY="$(command -v python3 || command -v python || true)"
if [[ -z "$PY" ]]; then
  printf '%s\n' 'Python is missing. In Termux, run: pkg install python'
  exit 1
fi
"$PY" "$ROOT/tools/install.py" "$ROOT"
