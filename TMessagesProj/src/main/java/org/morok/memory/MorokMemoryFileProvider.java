package org.morok.memory;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.OpenableColumns;

import org.telegram.messenger.SharedConfig;

import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.UUID;

/** Explicit, expiring read grants. Decrypted bytes are piped to a selected viewer, never cached on disk. */
public final class MorokMemoryFileProvider extends ContentProvider {
    private static final HashMap<String, Grant> GRANTS = new HashMap<>();
    private static final class Grant {
        long userId, expires, size;
        String card, blob, name, mime;
        MorokMemoryStore session;
    }

    public static Uri grant(Context context, MemoryCard card, MemoryCard.Snapshot snapshot) {
        return grant(context, card, snapshot.blob, snapshot.fileName, snapshot.mime, snapshot.fileSize);
    }

    public static Uri grantThumbnail(Context context, MemoryCard card, MemoryCard.Snapshot snapshot) {
        return grant(context, card, snapshot.thumbnailBlob, snapshot.thumbnailName,
                snapshot.thumbnailMime, snapshot.thumbnailSize);
    }

    private static Uri grant(Context context, MemoryCard card, String blob, String name, String mime, long size) {
        long now = SystemClock.elapsedRealtime();
        Grant grant = new Grant(); grant.userId = card.key.userId;
        grant.session = MorokMemoryStore.forAccount(MorokMemoryStore.resolveAccount(grant.userId)); grant.card = card.id; grant.blob = blob;
        grant.name = name; grant.mime = mime; grant.size = size;
        grant.expires = now + 10 * 60 * 1000;
        String token = UUID.randomUUID().toString();
        synchronized (GRANTS) {
            GRANTS.entrySet().removeIf(entry -> entry.getValue().expires <= now);
            if (!grant.session.isActive()) throw new IllegalStateException("Account session changed");
            GRANTS.put(token, grant);
        }
        return new Uri.Builder().scheme("content").authority(context.getPackageName() + ".morok.memory").appendPath(token).build();
    }

    static void revokeUser(long userId) {
        synchronized (GRANTS) { GRANTS.entrySet().removeIf(entry -> entry.getValue().userId == userId); }
    }

    private static Grant lookup(Uri uri) throws FileNotFoundException {
        Grant grant;
        synchronized (GRANTS) { grant = GRANTS.get(uri.getLastPathSegment()); }
        if (grant == null || grant.expires <= SystemClock.elapsedRealtime()
                || !grant.session.isActive() || MorokMemoryStore.resolveAccount(grant.userId) < 0 || SharedConfig.appLocked || SharedConfig.isWaitingForPasscodeEnter) {
            throw new FileNotFoundException("Memory grant expired or locked");
        }
        return grant;
    }

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) {
        try { return lookup(uri).mime; } catch (Exception error) { return "application/octet-stream"; }
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        String[] columns = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor cursor = new MatrixCursor(columns);
        try {
            Grant grant = lookup(uri);
            Object[] row = new Object[columns.length];
            for (int i = 0; i < columns.length; i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) row[i] = grant.name;
                else if (OpenableColumns.SIZE.equals(columns[i])) row[i] = grant.size;
            }
            cursor.addRow(row);
        } catch (Exception ignored) { }
        return cursor;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Memory attachments are read-only");
        Grant grant = lookup(uri);
        return openPipeHelper(uri, grant.mime, null, grant, (output, requestedUri, mime, options, entry) -> {
            try (OutputStream stream = new ParcelFileDescriptor.AutoCloseOutputStream(output)) {
                int account = MorokMemoryStore.resolveAccount(entry.userId);
                if (account < 0) return;
                byte[] data = entry.session.readAttachment(entry.card, entry.blob);
                lookup(requestedUri);
                stream.write(data);
            } catch (Exception ignored) { /* Closed/expired/tampered data is never released. */ }
        });
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException("Read-only"); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException("Read-only"); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new UnsupportedOperationException("Read-only"); }
}
