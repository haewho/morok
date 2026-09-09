package org.morok.interactions;

import org.morok.settings.MorokSettings;

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
}
