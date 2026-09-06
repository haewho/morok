package org.morok.privacy;

import org.morok.settings.MorokSettings;

/** Semantic privacy gates. A failure always preserves normal Telegram behavior. */
public final class MorokPrivacy {
    private MorokPrivacy() {}

    public static boolean allowsTypingAction(int account, int action) {
        try {
            return MorokSettings.privacy(account).allowsTypingAction(action);
        } catch (RuntimeException unavailableAccountOrSettings) {
            return true;
        }
    }

    public static boolean allowsOnlineStatus(int account) {
        try {
            return !MorokSettings.privacy(account).hidesOnline();
        } catch (RuntimeException unavailableAccountOrSettings) {
            return true;
        }
    }

    public static boolean allowsContentRead(int account) {
        try {
            return !MorokSettings.privacy(account).hidesContentRead();
        } catch (RuntimeException unavailableAccountOrSettings) {
            return true;
        }
    }

    public static boolean allowsReadReceipt(int account) {
        try {
            return !MorokSettings.privacy(account).hidesRead();
        } catch (RuntimeException unavailableAccountOrSettings) {
            return true;
        }
    }
}
