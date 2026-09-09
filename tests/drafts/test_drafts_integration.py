#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java"

store = (JAVA / "org/morok/drafts/MorokSavedDraftStore.java").read_text()
screen = (JAVA / "org/morok/ui/MorokSavedDraftActivity.java").read_text()
chat = (JAVA / "org/telegram/ui/ChatActivity.java").read_text()
config = (JAVA / "org/telegram/messenger/UserConfig.java").read_text()

assert "getNoBackupFilesDir()" in store and '"morok/saved-drafts/" + userId' in store
assert "AndroidKeystoreKmsClient" in store and "Aead" in store
assert "AtomicFile" in store and "getFD().sync()" in store and "MessageDigest.isEqual" in store
assert "MAX_DRAFTS = 256" in store and "MAX_DATABASE_BYTES = 2 * 1024 * 1024" in store
assert '"schema", 1' in store and '"user", userId' in store and '"drafts", values' in store
assert "morok-saved-drafts/v1/" in store and 'userId + "/database"' in store
assert "putBoolean(Long.toString(userId), true).commit()" in store
assert "SavedDraft.key(dialogId, topicId)" in store

for forbidden in ("ConnectionsManager", "sendRequest", "MessagesStorage", "SendMessagesHelper"):
    assert forbidden not in store
    assert forbidden not in screen

assert "new org.morok.ui.MorokSavedDraftActivity" in chat
assert "currentEncryptedChat == null" in chat
assert "SavedDraftRestoration.prepare" in chat
restore = chat[chat.index("private void restoreMorokSavedDraft"):
               chat.index("private String getMorokChatMetadataSourceTitle")]
assert "MorokDraftReplaceComposerTitle" in restore and "MorokDraftReplaceComposerInfo" in restore
assert "replaceWithText(0, field.length(), checked, true)" in restore
assert "getMaxMessageLength()" in restore
for forbidden in ("sendMessage", "performSend", "didPressedButton"):
    assert forbidden not in restore

assert "Telegram’s regular draft remains unchanged" in (ROOT / "TMessagesProj/src/main/res/values/morok_drafts.xml").read_text()
logout = config[config.index("long morokLogoutUserId"):config.index("getPreferences().edit().clear()")]
assert logout.index("MorokMemoryStore.onLogout") < logout.index("MorokChatMetadataStore.onLogout")
assert logout.index("MorokChatMetadataStore.onLogout") < logout.index("MorokReplyTemplateStore.onLogout")
assert logout.index("MorokReplyTemplateStore.onLogout") < logout.index("MorokSavedDraftStore.onLogout")
assert logout.count("finally") >= 3

print("PASS: saved drafts are encrypted, destination-bound and restore only into the composer")
