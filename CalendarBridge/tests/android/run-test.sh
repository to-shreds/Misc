#!/usr/bin/env bash
set -euo pipefail
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-"$PROJECT_DIR/../build-tools/sdk"}}"
ADB="$SDK_DIR/platform-tools/adb"
SERIAL="${CB_TEST_SERIAL:-emulator-5554}"
if [[ "$SERIAL" != emulator-* ]]; then
    echo "Refusing fixture tests on a physical phone." >&2
    exit 1
fi
"$ADB" -s "$SERIAL" install --no-incremental -r "$PROJECT_DIR/dist/CalendarBridge-1.0.0.apk"
"$ADB" -s "$SERIAL" install --no-incremental -r "$PROJECT_DIR/build/android-test/CalendarBridge-fixture-tests.apk"
"$ADB" -s "$SERIAL" shell pm grant com.jon.calendarbridge android.permission.READ_CALENDAR
"$ADB" -s "$SERIAL" shell pm grant com.jon.calendarbridge android.permission.WRITE_CALENDAR
TEST_LOG="$PROJECT_DIR/build/android-test/provider-test-result.txt"
"$ADB" -s "$SERIAL" shell am instrument -w com.jon.calendarbridge.tests/com.jon.calendarbridge.tests.BridgeInstrumentation | tee "$TEST_LOG"
if ! grep -q '^PASS: [0-9][0-9]* provider checks' "$TEST_LOG"; then
    echo "Calendar provider instrumentation failed. See $TEST_LOG" >&2
    exit 1
fi
