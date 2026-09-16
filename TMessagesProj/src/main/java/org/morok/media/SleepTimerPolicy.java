package org.morok.media;

/** Pure monotonic-time boundaries for MOROK's music sleep timer. */
public final class SleepTimerPolicy {
    public static final int[] PRESET_MINUTES = {15, 30, 45, 60};

    private SleepTimerPolicy() { }

    public static boolean isPreset(int minutes) {
        for (int preset : PRESET_MINUTES) if (preset == minutes) return true;
        return false;
    }

    public static long durationMillis(int minutes) {
        if (!isPreset(minutes)) throw new IllegalArgumentException("Unsupported sleep timer duration");
        return minutes * 60_000L;
    }

    public static long deadline(long elapsedNow, int minutes) {
        if (elapsedNow < 0) throw new IllegalArgumentException("Invalid monotonic time");
        return elapsedNow + durationMillis(minutes);
    }

    public static long remainingMillis(long deadline, long elapsedNow) {
        if (deadline <= 0 || elapsedNow < 0 || elapsedNow >= deadline) return 0;
        return deadline - elapsedNow;
    }

    public static int remainingMinutes(long deadline, long elapsedNow) {
        long remaining = remainingMillis(deadline, elapsedNow);
        return remaining == 0 ? 0 : (int) Math.min(60, (remaining + 59_999L) / 60_000L);
    }
}
