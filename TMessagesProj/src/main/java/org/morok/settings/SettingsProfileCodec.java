package org.morok.settings;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Strict, deterministic and secret-free text format for explicit settings transfer. */
public final class SettingsProfileCodec {
    public static final int FORMAT_VERSION = 1;
    public static final int MAX_CHARACTERS = 16 * 1024;
    public static final String MAGIC = "MOROK_SETTINGS_PROFILE";

    private static final String FORMAT = "format";
    private static final String GLASS = "appearance.liquid_glass";
    private static final String EFFECTS = "appearance.reduced_effects";
    private static final String ROUND_ENABLED = "camera.round_video_enhanced";
    private static final String ROUND_PROFILE = "camera.round_video_profile";
    private static final String GHOST = "privacy.ghost_preset";
    private static final String TYPING = "privacy.hide_typing";
    private static final String ONLINE = "privacy.hide_online";
    private static final String CONTENT_READ = "privacy.hide_content_read";
    private static final String READ = "privacy.hide_read";
    private static final String STORY = "privacy.hide_story_views";
    private static final String READ_REPLY = "privacy.mark_read_on_reply";
    private static final String DELAY_SEND = "privacy.delay_ghost_sends";
    private static final int FIELD_COUNT = 13;

    private SettingsProfileCodec() {}

    public static String encode(SettingsProfile profile) {
        if (profile == null) throw new IllegalArgumentException("Profile is required");
        StringBuilder result = new StringBuilder(MAGIC).append('\n');
        append(result, FORMAT, Integer.toString(FORMAT_VERSION));
        append(result, GLASS, bool(profile.appearance.liquidGlass));
        append(result, EFFECTS, bool(profile.appearance.reducedEffects));
        append(result, ROUND_ENABLED, bool(profile.roundVideo.enhanced));
        append(result, ROUND_PROFILE, profile.roundVideo.profile);
        append(result, GHOST, bool(profile.privacy.ghostPreset));
        append(result, TYPING, bool(profile.privacy.hideTyping));
        append(result, ONLINE, bool(profile.privacy.hideOnline));
        append(result, CONTENT_READ, bool(profile.privacy.hideContentRead));
        append(result, READ, bool(profile.privacy.hideRead));
        append(result, STORY, bool(profile.privacy.hideStoryViews));
        append(result, READ_REPLY, bool(profile.privacy.markReadOnReply));
        append(result, DELAY_SEND, bool(profile.privacy.delayGhostSends));
        return result.toString();
    }

    public static SettingsProfile decode(String encoded) {
        if (encoded == null || encoded.isEmpty() || encoded.length() > MAX_CHARACTERS) {
            throw new IllegalArgumentException("Invalid profile size");
        }
        String normalized = encoded.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        if (lines.length < 2 || !MAGIC.equals(lines[0])) throw new IllegalArgumentException("Invalid profile header");
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty()) continue;
            int separator = line.indexOf('=');
            if (separator <= 0 || separator == line.length() - 1) throw new IllegalArgumentException("Invalid profile field");
            String key = line.substring(0, separator);
            String value = line.substring(separator + 1);
            if (!isKnownKey(key) || values.put(key, value) != null) {
                throw new IllegalArgumentException("Unknown or duplicate profile field");
            }
        }
        if (values.size() != FIELD_COUNT) throw new IllegalArgumentException("Incomplete profile");
        int version;
        try { version = Integer.parseInt(required(values, FORMAT)); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("Invalid profile version", error); }
        if (version != FORMAT_VERSION) throw new IllegalArgumentException("Unsupported profile version");
        String roundProfile = required(values, ROUND_PROFILE);
        if (!RoundVideoSettings.isValidProfile(roundProfile)) throw new IllegalArgumentException("Invalid round-video profile");
        AppearanceSettings appearance = new AppearanceSettings(booleanValue(values, GLASS), booleanValue(values, EFFECTS));
        RoundVideoSettings roundVideo = new RoundVideoSettings(booleanValue(values, ROUND_ENABLED), roundProfile);
        PrivacySettings privacy = new PrivacySettings(booleanValue(values, GHOST), booleanValue(values, TYPING),
                booleanValue(values, ONLINE), booleanValue(values, CONTENT_READ), booleanValue(values, READ),
                booleanValue(values, STORY), booleanValue(values, READ_REPLY), booleanValue(values, DELAY_SEND),
                Collections.emptySet());
        return new SettingsProfile(appearance, roundVideo, privacy);
    }

    private static void append(StringBuilder output, String key, String value) {
        output.append(key).append('=').append(value).append('\n');
    }

    private static String bool(boolean value) { return value ? "true" : "false"; }

    private static boolean booleanValue(Map<String, String> values, String key) {
        String value = required(values, key);
        if ("true".equals(value)) return true;
        if ("false".equals(value)) return false;
        throw new IllegalArgumentException("Invalid boolean field");
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null) throw new IllegalArgumentException("Missing profile field");
        return value;
    }

    private static boolean isKnownKey(String key) {
        return FORMAT.equals(key) || GLASS.equals(key) || EFFECTS.equals(key)
                || ROUND_ENABLED.equals(key) || ROUND_PROFILE.equals(key) || GHOST.equals(key)
                || TYPING.equals(key) || ONLINE.equals(key) || CONTENT_READ.equals(key)
                || READ.equals(key) || STORY.equals(key) || READ_REPLY.equals(key) || DELAY_SEND.equals(key);
    }
}
