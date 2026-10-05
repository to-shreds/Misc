#!/usr/bin/env bash
set -euo pipefail
sf_root="$(cd "$(dirname "$0")/../.." && pwd)"
sf_dependencies="${SFD_TEST_DEPS:-${TMPDIR:-/tmp}/sfd-test-deps}"
mkdir -p "$sf_dependencies"
python3 - "$sf_dependencies" <<'PY'
import hashlib, pathlib, sys, urllib.request
root = pathlib.Path(sys.argv[1])
dependencies = [
 ('bsh.jar','org/beanshell/bsh/2.0b5/bsh-2.0b5.jar','6232199563807354b3bcb5aceb3dc136502f022c6b0ef743987a83f66fee5a5c'),
 ('json.jar','org/json/json/20240303/json-20240303.jar','3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed'),
 ('okhttp.jar','com/squareup/okhttp3/okhttp/3.14.9/okhttp-3.14.9.jar','2570fab55515cbf881d7a4ceef49fc515490bc027057e666776a2832465aeca0'),
 ('okio.jar','com/squareup/okio/okio/1.17.2/okio-1.17.2.jar','f80ce42d2ffac47ad4c47e1d6f980d604d247ceb1a886705cf4581ab0c9fe2b8'),
 ('android.jar','com/google/android/android/4.1.1.4/android-4.1.1.4.jar','84072541cbb711eff89f7277100ff854929a446dba7ceb1b195c340e0b4fd3cb'),
 ('rxjava.jar','io/reactivex/rxjava2/rxjava/2.2.21/rxjava-2.2.21.jar','59df6541a840018f0f4c899aae4f4c1f4383f4c16feb5268615fbe384d28501c'),
 ('rxstreams.jar','org/reactivestreams/reactive-streams/1.0.3/reactive-streams-1.0.3.jar','1dee0481072d19c929b623e155e14d2f6085dc011529a0a0dbefc84cf571d865'),
]
for name, path, digest in dependencies:
    target=root/name
    if not target.exists():
        with urllib.request.urlopen('https://repo.maven.apache.org/maven2/'+path,timeout=45) as response:
            target.write_bytes(response.read())
    if hashlib.sha256(target.read_bytes()).hexdigest()!=digest:
        raise SystemExit('Dependency checksum mismatch: '+name)
PY
sf_classpath="$sf_dependencies/bsh.jar:$sf_dependencies/json.jar:$sf_dependencies/okhttp.jar:$sf_dependencies/okio.jar:$sf_dependencies/android.jar:$sf_dependencies/rxjava.jar:$sf_dependencies/rxstreams.jar"
sf_classes="$(mktemp -d)"
trap 'rm -rf "$sf_classes"' EXIT
javac -cp "$sf_classpath" -d "$sf_classes" "$sf_root/tests/direct/DirectRuntimeTest.java" "$sf_root/tools/ParseBeanShell.java"
java -cp "$sf_classpath:$sf_classes" ParseBeanShell "$sf_root/tasker/direct/api.java" "$sf_root/tasker/direct/ui.java" "$sf_root/tasker/direct/core.java"
java -cp "$sf_classpath:$sf_classes" DirectRuntimeTest "$sf_root/tasker/direct"
