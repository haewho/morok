package org.morok.settings;

/** Versioned local-only schema. Unknown newer schemas are readable but never overwritten. */
public final class SettingsRepository {
    public static final int SCHEMA_VERSION = 12;
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
        // Migrations are additive: defaults are read without rewriting unrelated preferences.
        store.saveBooleans(SCHEMA_VERSION,
                new String[] {"appearance.liquid_glass", "appearance.reduced_effects"},
                new boolean[] {settings.liquidGlass, settings.reducedEffects});
    }

    public void resetAppearance() {
        saveAppearance(AppearanceSettings.DEFAULT);
    }

    public RoundVideoSettings roundVideo() {
        return new RoundVideoSettings(store.getBoolean("camera.round_video_enhanced", false),
                store.getString("camera.round_video_profile", RoundVideoSettings.PROFILE_AUTO));
    }

    public void saveRoundVideo(RoundVideoSettings settings) {
        checkWritable();
        store.save(SCHEMA_VERSION,
                new String[] {"camera.round_video_enhanced"}, new boolean[] {settings.enhanced},
                new String[] {"camera.round_video_profile"}, new String[] {settings.profile});
    }

    public void resetRoundVideo() {
        saveRoundVideo(RoundVideoSettings.DEFAULT);
    }

    public SafetySettings safety() {
        return new SafetySettings(store.getBoolean("safety.protect_screen", false),
                store.getBoolean("safety.confirm_outgoing_calls", false),
                store.getBoolean("safety.confirm_round_videos", false));
    }

    public void saveSafety(SafetySettings settings) {
        if (settings == null) throw new IllegalArgumentException("Safety settings are required");
        checkWritable();
        store.saveBooleans(SCHEMA_VERSION,
                new String[] {"safety.protect_screen", "safety.confirm_outgoing_calls", "safety.confirm_round_videos"},
                new boolean[] {settings.protectScreen, settings.confirmOutgoingCalls, settings.confirmRoundVideos});
    }

    public void resetSafety() {
        saveSafety(SafetySettings.DEFAULT);
    }

    public InteractionSettings interactions() {
        return new InteractionSettings(store.getBoolean("interactions.double_tap_reactions", true));
    }

    public void saveInteractions(InteractionSettings settings) {
        if (settings == null) throw new IllegalArgumentException("Interaction settings are required");
        checkWritable();
        store.saveBooleans(SCHEMA_VERSION,
                new String[] {"interactions.double_tap_reactions"},
                new boolean[] {settings.doubleTapReactionsEnabled});
    }

    public void resetInteractions() {
        saveInteractions(InteractionSettings.DEFAULT);
    }

    public PrivacySettings privacy() {
        return new PrivacySettings(store.getBoolean("privacy.ghost_preset", false),
                store.getBoolean("privacy.hide_typing", false),
                store.getBoolean("privacy.hide_online", false),
                store.getBoolean("privacy.hide_content_read", false),
                store.getBoolean("privacy.hide_read", false),
                store.getBoolean("privacy.hide_story_views", false),
                store.getBoolean("privacy.mark_read_on_reply", false),
                store.getBoolean("privacy.delay_ghost_sends", false),
                PrivacySettings.decodeNormalBehaviorChats(store.getString("privacy.normal_behavior_chats", "")));
    }

    public void savePrivacy(PrivacySettings settings) {
        checkWritable();
        store.save(SCHEMA_VERSION,
                new String[] {"privacy.ghost_preset", "privacy.hide_typing", "privacy.hide_online", "privacy.hide_content_read", "privacy.hide_read", "privacy.hide_story_views", "privacy.mark_read_on_reply", "privacy.delay_ghost_sends"},
                new boolean[] {settings.ghostPreset, settings.hideTyping, settings.hideOnline, settings.hideContentRead, settings.hideRead, settings.hideStoryViews, settings.markReadOnReply, settings.delayGhostSends},
                new String[] {"privacy.normal_behavior_chats"},
                new String[] {settings.encodeNormalBehaviorChats()});
    }

    public void resetPrivacy() {
        savePrivacy(PrivacySettings.DEFAULT);
    }

    public ArchiveSettings archive() {
        return new ArchiveSettings(store.getBoolean("archive.enabled", false),
                ArchiveSettings.decodeChats(store.getString("archive.chats", "")));
    }

    public void saveArchive(ArchiveSettings settings) {
        checkWritable();
        store.save(SCHEMA_VERSION,
                new String[] {"archive.enabled"}, new boolean[] {settings.enabled},
                new String[] {"archive.chats"}, new String[] {settings.encodeChats()});
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
