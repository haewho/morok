package org.morok.templates;

/** Immutable account-local reply template. Its text is inserted into the composer, never sent here. */
public final class ReplyTemplate {
    public static final int MAX_TITLE_LENGTH = 64;
    public static final int MAX_BODY_LENGTH = 4096;

    public final String id;
    public final String title;
    public final String body;
    public final long createdAt;
    public final long updatedAt;

    public ReplyTemplate(String id, String title, String body, long createdAt, long updatedAt) {
        if (!safeId(id)) throw new IllegalArgumentException("Invalid template identity");
        this.id = id;
        this.title = normalize(title, MAX_TITLE_LENGTH, true);
        this.body = normalize(body, MAX_BODY_LENGTH, false);
        if (this.title.isEmpty() || this.body.isEmpty()) throw new IllegalArgumentException("Template fields are required");
        if (createdAt < 0 || updatedAt < createdAt) throw new IllegalArgumentException("Invalid template time");
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public boolean matches(String query) {
        if (query == null || query.trim().isEmpty()) return true;
        String needle = query.trim().toLowerCase(java.util.Locale.ROOT);
        return title.toLowerCase(java.util.Locale.ROOT).contains(needle)
                || body.toLowerCase(java.util.Locale.ROOT).contains(needle);
    }

    public static boolean safeId(String value) {
        return value != null && value.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");
    }

    private static String normalize(String value, int limit, boolean oneLine) {
        String result = value == null ? "" : value.trim();
        if (result.indexOf('\u0000') >= 0 || result.length() > limit
                || oneLine && (result.indexOf('\n') >= 0 || result.indexOf('\r') >= 0)) {
            throw new IllegalArgumentException("Template field limit exceeded");
        }
        return result;
    }
}
