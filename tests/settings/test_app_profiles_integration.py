#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
notifications = (ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/NotificationsController.java").read_text()
manager = (ROOT / "TMessagesProj/src/main/java/org/morok/settings/MorokAppProfiles.java").read_text()
settings = (ROOT / "TMessagesProj/src/main/java/org/morok/settings/MorokSettings.java").read_text()
screen = (ROOT / "TMessagesProj/src/main/java/org/morok/ui/MorokProfilesActivity.java").read_text()

assert notifications.count("MorokAppProfiles.showsNotificationContent(currentAccount)") >= 2
assert "messageObject.isStoryPush || messageObject.isStoryMentionPush" in notifications
assert "return LocaleController.getString(R.string.YouHaveNewMessage);" in notifications
for forbidden in ("ConnectionsManager", "MorokProxyManager", "updateServerNotificationsSettings", "sendRequest"):
    assert forbidden not in manager, f"profile manager must not access {forbidden}"
assert "authenticatedUserId(accountSlot)" in manager
assert "public static long authenticatedUserId" in settings
assert manager.index("putString(PREVIOUS") < manager.index("applyState(accountSlot, target")
assert "NotificationsController.getInstance(accountSlot).showNotifications()" in manager
assert "setPositiveButton(text(R.string.MorokAppProfilesApply)" in screen
assert "MorokAppProfilesNetworkKept" in screen
assert "MorokAppProfilesPreviewFooter" in screen
print("PASS: app-profile notification, stable-account, previous-state, preview and no-network invariants")
