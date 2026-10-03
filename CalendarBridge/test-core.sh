#!/usr/bin/env bash
set -euo pipefail
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOCAL_JDK="$PROJECT_DIR/../build-tools/jdk/usr/lib/jvm/java-17-openjdk-amd64"
if ! command -v javac >/dev/null && test -x "$LOCAL_JDK/bin/javac"; then
  export PATH="$LOCAL_JDK/bin:$PATH"
fi
mkdir -p "$PROJECT_DIR/build/core-tests"
javac -encoding UTF-8 -source 8 -target 8 -d "$PROJECT_DIR/build/core-tests" \
  "$PROJECT_DIR"/app/src/main/java/com/jon/calendarbridge/core/*.java \
  "$PROJECT_DIR"/tests/ClassifierTest.java "$PROJECT_DIR"/tests/DeletionPolicyTest.java
java -cp "$PROJECT_DIR/build/core-tests" ClassifierTest
java -cp "$PROJECT_DIR/build/core-tests" DeletionPolicyTest
