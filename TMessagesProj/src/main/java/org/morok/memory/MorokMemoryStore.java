package org.morok.memory;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.util.AtomicFile;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.integration.android.AndroidKeystoreKmsClient;

import org.json.JSONArray;
import org.json.JSONObject;
import org.morok.history.MemoryCapture;
import org.morok.settings.ArchiveSettings;
import org.morok.settings.MorokSettings;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Serialized, fail-closed local projection. It never writes to Telegram's messages database. */
public final class MorokMemoryStore {
    public interface Callback<T> { void done(T result, String error); }
    public static final class ArchiveCleanupResult {
        public final int removedCards;
        public final long beforeBytes;
        public final long afterBytes;

        ArchiveCleanupResult(int removedCards, long beforeBytes, long afterBytes) {
            this.removedCards = removedCards;
            this.beforeBytes = beforeBytes;
            this.afterBytes = afterBytes;
        }
    }
    public static final class ExportResult {
        public final int cards;
        public final int files;
        public final long bytes;

        ExportResult(int cards, int files, long bytes) {
            this.cards = cards;
            this.files = files;
            this.bytes = bytes;
        }
    }
    public static final class ImportPreview {
        public final String token;
        public final int cards;
        public final int duplicateCards;
        public final int files;
        public final long bytes;

        ImportPreview(String token, int cards, int duplicateCards, int files, long bytes) {
            this.token = token;
            this.cards = cards;
            this.duplicateCards = duplicateCards;
            this.files = files;
            this.bytes = bytes;
        }
    }
    public static final class ImportResult {
        public final int cards;
        public final int duplicateCards;
        public final int files;
        public final long bytes;

        ImportResult(int cards, int duplicateCards, int files, long bytes) {
            this.cards = cards;
            this.duplicateCards = duplicateCards;
            this.files = files;
            this.bytes = bytes;
        }
    }

    private static final class PortableImport {
        final ArrayList<PortableCard> cards = new ArrayList<>();
        final LinkedHashMap<String, PortableBlob> blobs = new LinkedHashMap<>();
        String token;
        int importedCards;
        int duplicateCards;
        int files;
        long bytes;
    }

    private static final class PortableCard {
        final String exportId;
        final String exportedOrigin;
        final MemoryCard card;
        boolean included;

        PortableCard(String exportId, String exportedOrigin, MemoryCard card) {
            this.exportId = exportId;
            this.exportedOrigin = exportedOrigin;
            this.card = card;
        }
    }

    private static final class PortableBlob {
        final String entry;
        final long size;
        final String sha256;
        final boolean thumbnail;
        final String mime;
        final ArrayList<PortableTarget> targets = new ArrayList<>();
        boolean needed;

        PortableBlob(String entry, long size, String sha256, boolean thumbnail, String mime) {
            this.entry = entry;
            this.size = size;
            this.sha256 = sha256;
            this.thumbnail = thumbnail;
            this.mime = mime;
        }
    }

    private static final class PortableTarget {
        final PortableCard owner;
        final MemoryCard.Snapshot snapshot;

        PortableTarget(PortableCard owner, MemoryCard.Snapshot snapshot) {
            this.owner = owner;
            this.snapshot = snapshot;
        }
    }
    private interface Operation<T> { T run() throws Exception; }
    private static final ExecutorService QUEUE = Executors.newSingleThreadExecutor(r -> new Thread(r, "morok-memory"));
    private static final ExecutorService JOURNAL_QUEUE = Executors.newSingleThreadExecutor(r -> new Thread(r, "morok-memory-journal"));
    private static final HashMap<Long, MorokMemoryStore> INSTANCES = new HashMap<>();
    private static final String PURGE_PREFS = "morok_memory_revocations_v1";
    private final int account;
    public final long userId;
    private final File directory;
    private final AtomicFile index;
    private final AtomicFile journal;
    private volatile boolean revoked;
    private final MemoryTrackingIndex tracking = new MemoryTrackingIndex();
    private volatile boolean trackingLoaded;
    private boolean replayScheduled;
    private final ArrayDeque<String> recentJournalOrder = new ArrayDeque<>();
    private final HashSet<String> recentJournal = new HashSet<>();
    private Aead cipher;
    private static final int SAVE_MANUAL = 1;
    private static final int SAVE_TRACKED_UPDATE = 2;
    private static final int SAVE_AUTOMATIC_NEW = 3;

