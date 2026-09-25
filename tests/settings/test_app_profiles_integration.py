#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
notifications = (ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/NotificationsController.java").read_text()
manager = (ROOT / "TMessagesProj/src/main/java/org/morok/settings/MorokAppProfiles.java").read_text()
settings = (ROOT / "TMessagesProj/src/main/java/org/morok/settings/MorokSettings.java").read_text()
screen = (ROOT / "TMessagesProj/src/main/java/org/morok/ui/MorokProfilesActivity.java").read_text()

assert notifications.count("MorokAppProfiles.showsNotificationContent(currentAccount)") >= 2
assert notifications.count("MorokAppProfiles.showsNotificationNames(currentAccount)") >= 3
assert "messageObject.isStoryPush || messageObject.isStoryMentionPush" in notifications
assert "return LocaleController.getString(R.string.YouHaveNewMessage);" in notifications
assert "|| !morokShowsNotificationNames" in notifications
assert "morokShowsNotificationNames && chat == null && user != null" in notifications
assert "if (morokShowsNotificationNames && !AndroidUtilities.needShowPasscode()" in notifications
assert "if (morokShowsNotificationNames && !hasCallback" in notifications
assert "!waitingForPasscode && copybutton != null" in notifications
assert "if (!waitingForPasscode)" in notifications
assert "public void refreshMorokNotificationPrivacyLabels()" in notifications
assert "redactMorokDialogNotificationChannels();" in notifications
assert "isMorokDialogNotificationChannel(channel.getId())" in notifications
assert "channel.setName(name);" in notifications
assert "systemNotificationManager.createNotificationChannel(channel);" in notifications
assert "MorokAppProfiles.showsNotificationNames(currentAccount) && user != null" in notifications
assert "systemNotificationManager.createNotificationChannelGroups(channelGroups);" in notifications
redaction = notifications.split("private void redactMorokDialogNotificationChannels()", 1)[1]
redaction = redaction.split("private boolean isMorokDialogNotificationChannel", 1)[0]
assert "deleteNotificationChannel" not in redaction
for forbidden in ("ConnectionsManager", "MorokProxyManager", "updateServerNotificationsSettings", "sendRequest"):
    assert forbidden not in manager, f"profile manager must not access {forbidden}"
assert "authenticatedUserId(accountSlot)" in manager
assert "public static long authenticatedUserId" in settings
assert manager.index("putString(PREVIOUS") < manager.index("applyState(accountSlot, target")
assert "NotificationsController.getInstance(accountSlot).showNotifications()" in manager
assert manager.count("refreshMorokNotificationPrivacyLabels()") == 2
assert "CURRENT_NOTIFICATION_NAMES" in manager
assert "setNotificationNames" in manager
for flag in ("FLAG_AUTOPLAY_VIDEOS", "FLAG_AUTOPLAY_GIFS",
             "FLAG_ANIMATED_STICKERS_CHAT", "FLAG_ANIMATED_STICKERS_KEYBOARD"):
    assert f"LiteMode.isEnabledSetting(LiteMode.{flag})" in manager
    assert f"LiteMode.toggleFlag(LiteMode.{flag}" in manager
assert "setPositiveButton(text(R.string.MorokAppProfilesApply)" in screen
assert "state.animatedStickersChat" in screen
assert "state.animatedStickersKeyboard" in screen
assert "state.notificationNames" in screen
assert "MorokAppProfilesNetworkKept" in screen
assert "MorokAppProfilesPreviewFooter" in screen
print("PASS: app-profile notification, stable-account, previous-state, preview and no-network invariants")
