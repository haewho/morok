#!/usr/bin/env bash
set -euo pipefail
MOROK_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
python3 "$MOROK_ROOT/tests/build/test_prepare_apk_output.py"
python3 - "$MOROK_ROOT" <<'PY'
from pathlib import Path
import sys

root = Path(sys.argv[1])
script = (root / "scripts/build.sh").read_text()
report = (root / "scripts/report_apk.py").read_text()
prepare = script.index('python3 scripts/prepare_apk_output.py TMessagesProj_App/build "$morok_variant"')
gradle = script.index('./gradlew morokPreflight "$morok_task"')
if prepare >= gradle:
    raise AssertionError("Compact APK preparation must run before Gradle packaging")
if 'max_inter_entry_gap(apk)' not in report or 'maxZipEntryGapBytes' not in report:
    raise AssertionError("APK report must reject and record excessive ZIP gaps")
print("APK output integration: preparation precedes assemble and the report audits ZIP gaps")
PY
