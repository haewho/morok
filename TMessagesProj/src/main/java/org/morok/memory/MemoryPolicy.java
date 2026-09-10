package org.morok.memory;

/** Bounded first-beta defaults. No automatic eviction of manually saved cards. */
public final class MemoryPolicy {
    public static final int MAX_CARDS = 500;
    public static final int MAX_AUTOMATIC_CARDS = 400;
    public static final int MAX_LOCAL_HISTORY_IMPORT = 100;
    public static final long AUTOMATIC_RETENTION_MILLIS = 90L * 24 * 60 * 60 * 1000;
    public static final int MAX_VERSIONS = 20;
    public static final int MAX_TOMBSTONES = 20000;
    public static final int MAX_MESSAGE_BYTES = 256 * 1024;
    public static final int MAX_DATABASE_BYTES = 16 * 1024 * 1024;
    public static final long MAX_ATTACHMENT_BYTES = 8 * 1024 * 1024;
    public static final long MAX_ACCOUNT_BYTES = 256 * 1024 * 1024;
    public static final long MIN_FREE_BYTES = 32 * 1024 * 1024;

    private MemoryPolicy() { }

    public static boolean canCopy(long sourceBytes, long usedBytes, long freeBytes) {
        return canCopy(sourceBytes, usedBytes, freeBytes, MAX_ATTACHMENT_BYTES, MAX_ACCOUNT_BYTES);
    }

    public static boolean canCopy(long sourceBytes, long usedBytes, long freeBytes,
            long attachmentLimitBytes, long accountLimitBytes) {
        return attachmentLimitBytes > 0 && attachmentLimitBytes <= MAX_ATTACHMENT_BYTES
                && accountLimitBytes > 0 && accountLimitBytes <= MAX_ACCOUNT_BYTES
                && sourceBytes > 0 && sourceBytes <= attachmentLimitBytes
                && usedBytes >= 0 && usedBytes + sourceBytes + 64 <= accountLimitBytes
                && freeBytes - sourceBytes - 64 >= MIN_FREE_BYTES;
    }

    public static boolean automaticExpired(long createdAt, long now) {
        return automaticExpired(createdAt, now, AUTOMATIC_RETENTION_MILLIS);
    }

    public static boolean automaticExpired(long createdAt, long now, long retentionMillis) {
        return retentionMillis > 0 && retentionMillis <= 365L * 24 * 60 * 60 * 1000
                && createdAt >= 0 && now >= createdAt && now - createdAt > retentionMillis;
    }

    /** Older reordered edits must not replace the newest received revision. */
    public static boolean becomesLatest(int incomingEditTime, int existingEditTime) {
        return incomingEditTime >= existingEditTime;
    }
}
