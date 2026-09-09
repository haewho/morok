import org.morok.templates.ReplyTemplate;
import org.morok.templates.ReplyTemplateInsertion;

public final class ReplyTemplateTest {
    private static final String ID = "123e4567-e89b-12d3-a456-426614174000";

    public static void main(String[] args) {
        ReplyTemplate value = new ReplyTemplate(ID, "  Greeting  ", "  Hello\nthere  ", 10, 11);
        check("Greeting".equals(value.title), "title normalization");
        check("Hello\nthere".equals(value.body), "body normalization");
        check(value.matches("greet") && value.matches("there") && !value.matches("missing"), "search");
        check("world".equals(ReplyTemplateInsertion.prepare("", 0, "world", 5)), "empty draft");
        check(" world".equals(ReplyTemplateInsertion.prepare("hello", 5, "world", 11)), "leading separator");
        check("world ".equals(ReplyTemplateInsertion.prepare("hello", 0, "world", 11)), "trailing separator");
        check(" world ".equals(ReplyTemplateInsertion.prepare("hellothere", 5, "world", 17)), "middle separators");
        check("world ".equals(ReplyTemplateInsertion.prepare("hello there", 6, "world", 17)), "existing separator");
        check(ReplyTemplateInsertion.prepare("hello", 5, "world", 10) == null, "message length bound");
        check(ReplyTemplateInsertion.prepare("hello", -1, "world", 100) == null, "cursor bound");

        new ReplyTemplate(ID, repeat('t', ReplyTemplate.MAX_TITLE_LENGTH),
                repeat('b', ReplyTemplate.MAX_BODY_LENGTH), 0, 0);
        rejects(() -> new ReplyTemplate("bad", "title", "body", 0, 0));
        rejects(() -> new ReplyTemplate(ID, "", "body", 0, 0));
        rejects(() -> new ReplyTemplate(ID, "title", "", 0, 0));
        rejects(() -> new ReplyTemplate(ID, "line\nbreak", "body", 0, 0));
        rejects(() -> new ReplyTemplate(ID, repeat('t', ReplyTemplate.MAX_TITLE_LENGTH + 1), "body", 0, 0));
        rejects(() -> new ReplyTemplate(ID, "title", repeat('b', ReplyTemplate.MAX_BODY_LENGTH + 1), 0, 0));
        rejects(() -> new ReplyTemplate(ID, "title", "body", 5, 4));
        System.out.println("PASS: reply template identity, search, insertion and limits");
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) result.append(value);
        return result.toString();
    }

    private static void rejects(Runnable action) {
        try { action.run(); throw new AssertionError("Expected rejection"); }
        catch (IllegalArgumentException expected) { }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
