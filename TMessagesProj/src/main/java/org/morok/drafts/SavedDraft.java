package org.morok.drafts;

import java.util.Locale;

/** Immutable account-local snapshot of plain composer text for one dialog/topic. */
public final class SavedDraft {
    public static final int MAX_LABEL_LENGTH = 128;
    public static final int MAX_TEXT_LENGTH = 4096;

    public final long dialogId;
    public final long topicId;
    public final String label;
    public final String text;
    public final long createdAt;
    public final long updatedAt;

    public SavedDraft(long dialogId, long topicId, String label, String text, long createdAt, long updatedAt) {
        if (dialogId == 0 || topicId < 0) throw new IllegalArgumentException("Invalid draft destination");
        this.dialogId = dialogId;
        this.topicId = topicId;
        this.label = normalizeLabel(label);
        this.text = normalizeText(text);
        if (createdAt < 0 || updatedAt < createdAt) throw new IllegalArgumentException("Invalid draft time");
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String key() { return key(dialogId, topicId); }

    public boolean matches(String query) {
        if (query == null || query.trim().isEmpty()) return true;
        String needle = query.trim().toLowerCase(Locale.ROOT);
        return label.toLowerCase(Locale.ROOT).contains(needle) || text.toLowerCase(Locale.ROOT).contains(needle);
    }

    public static String key(long dialogId, long topicId) {
        if (dialogId == 0 || topicId < 0) throw new IllegalArgumentException("Invalid draft destination");
        return dialogId + ":" + topicId;
    }

    private static String normalizeLabel(String value) {
        String result = value == null ? "" : value.trim().replace('\n', ' ').replace('\r', ' ');
        if (result.indexOf('\u0000') >= 0 || result.length() > MAX_LABEL_LENGTH) {
            throw new IllegalArgumentException("Draft label limit exceeded");
        }
        return result;
    }

    private static String normalizeText(String value) {
        String result = value == null ? "" : value;
        if (result.indexOf('\u0000') >= 0 || result.length() > MAX_TEXT_LENGTH || result.trim().isEmpty()) {
            throw new IllegalArgumentException("Draft text limit exceeded");
        }
        return result;
    }
}
