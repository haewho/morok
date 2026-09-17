package org.morok.interactions;

import org.morok.settings.MorokSettings;
import org.morok.settings.InteractionSettings;

/** Read-only gesture policy boundary. Failures preserve upstream Telegram behavior. */
public final class MorokInteractionGate {
    private MorokInteractionGate() {}

    public static boolean allowsDoubleTapReaction(int accountSlot) {
        try {
            return MorokSettings.interactions(accountSlot).doubleTapReactionsEnabled;
        } catch (RuntimeException unavailableSettings) {
            return true;
        }
    }

    public static String messageSwipeAction(int accountSlot) {
        try {
            return MorokSettings.interactions(accountSlot).messageSwipeAction;
        } catch (RuntimeException unavailableSettings) {
            return InteractionSettings.MESSAGE_SWIPE_REPLY;
        }
    }
}