    private MorokMemoryStore(int account, long userId) {
        this.account = account; this.userId = userId;
        directory = new File(ApplicationLoader.applicationContext.getNoBackupFilesDir(), "morok/memory/" + userId);
        index = new AtomicFile(new File(directory, "cards.tink"));
        journal = new AtomicFile(new File(directory, "journal.tink"));
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
            try {
                // Drain every journal write that started before revocation before deleting the key/directory.
                JOURNAL_QUEUE.submit(() -> true).get();
                eraseUser(userId);
            } catch (Exception ignored) { /* revocation remains until erasure succeeds */ }
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

    private synchronized Aead cipher() throws Exception {
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
                if (index.getBaseFile().exists() || new File(index.getBaseFile() + ".bak").exists()
                        || journal.getBaseFile().exists() || new File(journal.getBaseFile() + ".bak").exists()) {
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
        if (!index.getBaseFile().exists() && !new File(index.getBaseFile() + ".bak").exists()) {
            updateTracked(database);
            return database;
        }
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
                if (!snapshot.thumbnailBlob.isEmpty() && !safeId(snapshot.thumbnailBlob)) throw new IOException("Invalid Memory thumbnail");
                if ("saved".equals(snapshot.thumbnailState)) {
                    if (snapshot.thumbnailSize <= 0 || snapshot.thumbnailSize > MemoryPolicy.MAX_THUMBNAIL_BYTES
                            || !snapshot.thumbnailSha256.matches("[a-f0-9]{64}")
                            || !MemoryThumbnailPolicy.supportedMime(snapshot.thumbnailMime)
                            || snapshot.thumbnailName.length() > MemoryExportPolicy.MAX_FILE_NAME) {
                        throw new IOException("Invalid Memory thumbnail metadata");
                    }
                    if (!new File(directory, snapshot.thumbnailBlob + ".tink").isFile()) snapshot.thumbnailState = "unavailable";
                }
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
        tracking.replacePersisted(keys);
        trackingLoaded = true;
    }

    public boolean tracks(MemoryKey key) {
        if (!isActive()) return false;
        String canonical = key.canonical();
        return tracking.tracks(canonical);
    }
    public boolean hasCards() { return isActive() && tracking.hasAny(); }
    public boolean hasTrackedCardsReady() { return ensureTrackingLoaded() && hasCards(); }

    public void noteCaptureGap() {
        if (isActive()) revocations().edit().putBoolean("gap_" + userId, true).commit();
    }
    public boolean hasCaptureGap() { return revocations().getBoolean("gap_" + userId, false); }

    private boolean ensureTrackingLoaded() {
        if (trackingLoaded) return true;
        try {
            Future<Boolean> load = QUEUE.submit(() -> { requireActive(); read(); return true; });
            return load.get(5, TimeUnit.SECONDS);
        } catch (Exception error) {
            noteCaptureGap();
            return false;
        }
    }

    /** Persist an edit before Telegram replaces its own stored revision. */
    public boolean journalEdit(MemoryCapture capture) {
        if (capture == null || capture.key.userId != userId || !ensureTrackingLoaded() || !tracks(capture.key)) return false;
        try {
            String identity = capture.key.canonical() + ":" + capture.snapshot.fingerprint;
            JSONObject event = new JSONObject().put("id", MemoryJournalPolicy.eventId("edit", identity))
                    .put("type", "edit").put("capture", capture.toJournalJson());
            return appendJournalAndReplay(event);
        } catch (Exception error) {
            noteCaptureGap();
            return false;
        }
    }

    /** Persist an allowlisted new-message batch before Telegram commits its own rows. */
    public boolean journalNew(ArrayList<MemoryCapture> captures) {
        if (captures == null || captures.isEmpty() || captures.size() > MemoryJournalPolicy.MAX_EVENTS) return false;
        try {
            ArrayList<JSONObject> events = new ArrayList<>();
            ArrayList<String> pending = new ArrayList<>();
            for (MemoryCapture capture : captures) {
                if (capture == null || capture.key.userId != userId) throw new IOException("Account mismatch");
                String identity = capture.key.canonical() + ":" + capture.snapshot.fingerprint;
                events.add(new JSONObject().put("id", MemoryJournalPolicy.eventId("new", identity))
                        .put("type", "new").put("capture", capture.toJournalJson()));
                pending.add(capture.key.canonical());
            }
            return appendJournalAndReplay(events, pending);
        } catch (Exception error) {
            noteCaptureGap();
            return false;
        }
    }

    /** Persist standard delete IDs before Telegram applies the server deletion. */
    public boolean journalDelete(long channelId, ArrayList<Integer> messageIds) {
        if (channelId < 0 || messageIds == null || messageIds.isEmpty() || messageIds.size() > 10000
                || !ensureTrackingLoaded() || !hasCards()) return false;
        try {
            JSONArray ids = new JSONArray();
            for (Integer id : messageIds) {
                if (id == null || id <= 0) throw new IOException("Invalid deleted message ID");
                ids.put(id);
            }
            String identity = channelId + ":" + ids;
            JSONObject event = new JSONObject().put("id", MemoryJournalPolicy.eventId("delete", identity))
                    .put("type", "delete").put("channel", channelId).put("messages", ids);
            return appendJournalAndReplay(event);
        } catch (Exception error) {
            noteCaptureGap();
            return false;
        }
    }

    /** Persist a channel history truncation before Telegram clears its rows. */
    public boolean journalHistoryClear(long dialogId, int maxMessageId) {
        if (dialogId >= 0 || maxMessageId <= 0 || !ensureTrackingLoaded() || !hasCards()) return false;
        try {
            String identity = dialogId + ":" + maxMessageId;
            JSONObject event = new JSONObject().put("id", MemoryJournalPolicy.eventId("history", identity))
                    .put("type", "history").put("dialog", dialogId).put("max", maxMessageId);
            return appendJournalAndReplay(event);
        } catch (Exception error) {
            noteCaptureGap();
            return false;
        }
    }

    private boolean appendJournalAndReplay(JSONObject event) throws Exception {
        ArrayList<JSONObject> events = new ArrayList<>(); events.add(event);
        return appendJournalAndReplay(events);
    }

    private boolean appendJournalAndReplay(ArrayList<JSONObject> events) throws Exception {
        return appendJournalAndReplay(events, null);
    }

    private boolean appendJournalAndReplay(ArrayList<JSONObject> events, ArrayList<String> pending) throws Exception {
        if (events.isEmpty()) return true;
        boolean allRecent = true;
        synchronized (this) {
            for (JSONObject event : events) {
                if (!recentJournal.contains(event.getString("id"))) { allRecent = false; break; }
            }
        }
        if (allRecent) return true;
        Future<Boolean> append = JOURNAL_QUEUE.submit(() -> appendJournal(events));
        boolean stored = append.get(5, TimeUnit.SECONDS);
        if (stored) {
            if (pending != null) tracking.addPending(pending);
            scheduleJournalReplay();
        }
        else noteCaptureGap();
        return stored;
    }

    private ArrayList<JSONObject> readJournal() throws Exception {
        ArrayList<JSONObject> events = new ArrayList<>();
        if (!journal.getBaseFile().exists() && !new File(journal.getBaseFile() + ".bak").exists()) return events;
        byte[] encrypted;
        try (FileInputStream input = journal.openRead()) {
            encrypted = readBounded(input, MemoryJournalPolicy.MAX_BYTES + 256);
        }
        byte[] plain = cipher().decrypt(encrypted, aad("journal"));
        if (plain.length > MemoryJournalPolicy.MAX_BYTES) throw new IOException("Memory journal limit exceeded");
        JSONObject root = new JSONObject(new String(plain, StandardCharsets.UTF_8));
        if (root.getInt("schema") != 1 || root.getLong("user") != userId) throw new IOException("Unsupported Memory journal");
        JSONArray source = root.getJSONArray("events");
        if (source.length() > MemoryJournalPolicy.MAX_EVENTS) throw new IOException("Memory journal event limit exceeded");
        HashSet<String> identities = new HashSet<>();
        for (int i = 0; i < source.length(); i++) {
            JSONObject event = source.getJSONObject(i);
            String id = event.getString("id"), type = event.getString("type");
            if (!MemoryJournalPolicy.safeEventId(id) || !("new".equals(type) || "edit".equals(type) || "delete".equals(type) || "history".equals(type))
                    || !identities.add(id) || event.toString().getBytes(StandardCharsets.UTF_8).length > MemoryJournalPolicy.MAX_EVENT_BYTES) {
                throw new IOException("Invalid Memory journal event");
            }
            events.add(event);
        }
        return events;
    }

    private byte[] journalPlain(ArrayList<JSONObject> events) throws Exception {
        JSONArray values = new JSONArray();
        for (JSONObject event : events) values.put(event);
        return new JSONObject().put("schema", 1).put("user", userId).put("events", values)
                .toString().getBytes(StandardCharsets.UTF_8);
    }

    private void writeJournal(ArrayList<JSONObject> events) throws Exception {
        requireActive();
        byte[] plain = journalPlain(events);
        if (plain.length > MemoryJournalPolicy.MAX_BYTES) throw new IOException("Memory journal storage limit reached");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Memory storage unavailable");
        if (directory.getUsableSpace() < plain.length + MemoryPolicy.MIN_FREE_BYTES) throw new IOException("Not enough free storage");
        byte[] encrypted = cipher().encrypt(plain, aad("journal"));
        requireActive();
        if (usedBytes() - journal.getBaseFile().length() + encrypted.length > MemoryPolicy.MAX_ACCOUNT_BYTES) {
            throw new IOException("Memory account storage limit reached");
        }
        writeAtomic(journal, encrypted);
    }

    private boolean appendJournal(JSONObject event) throws Exception {
        ArrayList<JSONObject> additions = new ArrayList<>(); additions.add(event);
        return appendJournal(additions);
    }

    private boolean appendJournal(ArrayList<JSONObject> additions) throws Exception {
        ArrayList<JSONObject> events = readJournal();
        HashSet<String> ids = new HashSet<>();
        for (JSONObject existing : events) ids.add(existing.getString("id"));
        for (JSONObject addition : additions) {
            String id = addition.getString("id");
            if (ids.contains(id)) continue;
            int currentBytes = journalPlain(events).length;
            int eventBytes = addition.toString().getBytes(StandardCharsets.UTF_8).length;
            if (!MemoryJournalPolicy.canAppend(events.size(), currentBytes, eventBytes)) return false;
            events.add(addition); ids.add(id);
        }
        writeJournal(events);
        return true;
    }

    private void removeJournal(String eventId) throws Exception {
        ArrayList<JSONObject> events = readJournal();
        boolean changed = false;
        for (int i = events.size() - 1; i >= 0; i--) {
            if (eventId.equals(events.get(i).getString("id"))) { events.remove(i); changed = true; }
        }
        if (changed) writeJournal(events);
    }

    private void scheduleJournalReplay() {
        synchronized (this) {
            if (replayScheduled || !isActive()) return;
            replayScheduled = true;
        }
        QUEUE.execute(() -> {
            boolean replayed = replayJournalInternal();
            synchronized (MorokMemoryStore.this) { replayScheduled = false; }
            if (replayed) {
                try {
                    boolean pending = JOURNAL_QUEUE.submit(() -> !readJournal().isEmpty()).get(5, TimeUnit.SECONDS);
                    if (pending) scheduleJournalReplay();
                } catch (Exception error) { noteCaptureGap(); }
            }
        });
    }

    private boolean replayJournalInternal() {
        try {
            ArrayList<JSONObject> events = JOURNAL_QUEUE.submit(this::readJournal).get(5, TimeUnit.SECONDS);
            for (JSONObject event : events) {
                requireActive();
                String type = event.getString("type");
                String completedPendingKey = null;
                if ("new".equals(type)) {
                    MemoryCapture capture = MemoryCapture.fromJournalJson(event.getJSONObject("capture"));
                    completedPendingKey = capture.key.canonical();
                    saveInternal(capture, SAVE_AUTOMATIC_NEW);
                } else if ("edit".equals(type)) {
                    saveInternal(MemoryCapture.fromJournalJson(event.getJSONObject("capture")), SAVE_TRACKED_UPDATE);
                } else if ("delete".equals(type)) {
                    long channelId = event.getLong("channel");
                    JSONArray source = event.getJSONArray("messages");
                    if (channelId < 0 || source.length() == 0 || source.length() > 10000) throw new IOException("Invalid delete journal event");
                    ArrayList<Integer> ids = new ArrayList<>();
                    for (int i = 0; i < source.length(); i++) {
                        int messageId = source.getInt(i);
                        if (messageId <= 0) throw new IOException("Invalid delete journal message ID");
                        ids.add(messageId);
                    }
                    markDeletedInternal(channelId, ids);
                } else {
                    long dialogId = event.getLong("dialog"); int maxMessageId = event.getInt("max");
                    if (dialogId >= 0 || maxMessageId <= 0) throw new IOException("Invalid history journal event");
                    markDialogDeletedInternal(dialogId, maxMessageId);
                }
                JOURNAL_QUEUE.submit(() -> { removeJournal(event.getString("id")); return true; })
                        .get(5, TimeUnit.SECONDS);
                if (completedPendingKey != null) tracking.completePending(completedPendingKey);
                rememberJournalEvent(event.getString("id"));
            }
            return true;
        } catch (Exception error) {
            noteCaptureGap();
            return false;
        }
    }

    private synchronized void rememberJournalEvent(String eventId) {
        if (!recentJournal.add(eventId)) return;
        recentJournalOrder.addLast(eventId);
        while (recentJournalOrder.size() > 128) recentJournal.remove(recentJournalOrder.removeFirst());
    }

    private void write(Database database) throws Exception {
        requireActive();
        enforceAutomaticPolicy(database, archiveSettings());
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
        referenced.add("journal.tink"); referenced.add("journal.tink.bak");
        for (MemoryCard card : database.cards) for (MemoryCard.Snapshot snapshot : card.versions) {
            if (!snapshot.blob.isEmpty()) referenced.add(snapshot.blob + ".tink");
            if (!snapshot.thumbnailBlob.isEmpty()) referenced.add(snapshot.thumbnailBlob + ".tink");
        }
        File[] files = directory.listFiles();
        if (files != null) for (File file : files) if (!referenced.contains(file.getName())) file.delete();
    }

    private <T> void execute(Operation<T> operation, Callback<T> callback) {
        QUEUE.execute(() -> {
            T result = null; String error = null;
            try { requireActive(); replayJournalInternal(); result = operation.run(); requireActive(); }
            catch (Exception exception) { error = "Memory storage unavailable. Check free space and the account encryption key."; }
            final T value = result; final String failure = error;
            if (callback != null) AndroidUtilities.runOnUIThread(() -> { if (isActive()) callback.done(value, failure); });
        });
    }

    public void list(Callback<ArrayList<MemoryCard>> callback) {
        execute(() -> {
            Database database = read();
            if (enforceAutomaticPolicy(database, archiveSettings())) write(database); else cleanup(database);
            MorokMemoryReminderReceiver.scheduleNext(userId, database.cards);
            Collections.sort(database.cards, (a, b) -> Long.compare(b.createdAt, a.createdAt));
            return database.cards;
        }, callback);
    }

    public void save(MemoryCapture capture, boolean explicitUserAction, Callback<MemoryCard> callback) {
        execute(() -> saveInternal(capture, explicitUserAction ? SAVE_MANUAL : SAVE_TRACKED_UPDATE), callback);
    }

    /** Retain one explicit cache-only import in a single index commit. Existing manual cards stay manual. */
    public void importLocal(ArrayList<MemoryCapture> captures, Callback<Integer> callback) {
        execute(() -> {
            if (captures == null || captures.size() > MemoryPolicy.MAX_LOCAL_HISTORY_IMPORT) {
                throw new IOException("Invalid local history batch");
            }
            Database database = read();
            int retained = 0;
            boolean changed = false;
            for (MemoryCapture capture : captures) {
                requireActive();
                if (capture == null || capture.key.userId != userId) throw new IOException("Account mismatch");
                MemoryCard existing = null;
                for (MemoryCard card : database.cards) if (card.key.equals(capture.key)) { existing = card; break; }
                if (existing == null) {
                    try { pruneForInsertion(database, true); }
                    catch (IOException full) { continue; }
                    database.tombstones.remove(capture.key.canonical());
                    existing = new MemoryCard(UUID.randomUUID().toString(), capture.key);
                    existing.createdAt = System.currentTimeMillis();
                    existing.source = capture.source; existing.sender = capture.sender;
                    existing.automatic = true; existing.imported = true;
                    database.cards.add(existing); changed = true;
                }
                boolean duplicate = false;
                for (MemoryCard.Snapshot version : existing.versions) {
                    if (!version.fingerprint.equals(capture.snapshot.fingerprint)) continue;
                    duplicate = true;
                    if (!"saved".equals(version.fileState) || !"saved".equals(version.thumbnailState)) {
                        String previous = version.fileState, previousThumbnail = version.thumbnailState;
                        copyAttachment(capture.message, version, database, existing.automatic);
                        changed |= !previous.equals(version.fileState) || !previousThumbnail.equals(version.thumbnailState);
                    }
                    break;
                }
                if (!duplicate) {
                    copyAttachment(capture.message, capture.snapshot, database, existing.automatic);
                    int position = existing.versions.size();
                    while (position > 0 && !MemoryPolicy.becomesLatest(capture.snapshot.editedAt,
                            existing.versions.get(position - 1).editedAt)) position--;
                    existing.versions.add(position, capture.snapshot);
                    while (existing.versions.size() > MemoryPolicy.MAX_VERSIONS) existing.versions.remove(0);
                    changed = true;
                }
                retained++;
            }
            if (changed) write(database); else cleanup(database);
            return retained;
        }, callback);
    }

    private MemoryCard saveInternal(MemoryCapture capture, int mode) throws Exception {
        if (capture.key.userId != userId) throw new IOException("Account mismatch");
        Database database = read();
        MemoryCard existing = null;
        for (MemoryCard card : database.cards) if (card.key.equals(capture.key)) { existing = card; break; }
        if (mode == SAVE_TRACKED_UPDATE && (existing == null || database.tombstones.contains(capture.key.canonical()))) return null;
        if (mode == SAVE_AUTOMATIC_NEW && database.tombstones.contains(capture.key.canonical())) return null;
        if (existing == null) {
            try { pruneForInsertion(database, mode == SAVE_AUTOMATIC_NEW); }
            catch (IOException full) {
                if (mode == SAVE_AUTOMATIC_NEW) return null;
                throw full;
            }
            existing = new MemoryCard(UUID.randomUUID().toString(), capture.key);
            existing.createdAt = System.currentTimeMillis(); existing.source = capture.source; existing.sender = capture.sender;
            existing.automatic = mode == SAVE_AUTOMATIC_NEW;
            database.cards.add(existing);
        }
        boolean promoted = mode == SAVE_MANUAL && existing.automatic;
        if (mode == SAVE_MANUAL) {
            database.tombstones.remove(capture.key.canonical());
            existing.automatic = false; existing.imported = false;
        }
        for (MemoryCard.Snapshot version : existing.versions) {
            if (version.fingerprint.equals(capture.snapshot.fingerprint)) {
                // Explicit retry may preserve an original that has since completed downloading.
                if (mode == SAVE_MANUAL && (!"saved".equals(version.fileState)
                        || !"saved".equals(version.thumbnailState))) {
                    copyAttachment(capture.message, version, database, false); write(database);
                } else if (promoted) {
                    write(database);
                }
                return existing;
            }
        }
        copyAttachment(capture.message, capture.snapshot, database,
                mode == SAVE_AUTOMATIC_NEW || mode == SAVE_TRACKED_UPDATE && existing.automatic);
        int position = existing.versions.size();
        while (position > 0 && !MemoryPolicy.becomesLatest(capture.snapshot.editedAt, existing.versions.get(position - 1).editedAt)) position--;
        existing.versions.add(position, capture.snapshot);
        while (existing.versions.size() > MemoryPolicy.MAX_VERSIONS) existing.versions.remove(0);
        write(database);
        return existing;
    }

    private void pruneForInsertion(Database database, boolean automatic) throws IOException {
        enforceAutomaticPolicy(database, archiveSettings());
        if (automatic) {
            while (automaticCount(database) >= MemoryPolicy.MAX_AUTOMATIC_CARDS) {
                if (!removeOldestAutomatic(database)) throw new IOException("Automatic archive limit reached");
            }
        }
        while (database.cards.size() >= MemoryPolicy.MAX_CARDS) {
            if (!removeOldestAutomatic(database)) throw new IOException("Memory card limit reached");
        }
    }

    private static boolean pruneExpiredAutomatic(Database database, long retentionMillis) {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (int i = database.cards.size() - 1; i >= 0; i--) {
            MemoryCard card = database.cards.get(i);
            if (card.automatic && MemoryPolicy.automaticExpired(card.createdAt, now, retentionMillis)) {
                database.cards.remove(i); changed = true;
            }
        }
        return changed;
    }

    private boolean enforceAutomaticPolicy(Database database, ArchiveSettings settings) {
        boolean changed = pruneExpiredAutomatic(database, settings.retentionMillis());
        while (estimatedUsedBytes(database) > settings.storageBytes()) {
            if (!removeOldestAutomatic(database)) break;
            changed = true;
        }
        return changed;
    }

    private ArchiveSettings archiveSettings() {
        try { return MorokSettings.archive(account); }
        catch (RuntimeException unavailable) { return ArchiveSettings.DEFAULT; }
    }

    private static int automaticCount(Database database) {
        int count = 0;
        for (MemoryCard card : database.cards) if (card.automatic) count++;
        return count;
    }

    private static boolean removeOldestAutomatic(Database database) {
        int oldest = -1;
        for (int i = 0; i < database.cards.size(); i++) {
            MemoryCard card = database.cards.get(i);
            if (card.automatic && (oldest < 0 || card.createdAt < database.cards.get(oldest).createdAt)) oldest = i;
        }
        if (oldest < 0) return false;
        database.cards.remove(oldest);
        return true;
    }

    private void copyAttachment(TLRPC.Message message, MemoryCard.Snapshot snapshot, Database database,
            boolean automatic) {
        TLRPC.Document document = MessageObject.getDocument(message);
        boolean photo = message.media instanceof TLRPC.TL_messageMediaPhoto;
        if (document == null && !photo) return;
        boolean originalAlreadySaved = "saved".equals(snapshot.fileState) && safeId(snapshot.blob);
        copyThumbnail(message, snapshot, database, automatic);
        if (originalAlreadySaved) return;
        String messageFileName = FileLoader.getMessageFileName(message);
        snapshot.fileState = messageFileName.isEmpty() ? "download_unavailable"
                : FileLoader.getInstance(account).isLoadingFile(messageFileName) ? "downloading" : "not_downloaded";
        snapshot.blob = ""; snapshot.sha256 = ""; snapshot.fileSize = 0;
        snapshot.fileName = document != null ? FileLoader.getDocumentFileName(document) : "photo.jpg";
        if (snapshot.fileName.isEmpty()) snapshot.fileName = "attachment";
        snapshot.mime = document != null && document.mime_type != null ? document.mime_type : "image/jpeg";
        try {
            File source = message.attachPath == null || message.attachPath.isEmpty() ? null : new File(message.attachPath);
            if (source == null || !source.isFile()) source = FileLoader.getInstance(account).getPathToMessage(message);
            if (source == null || !source.isFile() || source.getName().endsWith(".enc")) return;
            long size = source.length();
            snapshot.fileSize = size;
            ArchiveSettings policy = archiveSettings();
            long attachmentLimit = automatic ? policy.attachmentBytes() : MemoryPolicy.MAX_ATTACHMENT_BYTES;
            long storageLimit = automatic ? policy.storageBytes() : MemoryPolicy.MAX_ACCOUNT_BYTES;
            if (size > attachmentLimit) { snapshot.fileState = "too_large"; return; }
            if (automatic && !policy.allowsAutomaticAttachment(isUnmeteredNetwork())) {
                snapshot.fileState = "policy_blocked";
                return;
            }
            if (document != null && document.size > 0 && size != document.size) return;
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Storage unavailable");
            byte[] plain;
            try (FileInputStream stream = new FileInputStream(source)) { plain = readBounded(stream, (int) MemoryPolicy.MAX_ATTACHMENT_BYTES); }
            if (plain.length != size || source.length() != size) return;
            String hash = MemoryCapture.hex(MessageDigest.getInstance("SHA-256").digest(plain));
            String id = retainBlob(plain, hash, database, attachmentLimit, storageLimit);
            if (id == null) { snapshot.fileState = "storage_error"; return; }
            snapshot.blob = id; snapshot.sha256 = hash; snapshot.fileSize = size; snapshot.fileState = "saved";
        } catch (Exception error) { snapshot.fileState = "storage_error"; }
    }

    /** Retains only a complete, recognized image already present in Telegram's local cache or TL cached bytes. */
    private void copyThumbnail(TLRPC.Message message, MemoryCard.Snapshot snapshot, Database database,
            boolean automatic) {
        if ("saved".equals(snapshot.thumbnailState) && safeId(snapshot.thumbnailBlob)) return;
        ArrayList<TLRPC.PhotoSize> sizes = null;
        if (message.media instanceof TLRPC.TL_messageMediaPhoto && message.media.photo != null) {
            sizes = message.media.photo.sizes;
        } else {
            TLRPC.Document document = MessageObject.getDocument(message);
            if (document != null) sizes = document.thumbs;
        }
        if (sizes == null || sizes.isEmpty()) return;
        TLRPC.PhotoSize thumbnail = FileLoader.getClosestPhotoSizeWithSize(sizes, 320, false, null, true);
        if (thumbnail == null) return;
        snapshot.thumbnailState = "unavailable";
        snapshot.thumbnailBlob = ""; snapshot.thumbnailSha256 = ""; snapshot.thumbnailSize = 0;
        try {
            byte[] plain = null;
            if (thumbnail instanceof TLRPC.TL_photoCachedSize && thumbnail.bytes != null) {
                plain = thumbnail.bytes.clone();
            } else {
                File source = FileLoader.getInstance(account).getPathToAttach(thumbnail, true);
                if (source == null || !source.isFile()) source = FileLoader.getInstance(account).getPathToAttach(thumbnail, false);
                if (source == null || !source.isFile() || source.getName().endsWith(".enc")
                        || source.length() <= 0 || source.length() > MemoryPolicy.MAX_THUMBNAIL_BYTES) return;
                long size = source.length();
                try (FileInputStream stream = new FileInputStream(source)) {
                    plain = readBounded(stream, MemoryPolicy.MAX_THUMBNAIL_BYTES);
                }
                if (plain.length != size || source.length() != size) return;
            }
            String mime = MemoryThumbnailPolicy.mimeType(plain);
            if (mime.isEmpty()) return;
            String hash = MemoryCapture.hex(MessageDigest.getInstance("SHA-256").digest(plain));
            long storageLimit = automatic ? archiveSettings().storageBytes() : MemoryPolicy.MAX_ACCOUNT_BYTES;
            String id = retainBlob(plain, hash, database, MemoryPolicy.MAX_THUMBNAIL_BYTES, storageLimit);
            if (id == null) { snapshot.thumbnailState = "storage_error"; return; }
            snapshot.thumbnailBlob = id; snapshot.thumbnailSha256 = hash; snapshot.thumbnailSize = plain.length;
            snapshot.thumbnailMime = mime; snapshot.thumbnailName = "thumbnail" + MemoryThumbnailPolicy.extension(mime);
            snapshot.thumbnailState = "saved";
        } catch (Exception error) {
            snapshot.thumbnailState = "storage_error";
        }
    }

    /** Reuses equal encrypted data inside one account; metadata and references stay in the encrypted index. */
    private String retainBlob(byte[] plain, String hash, Database database,
            long objectLimit, long storageLimit) throws Exception {
        for (MemoryCard card : database.cards) for (MemoryCard.Snapshot version : card.versions) {
            if (hash.equals(version.sha256) && "saved".equals(version.fileState) && safeId(version.blob)) return version.blob;
            if (hash.equals(version.thumbnailSha256) && "saved".equals(version.thumbnailState)
                    && safeId(version.thumbnailBlob)) return version.thumbnailBlob;
        }
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Storage unavailable");
        if (!MemoryPolicy.canCopy(plain.length, estimatedUsedBytes(database), directory.getUsableSpace(),
                objectLimit, storageLimit)) return null;
        String id = UUID.randomUUID().toString();
        byte[] encrypted = cipher().encrypt(plain, aad("blob/" + id));
        requireActive();
        writeAtomic(new AtomicFile(new File(directory, id + ".tink")), encrypted);
        return id;
    }

    /** Explicitly retries a retained version from Telegram's current local cache; no network request is made. */
    public void retryAttachment(String cardId, String fingerprint, Callback<MemoryCard> callback) {
        execute(() -> {
            Database database = read();
            MemoryCard card = find(database, cardId);
            if (card.restored) throw new IOException("Restored Memory card has no Telegram cache source");
            MemoryCard.Snapshot target = null;
            for (MemoryCard.Snapshot snapshot : card.versions) {
                if (snapshot.fingerprint.equals(fingerprint)) { target = snapshot; break; }
            }
            if (target == null) throw new IOException("Memory version no longer exists");
            if ("saved".equals(target.fileState) && "saved".equals(target.thumbnailState)) return card;
            TLRPC.Message message = MemoryCapture.restoreCachedMessage(account, card.key, target);
            copyAttachment(message, target, database, false);
            write(database);
            return card;
        }, callback);
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
            if (!card.restored) {
                if (database.tombstones.size() >= MemoryPolicy.MAX_TOMBSTONES) throw new IOException("Memory removal limit reached");
                database.tombstones.add(card.key.canonical());
            }
            database.cards.remove(card); write(database);
            MorokMemoryReminderReceiver.cancelCard(userId, card.id); return true;
        }, callback);
    }

