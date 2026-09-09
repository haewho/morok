import org.morok.diagnostics.MorokDiagnosticReport;

public final class MorokDiagnosticReportTest {
    public static void main(String[] args) {
        MorokDiagnosticReport report = new MorokDiagnosticReport(
                "12.10.1\nforged=true", "io.github.haewho.morok.beta", 36,
                "Vendor\rModel", false, "DIRECT", "DIRECT", -4,
                false, false, -8, true, 19, "sign_in", -1,
                -2, -3, -4, -5, true, false, "auto", false, -6);
        String text = report.render();
        check(text.startsWith("MOROK diagnostics v1\nsecrets=false\n"));
        check(text.contains("app=12.10.1 forged_true\n"));
        check(text.contains("device=Vendor Model\n"));
        check(text.contains("account_available=false\n"));
        check(text.contains("archive_enabled=false\narchive_chat_count=0\n"));
        check(text.contains("proxy_trusted_nodes=0\n"));
        check(text.contains("memory_used_bytes=0\n"));
        check(text.contains("memory_capture_gap=false\n"));
        check(text.endsWith("round_diagnostic_events=0"));
        check(!text.contains("\nforged=true\n"));

        MorokDiagnosticReport ready = new MorokDiagnosticReport(
                "12.10.1", "io.github.haewho.morok.beta", 36, "Google sdk",
                true, "AUTO", "CONNECTED", 3, true, true, 123,
                true, 4, "ready", 4096, 5, 7, 2, 1, true,
                true, "high", true, 9);
        String readyText = ready.render();
        check(readyText.contains("archive_enabled=true\narchive_chat_count=4\n"));
        check(readyText.contains("memory_cards=5\nmemory_versions=7\n"));
        check(readyText.contains("round_video_profile=high\n"));
        check(readyText.contains("round_diagnostic_events=9"));
        System.out.println("Diagnostics report: bounded counters and secret-free line format passed");
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError();
    }
}
