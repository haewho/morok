package org.morok.media;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** Bounded playback positions scoped by stable authorized user ID. No network or Telegram DB access. */
public final class MorokPlaybackPositionStore {
    private static final int SCHEMA = 1;
    private static final String PREFS_PREFIX = "morok_playback_positions_v1_";
    private static final String REVOCATIONS = "morok_playback_position_revocations_v1";
    private static final String LEGACY_PREFS = "media_saved_pos";
    private static final String KEY_SCHEMA = "_schema";
    private static final String KEY_USER = "_user";
    private static final HashMap<Long, Long> GENERATIONS = new HashMap<>();
    private static boolean legacyCleared;

    public static final class Scope {
        public final int account;
        public final long userId;
        final long generation;
        final String storageKey;

        Scope(int account, long userId, long generation, String storageKey) {
            this.account = account;
            this.userId = userId;
            this.generation = generation;
            this.storageKey = storageKey;
        }
    }

    private MorokPlaybackPositionStore() { }

    private static SharedPreferences positions(long userId) {
        return ApplicationLoader.applicationContext.getSharedPreferences(
                PREFS_PREFIX + userId, Context.MODE_PRIVATE);
    }

    private static SharedPreferences revocations() {
        return ApplicationLoader.applicationContext.getSharedPreferences(REVOCATIONS, Context.MODE_PRIVATE);
    }

    private static long generation(long userId) {
        Long value = GENERATIONS.get(userId);
        return value == null ? 0 : value;
    }

    public static Scope scopeFor(MessageObject object, String mediaIdentity) {
        if (!isEligible(object)) return null;
        return scopeFor(object.currentAccount, mediaIdentity);
    }

    private static boolean isEligible(MessageObject object) {
        if (object == null || object.messageOwner == null || (!object.isVoice() && !object.isMusic())) return false;
        TLRPC.Message message = object.messageOwner;
        TLRPC.MessageMedia media = message.media;
        return message.id > 0 && object.getDialogId() != 0
                && !DialogObject.isEncryptedDialog(object.getDialogId())
                && !(message instanceof TLRPC.TL_message_secret)
                && !message.noforwards && message.ttl == 0 && message.ttl_period == 0
                && message.destroyTime == 0 && (media == null || media.ttl_seconds == 0)
                && !object.isSecretMedia() && !object.isEphemeralAndNotWelcome()
                && !object.needDrawBluredPreview() && !object.isSponsored()
                && !object.isPaidSuggestedPostProtected() && !object.hasRevealedExtendedMedia()
                && !MessagesController.getInstance(object.currentAccount).isPeerNoForwards(object.getDialogId());
    }

    private static synchronized Scope scopeFor(int account, String mediaIdentity) {
        UserConfig config = UserConfig.getInstance(account);
        long userId = config.getClientUserId();
        if (userId <= 0 || !config.isClientActivated() || mediaIdentity == null) return null;
        try {
            recoverRevoked(userId);
            if (!legacyCleared) {
                // The upstream namespace is ambiguous across accounts, so it is deliberately not migrated.
                ApplicationLoader.applicationContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
                        .edit().clear().apply();
                legacyCleared = true;
            }
            return new Scope(account, userId, generation(userId),
                    PlaybackPositionPolicy.storageKey(userId, mediaIdentity));
        } catch (RuntimeException error) {
            return null;
        }
    }

    private static boolean isActive(Scope scope) {
        if (scope == null || scope.generation != generation(scope.userId)) return false;
        try {
            UserConfig config = UserConfig.getInstance(scope.account);
            return config.isClientActivated() && config.getClientUserId() == scope.userId
                    && !revocations().getBoolean(Long.toString(scope.userId), false);
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static void recoverRevoked(long userId) {
        String key = Long.toString(userId);
        if (!revocations().getBoolean(key, false)) return;
        if (!positions(userId).edit().clear().commit()) {
            throw new IllegalStateException("Cannot erase revoked playback positions");
        }
        revocations().edit().remove(key).commit();
    }

    public static synchronized float load(Scope scope) {
        if (!isActive(scope)) return -1f;
        SharedPreferences values = positions(scope.userId);
        try {
            if (values.getInt(KEY_SCHEMA, 0) != SCHEMA || values.getLong(KEY_USER, 0) != scope.userId) {
                return -1f;
            }
            String encoded = values.getString(scope.storageKey, null);
            PlaybackPositionPolicy.Entry entry = PlaybackPositionPolicy.decode(encoded, System.currentTimeMillis());
            if (entry == null && encoded != null) values.edit().remove(scope.storageKey).apply();
            return entry == null ? -1f : entry.progress;
        } catch (RuntimeException error) {
            return -1f;
        }
    }

    public static synchronized boolean save(Scope scope, float progress) {
        if (!isActive(scope)) return false;
        SharedPreferences values = positions(scope.userId);
        SharedPreferences.Editor editor = values.edit();
        long now = System.currentTimeMillis();
        if (!PlaybackPositionPolicy.isRestorable(progress)) {
            return editor.remove(scope.storageKey).commit();
        }

        Map<String, ?> all;
        try { all = values.getAll(); }
        catch (RuntimeException error) { return false; }
        ArrayList<Map.Entry<String, PlaybackPositionPolicy.Entry>> valid = new ArrayList<>();
        for (Map.Entry<String, ?> item : all.entrySet()) {
            if (!item.getKey().startsWith("p.")) continue;
            PlaybackPositionPolicy.Entry decoded = item.getValue() instanceof String
                    ? PlaybackPositionPolicy.decode((String) item.getValue(), now) : null;
            if (decoded == null) editor.remove(item.getKey());
            else if (!item.getKey().equals(scope.storageKey)) {
                valid.add(new java.util.AbstractMap.SimpleImmutableEntry<>(item.getKey(), decoded));
            }
        }
        if (valid.size() >= PlaybackPositionPolicy.MAX_ENTRIES) {
            valid.sort((left, right) -> Long.compare(left.getValue().updatedAt, right.getValue().updatedAt));
            int remove = valid.size() - PlaybackPositionPolicy.MAX_ENTRIES + 1;
            for (int i = 0; i < remove; i++) editor.remove(valid.get(i).getKey());
        }
        editor.putInt(KEY_SCHEMA, SCHEMA).putLong(KEY_USER, scope.userId)
                .putString(scope.storageKey, PlaybackPositionPolicy.encode(now, progress));
        return isActive(scope) && editor.commit();
    }

    public static synchronized boolean clear(Scope scope) {
        return isActive(scope) && positions(scope.userId).edit().remove(scope.storageKey).commit();
    }

    /** Records revocation before the reusable account slot is cleared, then removes this user's positions. */
    public static synchronized void onLogout(long userId) {
        if (userId <= 0) return;
        GENERATIONS.put(userId, generation(userId) + 1);
        String key = Long.toString(userId);
        boolean marked = revocations().edit().putBoolean(key, true).commit();
        boolean erased = positions(userId).edit().clear().commit();
        if (erased) revocations().edit().remove(key).commit();
        if (!marked && !erased) throw new IllegalStateException("Cannot revoke playback positions");
    }
}
