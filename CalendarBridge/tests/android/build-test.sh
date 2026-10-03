#!/usr/bin/env bash
set -euo pipefail
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LOCAL_JDK="$PROJECT_DIR/../build-tools/jdk/usr/lib/jvm/java-17-openjdk-amd64"
if ! command -v javac >/dev/null && test -x "$LOCAL_JDK/bin/javac"; then
    export JAVA_HOME="$LOCAL_JDK"
    export PATH="$LOCAL_JDK/bin:$PATH"
fi
SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-"$PROJECT_DIR/../build-tools/sdk"}}"
TOOLS="$SDK_DIR/build-tools/35.0.0"
ANDROID_JAR="$SDK_DIR/platforms/android-35/android.jar"
SIGNING="${CB_SIGNING_DIR:-"$PROJECT_DIR/../build-tools/calendar-bridge-signing"}"
BUILD="$PROJECT_DIR/build/android-test"
mkdir -p "$BUILD/classes" "$BUILD/dex"
"$TOOLS/aapt2" compile --dir "$PROJECT_DIR/tests/android/res" -o "$BUILD/resources.zip"
"$TOOLS/aapt2" link -o "$BUILD/base.apk" -I "$ANDROID_JAR" --manifest "$PROJECT_DIR/tests/android/AndroidManifest.xml" --min-sdk-version 26 --target-sdk-version 35 "$BUILD/resources.zip"
javac -encoding UTF-8 -source 8 -target 8 -bootclasspath "$ANDROID_JAR:$TOOLS/core-lambda-stubs.jar" \
    -classpath "$PROJECT_DIR/build/manual/classes.jar" -d "$BUILD/classes" "$PROJECT_DIR/tests/android/BridgeInstrumentation.java" "$PROJECT_DIR/tests/android/FixtureAuthenticatorService.java"
jar cf "$BUILD/classes.jar" -C "$BUILD/classes" .
"$TOOLS/d8" --min-api 26 --lib "$ANDROID_JAR" --classpath "$PROJECT_DIR/build/manual/classes.jar" --output "$BUILD/dex" "$BUILD/classes.jar"
cp "$BUILD/base.apk" "$BUILD/unsigned.apk"
zip -q -j "$BUILD/unsigned.apk" "$BUILD/dex/classes.dex"
"$TOOLS/zipalign" -p -f 4 "$BUILD/unsigned.apk" "$BUILD/aligned.apk"
"$TOOLS/apksigner" sign --ks "$SIGNING/calendar-bridge.jks" --ks-key-alias calendarbridge --ks-pass "file:$SIGNING/store.password" --out "$BUILD/CalendarBridge-fixture-tests.apk" "$BUILD/aligned.apk"
"$TOOLS/apksigner" verify "$BUILD/CalendarBridge-fixture-tests.apk"
echo "Built $BUILD/CalendarBridge-fixture-tests.apk"
