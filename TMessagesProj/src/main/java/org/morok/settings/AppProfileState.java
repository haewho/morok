package org.morok.settings;

/** Complete local state handled by named MOROK profiles. Network routing is deliberately preserved. */
public final class AppProfileState {
    public static final String NETWORK_KEEP = "keep";

    public final SettingsProfile settings;
    public final boolean autoplayVideos;
    public final boolean autoplayGifs;
    public final boolean animatedStickersChat;
    public final boolean animatedStickersKeyboard;
    public final boolean notificationContent;
    public final boolean notificationNames;
    public final String networkPolicy;

    public AppProfileState(SettingsProfile settings, boolean autoplayVideos, boolean autoplayGifs,
                           boolean animatedStickersChat, boolean animatedStickersKeyboard,
                           boolean notificationContent, boolean notificationNames, String networkPolicy) {
        if (settings == null) throw new IllegalArgumentException("Settings profile is required");
        if (!NETWORK_KEEP.equals(networkPolicy)) throw new IllegalArgumentException("Unsupported network policy");
        this.settings = settings;
        this.autoplayVideos = autoplayVideos;
        this.autoplayGifs = autoplayGifs;
        this.animatedStickersChat = animatedStickersChat;
        this.animatedStickersKeyboard = animatedStickersKeyboard;
        this.notificationContent = notificationContent;
        this.notificationNames = notificationNames;
        this.networkPolicy = networkPolicy;
    }
}
