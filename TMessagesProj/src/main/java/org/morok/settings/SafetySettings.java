package org.morok.settings;

/** Device-local protection controls. Defaults preserve upstream Telegram behavior. */
public final class SafetySettings {
    public static final SafetySettings DEFAULT = new SafetySettings(false, false, false);

    public final boolean protectScreen;
    public final boolean confirmOutgoingCalls;
    public final boolean confirmRoundVideos;

    public SafetySettings(boolean protectScreen, boolean confirmOutgoingCalls, boolean confirmRoundVideos) {
        this.protectScreen = protectScreen;
        this.confirmOutgoingCalls = confirmOutgoingCalls;
        this.confirmRoundVideos = confirmRoundVideos;
    }

    public SafetySettings withProtectScreen(boolean enabled) {
        return new SafetySettings(enabled, confirmOutgoingCalls, confirmRoundVideos);
    }

    public SafetySettings withConfirmOutgoingCalls(boolean enabled) {
        return new SafetySettings(protectScreen, enabled, confirmRoundVideos);
    }

    public SafetySettings withConfirmRoundVideos(boolean enabled) {
        return new SafetySettings(protectScreen, confirmOutgoingCalls, enabled);
    }
}
