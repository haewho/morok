package org.morok.settings;

/** Transferable MOROK preferences. Account identities and chat-specific IDs are deliberately absent. */
public final class SettingsProfile {
    public final AppearanceSettings appearance;
    public final RoundVideoSettings roundVideo;
    public final PrivacySettings privacy;

    public SettingsProfile(AppearanceSettings appearance, RoundVideoSettings roundVideo, PrivacySettings privacy) {
        if (appearance == null || roundVideo == null || privacy == null) {
            throw new IllegalArgumentException("A complete MOROK settings profile is required");
        }
        this.appearance = appearance;
        this.roundVideo = roundVideo;
        this.privacy = privacy.withoutNormalBehaviorChats();
    }

    /** Import global policy flags while retaining the destination account's chat exceptions. */
    public PrivacySettings applyPrivacyTo(PrivacySettings destination) {
        if (destination == null) throw new IllegalArgumentException("Destination privacy settings are required");
        return new PrivacySettings(privacy.ghostPreset, privacy.hideTyping, privacy.hideOnline,
                privacy.hideContentRead, privacy.hideRead, privacy.hideStoryViews,
                privacy.markReadOnReply, privacy.delayGhostSends, destination.normalBehaviorChats);
    }
}
