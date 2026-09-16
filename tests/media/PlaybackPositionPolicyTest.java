import org.morok.media.PlaybackPositionPolicy;

public final class PlaybackPositionPolicyTest {
    private static int checks;

    private static void expect(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        String first = PlaybackPositionPolicy.storageKey(101, "document:44");
        expect(first.equals(PlaybackPositionPolicy.storageKey(101, "document:44"))
                        && first.startsWith("p.") && first.length() == 66,
                "Storage identity is deterministic fixed-length SHA-256");
        expect(!first.equals(PlaybackPositionPolicy.storageKey(102, "document:44"))
                        && !first.equals(PlaybackPositionPolicy.storageKey(101, "document:45"))
                        && !first.contains("document"),
                "Stable user and media identities are isolated and hidden from keys");
        expect(PlaybackPositionPolicy.MAX_ENTRIES == 256
                        && PlaybackPositionPolicy.MAX_AGE_MILLIS == 180L * 24 * 60 * 60 * 1000,
                "Position storage is bounded by count and age");
        expect(PlaybackPositionPolicy.isRestorable(0.5f)
                        && !PlaybackPositionPolicy.isRestorable(0.01f)
                        && !PlaybackPositionPolicy.isRestorable(0.98f)
                        && !PlaybackPositionPolicy.isRestorable(Float.NaN),
                "Only meaningful finite mid-track positions are retained");
        long now = 20_000_000_000L;
        String encoded = PlaybackPositionPolicy.encode(now - 1000, 0.42f);
        PlaybackPositionPolicy.Entry decoded = PlaybackPositionPolicy.decode(encoded, now);
        expect(decoded != null && decoded.updatedAt == now - 1000
                        && Math.abs(decoded.progress - 0.42f) < 0.0001f,
                "A fresh position round-trips exactly");
        expect(PlaybackPositionPolicy.decode(
                        PlaybackPositionPolicy.encode(now - PlaybackPositionPolicy.MAX_AGE_MILLIS - 1, 0.5f), now) == null,
                "Expired positions are rejected");
        expect(PlaybackPositionPolicy.decode("broken", now) == null
                        && PlaybackPositionPolicy.decode((now + PlaybackPositionPolicy.MAX_FUTURE_SKEW_MILLIS + 1) + ":0.5", now) == null
                        && PlaybackPositionPolicy.decode(now + ":NaN", now) == null,
                "Malformed, future and non-finite values fail closed");
        boolean rejected = false;
        try { PlaybackPositionPolicy.storageKey(0, "document:44"); }
        catch (IllegalArgumentException expected) { rejected = true; }
        expect(rejected, "Unauthenticated user identity is rejected");
        System.out.println("Playback position policy: " + checks + " checks passed");
    }
}
