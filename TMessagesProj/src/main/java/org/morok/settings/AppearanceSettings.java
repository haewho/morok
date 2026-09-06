package org.morok.settings;

/** Immutable device appearance preferences; rendering capability remains an upstream decision. */
public final class AppearanceSettings {
    public static final AppearanceSettings DEFAULT = new AppearanceSettings(true, false);

    public final boolean liquidGlass;
    public final boolean reducedEffects;

    public AppearanceSettings(boolean liquidGlass, boolean reducedEffects) {
        this.liquidGlass = liquidGlass;
        this.reducedEffects = reducedEffects;
    }

    public AppearanceSettings withLiquidGlass(boolean enabled) {
        return new AppearanceSettings(enabled, reducedEffects);
    }

    public AppearanceSettings withReducedEffects(boolean enabled) {
        return new AppearanceSettings(liquidGlass, enabled);
    }
}
