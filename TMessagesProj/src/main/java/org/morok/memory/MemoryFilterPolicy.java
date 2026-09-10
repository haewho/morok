package org.morok.memory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;

/** Pure bounded helpers for local Memory facets and saved-neighbor context. */
public final class MemoryFilterPolicy {
    public static final int MAX_VISIBLE_TAGS = 64;
    public static final int MAX_CONTEXT_CARDS = 4;

    private MemoryFilterPolicy() { }

    public static ArrayList<String> distinctTags(String value) {
        LinkedHashMap<String, String> unique = new LinkedHashMap<>();
        if (value == null || value.isEmpty()) return new ArrayList<>();
        String[] parts = value.split(",", -1);
        for (String part : parts) {
            String display = part.trim();
            if (display.isEmpty()) continue;
            String normalized = normalizeTag(display);
            if (!unique.containsKey(normalized)) unique.put(normalized, display);
            if (unique.size() == MAX_VISIBLE_TAGS) break;
        }
        return new ArrayList<>(unique.values());
    }

    public static boolean hasTag(String value, String expected) {
        String needle = normalizeTag(expected);
        if (needle.isEmpty() || value == null || value.isEmpty()) return false;
        for (String part : value.split(",", -1)) {
            if (needle.equals(normalizeTag(part))) return true;
        }
        return false;
    }

    public static boolean sameContext(long firstDialogId, long firstTopicId,
                                      long secondDialogId, long secondTopicId) {
        return firstDialogId == secondDialogId && firstTopicId == secondTopicId;
    }

    public static long messageDistance(int firstMessageId, int secondMessageId) {
        return Math.abs((long) firstMessageId - secondMessageId);
    }

    private static String normalizeTag(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
