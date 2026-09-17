package org.morok.settings;

/** Account-local gesture controls. Defaults preserve Telegram's current behavior. */
public final class InteractionSettings {
    public static final String MESSAGE_SWIPE_REPLY = "reply";
    public static final String MESSAGE_SWIPE_REMEMBER = "remember";
    public static final String MESSAGE_SWIPE_DISABLED = "disabled";
    public static final InteractionSettings DEFAULT = new InteractionSettings(true, MESSAGE_SWIPE_REPLY);

    public final boolean doubleTapReactionsEnabled;
    public final String messageSwipeAction;

    public InteractionSettings(boolean doubleTapReactionsEnabled, String messageSwipeAction) {
        this.doubleTapReactionsEnabled = doubleTapReactionsEnabled;
        this.messageSwipeAction = normalizeMessageSwipeAction(messageSwipeAction);
    }

    public InteractionSettings withDoubleTapReactionsEnabled(boolean enabled) {
        return new InteractionSettings(enabled, messageSwipeAction);
    }

    public InteractionSettings withMessageSwipeAction(String action) {
        return new InteractionSettings(doubleTapReactionsEnabled, action);
    }

    public static String normalizeMessageSwipeAction(String action) {
        if (MESSAGE_SWIPE_REMEMBER.equals(action) || MESSAGE_SWIPE_DISABLED.equals(action)) {
            return action;
        }
        return MESSAGE_SWIPE_REPLY;
    }
}