    public void clear(Callback<Boolean> callback) {
        execute(() -> {
            Database database = read();
            int tombstonesNeeded = 0;
            for (MemoryCard card : database.cards) if (!card.restored) tombstonesNeeded++;
            if (database.tombstones.size() + tombstonesNeeded > MemoryPolicy.MAX_TOMBSTONES) throw new IOException("Memory removal limit reached");
            for (MemoryCard card : database.cards) if (!card.restored) database.tombstones.add(card.key.canonical());
            database.cards.clear(); write(database); MorokMemoryReminderReceiver.cancel(userId); return true;
        }, callback);
    }

    /** Explicitly removes only automatic cards outside the selected age/storage policy. */
    public void cleanAutomaticArchive(Callback<ArchiveCleanupResult> callback) {
        execute(() -> {
            Database database = read();
            int beforeCards = automaticCount(database);
            long beforeBytes = usedBytes();
            boolean changed = enforceAutomaticPolicy(database, archiveSettings());
            if (changed) write(database); else cleanup(database);
            return new ArchiveCleanupResult(beforeCards - automaticCount(database), beforeBytes, usedBytes());
        }, callback);
    }

    /** Standard deleteMessages IDs are account-global outside channels; channel IDs use their channel namespace. */
    public void markDeleted(long channelId, ArrayList<Integer> messageIds) { markDeleted(channelId, messageIds, null); }
    public void markDeleted(long channelId, ArrayList<Integer> messageIds, Callback<Boolean> callback) {
        execute(() -> markDeletedInternal(channelId, messageIds), callback);
    }

