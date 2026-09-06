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

