package org.morok.drafts;

/** Pure validation for replacing composer text after an explicit restore action. */
public final class SavedDraftRestoration {
    private SavedDraftRestoration() { }

    public static String prepare(String savedText, int maxLength) {
        if (savedText == null || savedText.trim().isEmpty() || savedText.indexOf('\u0000') >= 0
                || savedText.length() > maxLength || maxLength < 1) return null;
        return savedText;
    }
}
