package org.morok.proxy;

/** Saturating backoff; jitter only lengthens the delay. */
public final class ProxyRetryPolicy {
    private ProxyRetryPolicy() {}
    public static long delayMillis(int consecutiveFailures, double randomUnit) {
        long base = Math.min(300_000L, 15_000L << Math.min(5, Math.max(0, consecutiveFailures)));
        return base + (long) (base * 0.2 * Math.max(0, Math.min(1, randomUnit)));
    }
}
