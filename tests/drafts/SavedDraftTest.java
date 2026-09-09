import org.morok.drafts.SavedDraft;
import org.morok.drafts.SavedDraftRestoration;

public final class SavedDraftTest {
    public static void main(String[] args) {
        SavedDraft value = new SavedDraft(-42, 7, "  Work\nchat  ", "  first\nsecond  ", 10, 11);
        check("Work chat".equals(value.label), "label normalization");
        check("  first\nsecond  ".equals(value.text), "draft whitespace preservation");
        check("-42:7".equals(value.key()), "dialog/topic identity");
        check(value.matches("work") && value.matches("second") && !value.matches("missing"), "search");
        check(value.text.equals(SavedDraftRestoration.prepare(value.text, value.text.length())), "valid restoration");
        check(SavedDraftRestoration.prepare(value.text, value.text.length() - 1) == null, "restore length bound");
        check(SavedDraftRestoration.prepare("   ", 100) == null, "blank restore rejection");

        new SavedDraft(1, 0, repeat('l', SavedDraft.MAX_LABEL_LENGTH),
                repeat('d', SavedDraft.MAX_TEXT_LENGTH), 0, 0);
        rejects(() -> new SavedDraft(0, 0, "chat", "draft", 0, 0));
        rejects(() -> new SavedDraft(1, -1, "chat", "draft", 0, 0));
        rejects(() -> new SavedDraft(1, 0, repeat('l', SavedDraft.MAX_LABEL_LENGTH + 1), "draft", 0, 0));
        rejects(() -> new SavedDraft(1, 0, "chat", repeat('d', SavedDraft.MAX_TEXT_LENGTH + 1), 0, 0));
        rejects(() -> new SavedDraft(1, 0, "chat", "\u0000", 0, 0));
        rejects(() -> new SavedDraft(1, 0, "chat", "draft", 5, 4));
        System.out.println("PASS: saved draft identity, preservation, search and restoration limits");
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
