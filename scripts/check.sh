#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -z "${JAVA_HOME:-}" && -d '/Applications/Android Studio.app/Contents/jbr/Contents/Home' ]]; then
    export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
fi
python3 scripts/check_upstream.py
git diff --check
for morok_test in tests/*/run.sh; do
    [[ -f "$morok_test" ]] || continue
    bash "$morok_test"
done

MOROK_JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac" MOROK_JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java" bash infra/test-proxy-core.sh
MOROK_JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac" MOROK_JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java" python3 infra/tests/test_signer.py
