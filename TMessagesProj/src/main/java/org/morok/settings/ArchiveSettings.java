package org.morok.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Account-local allowlist for the encrypted archive. Disabled by default. */
public final class ArchiveSettings {
    public static final int MAX_CHATS = 256;
    public static final ArchiveSettings DEFAULT = new ArchiveSettings(false, Collections.emptySet());

    public final boolean enabled;
    public final Set<Long> chats;

    public ArchiveSettings(boolean enabled, Set<Long> chats) {
        this.enabled = enabled;
        LinkedHashSet<Long> valid = new LinkedHashSet<>();
        if (chats != null) {
            for (Long dialogId : chats) {
                if (dialogId != null && dialogId != 0 && valid.size() < MAX_CHATS) valid.add(dialogId);
            }
        }
        this.chats = Collections.unmodifiableSet(valid);
    }

    public ArchiveSettings withEnabled(boolean value) {
        return new ArchiveSettings(value, chats);
    }

    public ArchiveSettings withChat(long dialogId, boolean value) {
        if (dialogId == 0) return this;
        LinkedHashSet<Long> updated = new LinkedHashSet<>(chats);
        if (value) {
            if (updated.size() >= MAX_CHATS && !updated.contains(dialogId)) return this;
            updated.add(dialogId);
        } else {
            updated.remove(dialogId);
        }
        return new ArchiveSettings(enabled, updated);
    }

    public ArchiveSettings withoutChats() {
        return chats.isEmpty() ? this : new ArchiveSettings(enabled, Collections.emptySet());
    }

    public boolean archives(long dialogId) {
        return enabled && dialogId != 0 && chats.contains(dialogId);
    }

    public String encodeChats() {
        List<Long> sorted = new ArrayList<>(chats);
        Collections.sort(sorted);
        StringBuilder result = new StringBuilder();
        for (long dialogId : sorted) {
            if (result.length() != 0) result.append(',');
            result.append(dialogId);
        }
        return result.toString();
    }

    public static Set<Long> decodeChats(String encoded) {
        if (encoded == null || encoded.isEmpty()) return Collections.emptySet();
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        for (String value : encoded.split(",")) {
            if (result.size() >= MAX_CHATS) break;
            try {
                long dialogId = Long.parseLong(value);
                if (dialogId != 0) result.add(dialogId);
            } catch (NumberFormatException ignored) { }
        }
        return result;
    }
}
