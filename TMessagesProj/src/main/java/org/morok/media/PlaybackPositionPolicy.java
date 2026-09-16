package org.morok.media;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Pure limits and serialization for account-scoped playback resume positions. */
public final class PlaybackPositionPolicy {
    public static final int MAX_ENTRIES = 256;
    public static final long MAX_AGE_MILLIS = 180L * 24 * 60 * 60 * 1000;
    public static final long MAX_FUTURE_SKEW_MILLIS = 24L * 60 * 60 * 1000;
    public static final float MIN_PROGRESS = 0.01f;
    public static final float MAX_PROGRESS = 0.98f;

    public static final class Entry {
        public final long updatedAt;
        public final float progress;

        Entry(long updatedAt, float progress) {
            this.updatedAt = updatedAt;
            this.progress = progress;
        }
    }

    private PlaybackPositionPolicy() { }

    public static boolean isRestorable(float progress) {
        return Float.isFinite(progress) && progress > MIN_PROGRESS && progress < MAX_PROGRESS;
    }

    public static String storageKey(long userId, String mediaIdentity) {
        if (userId <= 0 || mediaIdentity == null || mediaIdentity.isEmpty() || mediaIdentity.length() > 512) {
            throw new IllegalArgumentException("Invalid playback position identity");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    ("morok-playback-position/v1/" + userId + "/" + mediaIdentity)
                            .getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder("p.");
            for (byte part : digest) value.append(String.format("%02x", part & 0xff));
            return value.toString();
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    public static String encode(long updatedAt, float progress) {
        if (updatedAt <= 0 || !isRestorable(progress)) {
            throw new IllegalArgumentException("Invalid playback position");
        }
        return updatedAt + ":" + Float.toString(progress);
    }

    public static Entry decode(String value, long now) {
        if (value == null || now <= 0) return null;
        int separator = value.indexOf(':');
        if (separator <= 0 || separator != value.lastIndexOf(':')) return null;
        try {
            long updatedAt = Long.parseLong(value.substring(0, separator));
            float progress = Float.parseFloat(value.substring(separator + 1));
            if (!isRestorable(progress) || updatedAt <= 0 || updatedAt > now + MAX_FUTURE_SKEW_MILLIS
                    || now - updatedAt > MAX_AGE_MILLIS) return null;
            return new Entry(updatedAt, progress);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
