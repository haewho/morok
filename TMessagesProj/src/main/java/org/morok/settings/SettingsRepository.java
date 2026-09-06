package org.morok.settings;

/** Versioned local-only schema. Unknown newer schemas are readable but never overwritten. */
public final class SettingsRepository {
    public static final int SCHEMA_VERSION = 1;
    public static final String SCHEMA_KEY = "schema_version";
    private final SettingsStore store;

    public SettingsRepository(SettingsStore store) {
        this.store = store;
    }

    public AppearanceSettings appearance() {
        return new AppearanceSettings(store.getBoolean("appearance.liquid_glass", true),
                store.getBoolean("appearance.reduced_effects", false));
    }

    public void saveAppearance(AppearanceSettings settings) {
        checkWritable();
        // v0 -> v1 is additive: defaults are read without rewriting unrelated preferences.
        store.save(SCHEMA_VERSION, "appearance.liquid_glass", settings.liquidGlass,
                "appearance.reduced_effects", settings.reducedEffects);
    }

    public void resetAppearance() {
        saveAppearance(AppearanceSettings.DEFAULT);
    }

    public void checkWritable() {
        if (store.getInt(SCHEMA_KEY, 0) > SCHEMA_VERSION) {
            throw new IllegalStateException("MOROK settings were written by a newer version");
        }
    }

    /** Key by a stable authenticated user ID; callers must never pass a reusable account slot. */
    public static String accountNamespace(long userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("An authenticated Telegram user ID is required");
        }
        return "morok_account_user_" + userId;
    }
}
