#!/usr/bin/env bash
set -euo pipefail
TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-"$TEST_DIR/../../../../build-tools/sdk"}}"
TOOLS_DIR="$SDK_DIR/build-tools/${SF_BUILD_TOOLS:-35.0.0}"
ANDROID_JAR="$SDK_DIR/platforms/android-${SF_PLATFORM:-35}/android.jar"
BUILD_DIR="$TEST_DIR/build"
SIGNING_DIR="${SF_SIGNING_DIR:-"${XDG_STATE_HOME:-$HOME/.local/state}/santa-fe-api-lab/signing"}"
KEYSTORE="${SF_KEYSTORE:-"$SIGNING_DIR/santa-fe-api-lab.jks"}"
PASSWORD_FILE="${SF_PASSWORD_FILE:-"$SIGNING_DIR/store.password"}"
KEY_ALIAS="${SF_KEY_ALIAS:-santafelab}"
# Instrumentation must share the release's signing identity. Never create a new key here.
test -f "$KEYSTORE" && test -f "$PASSWORD_FILE" || { echo "Build the release app with its preserved signing key first." >&2; exit 1; }
if command -v javac >/dev/null; then JAVAC=(javac); else JAVAC=(java -m jdk.compiler/com.sun.tools.javac.Main); fi
if command -v jar >/dev/null; then JAR=(jar); else JAR=(java -m jdk.jartool/sun.tools.jar.Main); fi
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/classes" "$BUILD_DIR/dex"
"$TOOLS_DIR/aapt2" link -o "$BUILD_DIR/base.apk" -I "$ANDROID_JAR" \
    --manifest "$TEST_DIR/AndroidManifest.xml" --min-sdk-version 26 --target-sdk-version 35
mapfile -t SOURCES < <(find "$TEST_DIR/src" -name '*.java' -print)
"${JAVAC[@]}" -source 8 -target 8 -encoding UTF-8 \
    -bootclasspath "$ANDROID_JAR:$TOOLS_DIR/core-lambda-stubs.jar" -d "$BUILD_DIR/classes" "${SOURCES[@]}"
"${JAR[@]}" cf "$BUILD_DIR/classes.jar" -C "$BUILD_DIR/classes" .
"$TOOLS_DIR/d8" --min-api 26 --lib "$ANDROID_JAR" --output "$BUILD_DIR/dex" "$BUILD_DIR/classes.jar"
cp "$BUILD_DIR/base.apk" "$BUILD_DIR/unsigned.apk"
zip -q -j "$BUILD_DIR/unsigned.apk" "$BUILD_DIR/dex/classes.dex"
"$TOOLS_DIR/zipalign" -p -f 4 "$BUILD_DIR/unsigned.apk" "$BUILD_DIR/aligned.apk"
"$TOOLS_DIR/apksigner" sign --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" \
    --ks-pass "file:$PASSWORD_FILE" --v4-signing-enabled false \
    --out "$BUILD_DIR/SantaFe-runtime-verification.apk" "$BUILD_DIR/aligned.apk"
"$TOOLS_DIR/apksigner" verify "$BUILD_DIR/SantaFe-runtime-verification.apk"
echo "Built $BUILD_DIR/SantaFe-runtime-verification.apk"
echo "Run with a connected test device: adb install -t -r this test APK, then adb shell am instrument -w com.jon.santafelab.runtime/.RuntimeVerification"
