package org.morok.memory;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.AtomicFile;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.integration.android.AndroidKeystoreKmsClient;

import org.json.JSONArray;
import org.json.JSONObject;
import org.morok.history.MemoryCapture;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Serialized, fail-closed local projection. It never writes to Telegram's messages database. */
public final class MorokMemoryStore {
    public interface Callback<T> { void done(T result, String error); }
    private interface Operation<T> { T run() throws Exception; }
    private static final ExecutorService QUEUE = Executors.newSingleThreadExecutor(r -> new Thread(r, "morok-memory"));
    private static final HashMap<Long, MorokMemoryStore> INSTANCES = new HashMap<>();
    private static final String PURGE_PREFS = "morok_memory_revocations_v1";
    private final int account;
    public final long userId;
    private final File directory;
    private final AtomicFile index;
    private volatile boolean revoked;
    private volatile java.util.Set<String> tracked = Collections.emptySet();
    private Aead cipher;

    private MorokMemoryStore(int account, long userId) {
        this.account = account; this.userId = userId;
        directory = new File(ApplicationLoader.applicationContext.getNoBackupFilesDir(), "morok/memory/" + userId);
        index = new AtomicFile(new File(directory, "cards.tink"));
    }

    public static synchronized MorokMemoryStore forAccount(int account) {
        long id = UserConfig.getInstance(account).getClientUserId();
        if (id <= 0) throw new IllegalStateException("Account is not authorized");
        MorokMemoryStore store = INSTANCES.get(id);
        if (store == null || store.revoked || store.account != account) {
            store = new MorokMemoryStore(account, id); INSTANCES.put(id, store);
        }
        return store;
    }

