package org.morok.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Account-local allowlist for the encrypted archive. Disabled by default. */
public final class ArchiveSettings {
    public static final int MAX_CHATS = 256;
    private static final int[] RETENTION_DAYS = {30, 90, 180, 365};
    private static final int[] STORAGE_MIB = {64, 128, 256};
    private static final int[] ATTACHMENT_MIB = {1, 4, 8};
    public static final int DEFAULT_RETENTION_DAYS = 90;
    public static final int DEFAULT_STORAGE_MIB = 256;
    public static final int DEFAULT_ATTACHMENT_MIB = 8;
    public static final String ATTACHMENTS_NEVER = "never";
    public static final String ATTACHMENTS_WIFI = "wifi";
    public static final String ATTACHMENTS_ANY = "any";
    public static final ArchiveSettings DEFAULT = new ArchiveSettings(false, Collections.emptySet(),
            DEFAULT_RETENTION_DAYS, DEFAULT_STORAGE_MIB, DEFAULT_ATTACHMENT_MIB, ATTACHMENTS_WIFI);

    public final boolean enabled;
    public final Set<Long> chats;
    public final int retentionDays;
    public final int storageMib;
    public final int attachmentMib;
    public final String attachmentPolicy;

    public ArchiveSettings(boolean enabled, Set<Long> chats) {
        this(enabled, chats, DEFAULT.retentionDays, DEFAULT.storageMib, DEFAULT.attachmentMib,
                DEFAULT.attachmentPolicy);
    }

    public ArchiveSettings(boolean enabled, Set<Long> chats, int retentionDays, int storageMib,
            int attachmentMib, String attachmentPolicy) {
        this.enabled = enabled;
        LinkedHashSet<Long> valid = new LinkedHashSet<>();
        if (chats != null) {
            for (Long dialogId : chats) {
                if (dialogId != null && dialogId != 0 && valid.size() < MAX_CHATS) valid.add(dialogId);
            }
        }
        this.chats = Collections.unmodifiableSet(valid);
        this.retentionDays = allowed(retentionDays, RETENTION_DAYS, DEFAULT_RETENTION_DAYS);
        this.storageMib = allowed(storageMib, STORAGE_MIB, DEFAULT_STORAGE_MIB);
        this.attachmentMib = allowed(attachmentMib, ATTACHMENT_MIB, DEFAULT_ATTACHMENT_MIB);
        this.attachmentPolicy = ATTACHMENTS_NEVER.equals(attachmentPolicy)
                || ATTACHMENTS_ANY.equals(attachmentPolicy) ? attachmentPolicy : ATTACHMENTS_WIFI;
    }

    public ArchiveSettings withEnabled(boolean value) {
        return copy(value, chats, retentionDays, storageMib, attachmentMib, attachmentPolicy);
    }

    public ArchiveSettings withChat(long dialogId, boolean value) {
        if (dialogId == 0) return this;
        LinkedHashSet<Long> updated = new LinkedHashSet<>(chats);
        if (value) {
            if (updated.size() >= MAX_CHATS && !updated.contains(dialogId)) return this;
            updated.add(dialogId);
        } else {
            updated.remove(dialogId);
        }
        return copy(enabled, updated, retentionDays, storageMib, attachmentMib, attachmentPolicy);
    }

    public ArchiveSettings withoutChats() {
        return chats.isEmpty() ? this : copy(enabled, Collections.emptySet(), retentionDays, storageMib,
                attachmentMib, attachmentPolicy);
    }

    public ArchiveSettings withRetentionDays(int value) {
        return copy(enabled, chats, value, storageMib, attachmentMib, attachmentPolicy);
    }

    public ArchiveSettings withStorageMib(int value) {
        return copy(enabled, chats, retentionDays, value, attachmentMib, attachmentPolicy);
    }

    public ArchiveSettings withAttachmentMib(int value) {
        return copy(enabled, chats, retentionDays, storageMib, value, attachmentPolicy);
    }

    public ArchiveSettings withAttachmentPolicy(String value) {
        return copy(enabled, chats, retentionDays, storageMib, attachmentMib, value);
    }

    public long retentionMillis() { return retentionDays * 24L * 60 * 60 * 1000; }
    public long storageBytes() { return storageMib * 1024L * 1024; }
    public long attachmentBytes() { return attachmentMib * 1024L * 1024; }

    public boolean allowsAutomaticAttachment(boolean unmeteredNetwork) {
        return ATTACHMENTS_ANY.equals(attachmentPolicy)
                || ATTACHMENTS_WIFI.equals(attachmentPolicy) && unmeteredNetwork;
    }

    public static int[] retentionOptions() { return RETENTION_DAYS.clone(); }
    public static int[] storageOptions() { return STORAGE_MIB.clone(); }
    public static int[] attachmentOptions() { return ATTACHMENT_MIB.clone(); }

    public boolean archives(long dialogId) {
        return enabled && dialogId != 0 && chats.contains(dialogId);
    }

    public String encodeChats() {
        List<Long> sorted = new ArrayList<>(chats);
        Collections.sort(sorted);
        StringBuilder result = new StringBuilder();
        for (long dialogId : sorted) {
            if (result.length() != 0) result.append(',');
            result.append(dialogId);
        }
        return result.toString();
    }

    public static Set<Long> decodeChats(String encoded) {
        if (encoded == null || encoded.isEmpty()) return Collections.emptySet();
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        for (String value : encoded.split(",")) {
            if (result.size() >= MAX_CHATS) break;
            try {
                long dialogId = Long.parseLong(value);
                if (dialogId != 0) result.add(dialogId);
            } catch (NumberFormatException ignored) { }
        }
        return result;
    }

    private static ArchiveSettings copy(boolean enabled, Set<Long> chats, int retentionDays,
            int storageMib, int attachmentMib, String attachmentPolicy) {
        return new ArchiveSettings(enabled, chats, retentionDays, storageMib, attachmentMib, attachmentPolicy);
    }

    private static int allowed(int value, int[] values, int fallback) {
        for (int allowed : values) if (value == allowed) return value;
        return fallback;
    }
}
