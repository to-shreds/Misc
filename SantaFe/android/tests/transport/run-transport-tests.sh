#!/bin/sh
set -eu
transport_tests_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
android_dir=$(CDPATH= cd -- "$transport_tests_dir/../.." && pwd)
test_output=$(mktemp -d)
trap 'rm -rf "$test_output"' EXIT HUP INT TERM
if [ -n "${JAVA_HOME:-}" ]; then
    compiler="$JAVA_HOME/bin/javac"
    runtime="$JAVA_HOME/bin/java"
else
    compiler=javac
    runtime=java
fi
# This dependency is test-only. Android supplies org.json in the installed APK.
: "${JSON_JAR:?Set JSON_JAR to a genuine org.json runtime jar, not Android stub classes.}"
"$compiler" -source 8 -target 8 -cp "$JSON_JAR" -d "$test_output" \
    "$android_dir/app/src/main/java/com/jon/santafelab/RequestPolicy.java" \
    "$android_dir/app/src/main/java/com/jon/santafelab/NativeTransport.java" \
    "$transport_tests_dir/NativeTransportTest.java"
"$runtime" -cp "$test_output:$JSON_JAR" com.jon.santafelab.NativeTransportTest
