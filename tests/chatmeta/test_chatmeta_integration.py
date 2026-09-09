#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java"

store = (JAVA / "org/morok/chatmeta/MorokChatMetadataStore.java").read_text()
screen = (JAVA / "org/morok/ui/MorokChatMetadataActivity.java").read_text()
chat = (JAVA / "org/telegram/ui/ChatActivity.java").read_text()
config = (JAVA / "org/telegram/messenger/UserConfig.java").read_text()
settings = (JAVA / "org/morok/ui/MorokSettingsActivity.java").read_text()
listing = (JAVA / "org/morok/ui/MorokChatMetadataListActivity.java").read_text()

assert "getNoBackupFilesDir()" in store
assert "AndroidKeystoreKmsClient" in store and "Aead" in store
assert "AtomicFile" in store and "getFD().sync()" in store and "MessageDigest.isEqual" in store
assert "MAX_ENTRIES = 256" in store and "MAX_DATABASE_BYTES = 1024 * 1024" in store
assert '"schema", 1' in store and '"user", userId' in store
assert "morok-chat-metadata/v1/" in store and "userId + \"/database\"" in store
assert "putBoolean(Long.toString(userId), true).commit()" in store
assert config.index("MorokChatMetadataStore.onLogout") < config.index("getPreferences().edit().clear()")

for forbidden in ("ConnectionsManager", "sendRequest", "MessagesStorage", "SendMessagesHelper"):
    assert forbidden not in store
    assert forbidden not in screen

assert "currentEncryptedChat == null" in chat
assert "!UserObject.isUserSelf(currentUser)" in chat
assert "MorokChatMetadataActivity" in chat
assert "avatarContainer.getTitleTextView().setText(morokLocalAlias)" in chat
assert chat.index("avatarContainer.getTitleTextView().setText(morokLocalAlias)") < chat.index(
    "setParentActivityTitle(avatarContainer.getTitleTextView().getText())"
)
assert "expectedUserId != getUserConfig().getClientUserId()" in chat
assert "Encrypted on this device" not in screen  # text belongs in localized resources
assert "new MorokChatMetadataListActivity(currentAccount)" in settings
assert "store.clear" in listing and "store.list" in listing

print("PASS: encrypted chat metadata hooks remain local, account-bound and secret-chat safe")
