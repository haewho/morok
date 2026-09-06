package org.morok.history;

import org.morok.memory.MorokMemoryStore;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;

/** Bounded observation of already saved cards. This is not a full automatic message archive. */
public final class MemoryObserver implements NotificationCenter.NotificationCenterDelegate {
    private static final MemoryObserver[] INSTANCES = new MemoryObserver[UserConfig.MAX_ACCOUNT_COUNT];
    private static final int MAX_PENDING = 32;
    private interface Job { void run(MorokMemoryStore store, Runnable done); }
    private final int account;
    private final ArrayDeque<Job> pending = new ArrayDeque<>();
    private final HashSet<String> queuedRevisions = new HashSet<>();
    private MorokMemoryStore session;
    private boolean running;

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

    /**
     * Called while a server edit is still owned by MessagesController and before its database write.
     * The bounded immutable snapshot is taken on that processing thread; persistence remains ordered
     * on the Memory queue so Telegram update acknowledgements are never blocked on Android Keystore.
     */
    public static void beforeTelegramStorageEdit(MessageObject message) {
        if (message == null) return;
        final int account = message.currentAccount;
        MorokMemoryStore store = null;
        try {
            store = MorokMemoryStore.forAccount(account);
            if (!store.hasCards() || !MemoryCapture.isAllowed(message)
                    || !store.tracks(MemoryCapture.keyOf(message))) return;
            MemoryCapture capture = MemoryCapture.take(message);
            MorokMemoryStore target = store;
            AndroidUtilities.runOnUIThread(() -> {
                MemoryObserver observer = instance(account);
                if (observer != null) observer.enqueueCapture(target, capture);
            });
        } catch (Exception error) {
            if (store != null) store.noteCaptureGap();
        }
    }

    /** Queue raw difference/live-update deletion before MessagesController mutates Telegram storage. */
    public static void beforeTelegramStorageDelete(int account, long channelId, ArrayList<Integer> sourceIds) {
        MorokMemoryStore store = activeStore(account);
        if (store == null || sourceIds == null || sourceIds.isEmpty()) return;
        if (sourceIds.size() > 10000) { store.noteCaptureGap(); return; }
        ArrayList<Integer> ids = new ArrayList<>(sourceIds);
        AndroidUtilities.runOnUIThread(() -> {
            MemoryObserver observer = instance(account);
            if (observer != null) observer.enqueueDelete(store, channelId, ids);
        });
    }

    /** Queue server history truncation before the corresponding Telegram database cleanup. */
    public static void beforeTelegramStorageHistoryClear(int account, long dialogId, int maxId) {
        MorokMemoryStore store = activeStore(account);
        if (store == null) return;
        AndroidUtilities.runOnUIThread(() -> {
            MemoryObserver observer = instance(account);
            if (observer != null) observer.enqueueHistoryClear(store, dialogId, maxId);
        });
    }

    private static MorokMemoryStore activeStore(int account) {
        if (account < 0 || account >= INSTANCES.length || !UserConfig.getInstance(account).isClientActivated()) return null;
        try {
            MorokMemoryStore store = MorokMemoryStore.forAccount(account);
            return store.hasCards() ? store : null;
        } catch (RuntimeException error) {
            return null;
        }
    }

    @Override public void didReceivedNotification(int id, int currentAccount, Object... args) {
        if (!UserConfig.getInstance(account).isClientActivated()) return;
        MorokMemoryStore store = MorokMemoryStore.forAccount(account);
        bind(store);
        if (!store.hasCards()) return;
        if (id == NotificationCenter.replaceMessagesObjects) {
            @SuppressWarnings("unchecked") ArrayList<MessageObject> messages = (ArrayList<MessageObject>) args[1];
            for (MessageObject message : messages) {
                try {
                    // Membership is checked before bounded TL serialization or any file/crypto work.
                    if (!MemoryCapture.isAllowed(message) || !store.tracks(MemoryCapture.keyOf(message))) continue;
                    MemoryCapture capture = MemoryCapture.take(message);
                    enqueueCapture(store, capture);
                } catch (Exception error) { store.noteCaptureGap(); }
            }
        } else if (id == NotificationCenter.messagesDeleted) {
            if (args.length > 2 && Boolean.TRUE.equals(args[2])) return; // scheduled-message namespace
            @SuppressWarnings("unchecked") ArrayList<Integer> sourceIds = (ArrayList<Integer>) args[0];
            // IDs have a fixed 32-bit representation; a giant event is split by a bounded ceiling.
            if (sourceIds.size() > 10000) { store.noteCaptureGap(); return; }
            ArrayList<Integer> ids = new ArrayList<>(sourceIds);
            enqueueDelete(store, (Long) args[1], ids);
        } else if (id == NotificationCenter.historyCleared) {
            long dialogId = (Long) args[0]; int maxId = (Integer) args[1];
            enqueueHistoryClear(store, dialogId, maxId);
        }
    }

    private void bind(MorokMemoryStore store) {
        if (session != store) { session = store; running = false; pending.clear(); queuedRevisions.clear(); }
    }

    private void enqueueCapture(MorokMemoryStore store, MemoryCapture capture) {
        bind(store);
        if (!store.isActive() || !store.tracks(capture.key)) return;
        String revision = capture.key.canonical() + ":" + capture.snapshot.fingerprint;
        if (queuedRevisions.contains(revision)) return;
        if (pending.size() >= MAX_PENDING) { store.noteCaptureGap(); return; }
        queuedRevisions.add(revision);
        enqueue((target, done) -> target.save(capture, false, (card, error) -> {
            queuedRevisions.remove(revision);
            if (error != null) target.noteCaptureGap();
            done.run();
        }));
    }

    private void enqueueDelete(MorokMemoryStore store, long channelId, ArrayList<Integer> ids) {
        bind(store);
        if (!store.hasCards()) return;
        if (pending.size() >= MAX_PENDING) { store.noteCaptureGap(); return; }
        enqueue((target, done) -> target.markDeleted(channelId, ids, (changed, error) -> {
            if (error != null) target.noteCaptureGap();
            done.run();
        }));
    }

    private void enqueueHistoryClear(MorokMemoryStore store, long dialogId, int maxId) {
        bind(store);
        if (!store.hasCards()) return;
        if (pending.size() >= MAX_PENDING) { store.noteCaptureGap(); return; }
        enqueue((target, done) -> target.markDialogDeleted(dialogId, maxId, (changed, error) -> {
            if (error != null) target.noteCaptureGap();
            done.run();
        }));
    }

    private void enqueue(Job job) { pending.add(job); drain(); }
    private void drain() {
        if (running || pending.isEmpty() || session == null || !session.isActive()) return;
        running = true; MorokMemoryStore current = session;
        pending.remove().run(current, () -> {
            if (session == current) { running = false; drain(); }
        });
    }
}
