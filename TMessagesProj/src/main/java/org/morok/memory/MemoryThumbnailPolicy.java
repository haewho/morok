package org.morok.memory;

/** Pure validation for bounded thumbnail bytes copied from Telegram's existing local cache. */
public final class MemoryThumbnailPolicy {
    private MemoryThumbnailPolicy() { }

    public static String mimeType(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MemoryPolicy.MAX_THUMBNAIL_BYTES) return "";
        int length = bytes.length;
        if (length >= 4 && u(bytes[0]) == 0xff && u(bytes[1]) == 0xd8
                && u(bytes[length - 2]) == 0xff && u(bytes[length - 1]) == 0xd9) return "image/jpeg";
        if (length >= 20 && u(bytes[0]) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G'
                && u(bytes[4]) == 0x0d && u(bytes[5]) == 0x0a && u(bytes[6]) == 0x1a && u(bytes[7]) == 0x0a
                && bytes[length - 12] == 0 && bytes[length - 11] == 0 && bytes[length - 10] == 0 && bytes[length - 9] == 0
                && bytes[length - 8] == 'I' && bytes[length - 7] == 'E' && bytes[length - 6] == 'N' && bytes[length - 5] == 'D') {
            return "image/png";
        }
        if (length >= 20 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P'
                && bytes[12] == 'V' && bytes[13] == 'P' && bytes[14] == '8'
                && (bytes[15] == ' ' || bytes[15] == 'L' || bytes[15] == 'X')
                && littleEndianInt(bytes, 4) == length - 8) return "image/webp";
        if (length >= 14 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F'
                && bytes[3] == '8' && (bytes[4] == '7' || bytes[4] == '9') && bytes[5] == 'a'
                && bytes[length - 1] == 0x3b) return "image/gif";
        return "";
    }

    public static String extension(String mime) {
        if ("image/png".equals(mime)) return ".png";
        if ("image/webp".equals(mime)) return ".webp";
        if ("image/gif".equals(mime)) return ".gif";
        return ".jpg";
    }

    public static boolean supportedMime(String mime) {
        return "image/jpeg".equals(mime) || "image/png".equals(mime)
                || "image/webp".equals(mime) || "image/gif".equals(mime);
    }

    private static int u(byte value) { return value & 0xff; }

    private static long littleEndianInt(byte[] value, int offset) {
        return (long) u(value[offset]) | (long) u(value[offset + 1]) << 8
                | (long) u(value[offset + 2]) << 16 | (long) u(value[offset + 3]) << 24;
    }
}
