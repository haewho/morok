import org.morok.chatmeta.ChatMetadata;

public final class ChatMetadataTest {
    public static void main(String[] args) {
        ChatMetadata value = new ChatMetadata(-42, "  Work alias  ", "  private note\nline 2  ", 7);
        check(value.dialogId == -42, "dialog identity");
        check("Work alias".equals(value.alias), "alias normalization");
        check("private note\nline 2".equals(value.note), "note normalization");
        check(!value.isEmpty(), "non-empty metadata");
        check(ChatMetadata.empty(10).isEmpty(), "empty metadata");

        new ChatMetadata(1, repeat('a', ChatMetadata.MAX_ALIAS_LENGTH),
                repeat('n', ChatMetadata.MAX_NOTE_LENGTH), 0);
        rejects(() -> new ChatMetadata(0, "alias", "note", 0));
        rejects(() -> new ChatMetadata(1, repeat('a', ChatMetadata.MAX_ALIAS_LENGTH + 1), "", 0));
        rejects(() -> new ChatMetadata(1, "line\nbreak", "", 0));
        rejects(() -> new ChatMetadata(1, "", repeat('n', ChatMetadata.MAX_NOTE_LENGTH + 1), 0));
        rejects(() -> new ChatMetadata(1, "", "bad\u0000note", 0));
        rejects(() -> new ChatMetadata(1, "", "", -1));
        System.out.println("PASS: chat metadata identity, normalization and field limits");
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
