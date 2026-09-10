#!/usr/bin/env bash
set -euo pipefail
MOROK_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
MOROK_TEST_OUTPUT="$(mktemp -d "${TMPDIR:-/tmp}/morok-memory-tests.XXXXXX")"
trap 'rm -rf "$MOROK_TEST_OUTPUT"' EXIT
if [[ -n "${JAVA_HOME:-}" ]]; then
  MOROK_JAVA_BIN="$JAVA_HOME/bin/"
elif [[ -x '/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/javac' ]]; then
  MOROK_JAVA_BIN='/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/'
else
  MOROK_JAVA_BIN=''
fi
"${MOROK_JAVA_BIN}javac" -d "$MOROK_TEST_OUTPUT" \
  "$MOROK_ROOT/TMessagesProj/src/main/java/org/morok/memory/MemoryKey.java" \
  "$MOROK_ROOT/TMessagesProj/src/main/java/org/morok/memory/MemoryJournalPolicy.java" \
  "$MOROK_ROOT/TMessagesProj/src/main/java/org/morok/memory/MemoryExportPolicy.java" \
  "$MOROK_ROOT/TMessagesProj/src/main/java/org/morok/memory/MemoryPolicy.java" \
  "$MOROK_ROOT/TMessagesProj/src/main/java/org/morok/memory/MemoryStorageStats.java" \
  "$MOROK_ROOT/TMessagesProj/src/main/java/org/morok/memory/MemoryTrackingIndex.java" \
  "$MOROK_ROOT/tests/morok/MemoryDomainTest.java"
"${MOROK_JAVA_BIN}java" -cp "$MOROK_TEST_OUTPUT" MemoryDomainTest
