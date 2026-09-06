package org.morok.settings;

/** Device-local experimental round-video preferences. Disabled preserves upstream parameters. */
public final class RoundVideoSettings {
    public static final String PROFILE_AUTO = "auto";
    public static final String PROFILE_SAVER = "saver";
    public static final String PROFILE_HIGH = "high";
    public static final RoundVideoSettings DEFAULT = new RoundVideoSettings(false, PROFILE_AUTO);

    public final boolean enhanced;
    public final String profile;

    public RoundVideoSettings(boolean enhanced, String profile) {
        this.enhanced = enhanced;
        this.profile = isValidProfile(profile) ? profile : PROFILE_AUTO;
    }

    public RoundVideoSettings withEnhanced(boolean enabled) {
        return new RoundVideoSettings(enabled, profile);
    }

    public RoundVideoSettings withProfile(String value) {
        return new RoundVideoSettings(enhanced, value);
    }

    public int desiredResolution(int upstreamResolution) {
        if (!enhanced) return upstreamResolution;
        if (PROFILE_SAVER.equals(profile)) return Math.max(upstreamResolution, 384);
        if (PROFILE_HIGH.equals(profile)) return Math.max(upstreamResolution, 720);
        return Math.max(upstreamResolution, 640);
    }

    public int desiredBitrateKbps(int upstreamBitrateKbps, int resolution) {
        if (!enhanced) return upstreamBitrateKbps;
        int target;
        if (resolution >= 720) target = 4000;
        else if (resolution >= 640) target = PROFILE_HIGH.equals(profile) ? 3000 : 2500;
        else if (resolution >= 480) target = 1500;
        else target = 1000;
        return Math.max(upstreamBitrateKbps, target);
    }

    public static boolean isValidProfile(String value) {
        return PROFILE_AUTO.equals(value) || PROFILE_SAVER.equals(value) || PROFILE_HIGH.equals(value);
    }
}
