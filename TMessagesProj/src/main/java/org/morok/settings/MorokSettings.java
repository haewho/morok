package org.morok.settings;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserConfig;

import java.util.concurrent.ConcurrentHashMap;

/** Own preference files; never writes Telegram auth, LiteMode masks, theme or proxy settings. */
public final class MorokSettings {
    private static volatile AppearanceSettings appearance;
    private static volatile RoundVideoSettings roundVideo;
    private static volatile SafetySettings safety;
    private static final ConcurrentHashMap<Long, PrivacySettings> privacy = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, ArchiveSettings> archive = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, InteractionSettings> interactions = new ConcurrentHashMap<>();

    private MorokSettings() {}

    private static SettingsRepository repository(String namespace) {
        SharedPreferences preferences = ApplicationLoader.applicationContext
                .getSharedPreferences(namespace, Context.MODE_PRIVATE);
        return new SettingsRepository(new SettingsStore() {
            @Override
            public int getInt(String key, int fallback) {
                try { return preferences.getInt(key, fallback); }
                catch (ClassCastException e) { return fallback; }
            }

            @Override
            public boolean getBoolean(String key, boolean fallback) {
                try { return preferences.getBoolean(key, fallback); }
                catch (ClassCastException e) { return fallback; }
            }

            @Override
            public String getString(String key, String fallback) {
                try { return preferences.getString(key, fallback); }
                catch (ClassCastException e) { return fallback; }
            }

            @Override
            public void save(int version, String[] booleanKeys, boolean[] booleanValues,
                             String[] stringKeys, String[] stringValues) {
                if (booleanKeys.length != booleanValues.length || stringKeys.length != stringValues.length) {
                    throw new IllegalArgumentException("Mismatched settings");
                }
                SharedPreferences.Editor editor = preferences.edit().putInt(SettingsRepository.SCHEMA_KEY, version);
                for (int i = 0; i < booleanKeys.length; i++) editor.putBoolean(booleanKeys[i], booleanValues[i]);
                for (int i = 0; i < stringKeys.length; i++) editor.putString(stringKeys[i], stringValues[i]);
                editor.apply();
            }
        });
    }

    public static AppearanceSettings appearance() {
        AppearanceSettings result = appearance;
        if (result == null) {
            // A render capability query during early process initialization must remain safe.
            if (ApplicationLoader.applicationContext == null) return AppearanceSettings.DEFAULT;
            synchronized (MorokSettings.class) {
                result = appearance;
                if (result == null) appearance = result = repository("morok_device").appearance();
            }
        }
        return result;
    }

    public static synchronized void setAppearance(AppearanceSettings settings) {
        repository("morok_device").saveAppearance(settings);
        appearance = settings;
    }

    public static RoundVideoSettings roundVideo() {
        RoundVideoSettings result = roundVideo;
        if (result == null) {
            if (ApplicationLoader.applicationContext == null) return RoundVideoSettings.DEFAULT;
            synchronized (MorokSettings.class) {
                result = roundVideo;
                if (result == null) roundVideo = result = repository("morok_device").roundVideo();
            }
        }
        return result;
    }

    public static synchronized void setRoundVideo(RoundVideoSettings settings) {
        repository("morok_device").saveRoundVideo(settings);
        roundVideo = settings;
    }

    public static SafetySettings safety() {
        SafetySettings result = safety;
        if (result == null) {
            if (ApplicationLoader.applicationContext == null) return SafetySettings.DEFAULT;
            synchronized (MorokSettings.class) {
                result = safety;
                if (result == null) safety = result = repository("morok_device").safety();
            }
        }
        return result;
    }

    public static synchronized void setSafety(SafetySettings settings) {
        repository("morok_device").saveSafety(settings);
        safety = settings;
    }

    public static PrivacySettings privacy(int accountSlot) {
        long userId = authenticatedUserId(accountSlot);
        PrivacySettings result = privacy.get(userId);
        if (result == null) {
            PrivacySettings loaded = forUser(userId).privacy();
            PrivacySettings existing = privacy.putIfAbsent(userId, loaded);
            result = existing == null ? loaded : existing;
        }
        return result;
    }

    public static void setPrivacy(int accountSlot, PrivacySettings settings) {
        long userId = authenticatedUserId(accountSlot);
        forUser(userId).savePrivacy(settings);
        privacy.put(userId, settings);
    }

    public static ArchiveSettings archive(int accountSlot) {
        long userId = authenticatedUserId(accountSlot);
        ArchiveSettings result = archive.get(userId);
        if (result == null) {
            ArchiveSettings loaded = forUser(userId).archive();
            ArchiveSettings existing = archive.putIfAbsent(userId, loaded);
            result = existing == null ? loaded : existing;
        }
        return result;
    }

    public static void setArchive(int accountSlot, ArchiveSettings settings) {
        long userId = authenticatedUserId(accountSlot);
        forUser(userId).saveArchive(settings);
        archive.put(userId, settings);
    }

    public static InteractionSettings interactions(int accountSlot) {
        long userId = authenticatedUserId(accountSlot);
        InteractionSettings result = interactions.get(userId);
        if (result == null) {
            InteractionSettings loaded = forUser(userId).interactions();
            InteractionSettings existing = interactions.putIfAbsent(userId, loaded);
            result = existing == null ? loaded : existing;
        }
        return result;
    }

    public static void setInteractions(int accountSlot, InteractionSettings settings) {
        long userId = authenticatedUserId(accountSlot);
        forUser(userId).saveInteractions(settings);
        interactions.put(userId, settings);
    }

    /** Creates an explicit transfer bundle without account identity, chat IDs, archives, proxy data or secrets. */
    public static SettingsProfile exportProfile(int accountSlot) {
        return new SettingsProfile(appearance(), roundVideo(), privacy(accountSlot));
    }

    /** Applies a reviewed bundle locally. Existing chat exceptions remain scoped to the destination account. */
    public static synchronized void applyProfile(int accountSlot, SettingsProfile profile) {
        if (profile == null) throw new IllegalArgumentException("Profile is required");
        long userId = authenticatedUserId(accountSlot);
        SettingsRepository deviceRepository = repository("morok_device");
        SettingsRepository accountRepository = forUser(userId);
        deviceRepository.checkWritable();
        accountRepository.checkWritable();
        PrivacySettings importedPrivacy = profile.applyPrivacyTo(privacy(accountSlot));
        deviceRepository.saveAppearance(profile.appearance);
        deviceRepository.saveRoundVideo(profile.roundVideo);
        accountRepository.savePrivacy(importedPrivacy);
        appearance = profile.appearance;
        roundVideo = profile.roundVideo;
        privacy.put(userId, importedPrivacy);
    }

    /** Resolve a reusable upstream account slot to its authenticated stable identity. */
    public static SettingsRepository forAccount(int accountSlot) {
        return forUser(authenticatedUserId(accountSlot));
    }

    public static long authenticatedUserId(int accountSlot) {
        if (accountSlot < 0 || accountSlot >= UserConfig.MAX_ACCOUNT_COUNT) {
            throw new IllegalArgumentException("Invalid Telegram account slot");
        }
        UserConfig config = UserConfig.getInstance(accountSlot);
        if (!config.isClientActivated()) {
            throw new IllegalStateException("MOROK account settings require an authenticated account");
        }
        return config.getClientUserId();
    }

    /** Foundation for account-local settings. Callers must resolve a stable user ID first. */
    public static SettingsRepository forUser(long authenticatedUserId) {
        return repository(SettingsRepository.accountNamespace(authenticatedUserId));
    }
}
