package org.morok.settings;

import java.util.LinkedHashMap;
import java.util.Map;

/** Strict private persistence format for custom/previous local app profiles. */
public final class AppProfileStateCodec {
    public static final int FORMAT_VERSION = 1;
    public static final int MAX_CHARACTERS = 24 * 1024;
    public static final String MAGIC = "MOROK_LOCAL_APP_PROFILE";
    private static final int HEADER_FIELDS = 5;

    private AppProfileStateCodec() {}

    public static String encode(AppProfileState state) {
        if (state == null) throw new IllegalArgumentException("App profile is required");
        StringBuilder output = new StringBuilder(MAGIC).append('\n');
        append(output, "format", Integer.toString(FORMAT_VERSION));
        append(output, "autoplay.videos", bool(state.autoplayVideos));
        append(output, "autoplay.gifs", bool(state.autoplayGifs));
        append(output, "notifications.content", bool(state.notificationContent));
        append(output, "network.policy", state.networkPolicy);
        output.append('\n').append(SettingsProfileCodec.encode(state.settings));
        if (output.length() > MAX_CHARACTERS) throw new IllegalArgumentException("App profile is too large");
        return output.toString();
    }

    public static AppProfileState decode(String encoded) {
        if (encoded == null || encoded.isEmpty() || encoded.length() > MAX_CHARACTERS) {
            throw new IllegalArgumentException("Invalid app profile size");
        }
        String normalized = encoded.replace("\r\n", "\n").replace('\r', '\n');
        int body = normalized.indexOf("\n\n");
        if (body < 0) throw new IllegalArgumentException("Missing app profile body");
        String[] lines = normalized.substring(0, body).split("\n", -1);
        if (lines.length != HEADER_FIELDS + 1 || !MAGIC.equals(lines[0])) {
            throw new IllegalArgumentException("Invalid app profile header");
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int separator = lines[i].indexOf('=');
            if (separator <= 0 || separator == lines[i].length() - 1
                    || values.put(lines[i].substring(0, separator), lines[i].substring(separator + 1)) != null) {
                throw new IllegalArgumentException("Invalid app profile field");
            }
        }
        if (values.size() != HEADER_FIELDS || !values.keySet().equals(knownKeys())) {
            throw new IllegalArgumentException("Unknown or incomplete app profile");
        }
        int format;
        try { format = Integer.parseInt(values.get("format")); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("Invalid app profile version", error); }
        if (format != FORMAT_VERSION) throw new IllegalArgumentException("Unsupported app profile version");
        String network = values.get("network.policy");
        SettingsProfile settings = SettingsProfileCodec.decode(normalized.substring(body + 2));
        return new AppProfileState(settings, booleanValue(values, "autoplay.videos"),
                booleanValue(values, "autoplay.gifs"), booleanValue(values, "notifications.content"), network);
    }

    private static java.util.Set<String> knownKeys() {
        java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>();
        keys.add("format");
        keys.add("autoplay.videos");
        keys.add("autoplay.gifs");
        keys.add("notifications.content");
        keys.add("network.policy");
        return keys;
    }

    private static boolean booleanValue(Map<String, String> values, String key) {
        String value = values.get(key);
        if ("true".equals(value)) return true;
        if ("false".equals(value)) return false;
        throw new IllegalArgumentException("Invalid app profile boolean");
    }

    private static void append(StringBuilder output, String key, String value) {
        output.append(key).append('=').append(value).append('\n');
    }

    private static String bool(boolean value) { return value ? "true" : "false"; }
}
