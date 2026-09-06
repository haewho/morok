package org.morok.history;

import androidx.collection.LongSparseArray;

import org.morok.memory.MemoryJournalPolicy;
import org.morok.memory.MorokMemoryStore;
import org.morok.settings.ArchiveSettings;
import org.morok.settings.MorokSettings;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;

import java.util.ArrayList;

/** Durable bounded observation of allowlisted new messages and effects on retained snapshots. */
public final class MemoryObserver implements NotificationCenter.NotificationCenterDelegate {
    private static final MemoryObserver[] INSTANCES = new MemoryObserver[UserConfig.MAX_ACCOUNT_COUNT];
    private final int account;

    private MemoryObserver(int account) {
        this.account = account;
        NotificationCenter center = NotificationCenter.getInstance(account);
        center.addObserver(this, NotificationCenter.didReceiveNewMessages);
        center.addObserver(this, NotificationCenter.replaceMessagesObjects);
        center.addObserver(this, NotificationCenter.messagesDeleted);
        center.addObserver(this, NotificationCenter.historyCleared);
    }

    /** Persist allowlisted new messages as one bounded journal transaction before Telegram storage. */
    public static void beforeTelegramStorageNew(int account, LongSparseArray<ArrayList<MessageObject>> messages) {
        if (messages == null || messages.size() == 0 || !UserConfig.getInstance(account).isClientActivated()) return;
        try {
            ArchiveSettings settings = MorokSettings.archive(account);
            if (!settings.enabled || settings.chats.isEmpty()) return;
            ArrayList<MemoryCapture> captures = new ArrayList<>();
            boolean overflow = false;
            for (int i = 0; i < messages.size(); i++) {
                long dialogId = messages.keyAt(i);
                if (!settings.archives(dialogId)) continue;
                ArrayList<MessageObject> values = messages.valueAt(i);
                if (values == null) continue;
                for (MessageObject message : values) {
                    if (!MemoryCapture.isAllowed(message)) continue;
                    if (captures.size() >= MemoryJournalPolicy.MAX_EVENTS) { overflow = true; break; }
                    captures.add(MemoryCapture.take(message));
                }
            }
            if (captures.isEmpty()) return;
            MorokMemoryStore store = MorokMemoryStore.forAccount(account);
            if (!store.journalNew(captures) || overflow) store.noteCaptureGap();
        } catch (Exception error) {
            try { MorokMemoryStore.forAccount(account).noteCaptureGap(); } catch (RuntimeException ignored) { }
        }
    }

    /** NotificationCenter fallback for paths that bypass the main update-array hook. */
    private static void afterTelegramStorageNew(int account, long dialogId, ArrayList<MessageObject> messages) {
        if (messages == null || messages.isEmpty()) return;
        LongSparseArray<ArrayList<MessageObject>> batch = new LongSparseArray<>();
        batch.put(dialogId, messages);
        beforeTelegramStorageNew(account, batch);
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
        if (id == NotificationCenter.didReceiveNewMessages) {
            if (args.length < 2 || (args.length > 2 && Boolean.TRUE.equals(args[2]))
                    || (args.length > 3 && args[3] instanceof Integer && (Integer) args[3] != 0)) return;
            @SuppressWarnings("unchecked") ArrayList<MessageObject> newMessages = (ArrayList<MessageObject>) args[1];
            afterTelegramStorageNew(account, (Long) args[0], newMessages);
            return;
        }
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
