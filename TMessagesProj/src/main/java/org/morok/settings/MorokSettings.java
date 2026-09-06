package org.morok.settings;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserConfig;

import java.util.concurrent.ConcurrentHashMap;

/** Own preference files; never writes Telegram auth, LiteMode masks, theme or proxy settings. */
public final class MorokSettings {
    private static volatile AppearanceSettings appearance;
    private static final ConcurrentHashMap<Long, PrivacySettings> privacy = new ConcurrentHashMap<>();

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
            public void saveBooleans(int version, String[] keys, boolean[] values) {
                if (keys.length != values.length) throw new IllegalArgumentException("Mismatched settings");
                SharedPreferences.Editor editor = preferences.edit().putInt(SettingsRepository.SCHEMA_KEY, version);
                for (int i = 0; i < keys.length; i++) editor.putBoolean(keys[i], values[i]);
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

    public static PrivacySettings privacy(int accountSlot) {
        long userId = userId(accountSlot);
        PrivacySettings result = privacy.get(userId);
        if (result == null) {
            PrivacySettings loaded = forUser(userId).privacy();
            PrivacySettings existing = privacy.putIfAbsent(userId, loaded);
            result = existing == null ? loaded : existing;
        }
        return result;
    }

    public static void setPrivacy(int accountSlot, PrivacySettings settings) {
        long userId = userId(accountSlot);
        forUser(userId).savePrivacy(settings);
        privacy.put(userId, settings);
    }

    /** Resolve a reusable upstream account slot to its authenticated stable identity. */
    public static SettingsRepository forAccount(int accountSlot) {
        return forUser(userId(accountSlot));
    }

    private static long userId(int accountSlot) {
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
