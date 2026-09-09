#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
java = ROOT / "TMessagesProj/src/main/java"

screen = (java / "org/morok/ui/MorokDiagnosticsActivity.java").read_text()
report = (java / "org/morok/diagnostics/MorokDiagnosticReport.java").read_text()
settings = (java / "org/morok/ui/MorokSettingsActivity.java").read_text()

assert "new MorokDiagnosticsActivity(currentAccount)" in settings
assert "MorokMemoryStore.forAccount(currentAccount)" in screen
assert "store.storageStats" in screen
assert "archive.chats.size()" in screen
assert "proxy.nodes().size()" in screen
assert "RoundVideoDiagnostics.count()" in screen
assert "ClipData.newPlainText(\"MOROK diagnostics\", report().render())" in screen

report_method = screen[screen.index("private MorokDiagnosticReport report()"):
                       screen.index("private void copyReport()")]
for forbidden in ("selectedId", "ProxyNode", ".host", ".port", ".username", ".password",
                  ".secret", "getClientUserId", "dialogId", "message"):
    assert forbidden not in report_method

for forbidden_field in ("userId", "chatId", "dialogId", "proxyHost", "proxySecret",
                        "messageText", "filePath"):
    assert forbidden_field not in report

assert "secrets=false" in report
assert "replace('\\n', ' ')" in report
print("PASS: unified diagnostics uses aggregate local state and excludes identity/endpoint/content fields")
