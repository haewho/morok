#!/usr/bin/env bash
set -euo pipefail
MOROK_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
MOROK_TEST_OUTPUT="$(mktemp -d "${TMPDIR:-/tmp}/morok-chatmeta-tests.XXXXXX")"
trap 'rm -rf "$MOROK_TEST_OUTPUT"' EXIT
if [[ -n "${JAVA_HOME:-}" ]]; then
  MOROK_JAVA_BIN="$JAVA_HOME/bin/"
elif [[ -x '/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/javac' ]]; then
  MOROK_JAVA_BIN='/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/'
else
  MOROK_JAVA_BIN=''
fi
"${MOROK_JAVA_BIN}javac" -d "$MOROK_TEST_OUTPUT" \
  "$MOROK_ROOT/TMessagesProj/src/main/java/org/morok/chatmeta/ChatMetadata.java" \
  "$MOROK_ROOT/tests/chatmeta/ChatMetadataTest.java"
"${MOROK_JAVA_BIN}java" -cp "$MOROK_TEST_OUTPUT" ChatMetadataTest
python3 "$MOROK_ROOT/tests/chatmeta/test_chatmeta_integration.py"
