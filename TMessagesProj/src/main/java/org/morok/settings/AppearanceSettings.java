package org.morok.settings;

/** Immutable device appearance preferences; rendering capability remains an upstream decision. */
public final class AppearanceSettings {
    public static final String DENSITY_COMPACT = "compact";
    public static final String DENSITY_STANDARD = "standard";
    public static final String DENSITY_COMFORTABLE = "comfortable";
    public static final AppearanceSettings DEFAULT = new AppearanceSettings(true, false, DENSITY_STANDARD);

    public final boolean liquidGlass;
    public final boolean reducedEffects;
    public final String dialogListDensity;

    public AppearanceSettings(boolean liquidGlass, boolean reducedEffects) {
        this(liquidGlass, reducedEffects, DENSITY_STANDARD);
    }

    public AppearanceSettings(boolean liquidGlass, boolean reducedEffects, String dialogListDensity) {
        this.liquidGlass = liquidGlass;
        this.reducedEffects = reducedEffects;
        this.dialogListDensity = validDensity(dialogListDensity) ? dialogListDensity : DENSITY_STANDARD;
    }

    public AppearanceSettings withLiquidGlass(boolean enabled) {
        return new AppearanceSettings(enabled, reducedEffects, dialogListDensity);
    }

    public AppearanceSettings withReducedEffects(boolean enabled) {
        return new AppearanceSettings(liquidGlass, enabled, dialogListDensity);
    }

    public AppearanceSettings withDialogListDensity(String density) {
        return new AppearanceSettings(liquidGlass, reducedEffects, density);
    }

    public int dialogListHeightOffsetDp() {
        if (DENSITY_COMPACT.equals(dialogListDensity)) return -8;
        if (DENSITY_COMFORTABLE.equals(dialogListDensity)) return 8;
        return 0;
    }

    public static boolean validDensity(String density) {
        return DENSITY_COMPACT.equals(density) || DENSITY_STANDARD.equals(density)
                || DENSITY_COMFORTABLE.equals(density);
    }
}
