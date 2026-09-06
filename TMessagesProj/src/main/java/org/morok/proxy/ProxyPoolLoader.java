package org.morok.proxy;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.AndroidUtilities;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.net.ssl.HttpsURLConnection;

/** Bootstrap keys/endpoints come only from the APK, never from the downloaded pool. */
final class ProxyPoolLoader {
    interface Callback { void complete(SignedProxyPool pool, boolean failed); }
    private final Context context;
    private final SharedPreferences store;
    private final Map<String, String> keys = new HashMap<>();
    private final ArrayList<URL> endpoints = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "morok-proxy-pool"));
    private volatile HttpsURLConnection connection;
    private volatile int generation;
    private Future<?> pending;

    ProxyPoolLoader(Context context) {
        this.context = context;
        store = context.getSharedPreferences("morok_proxy_pool", Context.MODE_PRIVATE);
        try (InputStream input = context.getAssets().open("morok-proxy-trust.properties")) {
            Properties properties = new Properties();
            properties.load(new java.io.ByteArrayInputStream(read(input, 16 * 1024)));
            for (String key : properties.stringPropertyNames()) {
                String value = properties.getProperty(key);
                if (key.matches("key\\.[A-Za-z0-9_-]{1,48}")) keys.put(key.substring(4), value);
                else if (!key.matches("endpoint\\.[12]")) throw new IllegalArgumentException();
            }
            for (int i = 1; i <= 2; i++) {
                String endpointValue = properties.getProperty("endpoint." + i);
                if (endpointValue != null) {
                    URL endpoint = new URL(endpointValue);
                    if (!"https".equals(endpoint.getProtocol()) || endpoint.getHost().isEmpty()
                            || endpoint.getUserInfo() != null || endpoint.getRef() != null) throw new IllegalArgumentException();
                    endpoints.add(endpoint);
                }
            }
            if (keys.size() > 4) throw new IllegalArgumentException();
        } catch (Exception ignored) {
            keys.clear(); endpoints.clear();
        }
    }

    boolean configured() { return !keys.isEmpty() && !endpoints.isEmpty(); }

    void cancel() {
        generation++;
        if (pending != null) pending.cancel(true);
        HttpsURLConnection active = connection;
        if (active != null) active.disconnect();
    }

    void load(boolean fetch, Callback callback) {
        cancel();
        final int ticket = generation;
        pending = executor.submit(() -> {
            if (ticket != generation) return;
            SignedProxyPool best = null;
            boolean failed = false;
            String cached = store.getString("envelope", "");
            if (!cached.isEmpty()) {
                try { best = verify(cached.getBytes(StandardCharsets.US_ASCII)); } catch (Exception ignored) { failed = true; }
            } else if (!keys.isEmpty()) {
                try (InputStream input = context.getAssets().open("morok-proxy-pool.txt")) {
                    byte[] body = read(input, SignedProxyPool.MAX_BYTES);
                    best = verify(body);
                    if (ticket == generation) persist(body, best);
                } catch (Exception ignored) { failed = true; }
            }
            if (fetch && ticket == generation) {
                boolean downloaded = false;
                for (URL endpoint : endpoints) {
                    if (ticket != generation) return;
                    HttpsURLConnection active = null;
                    try {
                        active = (HttpsURLConnection) endpoint.openConnection();
                        connection = active;
                        active.setConnectTimeout(8000); active.setReadTimeout(8000);
                        active.setInstanceFollowRedirects(false);
                        active.setUseCaches(false);
                        active.setRequestProperty("Accept", "text/plain");
                        active.setRequestProperty("Accept-Encoding", "identity");
                        if (active.getResponseCode() != 200 || active.getContentLength() > SignedProxyPool.MAX_BYTES)
                            throw new IllegalArgumentException();
                        byte[] body;
                        try (InputStream input = active.getInputStream()) { body = read(input, SignedProxyPool.MAX_BYTES); }
                        SignedProxyPool pool = verify(body);
                        if (ticket != generation) return;
                        persist(body, pool);
                        best = pool; downloaded = true; failed = false;
                        break;
                    } catch (Exception ignored) {
                        failed = true;
                    } finally {
                        if (active != null) active.disconnect();
                        connection = null;
                    }
                }
                if (configured() && !downloaded) failed = true;
            }
            final SignedProxyPool result = best;
            final boolean error = failed;
            AndroidUtilities.runOnUIThread(() -> { if (ticket == generation) callback.complete(result, error); });
        });
    }

    private SignedProxyPool verify(byte[] body) throws Exception {
        return SignedProxyPool.verify(body, keys, System.currentTimeMillis() / 1000,
                store.getLong("version", 0), store.getString("digest", ""));
    }

    private void persist(byte[] body, SignedProxyPool pool) {
        if (!store.edit().putString("envelope", new String(body, StandardCharsets.US_ASCII))
                .putLong("version", pool.version).putString("digest", pool.digest).commit())
            throw new IllegalStateException("Cannot persist verified proxy pool");
    }

    static byte[] read(InputStream input, int limit) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (Thread.currentThread().isInterrupted() || output.size() + count > limit) throw new IllegalArgumentException("Pool size");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
}
