package org.morok.settings;

/** Immutable account-local experimental privacy policy. All controls default to normal Telegram behavior. */
public final class PrivacySettings {
    public static final PrivacySettings DEFAULT = new PrivacySettings(false, false, false);
    public static final int ACTION_CANCEL = 2;

    public final boolean ghostPreset;
    public final boolean hideTyping;
    public final boolean hideOnline;

    public PrivacySettings(boolean ghostPreset, boolean hideTyping, boolean hideOnline) {
        this.ghostPreset = ghostPreset;
        this.hideTyping = hideTyping;
        this.hideOnline = hideOnline;
    }

    public PrivacySettings withGhostPreset(boolean enabled) {
        return new PrivacySettings(enabled, hideTyping, hideOnline);
    }

    public PrivacySettings withHideTyping(boolean enabled) {
        return new PrivacySettings(ghostPreset, enabled, hideOnline);
    }

    public PrivacySettings withHideOnline(boolean enabled) {
        return new PrivacySettings(ghostPreset, hideTyping, enabled);
    }

    public boolean hidesTyping() {
        return ghostPreset || hideTyping;
    }

    public boolean hidesOnline() {
        return ghostPreset || hideOnline;
    }

    /** Cancellation remains allowed so enabling privacy cannot leave a stale remote typing indicator. */
    public boolean allowsTypingAction(int action) {
        return action == ACTION_CANCEL || !hidesTyping();
    }
}
