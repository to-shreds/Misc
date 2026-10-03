#!/usr/bin/env bash
set -euo pipefail

# This build uses only the Android SDK and JDK. No Gradle or Maven download is needed.
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOCAL_JDK="$PROJECT_DIR/../build-tools/jdk/usr/lib/jvm/java-17-openjdk-amd64"
if ! command -v javac >/dev/null && test -x "$LOCAL_JDK/bin/javac"; then
    export JAVA_HOME="$LOCAL_JDK"
    export PATH="$LOCAL_JDK/bin:$PATH"
fi
SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-"$PROJECT_DIR/../build-tools/sdk"}}"
SDK_PLATFORM="${CB_PLATFORM:-35}"
SDK_BUILD_TOOLS="${CB_BUILD_TOOLS:-35.0.0}"
TOOLS_DIR="$SDK_DIR/build-tools/$SDK_BUILD_TOOLS"
ANDROID_JAR="$SDK_DIR/platforms/android-$SDK_PLATFORM/android.jar"
BUILD_DIR="$PROJECT_DIR/build/manual"
OUTPUT_DIR="$PROJECT_DIR/dist"
# Keep signing material outside the source tree. Preserve this folder for APK updates.
SIGNING_DIR="${CB_SIGNING_DIR:-"$PROJECT_DIR/../build-tools/calendar-bridge-signing"}"
KEYSTORE="${CB_KEYSTORE:-"$SIGNING_DIR/calendar-bridge.jks"}"
PASSWORD_FILE="${CB_PASSWORD_FILE:-"$SIGNING_DIR/store.password"}"
ALIAS="${CB_KEY_ALIAS:-calendarbridge}"

for binary in javac jar keytool zip; do
    command -v "$binary" >/dev/null || { echo "Missing $binary. Install JDK 17 and zip." >&2; exit 1; }
done
for binary in aapt2 d8 zipalign apksigner; do
    test -x "$TOOLS_DIR/$binary" || { echo "Missing SDK tool: $TOOLS_DIR/$binary" >&2; exit 1; }
done
test -f "$ANDROID_JAR" || { echo "Missing Android platform: $ANDROID_JAR" >&2; exit 1; }

mkdir -p "$BUILD_DIR/generated" "$BUILD_DIR/classes" "$BUILD_DIR/dex" "$OUTPUT_DIR" "$SIGNING_DIR"
if ! test -f "$KEYSTORE"; then
    if test -f "$PASSWORD_FILE"; then
        echo "Signing password exists but its keystore is missing. Restore the existing keystore." >&2
        exit 1
    fi
    umask 077
    python3 - "$PASSWORD_FILE" <<'PY'
import secrets, sys
with open(sys.argv[1], 'x') as output:
    output.write(secrets.token_urlsafe(36))
PY
    keytool -genkeypair -noprompt -keystore "$KEYSTORE" -alias "$ALIAS" \
        -storepass:file "$PASSWORD_FILE" -keypass:file "$PASSWORD_FILE" \
        -keyalg RSA -keysize 3072 -validity 10000 \
        -dname "CN=Calendar Bridge, OU=Personal Android App, O=Jon, C=US"
    keytool -exportcert -rfc -keystore "$KEYSTORE" -alias "$ALIAS" \
        -storepass:file "$PASSWORD_FILE" -file "$SIGNING_DIR/calendar-bridge-certificate.pem"
fi
test -f "$PASSWORD_FILE" || { echo "Missing signing password file: $PASSWORD_FILE" >&2; exit 1; }

# Remove intermediate files only. Existing release APKs and signing identity are preserved.
rm -rf "$BUILD_DIR/generated" "$BUILD_DIR/classes" "$BUILD_DIR/dex"
mkdir -p "$BUILD_DIR/generated" "$BUILD_DIR/classes" "$BUILD_DIR/dex"
"$TOOLS_DIR/aapt2" compile --dir "$PROJECT_DIR/app/src/main/res" -o "$BUILD_DIR/resources.zip"
"$TOOLS_DIR/aapt2" link -o "$BUILD_DIR/base.apk" -I "$ANDROID_JAR" \
    --manifest "$PROJECT_DIR/app/src/main/AndroidManifest.xml" \
    --java "$BUILD_DIR/generated" --min-sdk-version 26 --target-sdk-version 35 \
    --version-code 1 --version-name 1.0.0 \
    --auto-add-overlay "$BUILD_DIR/resources.zip"
mapfile -t JAVA_SOURCES < <(find "$PROJECT_DIR/app/src/main/java" "$BUILD_DIR/generated" -name '*.java' -print)
javac -encoding UTF-8 -source 8 -target 8 -bootclasspath "$ANDROID_JAR:$TOOLS_DIR/core-lambda-stubs.jar" \
    -d "$BUILD_DIR/classes" "${JAVA_SOURCES[@]}"
jar cf "$BUILD_DIR/classes.jar" -C "$BUILD_DIR/classes" .
"$TOOLS_DIR/d8" --min-api 26 --lib "$ANDROID_JAR" --output "$BUILD_DIR/dex" "$BUILD_DIR/classes.jar"
cp "$BUILD_DIR/base.apk" "$BUILD_DIR/unsigned.apk"
for dex_file in "$BUILD_DIR/dex"/*.dex; do
    zip -q -j "$BUILD_DIR/unsigned.apk" "$dex_file"
done
"$TOOLS_DIR/zipalign" -p -f 4 "$BUILD_DIR/unsigned.apk" "$BUILD_DIR/aligned.apk"
APK="$OUTPUT_DIR/CalendarBridge-1.0.0.apk"
"$TOOLS_DIR/apksigner" sign --ks "$KEYSTORE" --ks-key-alias "$ALIAS" \
    --ks-pass "file:$PASSWORD_FILE" \
    --out "$APK" "$BUILD_DIR/aligned.apk"
"$TOOLS_DIR/apksigner" verify --verbose "$APK"
sha256sum "$APK" > "$APK.sha256"
echo "Built $APK"
