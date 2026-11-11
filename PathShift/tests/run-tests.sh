#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/core-tests
javac -encoding UTF-8 -d build/core-tests app/src/main/java/com/pathshift/app/RenameCore.java app/src/main/java/com/pathshift/app/BatchCommit.java app/src/main/java/com/pathshift/app/PresetCore.java app/src/main/java/com/pathshift/app/PresetCommit.java tests/RenameCoreTest.java tests/PresetCoreTest.java
java -cp build/core-tests RenameCoreTest
java -cp build/core-tests PresetCoreTest
