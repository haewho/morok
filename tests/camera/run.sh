#!/usr/bin/env bash
set -euo pipefail
MOROK_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
MOROK_TEST_OUTPUT="$(mktemp -d "${TMPDIR:-/tmp}/morok-camera-tests.XXXXXX")"
trap 'rm -rf "$MOROK_TEST_OUTPUT"' EXIT
if [[ -n "${JAVA_HOME:-}" ]]; then
  MOROK_JAVA_BIN="$JAVA_HOME/bin/"
elif [[ -x '/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/javac' ]]; then
  MOROK_JAVA_BIN='/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/'
else
  MOROK_JAVA_BIN=''
fi
"${MOROK_JAVA_BIN}javac" --release 8 -d "$MOROK_TEST_OUTPUT" \
  "$MOROK_ROOT/TMessagesProj/src/main/java/org/morok/camera/RoundVideoDiagnosticBuffer.java" \
  "$MOROK_ROOT/tests/camera/RoundVideoDiagnosticBufferTest.java"
"${MOROK_JAVA_BIN}java" -cp "$MOROK_TEST_OUTPUT" RoundVideoDiagnosticBufferTest
python3 "$MOROK_ROOT/tests/camera/test_diagnostics_integration.py"
