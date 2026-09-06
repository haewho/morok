package org.morok.settings;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserConfig;

/** Own preference files; never writes Telegram auth, LiteMode masks, theme or proxy settings. */
public final class MorokSettings {
    private static volatile AppearanceSettings appearance;

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
            public void save(int version, String firstKey, boolean firstValue, String secondKey, boolean secondValue) {
                preferences.edit().putInt(SettingsRepository.SCHEMA_KEY, version)
                        .putBoolean(firstKey, firstValue).putBoolean(secondKey, secondValue).apply();
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

    /** Resolve a reusable upstream account slot to its authenticated stable identity. */
    public static SettingsRepository forAccount(int accountSlot) {
        if (accountSlot < 0 || accountSlot >= UserConfig.MAX_ACCOUNT_COUNT) {
            throw new IllegalArgumentException("Invalid Telegram account slot");
        }
        UserConfig config = UserConfig.getInstance(accountSlot);
        if (!config.isClientActivated()) {
            throw new IllegalStateException("MOROK account settings require an authenticated account");
        }
        return forUser(config.getClientUserId());
    }

    /** Foundation for account-local settings. Callers must resolve a stable user ID first. */
    public static SettingsRepository forUser(long authenticatedUserId) {
        return repository(SettingsRepository.accountNamespace(authenticatedUserId));
    }
}
