#!/usr/bin/env bash
set -euo pipefail

# Official Android SDK archives pinned to this release's tested build tools.
# Install a JDK 17 (or newer) separately. No app dependency download is needed.
SDK_DIR="${1:-${ANDROID_SDK_ROOT:-${ANDROID_HOME:-"$HOME/Android/Sdk"}}}"
DOWNLOAD_DIR="$(mktemp -d)"
trap 'rm -rf "$DOWNLOAD_DIR"' EXIT
mkdir -p "$SDK_DIR/build-tools" "$SDK_DIR/platforms"
curl -fL --retry 2 https://dl.google.com/android/repository/build-tools_r35_linux.zip \
    -o "$DOWNLOAD_DIR/build-tools.zip"
curl -fL --retry 2 https://dl.google.com/android/repository/platform-35_r02.zip \
    -o "$DOWNLOAD_DIR/platform.zip"
python3 - "$DOWNLOAD_DIR" <<'PY'
import hashlib, pathlib, sys
root = pathlib.Path(sys.argv[1])
expected = {
    'build-tools.zip': 'bd3a4966912eb8b30ed0d00b0cda6b6543b949d5ffe00bea54c04c81e1561d88',
    'platform.zip': '0988cacad01b38a18a47bac14a0695f246bc76c1b06c0eeb8eb0dc825ab0c8e0',
}
for name, checksum in expected.items():
    if hashlib.sha256((root / name).read_bytes()).hexdigest() != checksum:
        raise SystemExit('SDK archive checksum mismatch: ' + name)
PY
if ! test -d "$SDK_DIR/build-tools/35.0.0"; then
    unzip -q "$DOWNLOAD_DIR/build-tools.zip" -d "$DOWNLOAD_DIR/build-tools"
    mv "$DOWNLOAD_DIR/build-tools/android-15" "$SDK_DIR/build-tools/35.0.0"
fi
if ! test -d "$SDK_DIR/platforms/android-35"; then
    unzip -q "$DOWNLOAD_DIR/platform.zip" -d "$SDK_DIR/platforms"
fi
echo "SDK ready at $SDK_DIR"
echo "Build with ANDROID_SDK_ROOT pointing to this directory."
