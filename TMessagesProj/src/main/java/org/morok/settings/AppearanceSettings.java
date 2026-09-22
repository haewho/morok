package org.morok.settings;

/** Immutable device appearance preferences; rendering capability remains an upstream decision. */
public final class AppearanceSettings {
    public static final String DENSITY_COMPACT = "compact";
    public static final String DENSITY_STANDARD = "standard";
    public static final String DENSITY_COMFORTABLE = "comfortable";
    public static final String AVATAR_SMALL = "small";
    public static final String AVATAR_STANDARD = "standard";
    public static final String AVATAR_LARGE = "large";
    public static final String LINE_SPACING_TIGHT = "tight";
    public static final String LINE_SPACING_STANDARD = "standard";
    public static final String LINE_SPACING_RELAXED = "relaxed";
    public static final String TEXT_SIZE_SMALL = "small";
    public static final String TEXT_SIZE_STANDARD = "standard";
    public static final String TEXT_SIZE_LARGE = "large";
    public static final AppearanceSettings DEFAULT = new AppearanceSettings(
            true, false, DENSITY_STANDARD, AVATAR_STANDARD, false, LINE_SPACING_STANDARD,
            TEXT_SIZE_STANDARD);

    public final boolean liquidGlass;
    public final boolean reducedEffects;
    public final String dialogListDensity;
    public final String dialogListAvatarSize;
    public final boolean dialogListTimestampSeconds;
    public final String dialogListLineSpacing;
    public final String dialogListTextSize;

    public AppearanceSettings(boolean liquidGlass, boolean reducedEffects) {
        this(liquidGlass, reducedEffects, DENSITY_STANDARD, AVATAR_STANDARD, false,
                LINE_SPACING_STANDARD, TEXT_SIZE_STANDARD);
    }

    public AppearanceSettings(boolean liquidGlass, boolean reducedEffects, String dialogListDensity) {
        this(liquidGlass, reducedEffects, dialogListDensity, AVATAR_STANDARD, false,
                LINE_SPACING_STANDARD, TEXT_SIZE_STANDARD);
    }

    public AppearanceSettings(boolean liquidGlass, boolean reducedEffects, String dialogListDensity,
                              String dialogListAvatarSize) {
        this(liquidGlass, reducedEffects, dialogListDensity, dialogListAvatarSize, false,
                LINE_SPACING_STANDARD, TEXT_SIZE_STANDARD);
    }

    public AppearanceSettings(boolean liquidGlass, boolean reducedEffects, String dialogListDensity,
                              String dialogListAvatarSize, boolean dialogListTimestampSeconds) {
        this(liquidGlass, reducedEffects, dialogListDensity, dialogListAvatarSize,
                dialogListTimestampSeconds, LINE_SPACING_STANDARD, TEXT_SIZE_STANDARD);
    }

    public AppearanceSettings(boolean liquidGlass, boolean reducedEffects, String dialogListDensity,
                              String dialogListAvatarSize, boolean dialogListTimestampSeconds,
                              String dialogListLineSpacing) {
        this(liquidGlass, reducedEffects, dialogListDensity, dialogListAvatarSize,
                dialogListTimestampSeconds, dialogListLineSpacing, TEXT_SIZE_STANDARD);
    }

    public AppearanceSettings(boolean liquidGlass, boolean reducedEffects, String dialogListDensity,
                              String dialogListAvatarSize, boolean dialogListTimestampSeconds,
                              String dialogListLineSpacing, String dialogListTextSize) {
        this.liquidGlass = liquidGlass;
        this.reducedEffects = reducedEffects;
        this.dialogListDensity = validDensity(dialogListDensity) ? dialogListDensity : DENSITY_STANDARD;
        this.dialogListAvatarSize = validAvatarSize(dialogListAvatarSize)
                ? dialogListAvatarSize : AVATAR_STANDARD;
        this.dialogListTimestampSeconds = dialogListTimestampSeconds;
        this.dialogListLineSpacing = validLineSpacing(dialogListLineSpacing)
                ? dialogListLineSpacing : LINE_SPACING_STANDARD;
        this.dialogListTextSize = validTextSize(dialogListTextSize)
                ? dialogListTextSize : TEXT_SIZE_STANDARD;
    }

