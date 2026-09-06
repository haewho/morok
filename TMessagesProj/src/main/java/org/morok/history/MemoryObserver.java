package org.morok.history;

import org.morok.memory.MorokMemoryStore;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;

import java.util.ArrayList;

/** Durable bounded observation of server effects for messages already saved as Memory cards. */
public final class MemoryObserver implements NotificationCenter.NotificationCenterDelegate {
    private static final MemoryObserver[] INSTANCES = new MemoryObserver[UserConfig.MAX_ACCOUNT_COUNT];
    private final int account;

    private MemoryObserver(int account) {
        this.account = account;
        NotificationCenter center = NotificationCenter.getInstance(account);
        center.addObserver(this, NotificationCenter.replaceMessagesObjects);
        center.addObserver(this, NotificationCenter.messagesDeleted);
        center.addObserver(this, NotificationCenter.historyCleared);
    }

    /** Install once after normal application/account initialization, on the main thread. */
    public static void start() {
        AndroidUtilities.runOnUIThread(() -> {
            for (int i = 0; i < INSTANCES.length; i++) instance(i);
        });
    }

    private static MemoryObserver instance(int account) {
        if (account < 0 || account >= INSTANCES.length) return null;
        if (INSTANCES[account] == null) INSTANCES[account] = new MemoryObserver(account);
        return INSTANCES[account];
    }

    /** Persist the immutable edit before MessagesController replaces its Telegram database revision. */
    public static void beforeTelegramStorageEdit(MessageObject message) {
        if (message == null) return;
        MorokMemoryStore store = null;
        try {
            store = MorokMemoryStore.forAccount(message.currentAccount);
            if (!store.hasTrackedCardsReady() || !MemoryCapture.isAllowed(message)
                    || !store.tracks(MemoryCapture.keyOf(message))) return;
            if (!store.journalEdit(MemoryCapture.take(message))) store.noteCaptureGap();
        } catch (Exception error) {
            if (store != null) store.noteCaptureGap();
        }
    }

    /** Persist raw live/difference deletion IDs before MessagesController mutates Telegram storage. */
    public static void beforeTelegramStorageDelete(int account, long channelId, ArrayList<Integer> sourceIds) {
        MorokMemoryStore store = activeStore(account);
        if (store == null || sourceIds == null || sourceIds.isEmpty()) return;
        if (!store.journalDelete(channelId, new ArrayList<>(sourceIds))) store.noteCaptureGap();
    }

    /** Persist channel history truncation before the corresponding Telegram database cleanup. */
    public static void beforeTelegramStorageHistoryClear(int account, long dialogId, int maxId) {
        if (maxId <= 0) return;
        MorokMemoryStore store = activeStore(account);
        if (store != null && !store.journalHistoryClear(dialogId, maxId)) store.noteCaptureGap();
    }

    private static MorokMemoryStore activeStore(int account) {
        if (account < 0 || account >= INSTANCES.length || !UserConfig.getInstance(account).isClientActivated()) return null;
        try {
            MorokMemoryStore store = MorokMemoryStore.forAccount(account);
            return store.hasTrackedCardsReady() ? store : null;
        } catch (RuntimeException error) {
            return null;
        }
    }

    @Override public void didReceivedNotification(int id, int currentAccount, Object... args) {
        if (!UserConfig.getInstance(account).isClientActivated()) return;
        MorokMemoryStore store = activeStore(account);
        if (store == null) return;
        if (id == NotificationCenter.replaceMessagesObjects) {
            @SuppressWarnings("unchecked") ArrayList<MessageObject> messages = (ArrayList<MessageObject>) args[1];
            for (MessageObject message : messages) {
                try {
                    if (!MemoryCapture.isAllowed(message) || !store.tracks(MemoryCapture.keyOf(message))) continue;
                    if (!store.journalEdit(MemoryCapture.take(message))) store.noteCaptureGap();
                } catch (Exception error) { store.noteCaptureGap(); }
            }
        } else if (id == NotificationCenter.messagesDeleted) {
            if (args.length > 2 && Boolean.TRUE.equals(args[2])) return;
            @SuppressWarnings("unchecked") ArrayList<Integer> sourceIds = (ArrayList<Integer>) args[0];
            if (!store.journalDelete((Long) args[1], new ArrayList<>(sourceIds))) store.noteCaptureGap();
        } else if (id == NotificationCenter.historyCleared) {
            int maxId = (Integer) args[1];
            if (maxId > 0 && !store.journalHistoryClear((Long) args[0], maxId)) store.noteCaptureGap();
        }
    }
}
