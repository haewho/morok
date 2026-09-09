package org.morok.settings;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import org.morok.appearance.MorokAppearance;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.NotificationsController;

/** Applies reviewed local profiles; it never changes the Telegram proxy route or server settings. */
public final class MorokAppProfiles {
    private static final String CURRENT_NOTIFICATION_CONTENT = "current.notification_content";
    private static final String CUSTOM = "saved.custom";
    private static final String PREVIOUS = "saved.previous";

    private MorokAppProfiles() {}

    public static AppProfileState current(int accountSlot) {
        SharedPreferences preferences = preferences(accountSlot);
        return new AppProfileState(MorokSettings.exportProfile(accountSlot),
                LiteMode.isEnabledSetting(LiteMode.FLAG_AUTOPLAY_VIDEOS),
                LiteMode.isEnabledSetting(LiteMode.FLAG_AUTOPLAY_GIFS),
                preferences.getBoolean(CURRENT_NOTIFICATION_CONTENT, true), AppProfileState.NETWORK_KEEP);
    }

    public static void saveCustom(int accountSlot) {
        String encoded = AppProfileStateCodec.encode(current(accountSlot));
        if (!preferences(accountSlot).edit().putString(CUSTOM, encoded).commit()) {
            throw new IllegalStateException("Could not save custom app profile");
        }
    }

    public static AppProfileState custom(int accountSlot) { return read(accountSlot, CUSTOM); }

    public static AppProfileState previous(int accountSlot) { return read(accountSlot, PREVIOUS); }

    public static void apply(int accountSlot, AppProfileState target, Activity activity) {
        if (target == null) throw new IllegalArgumentException("Target app profile is required");
        AppProfileState before = current(accountSlot);
        SharedPreferences preferences = preferences(accountSlot);
        if (!preferences.edit().putString(PREVIOUS, AppProfileStateCodec.encode(before)).commit()) {
            throw new IllegalStateException("Could not save previous app profile");
        }
        try {
            applyState(accountSlot, target, before.settings.appearance, activity);
        } catch (RuntimeException error) {
            try { applyState(accountSlot, before, target.settings.appearance, activity); }
            catch (RuntimeException rollback) { error.addSuppressed(rollback); }
            throw error;
        }
    }

    public static boolean showsNotificationContent(int accountSlot) {
        try { return preferences(accountSlot).getBoolean(CURRENT_NOTIFICATION_CONTENT, true); }
        catch (RuntimeException unavailableAccount) { return true; }
    }

    public static void setNotificationContent(int accountSlot, boolean showContent) {
        if (!preferences(accountSlot).edit().putBoolean(CURRENT_NOTIFICATION_CONTENT, showContent).commit()) {
            throw new IllegalStateException("Could not save notification privacy setting");
        }
        NotificationsController.getInstance(accountSlot).showNotifications();
    }

    private static void applyState(int accountSlot, AppProfileState state,
                                   AppearanceSettings previousAppearance, Activity activity) {
        MorokSettings.applyProfile(accountSlot, state.settings);
        LiteMode.toggleFlag(LiteMode.FLAG_AUTOPLAY_VIDEOS, state.autoplayVideos);
        LiteMode.toggleFlag(LiteMode.FLAG_AUTOPLAY_GIFS, state.autoplayGifs);
        if (!preferences(accountSlot).edit()
                .putBoolean(CURRENT_NOTIFICATION_CONTENT, state.notificationContent).commit()) {
            throw new IllegalStateException("Could not save notification privacy profile");
        }
        NotificationsController.getInstance(accountSlot).showNotifications();
        MorokAppearance.refreshAfterImport(previousAppearance, state.settings.appearance, activity);
    }

    private static AppProfileState read(int accountSlot, String key) {
        SharedPreferences preferences = preferences(accountSlot);
        String encoded = preferences.getString(key, "");
        if (encoded == null || encoded.isEmpty()) return null;
        try { return AppProfileStateCodec.decode(encoded); }
        catch (RuntimeException invalid) {
            preferences.edit().remove(key).apply();
            return null;
        }
    }

    private static SharedPreferences preferences(int accountSlot) {
        long userId = MorokSettings.authenticatedUserId(accountSlot);
        return ApplicationLoader.applicationContext.getSharedPreferences(
                "morok_app_profiles_user_" + userId, Context.MODE_PRIVATE);
    }
}
