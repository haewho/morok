package org.morok.privacy;

import org.morok.settings.MorokSettings;

/** Semantic privacy gates. A failure always preserves normal Telegram behavior. */
public final class MorokPrivacy {
    private MorokPrivacy() {}

    public static boolean allowsTypingAction(int account, long dialogId, int action) {
        try {
            return MorokSettings.privacy(account).allowsTypingAction(dialogId, action);
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

    public static boolean allowsContentRead(int account, long dialogId) {
        try {
            return MorokSettings.privacy(account).allowsContentRead(dialogId);
        } catch (RuntimeException unavailableAccountOrSettings) {
            return true;
        }
    }

    public static boolean allowsReadReceipt(int account, long dialogId) {
        try {
            return MorokSettings.privacy(account).allowsReadReceipt(dialogId);
        } catch (RuntimeException unavailableAccountOrSettings) {
            return true;
        }
    }

    public static boolean allowsStoryViewReceipt(int account, long dialogId) {
        try {
            return MorokSettings.privacy(account).allowsStoryViewReceipt(dialogId);
        } catch (RuntimeException unavailableAccountOrSettings) {
            return true;
        }
    }

    /** Opt-in action: a settings failure must not unexpectedly disclose a read cursor. */
    public static boolean shouldMarkReadOnReply(int account, long dialogId) {
        try {
            return MorokSettings.privacy(account).shouldMarkReadOnReply(dialogId);
        } catch (RuntimeException unavailableAccountOrSettings) {
            return false;
        }
    }

    /** Returns zero unless the explicit delayed-send option is active for this dialog. */
    public static int ghostSendDelaySeconds(int account, long dialogId) {
        try {
            return MorokSettings.privacy(account).ghostSendDelaySeconds(dialogId);
        } catch (RuntimeException unavailableAccountOrSettings) {
            return 0;
        }
    }
}
