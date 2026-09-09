package org.morok.diagnostics;

/** Immutable, secret-free snapshot used by the local diagnostics screen. */
public final class MorokDiagnosticReport {
    public final String appVersion;
    public final String packageName;
    public final int androidSdk;
    public final String device;
    public final boolean accountAvailable;
    public final String proxyMode;
    public final String proxyStatus;
    public final int trustedProxyCount;
    public final boolean proxyPoolConfigured;
    public final boolean proxyPoolValid;
    public final long proxyLastCheckAt;
    public final boolean archiveEnabled;
    public final int archiveChatCount;
    public final String memoryState;
    public final long memoryUsedBytes;
    public final int memoryCards;
    public final int memoryVersions;
    public final int memorySavedOriginals;
    public final int memoryStorageErrors;
    public final boolean memoryCaptureGap;
    public final boolean roundVideoEnhanced;
    public final String roundVideoProfile;
    public final boolean roundDiagnosticsEnabled;
    public final int roundDiagnosticEvents;

    public MorokDiagnosticReport(String appVersion, String packageName, int androidSdk, String device,
                                 boolean accountAvailable, String proxyMode, String proxyStatus,
                                 int trustedProxyCount, boolean proxyPoolConfigured, boolean proxyPoolValid,
                                 long proxyLastCheckAt, boolean archiveEnabled, int archiveChatCount,
                                 String memoryState, long memoryUsedBytes, int memoryCards, int memoryVersions,
                                 int memorySavedOriginals, int memoryStorageErrors, boolean memoryCaptureGap,
                                 boolean roundVideoEnhanced, String roundVideoProfile,
                                 boolean roundDiagnosticsEnabled, int roundDiagnosticEvents) {
        this.appVersion = line(appVersion);
        this.packageName = line(packageName);
        this.androidSdk = Math.max(0, androidSdk);
        this.device = line(device);
        this.accountAvailable = accountAvailable;
        this.proxyMode = line(proxyMode);
        this.proxyStatus = line(proxyStatus);
        this.trustedProxyCount = Math.max(0, trustedProxyCount);
        this.proxyPoolConfigured = proxyPoolConfigured;
        this.proxyPoolValid = proxyPoolValid;
        this.proxyLastCheckAt = Math.max(0, proxyLastCheckAt);
        this.archiveEnabled = accountAvailable && archiveEnabled;
        this.archiveChatCount = accountAvailable ? Math.max(0, archiveChatCount) : 0;
        this.memoryState = line(memoryState);
        this.memoryUsedBytes = Math.max(0, memoryUsedBytes);
        this.memoryCards = Math.max(0, memoryCards);
        this.memoryVersions = Math.max(0, memoryVersions);
        this.memorySavedOriginals = Math.max(0, memorySavedOriginals);
        this.memoryStorageErrors = Math.max(0, memoryStorageErrors);
        this.memoryCaptureGap = accountAvailable && memoryCaptureGap;
        this.roundVideoEnhanced = roundVideoEnhanced;
        this.roundVideoProfile = line(roundVideoProfile);
        this.roundDiagnosticsEnabled = roundDiagnosticsEnabled;
        this.roundDiagnosticEvents = Math.max(0, roundDiagnosticEvents);
    }

    public String render() {
        return "MOROK diagnostics v1\n"
                + "secrets=false\n"
                + "app=" + appVersion + "\n"
                + "package=" + packageName + "\n"
                + "android_sdk=" + androidSdk + "\n"
                + "device=" + device + "\n"
                + "account_available=" + accountAvailable + "\n"
                + "proxy_mode=" + proxyMode + "\n"
                + "proxy_status=" + proxyStatus + "\n"
                + "proxy_trusted_nodes=" + trustedProxyCount + "\n"
                + "proxy_pool_configured=" + proxyPoolConfigured + "\n"
                + "proxy_pool_valid=" + proxyPoolValid + "\n"
                + "proxy_last_check_at=" + proxyLastCheckAt + "\n"
                + "archive_enabled=" + archiveEnabled + "\n"
                + "archive_chat_count=" + archiveChatCount + "\n"
                + "memory_state=" + memoryState + "\n"
                + "memory_used_bytes=" + memoryUsedBytes + "\n"
                + "memory_cards=" + memoryCards + "\n"
                + "memory_versions=" + memoryVersions + "\n"
                + "memory_saved_originals=" + memorySavedOriginals + "\n"
                + "memory_storage_errors=" + memoryStorageErrors + "\n"
                + "memory_capture_gap=" + memoryCaptureGap + "\n"
                + "round_video_enhanced=" + roundVideoEnhanced + "\n"
                + "round_video_profile=" + roundVideoProfile + "\n"
                + "round_diagnostics_enabled=" + roundDiagnosticsEnabled + "\n"
                + "round_diagnostic_events=" + roundDiagnosticEvents;
    }

    private static String line(String value) {
        if (value == null) return "unknown";
        String cleaned = value.replace('\n', ' ').replace('\r', ' ').replace('=', '_').trim();
        return cleaned.isEmpty() ? "unknown" : cleaned;
    }
}
