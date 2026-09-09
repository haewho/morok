package org.morok.settings;

/** Device-local protection controls. Defaults preserve upstream Telegram behavior. */
public final class SafetySettings {
    public static final SafetySettings DEFAULT = new SafetySettings(false, false);

    public final boolean protectScreen;
    public final boolean confirmOutgoingCalls;

    public SafetySettings(boolean protectScreen, boolean confirmOutgoingCalls) {
        this.protectScreen = protectScreen;
        this.confirmOutgoingCalls = confirmOutgoingCalls;
    }

    public SafetySettings withProtectScreen(boolean enabled) {
        return new SafetySettings(enabled, confirmOutgoingCalls);
    }

    public SafetySettings withConfirmOutgoingCalls(boolean enabled) {
        return new SafetySettings(protectScreen, enabled);
    }
}
