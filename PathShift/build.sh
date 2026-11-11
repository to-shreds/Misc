#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
API="${PATHSHIFT_API:-35}"
TOOLS="${PATHSHIFT_BUILD_TOOLS:-35.0.0}"
BIN="$SDK/build-tools/$TOOLS"
JAR="$SDK/platforms/android-$API/android.jar"
OUT=build/android
DIST=dist
VERSION="${PATHSHIFT_VERSION:-0.2.0}"
VERSION_CODE="${PATHSHIFT_VERSION_CODE:-6}"
for x in aapt2 d8 zipalign apksigner; do test -x "$BIN/$x" || { echo "Missing $BIN/$x" >&2; exit 1; }; done
test -f "$JAR" || { echo "Missing $JAR" >&2; exit 1; }
rm -rf "$OUT"
mkdir -p "$OUT/generated" "$OUT/classes" "$OUT/dex" "$DIST"
"$BIN/aapt2" compile --dir app/src/main/res -o "$OUT/resources.zip"
"$BIN/aapt2" link -o "$OUT/base.apk" -I "$JAR" \
    --manifest app/src/main/AndroidManifest.xml --java "$OUT/generated" \
    --min-sdk-version 30 --target-sdk-version 35 \
    --version-code "$VERSION_CODE" --version-name "$VERSION" \
    --auto-add-overlay "$OUT/resources.zip"
mapfile -t SOURCES < <(find app/src/main/java "$OUT/generated" -name '*.java' -print)
javac -encoding UTF-8 -source 8 -target 8 -bootclasspath "$JAR:$BIN/core-lambda-stubs.jar" \
    -d "$OUT/classes" "${SOURCES[@]}"
jar cf "$OUT/classes.jar" -C "$OUT/classes" .
"$BIN/d8" --min-api 30 --lib "$JAR" --output "$OUT/dex" "$OUT/classes.jar"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
for dex in "$OUT/dex"/*.dex; do zip -q -j "$OUT/unsigned.apk" "$dex"; done
"$BIN/zipalign" -p -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
KEYSTORE="${PATHSHIFT_KEYSTORE:-$HOME/.local/state/pathshift/signing.jks}"
KEYPASS_FILE="${PATHSHIFT_KEYPASS_FILE:-$HOME/.local/state/pathshift/password.txt}"
KEY_ALIAS="${PATHSHIFT_ALIAS:-pathshift}"
mkdir -p "$(dirname "$KEYSTORE")" "$(dirname "$KEYPASS_FILE")"
if ! test -f "$KEYSTORE"; then
  if test -f "$KEYPASS_FILE"; then
    echo "Missing keystore; refusing to silently change signing identity." >&2; exit 1
  fi
  python3 - "$KEYPASS_FILE" <<'PY'
import secrets,sys
with open(sys.argv[1], 'x') as f: f.write(secrets.token_urlsafe(32))
PY
  chmod 600 "$KEYPASS_FILE"
  keytool -genkeypair -noprompt -keystore "$KEYSTORE" -alias "$KEY_ALIAS" \
      -storepass:file "$KEYPASS_FILE" -keypass:file "$KEYPASS_FILE" -keyalg RSA \
      -keysize 3072 -validity 10000 -dname "CN=PathShift, OU=Personal Android App, O=Personal, C=US"
fi
APK="$DIST/PathShift-$VERSION.apk"
"$BIN/apksigner" sign --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" \
    --ks-pass "file:$KEYPASS_FILE" --v4-signing-enabled false \
    --out "$APK" "$OUT/aligned.apk"
"$BIN/apksigner" verify --verbose --print-certs "$APK"
"$BIN/zipalign" -c -p 4 "$APK"
sha256sum "$APK" > "$APK.sha256"
echo "Built $APK"
