package org.morok.settings;

/** Account-local gesture controls. Defaults preserve Telegram's current behavior. */
public final class InteractionSettings {
    public static final InteractionSettings DEFAULT = new InteractionSettings(true);

    public final boolean doubleTapReactionsEnabled;

    public InteractionSettings(boolean doubleTapReactionsEnabled) {
        this.doubleTapReactionsEnabled = doubleTapReactionsEnabled;
    }

    public InteractionSettings withDoubleTapReactionsEnabled(boolean enabled) {
        return new InteractionSettings(enabled);
    }
}