    public static int resolveAccount(long userId) {
        if (userId <= 0) return -1;
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            if (UserConfig.getInstance(i).getClientUserId() == userId && UserConfig.getInstance(i).isClientActivated()) return i;
        }
        return -1;
    }

    public boolean isActive() {
        return !revoked && UserConfig.getInstance(account).getClientUserId() == userId
                && UserConfig.getInstance(account).isClientActivated();
    }

    private static SharedPreferences revocations() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PURGE_PREFS, Context.MODE_PRIVATE);
    }

    /** Called before the reusable account slot is cleared. A tiny durable revocation precedes async erasure. */
    public static synchronized void onLogout(long userId) {
        if (userId <= 0) return;
        MorokMemoryStore old = INSTANCES.remove(userId);
        if (old != null) old.revoked = true;
        // commit is intentional: a process death between logout and queued erasure must not resurrect data.
        MorokMemoryFileProvider.revokeUser(userId);
        if (!revocations().edit().putBoolean(Long.toString(userId), true).commit()) {
            // Fail closed if even the small logout marker cannot be persisted.
            try {
                KeyStore keys = KeyStore.getInstance("AndroidKeyStore"); keys.load(null);
                keys.deleteEntry(alias(userId));
            } catch (Exception error) {
                throw new IllegalStateException("Cannot revoke Memory encryption key for logout", error);
            }
        }
        try { MorokMemoryReminderReceiver.cancel(userId); } catch (RuntimeException ignored) { }
        QUEUE.execute(() -> {
            try { eraseUser(userId); } catch (Exception ignored) { /* revocation remains until erasure succeeds */ }
        });
    }

    private static void eraseUser(long userId) throws Exception {
        KeyStore keys = KeyStore.getInstance("AndroidKeyStore"); keys.load(null);
        keys.deleteEntry(alias(userId));
        File directory = new File(ApplicationLoader.applicationContext.getNoBackupFilesDir(), "morok/memory/" + userId);
        deleteTree(directory);
        if (!revocations().edit().remove(Long.toString(userId)).remove("gap_" + userId).commit()) throw new IOException("Revocation state unavailable");
    }

    private static void deleteTree(File file) throws IOException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) throw new IOException("Memory directory unavailable");
            for (File child : children) deleteTree(child);
        }
        if (file.exists() && !file.delete()) throw new IOException("Could not erase Memory data");
    }

    private static String alias(long userId) { return "morok.memory.v1." + userId; }

    private void requireActive() throws IOException {
        if (!isActive()) throw new IOException("Account session changed");
    }

    private Aead cipher() throws Exception {
        requireActive();
        if (Build.VERSION.SDK_INT < 23) throw new IOException("Android 6 or later is required");
        if (revocations().getBoolean(Long.toString(userId), false)) {
            eraseUser(userId); cipher = null;
        }
        if (cipher == null) {
            KeyStore keys = KeyStore.getInstance("AndroidKeyStore"); keys.load(null);
            String uri = "android-keystore://" + alias(userId);
            if (!keys.containsAlias(alias(userId))) {
                // A lost key must never silently replace a key belonging to existing ciphertext.
                if (index.getBaseFile().exists() || new File(index.getBaseFile() + ".bak").exists()) {
                    throw new IOException("Memory key is unavailable; encrypted data was kept");
                }
                AndroidKeystoreKmsClient.generateNewAeadKey(uri);
            }
            cipher = new AndroidKeystoreKmsClient().getAead(uri);
        }
        return cipher;
    }

    private byte[] aad(String object) {
        return ("morok-memory/v1/" + userId + "/" + object).getBytes(StandardCharsets.UTF_8);
    }

    private static final class Database {
        final ArrayList<MemoryCard> cards = new ArrayList<>();
        final HashSet<String> tombstones = new HashSet<>();
    }

    private Database read() throws Exception {
        Aead aead = cipher();
        Database database = new Database();
        if (!index.getBaseFile().exists() && !new File(index.getBaseFile() + ".bak").exists()) return database;
        byte[] bytes;
        try (FileInputStream input = index.openRead()) { bytes = readBounded(input, MemoryPolicy.MAX_DATABASE_BYTES + 128); }
        JSONObject json = new JSONObject(new String(aead.decrypt(bytes, aad("index")), StandardCharsets.UTF_8));
        if (json.getInt("schema") != 1 || json.getLong("user") != userId) throw new IOException("Unsupported Memory database");
        JSONArray cards = json.getJSONArray("cards"), tombstones = json.getJSONArray("tombstones");
        if (cards.length() > MemoryPolicy.MAX_CARDS || tombstones.length() > MemoryPolicy.MAX_TOMBSTONES) throw new IOException("Memory limit exceeded");
        for (int i = 0; i < cards.length(); i++) {
            MemoryCard card = MemoryCard.fromJson(cards.getJSONObject(i));
            if (card.key.userId != userId || !safeId(card.id) || card.versions.size() > MemoryPolicy.MAX_VERSIONS) throw new IOException("Invalid Memory identity");
            for (MemoryCard.Snapshot snapshot : card.versions) {
                if (!snapshot.blob.isEmpty() && !safeId(snapshot.blob)) throw new IOException("Invalid Memory attachment");
                if ("saved".equals(snapshot.fileState) && !new File(directory, snapshot.blob + ".tink").isFile()) snapshot.fileState = "unavailable";
            }
            database.cards.add(card);
        }
        for (int i = 0; i < tombstones.length(); i++) database.tombstones.add(tombstones.getString(i));
        updateTracked(database);
        return database;
    }

    private void updateTracked(Database database) {
        HashSet<String> keys = new HashSet<>();
        for (MemoryCard card : database.cards) keys.add(card.key.canonical());
        tracked = Collections.unmodifiableSet(keys);
    }

    public boolean tracks(MemoryKey key) { return isActive() && tracked.contains(key.canonical()); }
    public boolean hasCards() { return isActive() && !tracked.isEmpty(); }

    public void noteCaptureGap() {
        if (isActive()) revocations().edit().putBoolean("gap_" + userId, true).apply();
    }
    public boolean hasCaptureGap() { return revocations().getBoolean("gap_" + userId, false); }

    private void write(Database database) throws Exception {
        requireActive();
        JSONArray cards = new JSONArray(), tombstones = new JSONArray();
        for (MemoryCard card : database.cards) cards.put(card.toJson());
        for (String key : database.tombstones) tombstones.put(key);
        byte[] plain = new JSONObject().put("schema", 1).put("user", userId).put("cards", cards)
                .put("tombstones", tombstones).toString().getBytes(StandardCharsets.UTF_8);
        if (plain.length > MemoryPolicy.MAX_DATABASE_BYTES) throw new IOException("Memory text storage limit reached");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Memory storage unavailable");
        if (directory.getUsableSpace() < plain.length + MemoryPolicy.MIN_FREE_BYTES) throw new IOException("Not enough free storage");
        byte[] encrypted = cipher().encrypt(plain, aad("index"));
        requireActive();
        if (usedBytes() - index.getBaseFile().length() + encrypted.length > MemoryPolicy.MAX_ACCOUNT_BYTES) {
            throw new IOException("Memory account storage limit reached");
        }
        writeAtomic(index, encrypted);
        requireActive();
        updateTracked(database);
        cleanup(database);
        MorokMemoryReminderReceiver.scheduleNext(userId, database.cards);
    }

    private static void writeAtomic(AtomicFile file, byte[] bytes) throws IOException {
        FileOutputStream stream = null;
        try {
            stream = file.startWrite(); stream.write(bytes); stream.getFD().sync(); file.finishWrite(stream);
            stream = null;
            // AtomicFile logs some rename/fsync failures instead of throwing. Verify the committed base
            // before reporting success or deleting anything referenced by the previously committed index.
            try (FileInputStream committed = file.openRead()) {
                if (!MessageDigest.isEqual(bytes, readBounded(committed, bytes.length))) {
                    throw new IOException("Memory atomic commit could not be verified");
                }
            }
        } catch (Exception error) {
            if (stream != null) file.failWrite(stream);
            throw new IOException("Atomic Memory write failed", error);
        }
    }

    private void cleanup(Database database) {
        HashSet<String> referenced = new HashSet<>();
        referenced.add("cards.tink"); referenced.add("cards.tink.bak");
        for (MemoryCard card : database.cards) for (MemoryCard.Snapshot snapshot : card.versions) {
            if (!snapshot.blob.isEmpty()) referenced.add(snapshot.blob + ".tink");
        }
        File[] files = directory.listFiles();
        if (files != null) for (File file : files) if (!referenced.contains(file.getName())) file.delete();
    }

    private <T> void execute(Operation<T> operation, Callback<T> callback) {
        QUEUE.execute(() -> {
            T result = null; String error = null;
            try { requireActive(); result = operation.run(); requireActive(); }
            catch (Exception exception) { error = "Memory storage unavailable. Check free space and the account encryption key."; }
            final T value = result; final String failure = error;
            if (callback != null) AndroidUtilities.runOnUIThread(() -> { if (isActive()) callback.done(value, failure); });
        });
    }

    public void list(Callback<ArrayList<MemoryCard>> callback) {
        execute(() -> {
            Database database = read(); cleanup(database);
            MorokMemoryReminderReceiver.scheduleNext(userId, database.cards);
            Collections.sort(database.cards, (a, b) -> Long.compare(b.createdAt, a.createdAt));
            return database.cards;
        }, callback);
    }

    public void save(MemoryCapture capture, boolean explicitUserAction, Callback<MemoryCard> callback) {
        execute(() -> {
            if (capture.key.userId != userId) throw new IOException("Account mismatch");
            Database database = read();
            MemoryCard existing = null;
            for (MemoryCard card : database.cards) if (card.key.equals(capture.key)) { existing = card; break; }
            if (!explicitUserAction && (existing == null || database.tombstones.contains(capture.key.canonical()))) return null;
            if (existing == null) {
                if (database.cards.size() >= MemoryPolicy.MAX_CARDS) throw new IOException("Memory card limit reached");
                existing = new MemoryCard(UUID.randomUUID().toString(), capture.key);
                existing.createdAt = System.currentTimeMillis(); existing.source = capture.source; existing.sender = capture.sender;
                database.cards.add(existing);
            }
            if (explicitUserAction) database.tombstones.remove(capture.key.canonical());
            for (MemoryCard.Snapshot version : existing.versions) {
                if (version.fingerprint.equals(capture.snapshot.fingerprint)) {
                    // Explicit retry may preserve an original that has since completed downloading.
                    if (explicitUserAction && !"saved".equals(version.fileState)) {
                        copyAttachment(capture, version); write(database);
                    }
                    return existing;
                }
            }
            copyAttachment(capture, capture.snapshot);
            int position = existing.versions.size();
            while (position > 0 && !MemoryPolicy.becomesLatest(capture.snapshot.editedAt, existing.versions.get(position - 1).editedAt)) position--;
            existing.versions.add(position, capture.snapshot);
            while (existing.versions.size() > MemoryPolicy.MAX_VERSIONS) existing.versions.remove(0);
            write(database);
            return existing;
        }, callback);
    }

    private void copyAttachment(MemoryCapture capture, MemoryCard.Snapshot snapshot) {
        TLRPC.Message message = capture.message;
        TLRPC.Document document = MessageObject.getDocument(message);
        boolean photo = message.media instanceof TLRPC.TL_messageMediaPhoto;
        if (document == null && !photo) return;
        snapshot.fileState = "not_downloaded";
        snapshot.fileName = document != null ? FileLoader.getDocumentFileName(document) : "photo.jpg";
        if (snapshot.fileName.isEmpty()) snapshot.fileName = "attachment";
        snapshot.mime = document != null && document.mime_type != null ? document.mime_type : "image/jpeg";
        try {
            File source = message.attachPath == null || message.attachPath.isEmpty() ? null : new File(message.attachPath);
            if (source == null || !source.isFile()) source = FileLoader.getInstance(account).getPathToMessage(message);
            if (source == null || !source.isFile() || source.getName().endsWith(".enc")) return;
            long size = source.length();
            if (size > MemoryPolicy.MAX_ATTACHMENT_BYTES) { snapshot.fileState = "too_large"; return; }
            if (document != null && document.size > 0 && size != document.size) return;
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Storage unavailable");
            if (!MemoryPolicy.canCopy(size, usedBytes(), directory.getUsableSpace())) { snapshot.fileState = "storage_error"; return; }
            byte[] plain;
            try (FileInputStream stream = new FileInputStream(source)) { plain = readBounded(stream, (int) MemoryPolicy.MAX_ATTACHMENT_BYTES); }
            if (plain.length != size || source.length() != size) return;
            String hash = MemoryCapture.hex(MessageDigest.getInstance("SHA-256").digest(plain));
            // Reuse already retained originals inside this account; metadata remains encrypted.
            Database database = read();
            for (MemoryCard card : database.cards) for (MemoryCard.Snapshot version : card.versions) {
                if (hash.equals(version.sha256) && "saved".equals(version.fileState)) {
                    snapshot.blob = version.blob; snapshot.sha256 = hash; snapshot.fileSize = size; snapshot.fileState = "saved"; return;
                }
            }
            String id = UUID.randomUUID().toString();
            byte[] encrypted = cipher().encrypt(plain, aad("blob/" + id));
            requireActive();
            writeAtomic(new AtomicFile(new File(directory, id + ".tink")), encrypted);
            snapshot.blob = id; snapshot.sha256 = hash; snapshot.fileSize = size; snapshot.fileState = "saved";
        } catch (Exception error) { snapshot.fileState = "storage_error"; }
    }

    public void update(String id, String note, String tags, boolean needsReply, boolean completed,
                       long reminderAt, Callback<MemoryCard> callback) {
        execute(() -> {
            if (note.length() > 8192 || tags.length() > 512 || reminderAt < 0) throw new IOException("Card field limit exceeded");
            Database database = read();
            MemoryCard card = find(database, id);
            card.note = note; card.tags = tags; card.needsReply = needsReply; card.completed = completed;
            long nextReminder = completed ? 0 : reminderAt;
            boolean cancelNotification = card.completed || card.reminderAt != nextReminder;
            if (card.reminderAt != nextReminder) card.reminderDeliveredAt = 0;
            card.reminderAt = nextReminder;
            write(database);
            if (cancelNotification) MorokMemoryReminderReceiver.cancelCard(userId, card.id);
            return card;
        }, callback);
    }

    public void remove(String id, Callback<Boolean> callback) {
        execute(() -> {
            Database database = read(); MemoryCard card = find(database, id);
            if (database.tombstones.size() >= MemoryPolicy.MAX_TOMBSTONES) throw new IOException("Memory removal limit reached");
            database.tombstones.add(card.key.canonical()); database.cards.remove(card); write(database);
            MorokMemoryReminderReceiver.cancelCard(userId, card.id); return true;
        }, callback);
    }

    public void clear(Callback<Boolean> callback) {
        execute(() -> {
            Database database = read();
            if (database.tombstones.size() + database.cards.size() > MemoryPolicy.MAX_TOMBSTONES) throw new IOException("Memory removal limit reached");
            for (MemoryCard card : database.cards) database.tombstones.add(card.key.canonical());
            database.cards.clear(); write(database); MorokMemoryReminderReceiver.cancel(userId); return true;
        }, callback);
    }

    /** Standard deleteMessages IDs are account-global outside channels; channel IDs use their channel namespace. */
    public void markDeleted(long channelId, ArrayList<Integer> messageIds) { markDeleted(channelId, messageIds, null); }
    public void markDeleted(long channelId, ArrayList<Integer> messageIds, Callback<Boolean> callback) {
        final HashSet<Integer> ids = new HashSet<>(messageIds);
        execute(() -> {
            Database database = read(); boolean changed = false;
            for (MemoryCard card : database.cards) {
                boolean matchingPeer = channelId == 0 ? !"channel".equals(card.key.peerKind)
                        : "channel".equals(card.key.peerKind) && card.key.peerId == channelId;
                if (matchingPeer && ids.contains(card.key.messageId) && !card.deletedInTelegram) {
                    card.deletedInTelegram = true; changed = true;
                }
            }
            if (changed) write(database); return changed;
        }, callback);
    }

    public void markDialogDeleted(long dialogId) { markDialogDeleted(dialogId, Integer.MAX_VALUE, null); }
    public void markDialogDeleted(long dialogId, int maxMessageId, Callback<Boolean> callback) {
        execute(() -> {
            Database database = read();
            for (MemoryCard card : database.cards) if (card.key.dialogId() == dialogId && card.key.messageId <= maxMessageId) card.deletedInTelegram = true;
            write(database); return true;
        }, callback);
    }

    public void storageSize(Callback<Long> callback) { execute(this::usedBytes, callback); }

    private long usedBytes() {
        long bytes = 0;
        File[] files = directory.listFiles(); if (files != null) for (File file : files) bytes += file.length();
        return bytes;
    }

    static boolean safeId(String value) { return value != null && value.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"); }

    private static MemoryCard find(Database database, String id) throws IOException {
        for (MemoryCard card : database.cards) if (card.id.equals(id)) return card;
        throw new IOException("Memory card no longer exists");
    }

    /** Runs only on the provider's background reader; plaintext is never written to disk. */
    byte[] readAttachment(String cardId, String blob) throws Exception {
        if (!safeId(cardId) || !safeId(blob)) throw new IOException("Invalid attachment");
        java.util.concurrent.Future<byte[]> pending = QUEUE.submit(() -> {
            requireActive(); MemoryCard card = find(read(), cardId);
            for (MemoryCard.Snapshot snapshot : card.versions) {
                if (snapshot.blob.equals(blob) && "saved".equals(snapshot.fileState)) {
                    byte[] encrypted;
                    try (FileInputStream stream = new FileInputStream(new File(directory, blob + ".tink"))) {
                        encrypted = readBounded(stream, (int) MemoryPolicy.MAX_ATTACHMENT_BYTES + 128);
                    }
                    byte[] plain = cipher().decrypt(encrypted, aad("blob/" + blob));
                    if (plain.length != snapshot.fileSize || !snapshot.sha256.equals(MemoryCapture.hex(MessageDigest.getInstance("SHA-256").digest(plain)))) throw new IOException("Attachment integrity check failed");
                    requireActive(); return plain;
                }
            }
            throw new IOException("Attachment no longer exists");
        });
        return pending.get();
    }

    static byte[] readBounded(FileInputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[32768]; int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > maxBytes) throw new IOException("Memory size limit exceeded");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    void deliverReminders(Runnable finished) {
        QUEUE.execute(() -> {
            try {
                requireActive(); Database database = read(); long now = System.currentTimeMillis();
                for (MemoryCard card : database.cards) {
                    if (!card.completed && card.reminderAt > 0 && card.reminderAt <= now && card.reminderDeliveredAt < card.reminderAt) {
                        if (MorokMemoryReminderReceiver.notifyCard(userId, card)) card.reminderDeliveredAt = now;
                    }
                }
                write(database);
            } catch (Exception ignored) { /* Keep encrypted reminder state for the next permitted retry. */ }
            finally { finished.run(); }
        });
    }
}
