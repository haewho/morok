package org.morok.history;

import org.morok.memory.MemoryPolicy;
import org.morok.memory.MorokMemoryStore;
import org.telegram.SQLite.SQLiteCursor;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageCustomParamsHelper;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Locale;

/** Explicit, bounded cache-only import. It never asks Telegram to load history or media. */
public final class LocalHistoryImporter {
    public interface Callback { void done(Result result, String error); }

    public static final class Result {
        public final int scanned;
        public final int eligible;
        public final int retained;

        Result(int scanned, int eligible, int retained) {
            this.scanned = scanned;
            this.eligible = eligible;
            this.retained = retained;
        }
    }

    private LocalHistoryImporter() { }

    public static void importRecent(int account, long dialogId, Callback callback) {
        if (account < 0 || account >= UserConfig.MAX_ACCOUNT_COUNT
                || !UserConfig.getInstance(account).isClientActivated() || dialogId == 0
                || DialogObject.isEncryptedDialog(dialogId)) {
            callback(callback, null, "Local history is unavailable for this chat.");
            return;
        }
        final long userId = UserConfig.getInstance(account).getClientUserId();
        MessagesStorage storage = MessagesStorage.getInstance(account);
        storage.getStorageQueue().postRunnable(() -> {
            SQLiteCursor cursor = null;
            int scanned = 0;
            ArrayList<MemoryCapture> captures = new ArrayList<>();
            try {
                if (userId <= 0 || UserConfig.getInstance(account).getClientUserId() != userId) {
                    throw new IllegalStateException("Account session changed");
                }
                cursor = storage.getDatabase().queryFinalized(String.format(Locale.US,
                        "SELECT data, mid, date, ttl, custom_params FROM messages_v2 "
                                + "WHERE uid = %d AND mid > 0 ORDER BY date DESC, mid DESC LIMIT %d",
                        dialogId, MemoryPolicy.MAX_LOCAL_HISTORY_IMPORT));
                while (cursor.next()) {
                    scanned++;
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data == null) continue;
                    TLRPC.Message message;
                    try {
                        message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                        if (message != null) message.readAttachPath(data, userId);
                    } finally {
                        data.reuse();
                    }
                    if (message == null) continue;
                    message.id = cursor.intValue(1);
                    message.date = cursor.intValue(2);
                    message.dialog_id = dialogId;
                    if (message.ttl == 0) message.ttl = cursor.intValue(3);
                    NativeByteBuffer customParams = cursor.byteBufferValue(4);
                    if (customParams != null) {
                        try { MessageCustomParamsHelper.readLocalParams(message, customParams); }
                        finally { customParams.reuse(); }
                    }
                    MessageObject object = new MessageObject(account, message, false, false);
                    if (MemoryCapture.isAllowed(object)) captures.add(MemoryCapture.take(object));
                }
            } catch (Exception error) {
                callback(callback, null, "Could not read Telegram's local message cache.");
                return;
            } finally {
                if (cursor != null) cursor.dispose();
            }
            if (userId != UserConfig.getInstance(account).getClientUserId()) {
                callback(callback, null, "Account session changed.");
                return;
            }
            final int read = scanned;
            final int eligible = captures.size();
            try {
                MorokMemoryStore.forAccount(account).importLocal(captures, (retained, error) -> {
                    if (error != null) callback.done(null, error);
                    else callback.done(new Result(read, eligible, retained == null ? 0 : retained), null);
                });
            } catch (RuntimeException error) {
                callback(callback, null, "Encrypted Memory storage is unavailable.");
            }
        });
    }

    private static void callback(Callback callback, Result result, String error) {
        AndroidUtilities.runOnUIThread(() -> callback.done(result, error));
    }
}
