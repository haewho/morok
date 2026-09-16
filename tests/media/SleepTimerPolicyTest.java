import org.morok.media.SleepTimerPolicy;

public final class SleepTimerPolicyTest {
    private static int checks;

    private static void expect(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        expect(SleepTimerPolicy.PRESET_MINUTES.length == 4
                        && SleepTimerPolicy.isPreset(15) && SleepTimerPolicy.isPreset(30)
                        && SleepTimerPolicy.isPreset(45) && SleepTimerPolicy.isPreset(60),
                "Sleep timer exposes the reviewed presets");
        expect(!SleepTimerPolicy.isPreset(0) && !SleepTimerPolicy.isPreset(14)
                        && !SleepTimerPolicy.isPreset(61),
                "Arbitrary or unbounded sleep timer durations are rejected");
        expect(SleepTimerPolicy.durationMillis(15) == 900_000L
                        && SleepTimerPolicy.durationMillis(60) == 3_600_000L,
                "Preset minutes convert to exact millisecond delays");
        long deadline = SleepTimerPolicy.deadline(1_000L, 15);
        expect(deadline == 901_000L && SleepTimerPolicy.remainingMillis(deadline, 1_000L) == 900_000L,
                "Sleep timer uses a monotonic deadline");
        expect(SleepTimerPolicy.remainingMinutes(deadline, 1_001L) == 15
                        && SleepTimerPolicy.remainingMinutes(deadline, 61_001L) == 14,
                "Displayed remaining minutes round up without expiring early");
        expect(SleepTimerPolicy.remainingMillis(deadline, deadline) == 0
                        && SleepTimerPolicy.remainingMinutes(deadline, deadline + 1) == 0,
                "Deadline and overdue timers report expired");
        boolean rejected = false;
        try { SleepTimerPolicy.durationMillis(5); } catch (IllegalArgumentException expected) { rejected = true; }
        expect(rejected, "Unsupported timer duration fails closed");
        System.out.println("Sleep timer policy: " + checks + " checks passed");
    }
}
