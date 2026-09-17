import org.morok.media.PlaybackSeekPolicy;

public final class PlaybackSeekPolicyTest {
    private static int checks;

    private static void expect(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        expect(PlaybackSeekPolicy.BACKWARD_MILLIS == -15_000L
                        && PlaybackSeekPolicy.FORWARD_MILLIS == 30_000L,
                "Reviewed time jumps remain explicit constants");
        expect(PlaybackSeekPolicy.targetMillis(60_000, 180_000,
                        PlaybackSeekPolicy.BACKWARD_MILLIS) == 45_000,
                "Backward jump subtracts fifteen seconds");
        expect(PlaybackSeekPolicy.targetMillis(60_000, 180_000,
                        PlaybackSeekPolicy.FORWARD_MILLIS) == 90_000,
                "Forward jump adds thirty seconds");
        expect(PlaybackSeekPolicy.targetMillis(8_000, 180_000,
                        PlaybackSeekPolicy.BACKWARD_MILLIS) == 0,
                "Backward jump clamps to track start");
        expect(PlaybackSeekPolicy.targetMillis(170_000, 180_000,
                        PlaybackSeekPolicy.FORWARD_MILLIS) == 180_000,
                "Forward jump clamps to track end");
        expect(PlaybackSeekPolicy.targetMillis(200_000, 180_000, -15_000) == 165_000,
                "Stale current position is bounded by current duration");
        expect(PlaybackSeekPolicy.targetMillis(-1, 180_000, 30_000) == -1
                        && PlaybackSeekPolicy.targetMillis(10_000, 0, 30_000) == -1
                        && PlaybackSeekPolicy.targetMillis(10_000, 180_000, 0) == -1,
                "Unavailable player state and zero jumps fail closed");
        expect(PlaybackSeekPolicy.targetMillis(Long.MAX_VALUE, 180_000, Long.MAX_VALUE) == 180_000
                        && PlaybackSeekPolicy.targetMillis(1, 180_000, Long.MIN_VALUE) == 0,
                "Extreme deltas clamp without overflow");
        System.out.println("Playback seek policy: " + checks + " checks passed");
    }
}
