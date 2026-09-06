package org.morok.proxy;

import java.util.HashMap;
import java.util.Map;

/** Preserve the active route when Android updates memory but fails to persist an editor. */
public final class ProxyRouteTransaction {
    public interface Store {
        Map<String, ?> values();
        /** Null values remove only the specified key. */
        boolean commit(Map<String, Object> values);
    }

    private ProxyRouteTransaction() {}

    public static boolean commit(Store store, Map<String, Object> next) {
        Map<String, ?> before = store.values();
        Map<String, Object> previous = new HashMap<>();
        for (String key : next.keySet()) previous.put(key, before.get(key));
        try {
            if (store.commit(next)) return true;
        } catch (RuntimeException ignored) {
            // Restore the in-process values too; SharedPreferences commits memory first.
        }
        try { store.commit(previous); } catch (RuntimeException ignored) { }
        return false;
    }
}
