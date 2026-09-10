#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java"

store = (JAVA / "org/morok/templates/MorokReplyTemplateStore.java").read_text()
screen = (JAVA / "org/morok/ui/MorokReplyTemplatesActivity.java").read_text()
chat = (JAVA / "org/telegram/ui/ChatActivity.java").read_text()
variables = (JAVA / "org/morok/templates/ReplyTemplateVariables.java").read_text()
settings = (JAVA / "org/morok/ui/MorokSettingsActivity.java").read_text()
config = (JAVA / "org/telegram/messenger/UserConfig.java").read_text()

assert "getNoBackupFilesDir()" in store
assert "AndroidKeystoreKmsClient" in store and "Aead" in store
assert "AtomicFile" in store and "getFD().sync()" in store and "MessageDigest.isEqual" in store
assert "MAX_TEMPLATES = 100" in store and "MAX_DATABASE_BYTES = 512 * 1024" in store
assert '"schema", 1' in store and '"user", userId' in store
assert "morok-reply-templates/v1/" in store and "userId + \"/database\"" in store
assert "putBoolean(Long.toString(userId), true).commit()" in store
assert "UUID.randomUUID()" in store

for forbidden in ("ConnectionsManager", "sendRequest", "MessagesStorage", "SendMessagesHelper"):
    assert forbidden not in store
    assert forbidden not in screen

assert "new MorokReplyTemplatesActivity(currentAccount)" in settings
assert "editor.setOnShowListener" in screen and "editor.dismiss()" in screen
assert "if (selection != null) return false" in screen
assert "new org.morok.ui.MorokReplyTemplatesActivity.Selection()" in chat
assert "currentEncryptedChat == null" in chat
assert "!UserObject.isService(dialog_id)" in chat
assert "ReplyTemplateInsertion.prepare" in chat
assert "ReplyTemplateVariables.expand" in chat
assert "MorokTemplatesPreviewTitle" in screen and "selection.preview(template.body)" in screen
assert screen.index("selection.preview(template.body)") < screen.index("selection.selected(expanded)")
for token in ("{name}", "{first_name}", "{date}", "{time}"):
    assert token in variables
method = chat[chat.index("private void insertMorokReplyTemplate"):
              chat.index("private String getMorokChatMetadataSourceTitle")]
assert "replaceWithText(cursor, 0, insertion, true)" in method
assert "getSelectionStart()" in method and "getMaxMessageLength()" in method
for forbidden in ("sendMessage", "performSend", "didPressedButton", "setFieldText"):
    assert forbidden not in method

assert config.index("MorokReplyTemplateStore.onLogout") < config.index("getPreferences().edit().clear()")
assert "finally" in config[config.index("long morokLogoutUserId"):config.index("getPreferences().edit().clear()")]

print("PASS: reply templates are encrypted, account-bound and composer-only")
