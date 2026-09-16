package org.morok.memory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** Pure validation and identity rules for user-selected portable Memory ZIP imports. */
public final class MemoryImportPolicy {
    public static final int MAX_MANIFEST_BYTES = MemoryPolicy.MAX_DATABASE_BYTES;
    public static final int MAX_ENTRY_NAME = 256;
    public static final int MAX_ENTRY_COUNT = 1 + MemoryPolicy.MAX_CARDS * MemoryPolicy.MAX_VERSIONS * 2;
    public static final long MAX_UNCOMPRESSED_BYTES = MemoryPolicy.MAX_ACCOUNT_BYTES + MemoryPolicy.MAX_DATABASE_BYTES;
    public static final int MAX_SOURCE_CHARS = 512;
    public static final int MAX_SENDER_CHARS = 512;
    public static final int MAX_NOTE_CHARS = 8192;
    public static final int MAX_TAGS_CHARS = 512;
    public static final int MAX_MIME_CHARS = 128;

    private MemoryImportPolicy() { }

    public static boolean validToken(String value) {
        return value != null && value.matches("[a-f0-9]{64}");
    }

    public static boolean validPeerKind(String value) {
        return "user".equals(value) || "group".equals(value) || "channel".equals(value)
                || "portable".equals(value);
    }

    public static boolean validEntry(String value, boolean thumbnail) {
        if (value == null || value.length() < 3 || value.length() > MAX_ENTRY_NAME
                || value.startsWith("/") || value.contains("\\") || value.indexOf('\0') >= 0) return false;
        String prefix = thumbnail ? "thumbnails/" : "attachments/";
        if (!value.startsWith(prefix) || value.indexOf('/', prefix.length()) >= 0) return false;
        String name = value.substring(prefix.length());
        int first = name.indexOf('-'), second = first < 0 ? -1 : name.indexOf('-', first + 1);
        if (first <= 0 || second <= first + 1) return false;
        String card = name.substring(0, first), version = name.substring(first + 1, second);
        String file = name.substring(second + 1);
        return card.matches("[1-9][0-9]{0,2}") && version.matches("[1-9][0-9]?")
                && !file.isEmpty() && !file.equals(".") && !file.equals("..")
                && !file.startsWith(".") && file.equals(MemoryExportPolicy.safeFileName(file));
    }

    public static boolean validMime(String value) {
        return value != null && value.length() <= MAX_MIME_CHARS
                && value.toLowerCase(Locale.ROOT).matches("[a-z0-9][a-z0-9.+-]*/[a-z0-9][a-z0-9.+-]*");
    }

    public static boolean validText(String value, int maxChars, int maxBytes) {
        return value != null && value.length() <= maxChars
                && value.getBytes(StandardCharsets.UTF_8).length <= maxBytes;
    }

    public static String restoredOrigin(String archiveToken, String cardId) {
        if (!validToken(archiveToken) || cardId == null || cardId.length() > 128) {
            throw new IllegalArgumentException("Invalid portable Memory origin");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    (archiveToken + "/" + cardId).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) result.append(String.format(Locale.US, "%02x", value & 0xff));
            return result.toString();
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
