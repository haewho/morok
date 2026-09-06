package org.morok.memory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe visibility bridge between a durable new-event journal and the projected card index. */
public final class MemoryTrackingIndex {
    private volatile Set<String> persisted = Collections.emptySet();
    private final Set<String> pending = ConcurrentHashMap.newKeySet();

    public void replacePersisted(Collection<String> keys) {
        persisted = Collections.unmodifiableSet(new HashSet<>(keys));
    }

    public void addPending(Collection<String> keys) { pending.addAll(keys); }
    public void completePending(String key) { pending.remove(key); }
    public boolean tracks(String key) { return persisted.contains(key) || pending.contains(key); }
    public boolean hasAny() { return !persisted.isEmpty() || !pending.isEmpty(); }
}
