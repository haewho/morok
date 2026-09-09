#!/usr/bin/env bash
set -euo pipefail
MOROK_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
python3 "$MOROK_ROOT/tests/selection/test_selection_integration.py"
