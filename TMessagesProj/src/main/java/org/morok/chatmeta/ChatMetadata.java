package org.morok.chatmeta;

/** Immutable account-local metadata for one ordinary Telegram dialog. */
public final class ChatMetadata {
    public static final int MAX_ALIAS_LENGTH = 64;
    public static final int MAX_NOTE_LENGTH = 4096;

    public final long dialogId;
    public final String alias;
    public final String note;
    public final long updatedAt;

    public ChatMetadata(long dialogId, String alias, String note, long updatedAt) {
        if (dialogId == 0) throw new IllegalArgumentException("Dialog ID is required");
        this.dialogId = dialogId;
        this.alias = normalize(alias, MAX_ALIAS_LENGTH, true);
        this.note = normalize(note, MAX_NOTE_LENGTH, false);
        if (updatedAt < 0) throw new IllegalArgumentException("Invalid update time");
        this.updatedAt = updatedAt;
    }

    public boolean isEmpty() { return alias.isEmpty() && note.isEmpty(); }

    public static ChatMetadata empty(long dialogId) {
        return new ChatMetadata(dialogId, "", "", 0);
    }

    private static String normalize(String value, int limit, boolean oneLine) {
        String result = value == null ? "" : value.trim();
        if (result.indexOf('\u0000') >= 0 || result.length() > limit || oneLine && (result.indexOf('\n') >= 0 || result.indexOf('\r') >= 0)) {
            throw new IllegalArgumentException("Chat metadata field limit exceeded");
        }
        return result;
    }
}