    public AppearanceSettings withLiquidGlass(boolean enabled) {
        return new AppearanceSettings(enabled, reducedEffects, dialogListDensity, dialogListAvatarSize,
                dialogListTimestampSeconds, dialogListLineSpacing, dialogListTextSize);
    }

    public AppearanceSettings withReducedEffects(boolean enabled) {
        return new AppearanceSettings(liquidGlass, enabled, dialogListDensity, dialogListAvatarSize,
                dialogListTimestampSeconds, dialogListLineSpacing, dialogListTextSize);
    }

    public AppearanceSettings withDialogListDensity(String density) {
        return new AppearanceSettings(liquidGlass, reducedEffects, density, dialogListAvatarSize,
                dialogListTimestampSeconds, dialogListLineSpacing, dialogListTextSize);
    }

    public AppearanceSettings withDialogListAvatarSize(String size) {
        return new AppearanceSettings(liquidGlass, reducedEffects, dialogListDensity, size,
                dialogListTimestampSeconds, dialogListLineSpacing, dialogListTextSize);
    }

    public AppearanceSettings withDialogListTimestampSeconds(boolean enabled) {
        return new AppearanceSettings(liquidGlass, reducedEffects, dialogListDensity,
                dialogListAvatarSize, enabled, dialogListLineSpacing, dialogListTextSize);
    }

    public AppearanceSettings withDialogListLineSpacing(String spacing) {
        return new AppearanceSettings(liquidGlass, reducedEffects, dialogListDensity,
                dialogListAvatarSize, dialogListTimestampSeconds, spacing, dialogListTextSize);
    }

    public AppearanceSettings withDialogListTextSize(String size) {
        return new AppearanceSettings(liquidGlass, reducedEffects, dialogListDensity,
                dialogListAvatarSize, dialogListTimestampSeconds, dialogListLineSpacing, size);
    }

    public int dialogListHeightOffsetDp() {
        if (DENSITY_COMPACT.equals(dialogListDensity)) return -8;
        if (DENSITY_COMFORTABLE.equals(dialogListDensity)) return 8;
        return 0;
    }

    public int dialogListHeightDp(int upstreamDp) {
        int height = upstreamDp + dialogListHeightOffsetDp();
        if (dialogListHeightOffsetDp() < 0 && dialogListAvatarSizeOffsetDp() > 0) {
            height += dialogListAvatarSizeOffsetDp() / 2;
        }
        return Math.max(56, height);
    }

    public static boolean validDensity(String density) {
        return DENSITY_COMPACT.equals(density) || DENSITY_STANDARD.equals(density)
                || DENSITY_COMFORTABLE.equals(density);
    }

    public int dialogListAvatarSizeOffsetDp() {
        if (AVATAR_SMALL.equals(dialogListAvatarSize)) return -4;
        if (AVATAR_LARGE.equals(dialogListAvatarSize)) return 4;
        return 0;
    }

    public int dialogListAvatarSizeDp(int upstreamDp) {
        return Math.max(44, Math.min(60, upstreamDp + dialogListAvatarSizeOffsetDp()));
    }

    public static boolean validAvatarSize(String size) {
        return AVATAR_SMALL.equals(size) || AVATAR_STANDARD.equals(size) || AVATAR_LARGE.equals(size);
    }

    public int dialogListLineSpacingExtraDp() {
        if (LINE_SPACING_TIGHT.equals(dialogListLineSpacing)) return 0;
        if (LINE_SPACING_RELAXED.equals(dialogListLineSpacing)) return 2;
        return 1;
    }

    public static boolean validLineSpacing(String spacing) {
        return LINE_SPACING_TIGHT.equals(spacing) || LINE_SPACING_STANDARD.equals(spacing)
                || LINE_SPACING_RELAXED.equals(spacing);
    }

    public int dialogListTextSizeDp(int upstreamDp) {
        int offset = TEXT_SIZE_SMALL.equals(dialogListTextSize) ? -1
                : TEXT_SIZE_LARGE.equals(dialogListTextSize) ? 1 : 0;
        return Math.max(13, Math.min(20, upstreamDp + offset));
    }

    public static boolean validTextSize(String size) {
        return TEXT_SIZE_SMALL.equals(size) || TEXT_SIZE_STANDARD.equals(size)
                || TEXT_SIZE_LARGE.equals(size);
    }
}
