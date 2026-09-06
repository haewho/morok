package org.morok.history;

import android.os.Build;
import android.util.Base64;

import org.morok.memory.MemoryCard;
import org.morok.memory.MemoryKey;
import org.morok.memory.MemoryPolicy;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;

import java.security.MessageDigest;

/** Takes a bounded immutable snapshot at the receiving/UI boundary. Never downloads media. */
public final class MemoryCapture {
    public final MemoryKey key;
    public final MemoryCard.Snapshot snapshot;
    public final TLRPC.Message message;
    public final String source;
    public final String sender;

    private MemoryCapture(MemoryKey key, MemoryCard.Snapshot snapshot, TLRPC.Message message, String source, String sender) {
        this.key = key; this.snapshot = snapshot; this.message = message; this.source = source; this.sender = sender;
    }

    public static boolean isAllowed(MessageObject object) {
        if (Build.VERSION.SDK_INT < 23 || object == null || object.messageOwner == null) return false;
        TLRPC.Message message = object.messageOwner;
        long userId = UserConfig.getInstance(object.currentAccount).getClientUserId();
        if (userId <= 0 || message.id <= 0 || message.peer_id == null || object.getDialogId() == 0
                || DialogObject.isEncryptedDialog(object.getDialogId()) || message instanceof TLRPC.TL_message_secret
                || message.noforwards || message.ttl != 0 || message.ttl_period != 0 || message.destroyTime != 0
                || message.action != null || object.isSponsored() || object.isSecretMedia()
                || object.isEphemeralAndNotWelcome() || object.isPaidSuggestedPostProtected()
                || object.hasRevealedExtendedMedia() || object.needDrawBluredPreview()
                || MessagesController.getInstance(object.currentAccount).isPeerNoForwards(object.getDialogId())) return false;
        if (message.restriction_reason != null && !message.restriction_reason.isEmpty()) return false;
        TLRPC.MessageMedia media = message.media;
        if (media != null && media.ttl_seconds != 0) return false;
        // First slice supports text, regular photos and documents; other dynamic media remains in Telegram.
        return (media == null || media instanceof TLRPC.TL_messageMediaEmpty
                || media instanceof TLRPC.TL_messageMediaWebPage || media instanceof TLRPC.TL_messageMediaPhoto
                || media instanceof TLRPC.TL_messageMediaDocument) && object.getTopicId() >= 0;
    }

    public static MemoryKey keyOf(MessageObject object) {
        TLRPC.Message message = object.messageOwner;
        String kind = message.peer_id.channel_id != 0 ? "channel" : message.peer_id.chat_id != 0 ? "group" : "user";
        return new MemoryKey(UserConfig.getInstance(object.currentAccount).getClientUserId(), kind,
                Math.abs(object.getDialogId()), object.getId(), object.getTopicId());
    }

    public static MemoryCapture take(MessageObject object) throws Exception {
        if (!isAllowed(object)) throw new IllegalArgumentException("Content cannot be saved");
        TLRPC.Message original = object.messageOwner;
        if (original.getObjectSize() > MemoryPolicy.MAX_MESSAGE_BYTES) throw new IllegalArgumentException("Message too large");
        SerializedData serialized = new SerializedData(original.getObjectSize());
        original.serializeToStream(serialized);
        byte[] raw = serialized.toByteArray();
        serialized.cleanup();
        SerializedData input = new SerializedData(raw);
        TLRPC.Message copy = TLRPC.Message.TLdeserialize(input, input.readInt32(true), true);
        input.cleanup();
        // attachPath is local and not part of the server TL serialization.
        copy.attachPath = original.attachPath;
        copy.dialog_id = object.getDialogId();
        MemoryKey key = keyOf(object);
        MemoryCard.Snapshot snapshot = new MemoryCard.Snapshot();
        snapshot.text = original.message == null ? "" : original.message;
        snapshot.serializedMessage = Base64.encodeToString(raw, Base64.NO_WRAP);
        snapshot.receivedAt = System.currentTimeMillis(); snapshot.editedAt = original.edit_date;
        SerializedData content = new SerializedData();
        content.writeString(snapshot.text);
        content.writeInt32(original.edit_date);
        if (original.entities != null) for (TLRPC.MessageEntity entity : original.entities) entity.serializeToStream(content);
        if (original.media != null) original.media.serializeToStream(content);
        snapshot.fingerprint = hex(MessageDigest.getInstance("SHA-256").digest(content.toByteArray()));
        content.cleanup();
        return new MemoryCapture(key, snapshot, copy, peerName(object.currentAccount, object.getDialogId()),
                peerName(object.currentAccount, object.getFromChatId()));
    }

    private static String peerName(int account, long dialogId) {
        if (dialogId > 0) {
            TLRPC.User user = MessagesController.getInstance(account).getUser(dialogId);
            if (user != null) return ContactsController.formatName(user.first_name, user.last_name);
        } else {
            TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-dialogId);
            if (chat != null && chat.title != null) return chat.title;
        }
        return Long.toString(dialogId);
    }

    public static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) builder.append(Character.forDigit((value >>> 4) & 15, 16)).append(Character.forDigit(value & 15, 16));
        return builder.toString();
    }
}
