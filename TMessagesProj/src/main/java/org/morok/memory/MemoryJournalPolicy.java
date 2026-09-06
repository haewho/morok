package org.morok.memory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Pure bounds and stable identities for the encrypted incoming Memory journal. */
public final class MemoryJournalPolicy {
    public static final int MAX_EVENTS = 32;
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    public static final int MAX_EVENT_BYTES = MemoryPolicy.MAX_MESSAGE_BYTES * 3;

    private MemoryJournalPolicy() { }

    public static boolean canAppend(int existingEvents, int existingBytes, int eventBytes) {
        return existingEvents >= 0 && existingEvents < MAX_EVENTS
                && existingBytes >= 0 && eventBytes > 0 && eventBytes <= MAX_EVENT_BYTES
                && existingBytes + eventBytes <= MAX_BYTES;
    }

    public static String eventId(String type, String canonicalPayload) {
        if (type == null || type.isEmpty() || canonicalPayload == null || canonicalPayload.isEmpty()) {
            throw new IllegalArgumentException("Invalid Memory journal identity");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((type + "\n" + canonicalPayload).getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder(64);
            for (byte item : digest) {
                value.append(Character.forDigit((item >>> 4) & 15, 16));
                value.append(Character.forDigit(item & 15, 16));
            }
            return value.toString();
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    public static boolean safeEventId(String value) {
        return value != null && value.matches("[a-f0-9]{64}");
    }
}
