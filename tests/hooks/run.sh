#!/usr/bin/env bash
set -euo pipefail
MOROK_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
python3 "$MOROK_ROOT/tests/hooks/test_memory_hook_order.py"
