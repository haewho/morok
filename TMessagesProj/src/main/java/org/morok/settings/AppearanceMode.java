package org.morok.settings;

/** Named shortcuts over the two independently editable MOROK appearance controls. */
public final class AppearanceMode {
    public static final int TELEGRAM = 0;
    public static final int SOLID = 1;
    public static final int MINIMAL = 2;
    public static final int CUSTOM = 3;

    private AppearanceMode() {}

    public static AppearanceSettings settings(int mode) {
        if (mode == TELEGRAM) return AppearanceSettings.DEFAULT;
        if (mode == SOLID) return new AppearanceSettings(false, false);
        if (mode == MINIMAL) return new AppearanceSettings(false, true);
        throw new IllegalArgumentException("A named appearance mode is required");
    }

    public static int detect(AppearanceSettings settings) {
        if (settings == null) throw new IllegalArgumentException("Appearance settings are required");
        if (settings.liquidGlass && !settings.reducedEffects) return TELEGRAM;
        if (!settings.liquidGlass && !settings.reducedEffects) return SOLID;
        if (!settings.liquidGlass) return MINIMAL;
        return CUSTOM;
    }
}
