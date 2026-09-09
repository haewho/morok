package org.morok.templates;

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
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Bounded encrypted store for composer templates. It has no Telegram network or send dependency. */
public final class MorokReplyTemplateStore {
    public interface Callback<T> { void done(T value, String error); }
    private interface Operation<T> { T run() throws Exception; }

    public static final int MAX_TEMPLATES = 100;
    public static final int MAX_DATABASE_BYTES = 512 * 1024;
    private static final long MIN_FREE_BYTES = 8L * 1024 * 1024;
    private static final String REVOCATIONS = "morok_reply_template_revocations_v1";
    private static final ExecutorService QUEUE = Executors.newSingleThreadExecutor(r -> new Thread(r, "morok-reply-templates"));
    private static final HashMap<Long, MorokReplyTemplateStore> INSTANCES = new HashMap<>();

    private final int account;
    public final long userId;
    private final File directory;
    private final AtomicFile databaseFile;
    private volatile boolean revoked;
    private Aead cipher;

    private MorokReplyTemplateStore(int account, long userId) {
        this.account = account;
        this.userId = userId;
        directory = new File(ApplicationLoader.applicationContext.getNoBackupFilesDir(), "morok/reply-templates/" + userId);
        databaseFile = new AtomicFile(new File(directory, "templates.tink"));
    }

    public static synchronized MorokReplyTemplateStore forAccount(int account) {
        UserConfig config = UserConfig.getInstance(account);
        long userId = config.getClientUserId();
        if (userId <= 0 || !config.isClientActivated()) throw new IllegalStateException("Account is not authorized");
        MorokReplyTemplateStore store = INSTANCES.get(userId);
        if (store == null || store.revoked || store.account != account) {
            store = new MorokReplyTemplateStore(account, userId);
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
        MorokReplyTemplateStore old = INSTANCES.remove(userId);
        if (old != null) old.revoked = true;
        if (!revocations().edit().putBoolean(Long.toString(userId), true).commit()) {
            try { deleteKey(userId); }
            catch (Exception error) { throw new IllegalStateException("Cannot revoke reply template key", error); }
        }
        QUEUE.execute(() -> {
            try { eraseUser(userId); }
            catch (Exception ignored) { /* Marker keeps the namespace revoked until a later retry succeeds. */ }
        });
    }

    private static void eraseUser(long userId) throws Exception {
        deleteKey(userId);
        deleteTree(new File(ApplicationLoader.applicationContext.getNoBackupFilesDir(), "morok/reply-templates/" + userId));
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
            if (children == null) throw new IOException("Template directory unavailable");
            for (File child : children) deleteTree(child);
        }
        if (file.exists() && !file.delete()) throw new IOException("Could not erase templates");
    }

