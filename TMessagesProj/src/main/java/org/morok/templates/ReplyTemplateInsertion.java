package org.morok.templates;

/** Pure insertion policy: preserves the existing draft and adds only needed word separators. */
public final class ReplyTemplateInsertion {
    private ReplyTemplateInsertion() { }

    public static String prepare(CharSequence current, int cursor, String template, int maxLength) {
        if (current == null || template == null || template.isEmpty() || cursor < 0 || cursor > current.length()
                || maxLength < 1) return null;
        StringBuilder insertion = new StringBuilder(template.length() + 2);
        if (cursor > 0 && !Character.isWhitespace(current.charAt(cursor - 1))
                && !Character.isWhitespace(template.charAt(0))) insertion.append(' ');
        insertion.append(template);
        if (cursor < current.length() && !Character.isWhitespace(current.charAt(cursor))
                && !Character.isWhitespace(template.charAt(template.length() - 1))) insertion.append(' ');
        return current.length() + insertion.length() <= maxLength ? insertion.toString() : null;
    }
}