    private boolean markDeletedInternal(long channelId, ArrayList<Integer> messageIds) throws Exception {
        final HashSet<Integer> ids = new HashSet<>(messageIds);
        Database database = read(); boolean changed = false;
        for (MemoryCard card : database.cards) {
            if (card.restored) continue;
            boolean matchingPeer = channelId == 0 ? !"channel".equals(card.key.peerKind)
                    : "channel".equals(card.key.peerKind) && card.key.peerId == channelId;
            if (matchingPeer && ids.contains(card.key.messageId) && !card.deletedInTelegram) {
                card.deletedInTelegram = true; changed = true;
            }
        }
        if (changed) write(database);
        return changed;
    }

    public void markDialogDeleted(long dialogId) { markDialogDeleted(dialogId, Integer.MAX_VALUE, null); }
    public void markDialogDeleted(long dialogId, int maxMessageId, Callback<Boolean> callback) {
        execute(() -> markDialogDeletedInternal(dialogId, maxMessageId), callback);
    }

    private boolean markDialogDeletedInternal(long dialogId, int maxMessageId) throws Exception {
        Database database = read(); boolean changed = false;
        for (MemoryCard card : database.cards) {
            if (card.restored) continue;
            if (card.key.dialogId() == dialogId && card.key.messageId <= maxMessageId && !card.deletedInTelegram) {
                card.deletedInTelegram = true; changed = true;
            }
        }
        if (changed) write(database);
        return changed;
    }

