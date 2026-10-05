#!/bin/sh
set -eu
tests_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
android_dir=$(CDPATH= cd -- "$tests_dir/.." && pwd)
test_output=$(mktemp -d)
trap 'rm -rf "$test_output"' EXIT HUP INT TERM
if [ -n "${JAVA_HOME:-}" ]; then
    compiler="$JAVA_HOME/bin/javac"
    runtime="$JAVA_HOME/bin/java"
else
    compiler=javac
    runtime=java
fi
"$compiler" -source 8 -target 8 -Xlint:all -d "$test_output" \
    "$android_dir/app/src/main/java/com/jon/santafelab/RequestPolicy.java" \
    "$tests_dir/RequestPolicyTest.java"
"$runtime" -cp "$test_output" com.jon.santafelab.RequestPolicyTest
