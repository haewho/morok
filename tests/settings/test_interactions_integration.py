#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
java = ROOT / "TMessagesProj/src/main/java"

chat = (java / "org/telegram/ui/ChatActivity.java").read_text()
gate = (java / "org/morok/interactions/MorokInteractionGate.java").read_text()
screen = (java / "org/morok/ui/MorokInteractionsActivity.java").read_text()
settings_screen = (java / "org/morok/ui/MorokSettingsActivity.java").read_text()
profiles = (java / "org/morok/settings/MorokAppProfiles.java").read_text()
profile_codec = (java / "org/morok/settings/AppProfileStateCodec.java").read_text()

hook = "MorokInteractionGate.allowsDoubleTapReaction(currentAccount)"
assert chat.count(hook) == 2
has_double_tap = chat.index("public boolean hasDoubleTap(View view, int position)")
has_guard = chat.index(hook, has_double_tap)
has_reaction = chat.index("getDoubleTapReaction()", has_double_tap)
assert has_double_tap < has_guard < has_reaction
on_double_tap = chat.index("public void onDoubleTap(View view, int position, float x, float y)")
on_guard = chat.index(hook, on_double_tap)
select_reaction = chat.index("selectReaction(view, messageObject", on_double_tap)
assert on_double_tap < on_guard < select_reaction

assert "return MorokSettings.interactions(accountSlot).doubleTapReactionsEnabled" in gate
assert "catch (RuntimeException unavailableSettings)" in gate
assert "return true;" in gate
for forbidden in ("ConnectionsManager", "sendRequest", "selectReaction", "MediaDataController"):
    assert forbidden not in gate

assert "new ReactionsDoubleTapManageActivity()" in screen
assert "picker.setCurrentAccount(currentAccount)" in screen
assert "withDoubleTapReactionsEnabled" in screen
assert "new SwipeGestureSettingsView(content.getContext(), currentAccount)" in screen
assert "new MorokInteractionsActivity(currentAccount)" in settings_screen
assert "setInteractions" not in profiles
assert "interactions." not in profile_codec

print("PASS: account-local double-tap gate, upstream reaction/swipe routing and profile-isolation invariants")