    public void storageStats(Callback<MemoryStorageStats> callback) {
        execute(() -> {
            Database database = read();
            cleanup(database);
            MemoryStorageStats stats = new MemoryStorageStats(usedBytes());
            for (MemoryCard card : database.cards) {
                stats.addCard(card.automatic);
                for (MemoryCard.Snapshot snapshot : card.versions) {
                    stats.addSnapshot(snapshot.fileState, snapshot.blob);
                    stats.addThumbnail(snapshot.thumbnailState, snapshot.thumbnailBlob);
                }
            }
            return stats;
        }, callback);
    }

    /** Writes plaintext only to the user-selected SAF destination; no decrypted temporary file is created. */
    public void exportSelected(Uri destination, ArrayList<String> cardIds, Callback<ExportResult> callback) {
        execute(() -> {
            if (destination == null || !"content".equals(destination.getScheme()) || cardIds == null
                    || !MemoryExportPolicy.validSelectionSize(cardIds.size())) {
                throw new IOException("Invalid Memory export selection");
            }
            HashSet<String> uniqueIds = new HashSet<>(cardIds);
            if (uniqueIds.size() != cardIds.size()) throw new IOException("Duplicate Memory export selection");
            Database database = read();
            ArrayList<MemoryCard> selected = new ArrayList<>();
            for (String id : cardIds) {
                if (!safeId(id)) throw new IOException("Invalid Memory export identity");
                selected.add(find(database, id));
            }
            try {
                return writeExport(destination, selected);
            } catch (Exception error) {
                try { ApplicationLoader.applicationContext.getContentResolver().delete(destination, null, null); }
                catch (RuntimeException ignored) { }
                throw error;
            }
        }, callback);
    }

    /** Fully validates a user-selected plaintext ZIP before the UI asks for import confirmation. */
    public void inspectPortableImport(Uri source, Callback<ImportPreview> callback) {
        execute(() -> {
            Database database = read();
            PortableImport parsed = readPortableImport(source, database, false, null);
            return new ImportPreview(parsed.token, parsed.cards.size(), parsed.duplicateCards,
                    parsed.files, parsed.bytes);
        }, callback);
    }

