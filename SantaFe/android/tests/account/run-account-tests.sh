#!/bin/sh
set -eu
account_tests_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
android_dir=$(CDPATH= cd -- "$account_tests_dir/../.." && pwd)
test_output=$(mktemp -d)
trap 'rm -rf "$test_output"' EXIT HUP INT TERM
if [ -n "${JAVA_HOME:-}" ]; then
    compiler="$JAVA_HOME/bin/javac"
    runtime="$JAVA_HOME/bin/java"
else
    compiler=javac
    runtime=java
fi
: "${JSON_JAR:?Set JSON_JAR to a genuine org.json runtime jar, not Android stub classes.}"
: "${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT to the installed Android SDK.}"
android_jar="$ANDROID_SDK_ROOT/platforms/android-${SF_PLATFORM:-35}/android.jar"
test -f "$android_jar" || { echo "Missing Android compilation API jar." >&2; exit 1; }
"$compiler" -source 8 -target 8 -cp "$JSON_JAR:$android_jar" -d "$test_output" \
    "$android_dir/app/src/main/java/com/jon/santafelab/AccountStore.java" \
    "$account_tests_dir/AccountStoreTest.java"
"$runtime" -cp "$test_output:$JSON_JAR:$android_jar" com.jon.santafelab.AccountStoreTest
