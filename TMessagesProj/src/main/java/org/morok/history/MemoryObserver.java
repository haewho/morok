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
            for (int i = 0; i < INSTANCES.length; i++) if (INSTANCES[i] == null) INSTANCES[i] = new MemoryObserver(i);
        });
    }

    @Override public void didReceivedNotification(int id, int currentAccount, Object... args) {
        if (!UserConfig.getInstance(account).isClientActivated()) return;
        MorokMemoryStore store = MorokMemoryStore.forAccount(account);
        if (session != store) { session = store; running = false; pending.clear(); queuedRevisions.clear(); }
        if (!store.hasCards()) return;
        if (id == NotificationCenter.replaceMessagesObjects) {
            @SuppressWarnings("unchecked") ArrayList<MessageObject> messages = (ArrayList<MessageObject>) args[1];
            for (MessageObject message : messages) {
                try {
                    // Membership is checked before bounded TL serialization or any file/crypto work.
                    if (!MemoryCapture.isAllowed(message) || !store.tracks(MemoryCapture.keyOf(message))) continue;
                    if (pending.size() >= MAX_PENDING) { store.noteCaptureGap(); break; }
                    MemoryCapture capture = MemoryCapture.take(message);
                    String revision = capture.key.canonical() + ":" + capture.snapshot.fingerprint;
                    if (!queuedRevisions.add(revision)) continue;
                    enqueue((target, done) -> target.save(capture, false, (card, error) -> {
                        queuedRevisions.remove(revision);
                        if (error != null) target.noteCaptureGap(); done.run();
                    }));
                } catch (Exception error) { store.noteCaptureGap(); }
            }
        } else if (id == NotificationCenter.messagesDeleted) {
            if (args.length > 2 && Boolean.TRUE.equals(args[2])) return; // scheduled-message namespace
            if (pending.size() >= MAX_PENDING) { store.noteCaptureGap(); return; }
            @SuppressWarnings("unchecked") ArrayList<Integer> sourceIds = (ArrayList<Integer>) args[0];
            // IDs have a fixed 32-bit representation; a giant event is split by a bounded ceiling.
            if (sourceIds.size() > 10000) { store.noteCaptureGap(); return; }
            ArrayList<Integer> ids = new ArrayList<>(sourceIds);
            long channelId = (Long) args[1];
            enqueue((target, done) -> target.markDeleted(channelId, ids, (changed, error) -> {
                if (error != null) target.noteCaptureGap(); done.run();
            }));
        } else if (id == NotificationCenter.historyCleared) {
            if (pending.size() >= MAX_PENDING) { store.noteCaptureGap(); return; }
            long dialogId = (Long) args[0]; int maxId = (Integer) args[1];
            enqueue((target, done) -> target.markDialogDeleted(dialogId, maxId, (changed, error) -> {
                if (error != null) target.noteCaptureGap(); done.run();
            }));
        }
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
