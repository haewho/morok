#!/bin/sh
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
test_dir=$(mktemp -d)
trap 'rm -rf "$test_dir"' EXIT HUP INT TERM
if [ -n "${JAVA_HOME:-}" ]; then
    javac_bin="$JAVA_HOME/bin/javac"
    java_bin="$JAVA_HOME/bin/java"
else
    javac_bin=javac
    java_bin=java
fi
"$javac_bin" --release 8 -d "$test_dir" \
    "$project_dir/TMessagesProj/src/main/java/org/morok/diagnostics/MorokDiagnosticReport.java" \
    "$project_dir/tests/diagnostics/MorokDiagnosticReportTest.java"
"$java_bin" -cp "$test_dir" MorokDiagnosticReportTest
python3 "$project_dir/tests/diagnostics/test_diagnostics_integration.py"
