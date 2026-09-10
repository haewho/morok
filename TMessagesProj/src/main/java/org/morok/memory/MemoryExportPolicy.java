package org.morok.memory;

import java.text.Normalizer;

/** Pure bounds and ZIP-entry naming for explicit Memory exports. */
public final class MemoryExportPolicy {
    public static final int MAX_CARDS = MemoryPolicy.MAX_CARDS;
    public static final int MAX_FILE_NAME = 96;

    private MemoryExportPolicy() { }

    public static boolean validSelectionSize(int count) {
        return count > 0 && count <= MAX_CARDS;
    }

    public static String attachmentEntry(int cardIndex, int versionIndex, String fileName) {
        if (cardIndex < 0 || cardIndex >= MAX_CARDS || versionIndex < 0 || versionIndex >= MemoryPolicy.MAX_VERSIONS) {
            throw new IllegalArgumentException("Invalid Memory export position");
        }
        return "attachments/" + (cardIndex + 1) + "-" + (versionIndex + 1) + "-" + safeFileName(fileName);
    }

    public static String thumbnailEntry(int cardIndex, int versionIndex, String fileName) {
        if (cardIndex < 0 || cardIndex >= MAX_CARDS || versionIndex < 0 || versionIndex >= MemoryPolicy.MAX_VERSIONS) {
            throw new IllegalArgumentException("Invalid Memory export position");
        }
        return "thumbnails/" + (cardIndex + 1) + "-" + (versionIndex + 1) + "-" + safeFileName(fileName);
    }

    public static String safeFileName(String value) {
        String source = value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFKC);
        StringBuilder safe = new StringBuilder();
        for (int i = 0; i < source.length() && safe.length() < MAX_FILE_NAME; i++) {
            char c = source.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_' || c == ' ') safe.append(c);
            else safe.append('_');
        }
        String result = safe.toString().trim();
        while (result.startsWith(".")) result = result.substring(1);
        if (result.isEmpty() || ".".equals(result) || "..".equals(result)) return "attachment.bin";
        return result;
    }
}
