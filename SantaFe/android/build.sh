#!/usr/bin/env bash
set -euo pipefail

# Direct Android SDK build. This app has no Maven or Gradle dependencies.
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-"$PROJECT_DIR/../../build-tools/sdk"}}"
SDK_PLATFORM="${SF_PLATFORM:-35}"
SDK_BUILD_TOOLS="${SF_BUILD_TOOLS:-35.0.0}"
TOOLS_DIR="$SDK_DIR/build-tools/$SDK_BUILD_TOOLS"
ANDROID_JAR="$SDK_DIR/platforms/android-$SDK_PLATFORM/android.jar"
BUILD_DIR="$PROJECT_DIR/build/manual"
OUTPUT_DIR="$PROJECT_DIR/dist"
VERSION_CODE="${SF_VERSION_CODE:-6}"
VERSION_NAME="${SF_VERSION_NAME:-0.3.3}"
# Signing material is private and outside the repository. Preserve it for updates.
SIGNING_DIR="${SF_SIGNING_DIR:-"${XDG_STATE_HOME:-$HOME/.local/state}/santa-fe-api-lab/signing"}"
KEYSTORE="${SF_KEYSTORE:-"$SIGNING_DIR/santa-fe-api-lab.jks"}"
PASSWORD_FILE="${SF_PASSWORD_FILE:-"$SIGNING_DIR/store.password"}"
KEY_ALIAS="${SF_KEY_ALIAS:-santafelab}"

for binary in java keytool zip python3; do
    command -v "$binary" >/dev/null || { echo "Missing $binary. Install JDK 17, Python 3 and zip." >&2; exit 1; }
done
# Some JDK distributions omit the launchers but still include compiler modules.
if command -v javac >/dev/null; then
    JAVAC=(javac)
else
    java -m jdk.compiler/com.sun.tools.javac.Main -version >/dev/null 2>&1 || { echo "A JDK with jdk.compiler is required." >&2; exit 1; }
    JAVAC=(java -m jdk.compiler/com.sun.tools.javac.Main)
fi
if command -v jar >/dev/null; then
    JAR=(jar)
else
    java -m jdk.jartool/sun.tools.jar.Main --version >/dev/null 2>&1 || { echo "A JDK with jdk.jartool is required." >&2; exit 1; }
    JAR=(java -m jdk.jartool/sun.tools.jar.Main)
fi
for binary in aapt2 d8 zipalign apksigner; do
    test -x "$TOOLS_DIR/$binary" || { echo "Missing SDK tool: $TOOLS_DIR/$binary" >&2; exit 1; }
done
test -f "$ANDROID_JAR" || { echo "Missing Android platform: $ANDROID_JAR" >&2; exit 1; }
for asset in index.html diagnostic.js diagnostic.css; do
    test -f "$PROJECT_DIR/../$asset" || { echo "Tester asset is missing: $asset" >&2; exit 1; }
done

umask 077
mkdir -p "$SIGNING_DIR" "$OUTPUT_DIR"
if ! test -f "$KEYSTORE"; then
    if test -f "$PASSWORD_FILE"; then
        echo "Signing password exists but its keystore is missing. Restore the existing keystore." >&2
        exit 1
    fi
    python3 - "$PASSWORD_FILE" <<'PY'
import secrets, sys
with open(sys.argv[1], 'x') as output:
    output.write(secrets.token_urlsafe(36))
PY
    keytool -genkeypair -noprompt -keystore "$KEYSTORE" -alias "$KEY_ALIAS" \
        -storepass:file "$PASSWORD_FILE" -keypass:file "$PASSWORD_FILE" \
        -keyalg RSA -keysize 3072 -validity 10000 \
        -dname "CN=Santa Fe API Lab, OU=Personal Android App, O=Jon, C=US"
    keytool -exportcert -rfc -keystore "$KEYSTORE" -alias "$KEY_ALIAS" \
        -storepass:file "$PASSWORD_FILE" -file "$SIGNING_DIR/santa-fe-api-lab-certificate.pem"
fi
test -f "$PASSWORD_FILE" || { echo "Missing signing password file: $PASSWORD_FILE" >&2; exit 1; }
chmod 700 "$SIGNING_DIR"
chmod 600 "$KEYSTORE" "$PASSWORD_FILE"

# Clean intermediates only. Existing releases and signing identity are preserved.
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/generated" "$BUILD_DIR/classes" "$BUILD_DIR/dex" "$BUILD_DIR/assets"
# Bundle the same reviewed tester used by GitHub Pages without duplicate source files.
cp "$PROJECT_DIR/../index.html" "$PROJECT_DIR/../diagnostic.js" "$PROJECT_DIR/../diagnostic.css" "$BUILD_DIR/assets/"
"$TOOLS_DIR/aapt2" compile --dir "$PROJECT_DIR/app/src/main/res" -o "$BUILD_DIR/resources.zip"
"$TOOLS_DIR/aapt2" link -o "$BUILD_DIR/base.apk" -I "$ANDROID_JAR" \
    --manifest "$PROJECT_DIR/app/src/main/AndroidManifest.xml" \
    --java "$BUILD_DIR/generated" --min-sdk-version 26 --target-sdk-version 35 \
    --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
    -A "$BUILD_DIR/assets" --auto-add-overlay "$BUILD_DIR/resources.zip"
mapfile -t JAVA_SOURCES < <(find "$PROJECT_DIR/app/src/main/java" "$BUILD_DIR/generated" -name '*.java' -print)
test "${#JAVA_SOURCES[@]}" -gt 0 || { echo "App Java sources are missing." >&2; exit 1; }
"${JAVAC[@]}" -encoding UTF-8 -source 8 -target 8 \
    -bootclasspath "$ANDROID_JAR:$TOOLS_DIR/core-lambda-stubs.jar" \
    -d "$BUILD_DIR/classes" "${JAVA_SOURCES[@]}"
"${JAR[@]}" cf "$BUILD_DIR/classes.jar" -C "$BUILD_DIR/classes" .
"$TOOLS_DIR/d8" --min-api 26 --lib "$ANDROID_JAR" --output "$BUILD_DIR/dex" "$BUILD_DIR/classes.jar"
cp "$BUILD_DIR/base.apk" "$BUILD_DIR/unsigned.apk"
for dex_file in "$BUILD_DIR/dex"/*.dex; do
    zip -q -j "$BUILD_DIR/unsigned.apk" "$dex_file"
done
"$TOOLS_DIR/zipalign" -p -f 4 "$BUILD_DIR/unsigned.apk" "$BUILD_DIR/aligned.apk"
APK="$OUTPUT_DIR/SantaFe-API-Lab-$VERSION_NAME.apk"
"$TOOLS_DIR/apksigner" sign --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" \
    --ks-pass "file:$PASSWORD_FILE" --v4-signing-enabled false \
    --out "$APK" "$BUILD_DIR/aligned.apk"
"$TOOLS_DIR/apksigner" verify --verbose --print-certs "$APK"
"$TOOLS_DIR/zipalign" -c -p 4 "$APK"
python3 - "$APK" <<'PY'
import hashlib, pathlib, sys
apk = pathlib.Path(sys.argv[1])
pathlib.Path(str(apk) + '.sha256').write_text(hashlib.sha256(apk.read_bytes()).hexdigest() + '  ' + apk.name + '\n')
PY
chmod 644 "$APK" "$APK.sha256"
echo "Built $APK"
