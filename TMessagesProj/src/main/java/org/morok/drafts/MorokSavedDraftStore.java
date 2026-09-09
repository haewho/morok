package org.morok.drafts;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.AtomicFile;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.integration.android.AndroidKeystoreKmsClient;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserConfig;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Bounded encrypted snapshots of composer text. It has no Telegram database or network dependency. */
public final class MorokSavedDraftStore {
    public interface Callback<T> { void done(T value, String error); }
    private interface Operation<T> { T run() throws Exception; }

    public static final int MAX_DRAFTS = 256;
    public static final int MAX_DATABASE_BYTES = 2 * 1024 * 1024;
    private static final long MIN_FREE_BYTES = 8L * 1024 * 1024;
    private static final String REVOCATIONS = "morok_saved_draft_revocations_v1";
    private static final ExecutorService QUEUE = Executors.newSingleThreadExecutor(r -> new Thread(r, "morok-saved-drafts"));
    private static final HashMap<Long, MorokSavedDraftStore> INSTANCES = new HashMap<>();

    private final int account;
    public final long userId;
    private final File directory;
    private final AtomicFile databaseFile;
    private volatile boolean revoked;
    private Aead cipher;

    private MorokSavedDraftStore(int account, long userId) {
        this.account = account;
        this.userId = userId;
        directory = new File(ApplicationLoader.applicationContext.getNoBackupFilesDir(), "morok/saved-drafts/" + userId);
        databaseFile = new AtomicFile(new File(directory, "drafts.tink"));
    }

    public static synchronized MorokSavedDraftStore forAccount(int account) {
        UserConfig config = UserConfig.getInstance(account);
        long userId = config.getClientUserId();
        if (userId <= 0 || !config.isClientActivated()) throw new IllegalStateException("Account is not authorized");
        MorokSavedDraftStore store = INSTANCES.get(userId);
        if (store == null || store.revoked || store.account != account) {
            store = new MorokSavedDraftStore(account, userId);
            INSTANCES.put(userId, store);
        }
        return store;
    }

    public boolean isActive() {
        UserConfig config = UserConfig.getInstance(account);
        return !revoked && config.isClientActivated() && config.getClientUserId() == userId;
    }

    private static SharedPreferences revocations() {
        return ApplicationLoader.applicationContext.getSharedPreferences(REVOCATIONS, Context.MODE_PRIVATE);
    }

    /** Durable revocation is recorded before Telegram can reuse this account slot. */
    public static synchronized void onLogout(long userId) {
        if (userId <= 0) return;
        MorokSavedDraftStore old = INSTANCES.remove(userId);
        if (old != null) old.revoked = true;
        if (!revocations().edit().putBoolean(Long.toString(userId), true).commit()) {
            try { deleteKey(userId); }
            catch (Exception error) { throw new IllegalStateException("Cannot revoke saved draft key", error); }
        }
        QUEUE.execute(() -> {
            try { eraseUser(userId); }
            catch (Exception ignored) { /* Marker keeps the namespace revoked until a later retry succeeds. */ }
        });
    }

    private static void eraseUser(long userId) throws Exception {
        deleteKey(userId);
        deleteTree(new File(ApplicationLoader.applicationContext.getNoBackupFilesDir(), "morok/saved-drafts/" + userId));
        if (!revocations().edit().remove(Long.toString(userId)).commit()) throw new IOException("Revocation state unavailable");
    }

    private static void deleteKey(long userId) throws Exception {
        KeyStore keys = KeyStore.getInstance("AndroidKeyStore");
        keys.load(null);
        keys.deleteEntry(keyAlias(userId));
    }

