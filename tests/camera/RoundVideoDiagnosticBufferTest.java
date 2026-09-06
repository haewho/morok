import org.morok.camera.RoundVideoDiagnosticBuffer;

/** JVM checks for bounded, single-line, metadata-only diagnostic persistence. */
public final class RoundVideoDiagnosticBufferTest {
    private static int checks;
    private static void expect(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        expect(RoundVideoDiagnosticBuffer.count("") == 0, "Empty diagnostics must have no events");
        String log = RoundVideoDiagnosticBuffer.append("", 100, "plan", "output=640 bitrate=2500");
        expect(RoundVideoDiagnosticBuffer.count(log) == 1, "One event must be retained");
        expect(log.startsWith("100\tplan\t"), "Event format must preserve timestamp and stage");
        log = RoundVideoDiagnosticBuffer.append(log, -1, "camera2\nrequest", "fps=30\t30\r\neis=true");
        expect(RoundVideoDiagnosticBuffer.count(log) == 2, "A second valid event must append");
        expect(!log.contains("camera2\n") && !log.contains("30\r") && !log.contains("30\t30"),
                "Fields must not inject event or column delimiters");
        expect(RoundVideoDiagnosticBuffer.render(log).contains("100 plan output=640 bitrate=2500"),
                "Rendered diagnostics must be human readable");
        StringBuilder oversizedDetail = new StringBuilder();
        for (int i = 0; i < 5000; i++) oversizedDetail.append('x');
        log = RoundVideoDiagnosticBuffer.append(log, 101, "encoder", oversizedDetail.toString());
        expect(log.length() < 2000, "A single diagnostic detail must be truncated before persistence");
        for (int i = 0; i < 100; i++) log = RoundVideoDiagnosticBuffer.append(log, 1000 + i, "event", "number=" + i);
        expect(RoundVideoDiagnosticBuffer.count(log) == RoundVideoDiagnosticBuffer.MAX_EVENTS,
                "Diagnostic history must retain only the bounded newest events");
        expect(!log.contains("number=0\n") && log.contains("number=99"),
                "Bounded retention must evict old events before new ones");
        expect(log.length() <= RoundVideoDiagnosticBuffer.MAX_STORED_CHARACTERS,
                "Serialized diagnostics must remain under the total storage ceiling");
        expect(RoundVideoDiagnosticBuffer.count("bad\n12\tstage\tdetail") == 1,
                "Malformed persisted lines must be ignored without hiding valid events");
        expect(RoundVideoDiagnosticBuffer.count(new String(new char[RoundVideoDiagnosticBuffer.MAX_STORED_CHARACTERS + 1])) == 0,
                "Oversized persisted state must fail closed");
        System.out.println("Round-video diagnostics: " + checks + " checks passed");
    }
}
