package org.morok.settings;

/** Deterministic built-in profiles. Chat exceptions, round-video choice and network route are preserved. */
public final class AppProfilePresets {
    public static final int NORMAL = 0;
    public static final int STEALTH = 1;
    public static final int WORK = 2;
    public static final int SAVER = 3;
    public static final int CUSTOM = 4;

    private AppProfilePresets() {}

    public static AppProfileState create(int preset, AppProfileState current) {
        if (current == null) throw new IllegalArgumentException("Current profile is required");
        AppearanceSettings appearance;
        PrivacySettings privacy = PrivacySettings.DEFAULT;
        boolean autoplay;
        boolean notificationContent = true;
        if (preset == NORMAL) {
            appearance = AppearanceSettings.DEFAULT;
            autoplay = true;
        } else if (preset == STEALTH) {
            appearance = AppearanceMode.settings(AppearanceMode.SOLID);
            privacy = PrivacySettings.DEFAULT.withGhostPreset(true);
            autoplay = false;
            notificationContent = false;
        } else if (preset == WORK) {
            appearance = AppearanceSettings.DEFAULT;
            autoplay = false;
        } else if (preset == SAVER) {
            appearance = AppearanceMode.settings(AppearanceMode.MINIMAL);
            autoplay = false;
        } else {
            throw new IllegalArgumentException("Unknown app profile preset");
        }
        SettingsProfile settings = new SettingsProfile(appearance, current.settings.roundVideo, privacy);
        return new AppProfileState(settings, autoplay, autoplay, notificationContent,
                AppProfileState.NETWORK_KEEP);
    }

    public static int detect(AppProfileState state) {
        if (state == null) throw new IllegalArgumentException("App profile is required");
        for (int preset = NORMAL; preset <= SAVER; preset++) {
            AppProfileState expected = create(preset, state);
            if (sameManagedState(state, expected)) return preset;
        }
        return CUSTOM;
    }

    private static boolean sameManagedState(AppProfileState first, AppProfileState second) {
        return first.autoplayVideos == second.autoplayVideos
                && first.autoplayGifs == second.autoplayGifs
                && first.notificationContent == second.notificationContent
                && first.settings.appearance.liquidGlass == second.settings.appearance.liquidGlass
                && first.settings.appearance.reducedEffects == second.settings.appearance.reducedEffects
                && samePrivacy(first.settings.privacy, second.settings.privacy);
    }

    private static boolean samePrivacy(PrivacySettings first, PrivacySettings second) {
        return first.ghostPreset == second.ghostPreset && first.hideTyping == second.hideTyping
                && first.hideOnline == second.hideOnline && first.hideContentRead == second.hideContentRead
                && first.hideRead == second.hideRead && first.hideStoryViews == second.hideStoryViews
                && first.markReadOnReply == second.markReadOnReply
                && first.delayGhostSends == second.delayGhostSends;
    }
}
