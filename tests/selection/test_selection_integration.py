#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java"

helper = (JAVA / "org/telegram/ui/Cells/TextSelectionHelper.java").read_text()
chat = (JAVA / "org/telegram/ui/ChatActivity.java").read_text()

assert "selectionStart" in helper and "selectionEnd" in helper and "movingHandle" in helper
assert "android.R.id.copy" in helper and "android.R.id.selectAll" in helper
assert "MOROK_INSERT_IN_COMPOSER" in helper and "MorokSelectionInsert" in helper
prepare = helper[helper.index("public boolean onPrepareActionMode"):
                 helper.index("private String translateFromLanguage")]
assert "canCopy()" in prepare
assert "TextSelectionHelper.this.callback.canInsertSelectedText()" in prepare

action = helper[helper.index("public boolean onActionItemClicked"):
                helper.index("public void onDestroyActionMode")]
assert "getSelectedText()" in action and "onInsertSelectedText(selectedText)" in action
assert "canCopy()" in action and "clear(true)" in action
assert "addToClipboard" not in action

callback = chat[chat.index("textSelectionHelper.setCallback"):
                chat.index("View overlay = textSelectionHelper.getOverlayView")]
assert "canInsertSelectedText" in callback and "insertMorokSelectedText(text)" in callback

insert = chat[chat.index("private boolean isMorokTextSelectionInsertAvailable"):
              chat.index("private String getMorokChatMetadataSourceTitle")]
assert "isMorokReplyTemplatesAvailable()" in insert
assert "!UserObject.isService(dialog_id)" in chat
assert "ReplyTemplateInsertion.prepare" in insert
assert "replaceWithText(cursor, 0, insertion, true)" in insert
assert "getMaxMessageLength()" in insert
for forbidden in ("addToClipboard", "sendMessage", "performSend", "didPressedButton"):
    assert forbidden not in insert

can_copy = chat[chat.index("protected boolean canCopy()"):
                chat.index("protected void onQuoteClick")]
assert "isPeerNoForwards" in can_copy and "messageOwner.noforwards" in can_copy

print("PASS: partial selection inserts locally only when upstream copy policy allows it")
