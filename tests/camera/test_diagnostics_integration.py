#!/usr/bin/env python3
"""Source invariants for opt-in, metadata-only round-video diagnostics."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[2]
diagnostics = (root / "TMessagesProj/src/main/java/org/morok/camera/RoundVideoDiagnostics.java").read_text()
quality = (root / "TMessagesProj/src/main/java/org/morok/camera/RoundVideoQuality.java").read_text()
camera2 = (root / "TMessagesProj/src/main/java/org/telegram/messenger/camera/Camera2Session.java").read_text()
recorder = (root / "TMessagesProj/src/main/java/org/telegram/ui/Components/InstantCameraView.java").read_text()
ui = (root / "TMessagesProj/src/main/java/org/morok/ui/MorokRoundVideoActivity.java").read_text()
checks = 0


def expect(value, message):
    global checks
    checks += 1
    if not value:
        raise AssertionError(message)


expect('getBoolean(ENABLED, false)' in diagnostics, "Diagnostics must default to disabled")
expect('if (!enabled()) return;' in diagnostics, "Disabled diagnostics must reject new events")
expect(all(token not in diagnostics for token in ('ConnectionsManager', 'HttpURLConnection', 'Socket', 'startActivity')),
       "Diagnostic persistence must not contain a network or external-app path")

calls = []
for source in (quality, camera2, recorder):
    calls.extend(re.findall(r'RoundVideoDiagnostics\.record\((.*?)\);', source, re.DOTALL))
expect(len(calls) >= 7, "Plan, Camera2 and encoder decisions must all be observable")
forbidden = ('currentAccount', 'dialog', 'message', 'videoFile', 'fileToWrite', 'audio', 'byte[]')
expect(all(all(token not in call for token in forbidden) for call in calls),
       "Diagnostic call sites must not pass account, message, media or file-path data")
expect('setPrimaryClip' in ui and 'RoundVideoDiagnostics.clear()' in ui,
       "Copy and clear must remain explicit actions on the local diagnostics screen")
expect('opt-in журнал' in (root / 'docs/HOOKS.md').read_text(),
       "Upstream diagnostic observations must remain documented")

print(f"Round-video diagnostic integration: {checks} checks passed")
