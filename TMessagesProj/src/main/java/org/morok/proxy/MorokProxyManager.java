package org.morok.proxy;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.SystemClock;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** UI-thread coordinator over the upstream transport; never changes send queues or auth. */
public final class MorokProxyManager implements NotificationCenter.NotificationCenterDelegate {
    public enum Mode { DIRECT, MANUAL, AUTO }
    public enum Status { DIRECT, MANUAL, CHECKING, CONNECTED, RETRY, OFFLINE, EMPTY, POOL_ERROR, STORAGE_ERROR }
    private static MorokProxyManager instance;
    public static MorokProxyManager getInstance() {
        if (instance == null) instance = new MorokProxyManager();
        return instance;
    }

    private final SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("morok_proxy", Context.MODE_PRIVATE);
    private final SharedPreferences routes = MessagesController.getGlobalMainSettings();
    private static final String MODE_KEY = "morokProxyMode";
    private final ProxyPoolLoader loader = new ProxyPoolLoader(ApplicationLoader.applicationContext);
    private final ArrayList<Runnable> listeners = new ArrayList<>();
    private final Map<String, Long> retryAfter = new HashMap<>();
    private final Set<String> attempted = new HashSet<>();
    private final Set<String> managed = new HashSet<>(preferences.getStringSet("managed_nodes", new HashSet<>()));
    private final Set<String> imported = new HashSet<>(preferences.getStringSet("imported_nodes", new HashSet<>()));
    private SignedProxyPool pool;
    private Mode mode = Mode.DIRECT;
    private Status status = Status.DIRECT;
    private int epoch, failures;
    private boolean started, applying, nativeCheckInFlight, chooseAnother;
    private long lastCheckWallTime, lastPoolFetch;
    private String excluded = "";
    private final Runnable step = this::checkNext;
    private final Runnable timeout = () -> { status = Status.RETRY; notifyListeners(); };
    private final Runnable monitor = this::monitor;

    private MorokProxyManager() {}

