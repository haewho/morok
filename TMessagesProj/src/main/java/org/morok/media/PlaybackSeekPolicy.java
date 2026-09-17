package org.morok.media;

/** Pure bounded time jumps for the existing Telegram audio player. */
public final class PlaybackSeekPolicy {
    public static final long BACKWARD_MILLIS = -15_000L;
    public static final long FORWARD_MILLIS = 30_000L;

    private PlaybackSeekPolicy() { }

    public static long targetMillis(long currentMillis, long durationMillis, long deltaMillis) {
        if (currentMillis < 0 || durationMillis <= 0 || deltaMillis == 0) return -1;
        long boundedCurrent = Math.min(currentMillis, durationMillis);
        if (deltaMillis < 0) {
            if (deltaMillis == Long.MIN_VALUE || boundedCurrent < -deltaMillis) return 0;
            return boundedCurrent + deltaMillis;
        }
        if (boundedCurrent > durationMillis - Math.min(deltaMillis, durationMillis)) return durationMillis;
        return Math.min(durationMillis, boundedCurrent + deltaMillis);
    }
}