    private static void deleteTree(File file) throws IOException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) throw new IOException("Saved draft directory unavailable");
            for (File child : children) deleteTree(child);
        }
        if (file.exists() && !file.delete()) throw new IOException("Could not erase saved drafts");
    }

    private static String keyAlias(long userId) { return "morok.drafts.v1." + userId; }

    private void requireActive() throws IOException {
        if (!isActive()) throw new IOException("Account session changed");
    }

    private synchronized Aead cipher() throws Exception {
        requireActive();
        if (Build.VERSION.SDK_INT < 23) throw new IOException("Android 6 or later is required");
        if (revocations().getBoolean(Long.toString(userId), false)) {
            eraseUser(userId);
            cipher = null;
        }
        if (cipher == null) {
            KeyStore keys = KeyStore.getInstance("AndroidKeyStore");
            keys.load(null);
            String alias = keyAlias(userId);
            String uri = "android-keystore://" + alias;
            if (!keys.containsAlias(alias)) {
                if (databaseFile.getBaseFile().exists() || new File(databaseFile.getBaseFile() + ".bak").exists()) {
                    throw new IOException("Saved draft key is unavailable; ciphertext was kept");
                }
                AndroidKeystoreKmsClient.generateNewAeadKey(uri);
            }
            cipher = new AndroidKeystoreKmsClient().getAead(uri);
        }
        return cipher;
    }

    private byte[] aad() {
        return ("morok-saved-drafts/v1/" + userId + "/database").getBytes(StandardCharsets.UTF_8);
    }

    private HashMap<String, SavedDraft> read() throws Exception {
        HashMap<String, SavedDraft> entries = new HashMap<>();
        if (!databaseFile.getBaseFile().exists() && !new File(databaseFile.getBaseFile() + ".bak").exists()) return entries;
        byte[] encrypted;
        try (FileInputStream input = databaseFile.openRead()) { encrypted = readBounded(input, MAX_DATABASE_BYTES + 256); }
        byte[] plain = cipher().decrypt(encrypted, aad());
        if (plain.length > MAX_DATABASE_BYTES) throw new IOException("Saved draft storage limit exceeded");
        JSONObject root = new JSONObject(new String(plain, StandardCharsets.UTF_8));
        if (root.getInt("schema") != 1 || root.getLong("user") != userId) throw new IOException("Unsupported saved draft database");
        JSONArray values = root.getJSONArray("drafts");
        if (values.length() > MAX_DRAFTS) throw new IOException("Saved draft count limit exceeded");
        for (int i = 0; i < values.length(); i++) {
            JSONObject value = values.getJSONObject(i);
            SavedDraft draft = new SavedDraft(value.getLong("dialog"), value.getLong("topic"),
                    value.getString("label"), value.getString("text"), value.getLong("created"),
                    value.getLong("updated"));
            if (entries.put(draft.key(), draft) != null) throw new IOException("Duplicate saved draft destination");
        }
        return entries;
    }

    private void write(HashMap<String, SavedDraft> entries) throws Exception {
        requireActive();
        if (entries.size() > MAX_DRAFTS) throw new IOException("Saved draft count limit reached");
        JSONArray values = new JSONArray();
        for (SavedDraft draft : entries.values()) {
            values.put(new JSONObject().put("dialog", draft.dialogId).put("topic", draft.topicId)
                    .put("label", draft.label).put("text", draft.text).put("created", draft.createdAt)
                    .put("updated", draft.updatedAt));
        }
        byte[] plain = new JSONObject().put("schema", 1).put("user", userId).put("drafts", values)
                .toString().getBytes(StandardCharsets.UTF_8);
        if (plain.length > MAX_DATABASE_BYTES) throw new IOException("Saved draft storage limit reached");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Saved draft storage unavailable");
        if (directory.getUsableSpace() < plain.length + MIN_FREE_BYTES) throw new IOException("Not enough free storage");
        byte[] encrypted = cipher().encrypt(plain, aad());
        requireActive();
        writeAtomic(databaseFile, encrypted);
    }

    private <T> void execute(Operation<T> operation, Callback<T> callback) {
        QUEUE.execute(() -> {
            T result = null;
            String error = null;
            try { requireActive(); result = operation.run(); requireActive(); }
            catch (Exception exception) { error = "Encrypted saved drafts are unavailable."; }
            T value = result;
            String failure = error;
            if (callback != null) AndroidUtilities.runOnUIThread(() -> {
                if (isActive()) callback.done(value, failure);
            });
        });
    }

    public void get(long dialogId, long topicId, Callback<SavedDraft> callback) {
        execute(() -> read().get(SavedDraft.key(dialogId, topicId)), callback);
    }

    public void list(Callback<ArrayList<SavedDraft>> callback) {
        execute(() -> {
            ArrayList<SavedDraft> values = new ArrayList<>(read().values());
            values.sort((a, b) -> Long.compare(b.updatedAt, a.updatedAt));
            return values;
        }, callback);
    }

    public void save(long dialogId, long topicId, String label, String text, Callback<SavedDraft> callback) {
        execute(() -> {
            HashMap<String, SavedDraft> entries = read();
            String key = SavedDraft.key(dialogId, topicId);
            SavedDraft previous = entries.get(key);
            if (previous == null && entries.size() >= MAX_DRAFTS) throw new IOException("Saved draft count limit reached");
            long now = System.currentTimeMillis();
            SavedDraft value = new SavedDraft(dialogId, topicId, label, text,
                    previous == null ? now : previous.createdAt, previous == null ? now : Math.max(previous.createdAt, now));
            entries.put(key, value);
            write(entries);
            return value;
        }, callback);
    }

    public void remove(long dialogId, long topicId, Callback<Boolean> callback) {
        execute(() -> {
            HashMap<String, SavedDraft> entries = read();
            if (entries.remove(SavedDraft.key(dialogId, topicId)) == null) throw new IOException("Saved draft no longer exists");
            write(entries);
            return true;
        }, callback);
    }

    public void clear(Callback<Boolean> callback) {
        execute(() -> { write(new HashMap<>()); return true; }, callback);
    }

    private static void writeAtomic(AtomicFile file, byte[] bytes) throws IOException {
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write(bytes);
            output.getFD().sync();
            file.finishWrite(output);
            output = null;
            try (FileInputStream committed = file.openRead()) {
                if (!MessageDigest.isEqual(bytes, readBounded(committed, bytes.length))) {
                    throw new IOException("Commit verification failed");
                }
            }
        } catch (Exception error) {
            if (output != null) file.failWrite(output);
            throw new IOException("Atomic saved draft write failed", error);
        }
    }

    private static byte[] readBounded(FileInputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16384];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > maxBytes) throw new IOException("Saved draft size limit exceeded");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
}