    /** Revalidates the exact previewed content and encrypts it into the active account in one index commit. */
    public void importPortable(Uri source, String previewToken, Callback<ImportResult> callback) {
        execute(() -> {
            if (!MemoryImportPolicy.validToken(previewToken)) throw new IOException("Invalid Memory import preview");
            Database database = read();
            try {
                PortableImport parsed = readPortableImport(source, database, true, previewToken);
                if (parsed.importedCards > 0) write(database); else cleanup(database);
                return new ImportResult(parsed.importedCards, parsed.duplicateCards, parsed.files, parsed.bytes);
            } catch (Exception error) {
                // Any blob written before a provider change or validation failure is unreferenced by the
                // committed index and is removed before the failure is reported.
                try { cleanup(read()); } catch (Exception ignored) { }
                throw error;
            }
        }, callback);
    }

    private PortableImport readPortableImport(Uri source, Database database, boolean retain,
            String expectedToken) throws Exception {
        if (source == null || !"content".equals(source.getScheme())) {
            throw new IOException("Memory import requires a SAF content URI");
        }
        requireExportAllowed();
        InputStream raw = ApplicationLoader.applicationContext.getContentResolver().openInputStream(source);
        if (raw == null) throw new IOException("Memory import source unavailable");
        MessageDigest archiveDigest = MessageDigest.getInstance("SHA-256");
        PortableImport parsed;
        HashSet<String> seen = new HashSet<>();
        try (InputStream input = raw; ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry manifestEntry = zip.getNextEntry();
            if (manifestEntry == null || manifestEntry.isDirectory() || !"memory.json".equals(manifestEntry.getName())) {
                throw new IOException("Portable Memory manifest must be the first ZIP entry");
            }
            byte[] manifest = readPortableEntry(zip, MemoryImportPolicy.MAX_MANIFEST_BYTES);
            zip.closeEntry();
            updatePortableDigest(archiveDigest, "memory.json", manifest);
            seen.add("memory.json");
            parsed = parsePortableManifest(manifest, database);
            parsed.bytes = manifest.length;
            if (retain) preparePortableCards(parsed, database, expectedToken, true);

            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                requireExportAllowed();
                if (entry.isDirectory() || seen.size() >= MemoryImportPolicy.MAX_ENTRY_COUNT
                        || !seen.add(entry.getName())) throw new IOException("Invalid or duplicate Memory ZIP entry");
                PortableBlob blob = parsed.blobs.get(entry.getName());
                if (blob == null) throw new IOException("Unreferenced Memory ZIP entry");
                int limit = blob.thumbnail ? MemoryPolicy.MAX_THUMBNAIL_BYTES : (int) MemoryPolicy.MAX_ATTACHMENT_BYTES;
                byte[] plain = readPortableEntry(zip, limit);
                zip.closeEntry();
                updatePortableDigest(archiveDigest, entry.getName(), plain);
                if (plain.length != blob.size || !blob.sha256.equals(MemoryCapture.hex(
                        MessageDigest.getInstance("SHA-256").digest(plain)))) {
                    throw new IOException("Portable Memory attachment integrity check failed");
                }
                if (blob.thumbnail && !blob.mime.equals(MemoryThumbnailPolicy.mimeType(plain))) {
                    throw new IOException("Portable Memory thumbnail type mismatch");
                }
                parsed.bytes += plain.length;
                if (parsed.bytes > MemoryImportPolicy.MAX_UNCOMPRESSED_BYTES) {
                    throw new IOException("Portable Memory import is too large");
                }
                parsed.files++;
                if (retain && blob.needed) retainPortableBlob(database, blob, plain);
            }
        }
        if (parsed.files != parsed.blobs.size() || seen.size() != parsed.blobs.size() + 1) {
            throw new IOException("Portable Memory ZIP is incomplete");
        }
        parsed.token = MemoryCapture.hex(archiveDigest.digest());
        if (expectedToken != null && !MessageDigest.isEqual(expectedToken.getBytes(StandardCharsets.US_ASCII),
                parsed.token.getBytes(StandardCharsets.US_ASCII))) {
            throw new IOException("Portable Memory source changed after preview");
        }
        if (!retain) preparePortableCards(parsed, database, parsed.token, false);
        requireExportAllowed();
        return parsed;
    }

    private PortableImport parsePortableManifest(byte[] bytes, Database database) throws Exception {
        JSONObject manifest = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        if (manifest.getInt("schema") != 1 || !"MOROK Memory".equals(manifest.getString("product"))) {
            throw new IOException("Unsupported portable Memory manifest");
        }
        JSONArray cards = manifest.getJSONArray("cards");
        if (!MemoryExportPolicy.validSelectionSize(cards.length())) {
            throw new IOException("Invalid portable Memory card count");
        }
        HashSet<String> exportIds = new HashSet<>();
        HashSet<String> candidateKeys = new HashSet<>();
        HashSet<Long> occupiedPeers = new HashSet<>();
        for (MemoryCard existing : database.cards) if ("portable".equals(existing.key.peerKind)) {
            occupiedPeers.add(existing.key.peerId);
        }
        HashMap<String, Long> portablePeers = new HashMap<>();
        PortableImport parsed = new PortableImport();
        for (int cardIndex = 0; cardIndex < cards.length(); cardIndex++) {
            JSONObject value = cards.getJSONObject(cardIndex);
            String exportId = value.getString("id");
            if (!safeId(exportId) || !exportIds.add(exportId)) throw new IOException("Invalid portable Memory card ID");
            String peerKind = value.getString("peerKind");
            long peerId = value.getLong("peerId");
            int messageId = value.getInt("messageId");
            long topicId = value.optLong("topicId");
            if (!MemoryImportPolicy.validPeerKind(peerKind) || peerId <= 0 || messageId <= 0 || topicId < 0) {
                throw new IOException("Invalid portable Memory source identity");
            }
            String sourcePeer = peerKind + ":" + peerId;
            Long portablePeer = portablePeers.get(sourcePeer);
            if (portablePeer == null) {
                portablePeer = newPortablePeerId(occupiedPeers);
                portablePeers.put(sourcePeer, portablePeer);
            }
            MemoryCard card = new MemoryCard(UUID.randomUUID().toString(),
                    new MemoryKey(userId, "portable", portablePeer, messageId, topicId));
            if (!candidateKeys.add(card.key.canonical())) throw new IOException("Duplicate portable Memory source card");
            card.source = boundedImportString(value.getString("source"), MemoryImportPolicy.MAX_SOURCE_CHARS,
                    MemoryPolicy.MAX_MESSAGE_BYTES, "source");
            card.sender = boundedImportString(value.getString("sender"), MemoryImportPolicy.MAX_SENDER_CHARS,
                    MemoryPolicy.MAX_MESSAGE_BYTES, "sender");
            card.note = boundedImportString(value.optString("note"), MemoryImportPolicy.MAX_NOTE_CHARS,
                    MemoryPolicy.MAX_MESSAGE_BYTES, "note");
            card.tags = boundedImportString(value.optString("tags"), MemoryImportPolicy.MAX_TAGS_CHARS,
                    MemoryPolicy.MAX_MESSAGE_BYTES, "tags");
            card.needsReply = value.optBoolean("needsReply");
            card.completed = value.optBoolean("completed");
            card.deletedInTelegram = value.optBoolean("deletedInTelegram");
            card.automatic = false;
            card.imported = false;
            card.restored = true;
            card.createdAt = value.getLong("createdAt");
            if (card.createdAt < 0) throw new IOException("Invalid portable Memory timestamp");
            // Restoring data must never arm an old alarm without a fresh explicit user action.
            card.reminderAt = 0;
            card.reminderDeliveredAt = 0;
            String exportedOrigin = value.optString("restoredFrom");
            if (!exportedOrigin.isEmpty() && !MemoryImportPolicy.validToken(exportedOrigin)) {
                throw new IOException("Invalid portable Memory origin");
            }
            PortableCard portableCard = new PortableCard(exportId, exportedOrigin, card);
            parsed.cards.add(portableCard);
            JSONArray versions = value.getJSONArray("versions");
            if (versions.length() == 0 || versions.length() > MemoryPolicy.MAX_VERSIONS) {
                throw new IOException("Invalid portable Memory version count");
            }
            for (int versionIndex = 0; versionIndex < versions.length(); versionIndex++) {
                JSONObject version = versions.getJSONObject(versionIndex);
                MemoryCard.Snapshot snapshot = new MemoryCard.Snapshot();
                snapshot.text = boundedImportString(version.getString("text"), Integer.MAX_VALUE,
                        MemoryPolicy.MAX_MESSAGE_BYTES, "text");
                snapshot.receivedAt = version.getLong("receivedAt");
                snapshot.editedAt = version.optInt("editedAt");
                if (snapshot.receivedAt < 0 || snapshot.editedAt < 0) {
                    throw new IOException("Invalid portable Memory version timestamp");
                }
                parsePortableOriginal(parsed, portableCard, snapshot, version);
                parsePortableThumbnail(parsed, portableCard, snapshot, version);
                card.versions.add(snapshot);
            }
        }
        return parsed;
    }

    private void parsePortableOriginal(PortableImport parsed, PortableCard owner, MemoryCard.Snapshot snapshot,
            JSONObject version) throws Exception {
        snapshot.fileName = safeImportedName(version.optString("fileName"));
        snapshot.mime = version.optString("mime", "application/octet-stream");
        if (!MemoryImportPolicy.validMime(snapshot.mime)) throw new IOException("Invalid portable Memory MIME type");
        String entry = version.optString("attachment");
        if (entry.isEmpty()) {
            snapshot.fileState = "none".equals(version.optString("fileState")) ? "none" : "unavailable";
            snapshot.fileSize = boundedAbsentSize(version.optLong("fileSize"));
            return;
        }
        if (!MemoryImportPolicy.validEntry(entry, false)) throw new IOException("Invalid portable attachment path");
        snapshot.fileSize = version.getLong("fileSize");
        snapshot.sha256 = version.getString("sha256");
        if (snapshot.fileSize <= 0 || snapshot.fileSize > MemoryPolicy.MAX_ATTACHMENT_BYTES
                || !MemoryImportPolicy.validToken(snapshot.sha256)) throw new IOException("Invalid portable attachment metadata");
        snapshot.fileState = "pending_import";
        addPortableBlob(parsed, owner, snapshot, entry, snapshot.fileSize, snapshot.sha256, false, snapshot.mime);
    }

    private void parsePortableThumbnail(PortableImport parsed, PortableCard owner, MemoryCard.Snapshot snapshot,
            JSONObject version) throws Exception {
        snapshot.thumbnailName = safeImportedName(version.optString("thumbnailName"));
        snapshot.thumbnailMime = version.optString("thumbnailMime", "image/jpeg");
        if (!MemoryThumbnailPolicy.supportedMime(snapshot.thumbnailMime)) {
            throw new IOException("Invalid portable thumbnail MIME type");
        }
        String entry = version.optString("thumbnail");
        if (entry.isEmpty()) {
            snapshot.thumbnailState = "none".equals(version.optString("thumbnailState")) ? "none" : "unavailable";
            snapshot.thumbnailSize = boundedAbsentSize(version.optLong("thumbnailSize"));
            return;
        }
        if (!MemoryImportPolicy.validEntry(entry, true)) throw new IOException("Invalid portable thumbnail path");
        snapshot.thumbnailSize = version.getLong("thumbnailSize");
        snapshot.thumbnailSha256 = version.getString("thumbnailSha256");
        if (snapshot.thumbnailSize <= 0 || snapshot.thumbnailSize > MemoryPolicy.MAX_THUMBNAIL_BYTES
                || !MemoryImportPolicy.validToken(snapshot.thumbnailSha256)) {
            throw new IOException("Invalid portable thumbnail metadata");
        }
        snapshot.thumbnailState = "pending_import";
        addPortableBlob(parsed, owner, snapshot, entry, snapshot.thumbnailSize,
                snapshot.thumbnailSha256, true, snapshot.thumbnailMime);
    }

    private static void addPortableBlob(PortableImport parsed, PortableCard owner, MemoryCard.Snapshot snapshot,
            String entry, long size, String sha256, boolean thumbnail, String mime) throws IOException {
        PortableBlob blob = parsed.blobs.get(entry);
        if (blob == null) {
            blob = new PortableBlob(entry, size, sha256, thumbnail, mime);
            parsed.blobs.put(entry, blob);
        } else if (blob.size != size || !blob.sha256.equals(sha256) || blob.thumbnail != thumbnail
                || !blob.mime.equals(mime)) {
            throw new IOException("Conflicting portable Memory attachment metadata");
        }
        blob.targets.add(new PortableTarget(owner, snapshot));
    }

    private void preparePortableCards(PortableImport parsed, Database database, String token,
            boolean include) throws Exception {
        if (!MemoryImportPolicy.validToken(token)) throw new IOException("Invalid portable Memory token");
        HashSet<String> existingOrigins = new HashSet<>();
        for (MemoryCard card : database.cards) if (card.restored && MemoryImportPolicy.validToken(card.restoredFrom)) {
            existingOrigins.add(card.restoredFrom);
        }
        for (PortableCard portable : parsed.cards) {
            String origin = portable.exportedOrigin.isEmpty()
                    ? MemoryImportPolicy.restoredOrigin(token, portable.exportId) : portable.exportedOrigin;
            portable.card.restoredFrom = origin;
            for (int i = 0; i < portable.card.versions.size(); i++) {
                portable.card.versions.get(i).fingerprint = MemoryImportPolicy.restoredOrigin(origin, Integer.toString(i));
            }
            if (existingOrigins.contains(origin)) {
                parsed.duplicateCards++;
                continue;
            }
            parsed.importedCards++;
            portable.included = include;
            existingOrigins.add(origin);
            if (include) {
                pruneForInsertion(database, false);
                database.cards.add(portable.card);
            }
        }
        if (include) for (PortableBlob blob : parsed.blobs.values()) {
            for (PortableTarget target : blob.targets) if (target.owner.included) { blob.needed = true; break; }
        }
    }

    private void retainPortableBlob(Database database, PortableBlob blob, byte[] plain) throws Exception {
        long limit = blob.thumbnail ? MemoryPolicy.MAX_THUMBNAIL_BYTES : MemoryPolicy.MAX_ATTACHMENT_BYTES;
        String id = retainBlob(plain, blob.sha256, database, limit, MemoryPolicy.MAX_ACCOUNT_BYTES);
        if (id == null) throw new IOException("Portable Memory storage limit reached");
        for (PortableTarget target : blob.targets) if (target.owner.included) {
            if (blob.thumbnail) {
                target.snapshot.thumbnailBlob = id;
                target.snapshot.thumbnailState = "saved";
            } else {
                target.snapshot.blob = id;
                target.snapshot.fileState = "saved";
            }
        }
    }

    private static String boundedImportString(String value, int maxChars, int maxBytes, String field)
            throws IOException {
        if (!MemoryImportPolicy.validText(value, maxChars, maxBytes)) {
            throw new IOException("Invalid portable Memory " + field);
        }
        return value;
    }

    private static String safeImportedName(String value) throws IOException {
        if (value == null || value.length() > 1024) throw new IOException("Invalid portable Memory file name");
        return MemoryExportPolicy.safeFileName(value);
    }

    private static long boundedAbsentSize(long value) throws IOException {
        // Metadata for an omitted oversized original may legitimately exceed this client's retention limit.
        // It is never allocated or treated as a retained blob.
        if (value < 0) throw new IOException("Invalid portable Memory absent file size");
        return value;
    }

    private static long newPortablePeerId(HashSet<Long> occupied) {
        long candidate;
        do {
            UUID id = UUID.randomUUID();
            candidate = (id.getMostSignificantBits() ^ id.getLeastSignificantBits()) & Long.MAX_VALUE;
        } while (candidate == 0 || occupied.contains(candidate));
        occupied.add(candidate);
        return candidate;
    }

    private static byte[] readPortableEntry(ZipInputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxBytes, 32768));
        byte[] buffer = new byte[32768];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > maxBytes) throw new IOException("Portable Memory entry exceeds its limit");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static void updatePortableDigest(MessageDigest digest, String name, byte[] bytes) {
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        updatePortableLength(digest, nameBytes.length);
        digest.update(nameBytes);
        updatePortableLength(digest, bytes.length);
        digest.update(bytes);
    }

    private static void updatePortableLength(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private ExportResult writeExport(Uri destination, ArrayList<MemoryCard> cards) throws Exception {
        requireExportAllowed();
        JSONArray exportedCards = new JSONArray();
        LinkedHashMap<String, String> blobs = new LinkedHashMap<>();
        for (int cardIndex = 0; cardIndex < cards.size(); cardIndex++) {
            MemoryCard card = cards.get(cardIndex);
            JSONObject exported = new JSONObject().put("id", card.id)
                    .put("peerKind", card.key.peerKind).put("peerId", card.key.peerId)
                    .put("messageId", card.key.messageId).put("topicId", card.key.topicId)
                    .put("source", card.source).put("sender", card.sender).put("note", card.note)
                    .put("tags", card.tags).put("needsReply", card.needsReply).put("completed", card.completed)
                    .put("deletedInTelegram", card.deletedInTelegram).put("automatic", card.automatic)
                    .put("imported", card.imported).put("createdAt", card.createdAt)
                    .put("reminderAt", card.reminderAt);
            if (card.restored && MemoryImportPolicy.validToken(card.restoredFrom)) {
                exported.put("restoredFrom", card.restoredFrom);
            }
            JSONArray versions = new JSONArray();
            for (int versionIndex = 0; versionIndex < card.versions.size(); versionIndex++) {
                MemoryCard.Snapshot snapshot = card.versions.get(versionIndex);
                JSONObject version = new JSONObject().put("text", snapshot.text)
                        .put("receivedAt", snapshot.receivedAt).put("editedAt", snapshot.editedAt)
                        .put("fileState", snapshot.fileState).put("fileName", snapshot.fileName)
                        .put("mime", snapshot.mime).put("sha256", snapshot.sha256).put("fileSize", snapshot.fileSize)
                        .put("thumbnailState", snapshot.thumbnailState).put("thumbnailName", snapshot.thumbnailName)
                        .put("thumbnailMime", snapshot.thumbnailMime).put("thumbnailSha256", snapshot.thumbnailSha256)
                        .put("thumbnailSize", snapshot.thumbnailSize);
                if ("saved".equals(snapshot.fileState) && safeId(snapshot.blob)) {
                    String entry = blobs.get(snapshot.blob);
                    if (entry == null) {
                        entry = MemoryExportPolicy.attachmentEntry(cardIndex, versionIndex, snapshot.fileName);
                        blobs.put(snapshot.blob, entry);
                    }
                    version.put("attachment", entry);
                }
                if ("saved".equals(snapshot.thumbnailState) && safeId(snapshot.thumbnailBlob)) {
                    String entry = blobs.get(snapshot.thumbnailBlob);
                    if (entry == null) {
                        entry = MemoryExportPolicy.thumbnailEntry(cardIndex, versionIndex, snapshot.thumbnailName);
                        blobs.put(snapshot.thumbnailBlob, entry);
                    }
                    version.put("thumbnail", entry);
                }
                versions.put(version);
            }
            exportedCards.put(exported.put("versions", versions));
        }
        JSONObject manifest = new JSONObject().put("schema", 1).put("product", "MOROK Memory")
                .put("exportedAt", System.currentTimeMillis()).put("cards", exportedCards);
        byte[] manifestBytes = manifest.toString(2).getBytes(StandardCharsets.UTF_8);
        long exportedBytes = manifestBytes.length;
        int exportedFiles = 0;
        OutputStream raw = ApplicationLoader.applicationContext.getContentResolver().openOutputStream(destination, "wt");
        if (raw == null) throw new IOException("Memory export destination unavailable");
        try (OutputStream output = raw; ZipOutputStream zip = new ZipOutputStream(output)) {
            writeZipEntry(zip, "memory.json", manifestBytes);
            for (Map.Entry<String, String> entry : blobs.entrySet()) {
                requireExportAllowed();
                byte[] plain = readAttachmentBlob(cards, entry.getKey());
                writeZipEntry(zip, entry.getValue(), plain);
                exportedBytes += plain.length;
                exportedFiles++;
            }
            requireExportAllowed();
            zip.finish();
        }
        return new ExportResult(cards.size(), exportedFiles, exportedBytes);
    }

    private void writeZipEntry(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0);
        zip.putNextEntry(entry);
        for (int offset = 0; offset < bytes.length; offset += 32768) {
            requireExportAllowed();
            zip.write(bytes, offset, Math.min(32768, bytes.length - offset));
        }
        zip.closeEntry();
    }

    private byte[] readAttachmentBlob(ArrayList<MemoryCard> cards, String blob) throws Exception {
        BlobReference reference = null;
        for (MemoryCard card : cards) for (MemoryCard.Snapshot snapshot : card.versions) {
            if (snapshot.blob.equals(blob) && "saved".equals(snapshot.fileState)) {
                reference = new BlobReference(snapshot.fileSize, snapshot.sha256, MemoryPolicy.MAX_ATTACHMENT_BYTES); break;
            }
            if (snapshot.thumbnailBlob.equals(blob) && "saved".equals(snapshot.thumbnailState)) {
                reference = new BlobReference(snapshot.thumbnailSize, snapshot.thumbnailSha256, MemoryPolicy.MAX_THUMBNAIL_BYTES); break;
            }
        }
        if (reference == null) throw new IOException("Attachment no longer exists");
        byte[] encrypted;
        try (FileInputStream stream = new FileInputStream(new File(directory, blob + ".tink"))) {
            encrypted = readBounded(stream, reference.maxBytes + 128);
        }
        byte[] plain = cipher().decrypt(encrypted, aad("blob/" + blob));
        if (plain.length != reference.size || !reference.sha256.equals(MemoryCapture.hex(
                MessageDigest.getInstance("SHA-256").digest(plain)))) {
            throw new IOException("Attachment integrity check failed");
        }
        return plain;
    }

    private void requireExportAllowed() throws IOException {
        requireActive();
        if (SharedConfig.appLocked || SharedConfig.isWaitingForPasscodeEnter) {
            throw new IOException("Memory export stopped while the app is locked");
        }
    }

    private long usedBytes() {
        long bytes = 0;
        File[] files = directory.listFiles(); if (files != null) for (File file : files) bytes += file.length();
        return bytes;
    }

    private long estimatedUsedBytes(Database database) {
        long bytes = fileBytes(index.getBaseFile()) + fileBytes(new File(index.getBaseFile() + ".bak"))
                + fileBytes(journal.getBaseFile()) + fileBytes(new File(journal.getBaseFile() + ".bak"));
        HashSet<String> blobs = new HashSet<>();
        for (MemoryCard card : database.cards) for (MemoryCard.Snapshot snapshot : card.versions) {
            if ("saved".equals(snapshot.fileState) && safeId(snapshot.blob) && blobs.add(snapshot.blob)) {
                bytes += fileBytes(new File(directory, snapshot.blob + ".tink"));
            }
            if ("saved".equals(snapshot.thumbnailState) && safeId(snapshot.thumbnailBlob)
                    && blobs.add(snapshot.thumbnailBlob)) {
                bytes += fileBytes(new File(directory, snapshot.thumbnailBlob + ".tink"));
            }
        }
        return bytes;
    }

    private static long fileBytes(File file) { return file.isFile() ? file.length() : 0; }

    private boolean isUnmeteredNetwork() {
        try {
            ConnectivityManager manager = (ConnectivityManager) ApplicationLoader.applicationContext
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager == null) return false;
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(manager.getActiveNetwork());
            return capabilities != null && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        } catch (RuntimeException unavailable) {
            return false;
        }
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
            ArrayList<MemoryCard> cards = new ArrayList<>(); cards.add(card);
            byte[] plain = readAttachmentBlob(cards, blob);
            requireActive(); return plain;
        });
        return pending.get();
    }

    private static final class BlobReference {
        final long size;
        final String sha256;
        final int maxBytes;
        BlobReference(long size, String sha256, long maxBytes) {
            this.size = size; this.sha256 = sha256; this.maxBytes = (int) maxBytes;
        }
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
