package org.morok.settings;

/** Immutable account-local experimental privacy policy. All controls default to normal Telegram behavior. */
public final class PrivacySettings {
    public static final PrivacySettings DEFAULT = new PrivacySettings(false, false, false, false, false);
    public static final int ACTION_CANCEL = 2;

    public final boolean ghostPreset;
    public final boolean hideTyping;
    public final boolean hideOnline;
    public final boolean hideContentRead;
    public final boolean hideRead;

    public PrivacySettings(boolean ghostPreset, boolean hideTyping, boolean hideOnline, boolean hideContentRead, boolean hideRead) {
        this.ghostPreset = ghostPreset;
        this.hideTyping = hideTyping;
        this.hideOnline = hideOnline;
        this.hideContentRead = hideContentRead;
        this.hideRead = hideRead;
    }

    public PrivacySettings withGhostPreset(boolean enabled) {
        return new PrivacySettings(enabled, hideTyping, hideOnline, hideContentRead, hideRead);
    }

    public PrivacySettings withHideTyping(boolean enabled) {
        return new PrivacySettings(ghostPreset, enabled, hideOnline, hideContentRead, hideRead);
    }

    public PrivacySettings withHideOnline(boolean enabled) {
        return new PrivacySettings(ghostPreset, hideTyping, enabled, hideContentRead, hideRead);
    }

    public PrivacySettings withHideContentRead(boolean enabled) {
        return new PrivacySettings(ghostPreset, hideTyping, hideOnline, enabled, hideRead);
    }

    public PrivacySettings withHideRead(boolean enabled) {
        return new PrivacySettings(ghostPreset, hideTyping, hideOnline, hideContentRead, enabled);
    }

    public boolean hidesTyping() {
        return ghostPreset || hideTyping;
    }

    public boolean hidesOnline() {
        return ghostPreset || hideOnline;
    }

    public boolean hidesContentRead() {
        return ghostPreset || hideContentRead;
    }

    public boolean hidesRead() {
        return ghostPreset || hideRead;
    }

    /** Cancellation remains allowed so enabling privacy cannot leave a stale remote typing indicator. */
    public boolean allowsTypingAction(int action) {
        return action == ACTION_CANCEL || !hidesTyping();
    }
}