    private static String keyAlias(long userId) { return "morok.templates.v1." + userId; }

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
                    throw new IOException("Template key is unavailable; ciphertext was kept");
                }
                AndroidKeystoreKmsClient.generateNewAeadKey(uri);
            }
            cipher = new AndroidKeystoreKmsClient().getAead(uri);
        }
        return cipher;
    }

    private byte[] aad() {
        return ("morok-reply-templates/v1/" + userId + "/database").getBytes(StandardCharsets.UTF_8);
    }

    private HashMap<String, ReplyTemplate> read() throws Exception {
        HashMap<String, ReplyTemplate> entries = new HashMap<>();
        if (!databaseFile.getBaseFile().exists() && !new File(databaseFile.getBaseFile() + ".bak").exists()) return entries;
        byte[] encrypted;
        try (FileInputStream input = databaseFile.openRead()) { encrypted = readBounded(input, MAX_DATABASE_BYTES + 256); }
        byte[] plain = cipher().decrypt(encrypted, aad());
        if (plain.length > MAX_DATABASE_BYTES) throw new IOException("Template storage limit exceeded");
        JSONObject root = new JSONObject(new String(plain, StandardCharsets.UTF_8));
        if (root.getInt("schema") != 1 || root.getLong("user") != userId) throw new IOException("Unsupported template database");
        JSONArray values = root.getJSONArray("templates");
        if (values.length() > MAX_TEMPLATES) throw new IOException("Template count limit exceeded");
        for (int i = 0; i < values.length(); i++) {
            JSONObject value = values.getJSONObject(i);
            ReplyTemplate template = new ReplyTemplate(value.getString("id"), value.getString("title"),
                    value.getString("body"), value.getLong("created"), value.getLong("updated"));
            if (entries.put(template.id, template) != null) throw new IOException("Duplicate template identity");
        }
        return entries;
    }

    private void write(HashMap<String, ReplyTemplate> entries) throws Exception {
        requireActive();
        if (entries.size() > MAX_TEMPLATES) throw new IOException("Template count limit reached");
        JSONArray values = new JSONArray();
        for (ReplyTemplate template : entries.values()) {
            values.put(new JSONObject().put("id", template.id).put("title", template.title).put("body", template.body)
                    .put("created", template.createdAt).put("updated", template.updatedAt));
        }
        byte[] plain = new JSONObject().put("schema", 1).put("user", userId).put("templates", values)
                .toString().getBytes(StandardCharsets.UTF_8);
        if (plain.length > MAX_DATABASE_BYTES) throw new IOException("Template storage limit reached");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Template storage unavailable");
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
            catch (Exception exception) { error = "Encrypted reply templates are unavailable."; }
            T value = result;
            String failure = error;
            if (callback != null) AndroidUtilities.runOnUIThread(() -> {
                if (isActive()) callback.done(value, failure);
            });
        });
    }

    public void list(Callback<ArrayList<ReplyTemplate>> callback) {
        execute(() -> {
            ArrayList<ReplyTemplate> values = new ArrayList<>(read().values());
            values.sort((a, b) -> Long.compare(b.updatedAt, a.updatedAt));
            return values;
        }, callback);
    }

    public void create(String title, String body, Callback<ReplyTemplate> callback) {
        execute(() -> {
            HashMap<String, ReplyTemplate> entries = read();
            if (entries.size() >= MAX_TEMPLATES) throw new IOException("Template count limit reached");
            long now = System.currentTimeMillis();
            ReplyTemplate value = new ReplyTemplate(UUID.randomUUID().toString(), title, body, now, now);
            entries.put(value.id, value);
            write(entries);
            return value;
        }, callback);
    }

    public void update(String id, String title, String body, Callback<ReplyTemplate> callback) {
        execute(() -> {
            HashMap<String, ReplyTemplate> entries = read();
            ReplyTemplate previous = entries.get(id);
            if (previous == null) throw new IOException("Template no longer exists");
            ReplyTemplate value = new ReplyTemplate(id, title, body, previous.createdAt,
                    Math.max(previous.createdAt, System.currentTimeMillis()));
            entries.put(id, value);
            write(entries);
            return value;
        }, callback);
    }

    public void remove(String id, Callback<Boolean> callback) {
        execute(() -> {
            if (!ReplyTemplate.safeId(id)) throw new IOException("Invalid template identity");
            HashMap<String, ReplyTemplate> entries = read();
            if (entries.remove(id) == null) throw new IOException("Template no longer exists");
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
            output = file.startWrite(); output.write(bytes); output.getFD().sync(); file.finishWrite(output); output = null;
            try (FileInputStream committed = file.openRead()) {
                if (!MessageDigest.isEqual(bytes, readBounded(committed, bytes.length))) throw new IOException("Commit verification failed");
            }
        } catch (Exception error) {
            if (output != null) file.failWrite(output);
            throw new IOException("Atomic template write failed", error);
        }
    }

    private static byte[] readBounded(FileInputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16384];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > maxBytes) throw new IOException("Template size limit exceeded");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
}
