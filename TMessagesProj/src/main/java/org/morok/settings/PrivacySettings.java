package org.morok.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Immutable account-local experimental privacy policy. All controls default to normal Telegram behavior. */
public final class PrivacySettings {
    private static final int MAX_CHAT_EXCEPTIONS = 256;
    public static final PrivacySettings DEFAULT = new PrivacySettings(false, false, false, false, false, false, false, Collections.emptySet());
    public static final int ACTION_CANCEL = 2;

    public final boolean ghostPreset;
    public final boolean hideTyping;
    public final boolean hideOnline;
    public final boolean hideContentRead;
    public final boolean hideRead;
    public final boolean hideStoryViews;
    public final boolean markReadOnReply;
    public final Set<Long> normalBehaviorChats;

    public PrivacySettings(boolean ghostPreset, boolean hideTyping, boolean hideOnline, boolean hideContentRead,
                           boolean hideRead, boolean hideStoryViews, boolean markReadOnReply,
                           Set<Long> normalBehaviorChats) {
        this.ghostPreset = ghostPreset;
        this.hideTyping = hideTyping;
        this.hideOnline = hideOnline;
        this.hideContentRead = hideContentRead;
        this.hideRead = hideRead;
        this.hideStoryViews = hideStoryViews;
        this.markReadOnReply = markReadOnReply;
        this.normalBehaviorChats = immutableValidChats(normalBehaviorChats);
    }

    public PrivacySettings withGhostPreset(boolean enabled) {
        return copy(enabled, hideTyping, hideOnline, hideContentRead, hideRead, hideStoryViews, markReadOnReply, normalBehaviorChats);
    }

    public PrivacySettings withHideTyping(boolean enabled) {
        return copy(ghostPreset, enabled, hideOnline, hideContentRead, hideRead, hideStoryViews, markReadOnReply, normalBehaviorChats);
    }

    public PrivacySettings withHideOnline(boolean enabled) {
        return copy(ghostPreset, hideTyping, enabled, hideContentRead, hideRead, hideStoryViews, markReadOnReply, normalBehaviorChats);
    }

    public PrivacySettings withHideContentRead(boolean enabled) {
        return copy(ghostPreset, hideTyping, hideOnline, enabled, hideRead, hideStoryViews, markReadOnReply, normalBehaviorChats);
    }

    public PrivacySettings withHideRead(boolean enabled) {
        return copy(ghostPreset, hideTyping, hideOnline, hideContentRead, enabled, hideStoryViews, markReadOnReply, normalBehaviorChats);
    }

    public PrivacySettings withHideStoryViews(boolean enabled) {
        return copy(ghostPreset, hideTyping, hideOnline, hideContentRead, hideRead, enabled, markReadOnReply, normalBehaviorChats);
    }

    public PrivacySettings withMarkReadOnReply(boolean enabled) {
        return copy(ghostPreset, hideTyping, hideOnline, hideContentRead, hideRead, hideStoryViews, enabled, normalBehaviorChats);
    }

    public PrivacySettings withNormalBehaviorForChat(long dialogId, boolean enabled) {
        if (dialogId == 0) return this;
        LinkedHashSet<Long> chats = new LinkedHashSet<>(normalBehaviorChats);
        if (enabled) {
            if (chats.size() >= MAX_CHAT_EXCEPTIONS && !chats.contains(dialogId)) return this;
            chats.add(dialogId);
        } else {
            chats.remove(dialogId);
        }
        return copy(ghostPreset, hideTyping, hideOnline, hideContentRead, hideRead, hideStoryViews, markReadOnReply, chats);
    }

    public boolean hidesTyping() {
        return ghostPreset || hideTyping;
    }

    public boolean hidesOnline() {
        return ghostPreset || hideOnline;
    }

    public boolean hidesContentRead() {
        return ghostPreset || hideContentRead;
    }

    public boolean hidesRead() {
        return ghostPreset || hideRead;
    }

    public boolean hidesStoryViews() {
        return ghostPreset || hideStoryViews;
    }

    public boolean usesNormalBehavior(long dialogId) {
        return dialogId != 0 && normalBehaviorChats.contains(dialogId);
    }

    /** Cancellation remains allowed so enabling privacy cannot leave a stale remote typing indicator. */
    public boolean allowsTypingAction(int action) {
        return action == ACTION_CANCEL || !hidesTyping();
    }

    public boolean allowsTypingAction(long dialogId, int action) {
        return usesNormalBehavior(dialogId) || allowsTypingAction(action);
    }

    public boolean allowsContentRead(long dialogId) {
        return usesNormalBehavior(dialogId) || !hidesContentRead();
    }

    public boolean allowsReadReceipt(long dialogId) {
        return usesNormalBehavior(dialogId) || !hidesRead();
    }

    public boolean allowsStoryViewReceipt(long dialogId) {
        return usesNormalBehavior(dialogId) || !hidesStoryViews();
    }

    public boolean shouldMarkReadOnReply(long dialogId) {
        return !usesNormalBehavior(dialogId) && hidesRead() && markReadOnReply;
    }

    public String encodeNormalBehaviorChats() {
        List<Long> chats = new ArrayList<>(normalBehaviorChats);
        Collections.sort(chats);
        StringBuilder result = new StringBuilder();
        for (long chat : chats) {
            if (result.length() != 0) result.append(',');
            result.append(chat);
        }
        return result.toString();
    }

    public static Set<Long> decodeNormalBehaviorChats(String encoded) {
        if (encoded == null || encoded.isEmpty()) return Collections.emptySet();
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        for (String value : encoded.split(",")) {
            if (result.size() >= MAX_CHAT_EXCEPTIONS) break;
            try {
                long dialogId = Long.parseLong(value);
                if (dialogId != 0) result.add(dialogId);
            } catch (NumberFormatException ignored) {}
        }
        return result;
    }

    private static PrivacySettings copy(boolean ghostPreset, boolean hideTyping, boolean hideOnline,
                                        boolean hideContentRead, boolean hideRead, boolean hideStoryViews,
                                        boolean markReadOnReply, Set<Long> normalBehaviorChats) {
        return new PrivacySettings(ghostPreset, hideTyping, hideOnline, hideContentRead, hideRead,
                hideStoryViews, markReadOnReply, normalBehaviorChats);
    }

    private static Set<Long> immutableValidChats(Set<Long> chats) {
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        if (chats != null) {
            for (Long dialogId : chats) {
                if (dialogId != null && dialogId != 0 && result.size() < MAX_CHAT_EXCEPTIONS) result.add(dialogId);
            }
        }
        return Collections.unmodifiableSet(result);
    }
}
