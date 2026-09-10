package org.morok.update;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import org.telegram.messenger.AndroidUtilities;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.net.ssl.HttpsURLConnection;

/** Explicit-only update check, download verification and handoff to Android's package installer. */
public final class MorokUpdateManager {
    public enum Status { UNCONFIGURED, IDLE, CHECKING, CURRENT, AVAILABLE, DOWNLOADING, READY, ERROR }
    public interface Callback { void complete(); }

    private final Context context;
    private final android.content.SharedPreferences store;
    private final Map<String, String> keys = new HashMap<>();
    private final ArrayList<URL> endpoints = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            runnable -> new Thread(runnable, "morok-update"));
    private final long installedVersionCode;
    private final String installedVersionName;
    private volatile HttpsURLConnection connection;
    private volatile int generation;
    private volatile Status status;
    private volatile SignedUpdateManifest manifest;
    private volatile File verifiedApk;
    private volatile boolean usingCachedManifest;
    private Future<?> pending;

    public MorokUpdateManager(Context source) {
        context = source.getApplicationContext();
        store = context.getSharedPreferences("morok_update", Context.MODE_PRIVATE);
        PackageInfo own = ownPackage();
        installedVersionCode = own == null ? 0 : versionCode(own);
        installedVersionName = own == null || own.versionName == null ? "?" : own.versionName;
        loadTrust();
        status = configured() ? Status.IDLE : Status.UNCONFIGURED;
    }

    public boolean configured() { return !keys.isEmpty() && !endpoints.isEmpty(); }
    public Status status() { return status; }
    public SignedUpdateManifest manifest() { return manifest; }
    public long installedVersionCode() { return installedVersionCode; }
    public String installedVersionName() { return installedVersionName; }
    public boolean usingCachedManifest() { return usingCachedManifest; }

    public void cancel() {
        generation++;
        if (pending != null) pending.cancel(true);
        HttpsURLConnection active = connection;
        if (active != null) active.disconnect();
    }

    public void destroy() {
        cancel();
        executor.shutdownNow();
    }

    /** Network access begins only after this user-triggered call. */
    public void check(Callback callback) {
        cancel();
        verifiedApk = null;
        manifest = null;
        usingCachedManifest = false;
        if (!configured()) {
            status = Status.UNCONFIGURED;
            callback(callback);
            return;
        }
        status = Status.CHECKING;
        final int ticket = generation;
        pending = executor.submit(() -> {
            SignedUpdateManifest best = readCached();
            boolean downloaded = false;
            for (URL endpoint : endpoints) {
                if (ticket != generation || Thread.currentThread().isInterrupted()) return;
                HttpsURLConnection active = null;
                try {
                    active = (HttpsURLConnection) endpoint.openConnection();
                    connection = active;
                    active.setConnectTimeout(10_000);
                    active.setReadTimeout(10_000);
                    active.setInstanceFollowRedirects(false);
                    active.setUseCaches(false);
                    active.setRequestProperty("Accept", "text/plain");
                    active.setRequestProperty("Accept-Encoding", "identity");
                    if (active.getResponseCode() != 200
                            || active.getContentLength() > SignedUpdateManifest.MAX_ENVELOPE_BYTES)
                        throw new IllegalArgumentException("Update response");
                    byte[] envelope;
                    try (InputStream input = active.getInputStream()) {
                        envelope = read(input, SignedUpdateManifest.MAX_ENVELOPE_BYTES);
                    }
                    SignedUpdateManifest candidate = verify(envelope);
                    if (ticket != generation) return;
                    persist(envelope, candidate);
                    best = candidate;
                    downloaded = true;
                    break;
                } catch (Exception ignored) {
                    // Primary and backup are tried; only a verified unexpired cached manifest may remain.
                } finally {
                    if (active != null) active.disconnect();
                    connection = null;
                }
            }
            if (ticket != generation) return;
            manifest = best;
            usingCachedManifest = best != null && !downloaded;
            status = best == null ? Status.ERROR
                    : best.isNewerThan(installedVersionCode) ? Status.AVAILABLE : Status.CURRENT;
            callback(callback);
        });
    }

    /** Downloads exactly the URL authenticated by the selected signed manifest. */
    public void download(Callback callback) {
        cancel();
        final SignedUpdateManifest selected = manifest;
        if (selected == null || !selected.isNewerThan(installedVersionCode)) {
            status = Status.ERROR;
            callback(callback);
            return;
        }
        status = Status.DOWNLOADING;
        final int ticket = generation;
        pending = executor.submit(() -> {
            File directory = new File(context.getCacheDir(), "morok-updates");
            File temporary = new File(directory, "update.tmp");
            File complete = new File(directory, "morok-update.apk");
            HttpsURLConnection active = null;
            try {
                if ((!directory.exists() && !directory.mkdirs()) || !directory.isDirectory())
                    throw new IllegalStateException("Update directory");
                delete(temporary);
                delete(complete);
                URL url = new URL(selected.url);
                active = (HttpsURLConnection) url.openConnection();
                connection = active;
                active.setConnectTimeout(15_000);
                active.setReadTimeout(30_000);
                active.setInstanceFollowRedirects(false);
                active.setUseCaches(false);
                active.setRequestProperty("Accept", "application/vnd.android.package-archive");
                active.setRequestProperty("Accept-Encoding", "identity");
                long declared = active.getContentLength();
                if (active.getResponseCode() != 200 || declared > 0 && declared != selected.size)
                    throw new IllegalArgumentException("Update APK response");
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                long total = 0;
                try (InputStream input = active.getInputStream(); FileOutputStream output = new FileOutputStream(temporary)) {
                    byte[] buffer = new byte[64 * 1024];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (ticket != generation || Thread.currentThread().isInterrupted()
                                || total + count > selected.size) throw new IllegalStateException("Update cancelled or oversized");
                        output.write(buffer, 0, count);
                        digest.update(buffer, 0, count);
                        total += count;
                    }
                    output.flush();
                    output.getFD().sync();
                }
                if (total != selected.size || !selected.sha256.equals(SignedUpdateManifest.hex(digest.digest())))
                    throw new IllegalArgumentException("Update APK digest");
                verifyArchive(temporary, selected);
                if (!temporary.renameTo(complete)) throw new IllegalStateException("Update APK commit");
                if (ticket != generation) { delete(complete); return; }
                verifiedApk = complete;
                status = Status.READY;
            } catch (Exception ignored) {
                delete(temporary);
                delete(complete);
                verifiedApk = null;
                if (ticket == generation) status = Status.ERROR;
            } finally {
                if (active != null) active.disconnect();
                connection = null;
            }
            if (ticket == generation) callback(callback);
        });
    }

    /** Returns false after routing to Android's unknown-sources permission or when no APK is ready. */
    public boolean install(Activity activity) {
        File apk = verifiedApk;
        if (activity == null || apk == null || !apk.isFile() || status != Status.READY) return false;
        if (Build.VERSION.SDK_INT >= 26 && !context.getPackageManager().canRequestPackageInstalls()) {
            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + context.getPackageName()));
            activity.startActivity(settings);
            return false;
        }
        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", apk);
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
        return true;
    }

    private void loadTrust() {
        try (InputStream input = context.getAssets().open("morok-update-trust.properties")) {
            Properties properties = new Properties();
            properties.load(new ByteArrayInputStream(read(input, 16 * 1024)));
            for (String property : properties.stringPropertyNames()) {
                String value = properties.getProperty(property);
                if (property.matches("key\\.[A-Za-z0-9_-]{1,48}")) {
                    keys.put(property.substring(4), value);
                } else if (!property.matches("endpoint\\.[12]")) {
                    throw new IllegalArgumentException("Update trust property");
                }
            }
            for (int index = 1; index <= 2; index++) {
                String value = properties.getProperty("endpoint." + index);
                if (value == null) continue;
                URL endpoint = new URL(value);
                if (!"https".equals(endpoint.getProtocol()) || endpoint.getHost().isEmpty()
                        || endpoint.getUserInfo() != null || endpoint.getRef() != null)
                    throw new IllegalArgumentException("Update endpoint");
                endpoints.add(endpoint);
            }
            if (keys.isEmpty() || keys.size() > 4 || endpoints.isEmpty()) throw new IllegalArgumentException("Update trust");
        } catch (Exception ignored) {
            keys.clear();
            endpoints.clear();
        }
    }

    private SignedUpdateManifest readCached() {
        String cached = store.getString("envelope", "");
        if (cached.isEmpty()) return null;
        try { return verify(cached.getBytes(StandardCharsets.US_ASCII)); }
        catch (Exception ignored) { return null; }
    }

    private SignedUpdateManifest verify(byte[] envelope) throws Exception {
        return SignedUpdateManifest.verify(envelope, keys, System.currentTimeMillis() / 1000,
                store.getLong("version", 0), store.getString("digest", ""),
                context.getPackageName(), "arm64-v8a");
    }

    private void persist(byte[] envelope, SignedUpdateManifest value) {
        if (!store.edit().putString("envelope", new String(envelope, StandardCharsets.US_ASCII))
                .putLong("version", value.versionCode).putString("digest", value.digest).commit())
            throw new IllegalStateException("Update manifest storage");
    }

    private void verifyArchive(File apk, SignedUpdateManifest selected) throws Exception {
        PackageManager manager = context.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo archive = manager.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        PackageInfo own = ownPackage();
        if (archive == null || own == null || !selected.packageName.equals(archive.packageName)
                || versionCode(archive) != selected.versionCode)
            throw new IllegalArgumentException("Update APK identity");
        String expected = selected.certificateSha256;
        if (!expected.equals(singleSignerDigest(own)) || !expected.equals(singleSignerDigest(archive)))
            throw new IllegalArgumentException("Update APK certificate");
    }

    private PackageInfo ownPackage() {
        try {
            int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
            return context.getPackageManager().getPackageInfo(context.getPackageName(), flags);
        } catch (PackageManager.NameNotFoundException ignored) { return null; }
    }

    @SuppressWarnings("deprecation")
    private static String singleSignerDigest(PackageInfo info) throws Exception {
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= 28) {
            if (info.signingInfo == null || info.signingInfo.hasMultipleSigners())
                throw new IllegalArgumentException("Update signer set");
            signatures = info.signingInfo.getApkContentsSigners();
        } else {
            signatures = info.signatures;
        }
        if (signatures == null || signatures.length != 1) throw new IllegalArgumentException("Update signer set");
        return SignedUpdateManifest.hex(MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray()));
    }

    @SuppressWarnings("deprecation")
    private static long versionCode(PackageInfo info) {
        return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
    }

    private static byte[] read(InputStream input, int limit) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (Thread.currentThread().isInterrupted() || output.size() + count > limit)
                throw new IllegalArgumentException("Update response size");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static void delete(File file) { if (file.exists()) file.delete(); }
    private static void callback(Callback callback) {
        if (callback != null) AndroidUtilities.runOnUIThread(callback::complete);
    }
}