    public void start() {
        if (started) return;
        started = true;
        SharedConfig.loadProxyList();
        try { mode = Mode.valueOf(routes.getString(MODE_KEY, "DIRECT")); } catch (Exception ignored) {}
        if (mode == Mode.AUTO && !SharedConfig.isProxyEnabled()) mode = Mode.DIRECT;
        if (mode != Mode.AUTO) mode = SharedConfig.isProxyEnabled() ? Mode.MANUAL : Mode.DIRECT;
        status = mode == Mode.DIRECT ? Status.DIRECT : mode == Mode.MANUAL ? Status.MANUAL : Status.CHECKING;
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxySettingsChanged);
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++)
            NotificationCenter.getInstance(i).addObserver(this, NotificationCenter.didUpdateConnectionState);
        try {
            ConnectivityManager connectivity = (ConnectivityManager) ApplicationLoader.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            connectivity.registerNetworkCallback(new NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
                    new ConnectivityManager.NetworkCallback() {
                        @Override public void onAvailable(Network network) { AndroidUtilities.runOnUIThread(() -> networkChanged()); }
                        @Override public void onLost(Network network) { AndroidUtilities.runOnUIThread(() -> networkChanged()); }
                    });
        } catch (Exception ignored) { /* periodic monitor still observes native connection state */ }
        if (mode == Mode.AUTO) {
            disableUpstreamRotation();
            refreshPool();
            begin(false);
            scheduleMonitor();
        } else loadPool(false);
    }

    public Mode mode() { start(); return mode; }
    public Status status() { return status; }
    public long lastCheckWallTime() { return lastCheckWallTime; }
    public boolean infrastructureConfigured() { return loader.configured(); }
    public boolean hasValidPool() { return pool != null && pool.expires > System.currentTimeMillis() / 1000; }
    public void addListener(Runnable listener) { if (!listeners.contains(listener)) listeners.add(listener); }
    public void removeListener(Runnable listener) { listeners.remove(listener); }

    public ArrayList<ProxyNode> nodes() {
        SharedConfig.loadProxyList();
        ArrayList<ProxyNode> nodes = new ArrayList<>();
        HashSet<String> ids = new HashSet<>();
        for (SharedConfig.ProxyInfo info : SharedConfig.proxyList) {
            try {
                ProxyNode node = from(info);
                if ((!managed.contains(node.id()) || imported.contains(node.id())) && ids.add(node.id()) && nodes.size() < 50) nodes.add(node);
            } catch (Exception ignored) {}
        }
        if (pool != null && pool.expires > System.currentTimeMillis() / 1000) {
            for (ProxyNode node : pool.nodes) if (ids.add(node.id()) && nodes.size() < 50) nodes.add(node);
        }
        return nodes;
    }

    public String selectedId() {
        try { return from(SharedConfig.currentProxy).id(); } catch (Exception ignored) { return ""; }
    }

    public void importLink(String input) {
        ProxyNode node = ProxyNode.parse(input);
        managed.remove(node.id()); // explicit user import grants independent trust for this node
        imported.add(node.id());
        preferences.edit().putStringSet("managed_nodes", managed).putStringSet("imported_nodes", imported).apply();
        SharedConfig.addProxy(toInfo(node));
        notifyListeners();
    }

    public boolean setAutomatic() {
        start();
        ArrayList<ProxyNode> nodes = nodes();
        if (nodes.isEmpty()) { status = Status.EMPTY; refreshPool(); notifyListeners(); return false; }
        cancelWork();
        // Install a real explicitly trusted route before probing, so this mode cannot use an empty/direct route.
        ProxyNode initial = nodes.get(0);
        for (ProxyNode node : nodes) if (node.id().equals(selectedId())) initial = node;
        if (!apply(initial, Mode.AUTO)) return false;
        refreshPool(); begin(false); scheduleMonitor();
        return true;
    }

    public void setManual(ProxyNode node) {
        cancelWork();
        if (apply(node, Mode.MANUAL)) {
            status = Status.MANUAL;
            notifyListeners();
        }
    }

    public void setDirect() {
        cancelWork();
        Map<String, Object> next = new HashMap<>();
        next.put(MODE_KEY, Mode.DIRECT.name());
        next.put("proxy_enabled", false);
        next.put("proxy_enabled_calls", false);
        next.put("proxyRotationEnabled", false);
        if (!saveRoute(next)) return;
        mode = Mode.DIRECT; status = Status.DIRECT;
        SharedConfig.proxyRotationEnabled = false;
        applying = true;
        try {
            ConnectionsManager.setProxySettings(false, "", 1080, "", "", "");
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
        } finally { applying = false; }
        notifyListeners();
    }

    public void another() { if (mode == Mode.AUTO) begin(true); }

    public void refreshPool() {
        loadPool(true);
    }

    private void loadPool(boolean fetch) {
        lastPoolFetch = SystemClock.elapsedRealtime();
        final int ticket = epoch;
        loader.load(fetch, (result, failed) -> {
            if (ticket != epoch) return;
            epoch++; // pool authorization changed; an older native check must not install its captured candidate
            pool = result;
            if (result != null) {
                for (ProxyNode node : result.nodes) {
                    if (!managed.contains(node.id())) {
                        for (SharedConfig.ProxyInfo saved : SharedConfig.proxyList) {
                            try { if (from(saved).id().equals(node.id())) imported.add(node.id()); } catch (Exception ignored) {}
                        }
                    }
                    managed.add(node.id());
                }
                preferences.edit().putStringSet("managed_nodes", managed).putStringSet("imported_nodes", imported).apply();
            }
            if (failed && result == null && loader.configured()) status = Status.POOL_ERROR;
            notifyListeners();
            if (mode == Mode.AUTO && !nativeCheckInFlight) begin(false);
        });
    }

    private void cancelWork() {
        epoch++; loader.cancel();
        AndroidUtilities.cancelRunOnUIThread(step);
        AndroidUtilities.cancelRunOnUIThread(timeout);
        AndroidUtilities.cancelRunOnUIThread(monitor);
        attempted.clear();
        chooseAnother = false;
        excluded = "";
        // The upstream JNI API has no cancellation method. A single active probe may finish;
        // epoch prevents its late result from applying settings or starting work in direct/manual mode.
    }

    private void begin(boolean alternative) {
        if (mode != Mode.AUTO) return;
        AndroidUtilities.cancelRunOnUIThread(step);
        attempted.clear(); chooseAnother = alternative;
        excluded = alternative ? selectedId() : "";
        if (!nativeCheckInFlight) checkNext();
    }

    private void checkNext() {
        if (mode != Mode.AUTO || nativeCheckInFlight) return;
        if (!ApplicationLoader.isNetworkOnline()) { status = Status.OFFLINE; scheduleRetry(); return; }
        ArrayList<ProxyNode> nodes = nodes();
        if (nodes.isEmpty()) { status = Status.EMPTY; scheduleRetry(); return; }
        long now = SystemClock.elapsedRealtime();
        ProxyNode next = null;
        for (ProxyNode node : nodes) {
            if (!node.id().equals(excluded) && !attempted.contains(node.id()) && retryAfter.getOrDefault(node.id(), 0L) <= now) {
                next = node; break;
            }
        }
        if (next == null) { status = Status.RETRY; failures = Math.min(8, failures + 1); scheduleRetry(); return; }
        final ProxyNode candidate = next;
        final int ticket = epoch;
        attempted.add(candidate.id());
        nativeCheckInFlight = true; status = Status.CHECKING; notifyListeners();
        AndroidUtilities.runOnUIThread(timeout, 15_000);
        try {
            ConnectionsManager.getInstance(UserConfig.selectedAccount).checkProxy(candidate.host, candidate.port, candidate.username,
                    candidate.password, candidate.secret, time -> AndroidUtilities.runOnUIThread(() -> {
                        nativeCheckInFlight = false;
                        AndroidUtilities.cancelRunOnUIThread(timeout);
                        if (ticket != epoch || mode != Mode.AUTO) {
                            if (mode == Mode.AUTO) AndroidUtilities.runOnUIThread(step, 1000);
                            return;
                        }
                        lastCheckWallTime = System.currentTimeMillis();
                        boolean stillTrusted = false;
                        for (ProxyNode node : nodes()) if (node.id().equals(candidate.id())) stillTrusted = true;
                        if (time >= 0 && stillTrusted && !candidate.id().equals(excluded)) {
                            retryAfter.remove(candidate.id()); failures = 0;
                            if (apply(candidate, Mode.AUTO)) {
                                status = Status.CONNECTED; chooseAnother = false;
                                notifyListeners();
                            }
                        } else {
                            retryAfter.put(candidate.id(), SystemClock.elapsedRealtime() + ProxyRetryPolicy.delayMillis(failures, Math.random()));
                            AndroidUtilities.runOnUIThread(step, 500);
                        }
                    }));
        } catch (Exception ignored) {
            nativeCheckInFlight = false; AndroidUtilities.cancelRunOnUIThread(timeout);
            status = Status.RETRY; scheduleRetry();
        }
    }

    private void scheduleRetry() {
        notifyListeners();
        AndroidUtilities.cancelRunOnUIThread(step);
        AndroidUtilities.runOnUIThread(step, ProxyRetryPolicy.delayMillis(failures, Math.random()));
        attempted.clear();
    }

    private void scheduleMonitor() {
        AndroidUtilities.cancelRunOnUIThread(monitor);
        if (mode == Mode.AUTO) AndroidUtilities.runOnUIThread(monitor, 30_000);
    }

    private void monitor() {
        if (mode != Mode.AUTO) return;
        if (SystemClock.elapsedRealtime() - lastPoolFetch > 6 * 60 * 60 * 1000L) refreshPool();
        int state = ConnectionsManager.getInstance(UserConfig.selectedAccount).getConnectionState();
        if (state != ConnectionsManager.ConnectionStateConnected && state != ConnectionsManager.ConnectionStateUpdating && !nativeCheckInFlight
                && status != Status.RETRY && status != Status.OFFLINE && status != Status.STORAGE_ERROR) begin(false);
        if (pool != null && pool.expires <= System.currentTimeMillis() / 1000) {
            pool = null; epoch++; status = Status.POOL_ERROR; notifyListeners();
            refreshPool(); begin(false);
        }
        scheduleMonitor();
    }

    private void networkChanged() {
        if (mode != Mode.AUTO) return;
        epoch++; loader.cancel(); attempted.clear(); retryAfter.clear(); failures = 0;
        loadPool(false);
        AndroidUtilities.cancelRunOnUIThread(step);
        AndroidUtilities.runOnUIThread(step, 3000);
    }

    private boolean apply(ProxyNode node, Mode nextMode) {
        SharedConfig.ProxyInfo selected = null;
        for (SharedConfig.ProxyInfo saved : SharedConfig.proxyList) {
            try { if (from(saved).id().equals(node.id())) selected = saved; } catch (Exception ignored) {}
        }
        Map<String, Object> next = new HashMap<>();
        next.put(MODE_KEY, nextMode.name());
        next.put("proxy_enabled", true);
        next.put("proxy_ip", node.host);
        next.put("proxy_port", node.port);
        next.put("proxy_user", node.username);
        next.put("proxy_pass", node.password);
        next.put("proxy_secret", node.secret);
        next.put("proxy_enabled_calls", node.secret.isEmpty() && routes.getBoolean("proxy_enabled_calls", false));
        next.put("proxyRotationEnabled", false);
        if (!saveRoute(next)) return false;
        mode = nextMode;
        SharedConfig.proxyRotationEnabled = false;
        SharedConfig.currentProxy = selected != null ? selected : toInfo(node);
        applying = true;
        try {
            ConnectionsManager.setProxySettings(true, node.host, node.port, node.username, node.password, node.secret);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
        } finally { applying = false; }
        return true;
    }

    private boolean saveRoute(Map<String, Object> next) {
        boolean saved = ProxyRouteTransaction.commit(new ProxyRouteTransaction.Store() {
            @Override public Map<String, ?> values() { return routes.getAll(); }
            @Override public boolean commit(Map<String, Object> values) {
                SharedPreferences.Editor editor = routes.edit();
                for (Map.Entry<String, Object> entry : values.entrySet()) {
                    Object value = entry.getValue();
                    if (value == null) editor.remove(entry.getKey());
                    else if (value instanceof Boolean) editor.putBoolean(entry.getKey(), (Boolean) value);
                    else if (value instanceof Integer) editor.putInt(entry.getKey(), (Integer) value);
                    else editor.putString(entry.getKey(), (String) value);
                }
                return editor.commit();
            }
        }, next);
        if (!saved) {
            status = Status.STORAGE_ERROR;
            notifyListeners();
            // The previous mode and native route remain active; a failed toggle cannot claim success.
            if (mode == Mode.AUTO) {
                scheduleRetry();
                scheduleMonitor();
            }
        }
        return saved;
    }

    private void disableUpstreamRotation() {
        SharedConfig.proxyRotationEnabled = false;
        ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Context.MODE_PRIVATE).edit().putBoolean("proxyRotationEnabled", false).apply();
    }

    private void notifyListeners() { for (Runnable listener : new ArrayList<>(listeners)) listener.run(); }
    private static ProxyNode from(SharedConfig.ProxyInfo info) {
        return new ProxyNode(info.address, info.port, info.username, info.password, info.secret);
    }
    private static SharedConfig.ProxyInfo toInfo(ProxyNode node) {
        return new SharedConfig.ProxyInfo(node.host, node.port, node.username, node.password, node.secret);
    }

    @Override public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.proxySettingsChanged && !applying) {
            // A user's action in Telegram's existing proxy screen takes precedence.
            cancelWork();
            mode = SharedConfig.isProxyEnabled() ? Mode.MANUAL : Mode.DIRECT;
            routes.edit().putString(MODE_KEY, mode.name()).apply();
            if (mode == Mode.MANUAL && !selectedId().isEmpty()) {
                imported.add(selectedId());
                preferences.edit().putStringSet("imported_nodes", imported).apply();
            }
            status = mode == Mode.DIRECT ? Status.DIRECT : Status.MANUAL;
            notifyListeners();
        } else if (id == NotificationCenter.didUpdateConnectionState && mode == Mode.AUTO && account == UserConfig.selectedAccount) {
            int state = ConnectionsManager.getInstance(account).getConnectionState();
            if (state == ConnectionsManager.ConnectionStateConnected || state == ConnectionsManager.ConnectionStateUpdating) {
                status = Status.CONNECTED;
                if (!chooseAnother) AndroidUtilities.cancelRunOnUIThread(step);
                notifyListeners();
            }
        }
    }
}
