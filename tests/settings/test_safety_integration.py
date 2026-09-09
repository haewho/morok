#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
java = ROOT / "TMessagesProj/src/main/java"

voip = (java / "org/telegram/ui/Components/voip/VoIPHelper.java").read_text()
confirmation = (java / "org/morok/safety/MorokCallConfirmation.java").read_text()
launch = (java / "org/telegram/ui/LaunchActivity.java").read_text()
notifications = (java / "org/telegram/messenger/NotificationCenter.java").read_text()
android_utilities = (java / "org/telegram/messenger/AndroidUtilities.java").read_text()
story = (java / "org/telegram/ui/Stories/StoryViewer.java").read_text()
photo = (java / "org/telegram/ui/PhotoViewer.java").read_text()
payment = (java / "org/telegram/ui/PaymentFormActivity.java").read_text()
translate = (java / "org/telegram/ui/Components/TranslateAlert2.java").read_text()
bubble = (java / "org/telegram/ui/BubbleActivity.java").read_text()
external = (java / "org/telegram/ui/ExternalActionActivity.java").read_text()
screen = (java / "org/morok/ui/MorokSafetyActivity.java").read_text()
round_confirmation = (java / "org/morok/safety/MorokRoundVideoConfirmation.java").read_text()
chat = (java / "org/telegram/ui/ChatActivity.java").read_text()
story_replies = (java / "org/telegram/ui/Stories/PeerStoriesView.java").read_text()
instant_camera = (java / "org/telegram/ui/Components/InstantCameraView.java").read_text()

assert voip.count("initiatePrivateCall(user, videoCall, canVideoCall, activity, accountInstance)") == 2
assert "MorokCallConfirmation.request" in voip
assert voip.index("permissions.isEmpty()") < voip.index("initiatePrivateCall(user")
assert "confirmed.run();" in confirmation and "confirmOutgoingCalls" in confirmation
for forbidden in ("ConnectionsManager", "sendRequest", "VoIPService", "initiateCall("):
    assert forbidden not in confirmation, f"confirmation UI must not access {forbidden}"

assert "morokScreenPrivacyChanged" in notifications
assert "MorokScreenPrivacy.enabled()" in launch
assert ".add(NotificationCenter.morokScreenPrivacyChanged)" in launch
assert "id == NotificationCenter.didSetPasscode || id == NotificationCenter.morokScreenPrivacyChanged" in launch
assert "!org.morok.safety.MorokScreenPrivacy.enabled()" in android_utilities
assert story.count("MorokScreenPrivacy.enabled()") >= 4
assert "MorokScreenPrivacy.enabled()" in photo
assert payment.count("AndroidUtilities.allowScreenCapture()") >= 2
assert "noforwards || org.morok.safety.MorokScreenPrivacy.enabled()" in translate
assert "MorokScreenPrivacy.enabled() || !SharedConfig.passcodeHash.isEmpty()" in bubble
assert "MorokScreenPrivacy.enabled() || !SharedConfig.passcodeHash.isEmpty()" in external
assert "postNotificationName(NotificationCenter.morokScreenPrivacyChanged)" in screen
assert "new PasscodeActivity(" in screen
assert "withConfirmRoundVideos" in screen

assert "SEND_IMMEDIATELY = 1" in round_confirmation
assert "OPEN_PREVIEW = 3" in round_confirmation
assert "confirmRoundVideos" in round_confirmation
hook = "MorokRoundVideoConfirmation.guardedState(state)"
assert chat.count(hook) == 1
assert story_replies.count(hook) == 1
assert "if (state == 4)" in instant_camera
assert "state == 3 ? 2 : 5" in instant_camera
assert "send = 2;" in instant_camera
assert "send = 1;" in instant_camera

print("PASS: call/round-video confirmation and secure-window integration invariants")
