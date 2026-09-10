package org.morok.templates;

/** Pure, local expansion of the small documented reply-template variable set. */
public final class ReplyTemplateVariables {
    public static final String NAME = "{name}";
    public static final String FIRST_NAME = "{first_name}";
    public static final String DATE = "{date}";
    public static final String TIME = "{time}";

    private ReplyTemplateVariables() { }

    public static final class Values {
        public final String name;
        public final String firstName;
        public final String date;
        public final String time;

        public Values(String name, String firstName, String date, String time) {
            this.name = oneLine(name);
            String normalizedFirstName = oneLine(firstName);
            this.firstName = normalizedFirstName.isEmpty() ? this.name : normalizedFirstName;
            this.date = oneLine(date);
            this.time = oneLine(time);
        }
    }

    /**
     * Expands known case-sensitive variables. Unknown variables remain visible and doubled braces
     * escape a literal brace, so a typo never silently deletes user-authored template text.
     */
    public static String expand(String template, Values values) {
        if (template == null || values == null) return null;
        StringBuilder result = new StringBuilder(template.length() + 32);
        for (int i = 0; i < template.length();) {
            char current = template.charAt(i);
            if (current == '{' && i + 1 < template.length() && template.charAt(i + 1) == '{') {
                result.append('{');
                i += 2;
                continue;
            }
            if (current == '}' && i + 1 < template.length() && template.charAt(i + 1) == '}') {
                result.append('}');
                i += 2;
                continue;
            }
            if (current == '{') {
                int end = template.indexOf('}', i + 1);
                if (end > i) {
                    String replacement = replacement(template.substring(i, end + 1), values);
                    if (replacement != null) {
                        result.append(replacement);
                        i = end + 1;
                        continue;
                    }
                }
            }
            result.append(current);
            i++;
        }
        return result.toString();
    }

    private static String replacement(String token, Values values) {
        if (NAME.equals(token)) return values.name;
        if (FIRST_NAME.equals(token)) return values.firstName;
        if (DATE.equals(token)) return values.date;
        if (TIME.equals(token)) return values.time;
        return null;
    }

    private static String oneLine(String value) {
        if (value == null) return "";
        return value.replace('\u0000', ' ').replace('\r', ' ').replace('\n', ' ').trim();
    }
}
