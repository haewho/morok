package org.morok.camera;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import org.morok.settings.MorokSettings;
import org.morok.settings.RoundVideoSettings;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;

/** Opt-in local metadata log. It never receives frames, audio, account IDs or file paths. */
public final class RoundVideoDiagnostics {
    private static final String PREFS = "morok_round_video_diagnostics_v1";
    private static final String ENABLED = "enabled";
    private static final String EVENTS = "events";

    private RoundVideoDiagnostics() {}

    private static SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean enabled() {
        return preferences().getBoolean(ENABLED, false);
    }

    public static synchronized void setEnabled(boolean enabled) {
        preferences().edit().putBoolean(ENABLED, enabled).apply();
    }

    public static synchronized void record(String stage, String detail) {
        if (!enabled()) return;
        SharedPreferences preferences = preferences();
        String updated = RoundVideoDiagnosticBuffer.append(preferences.getString(EVENTS, ""),
                System.currentTimeMillis(), stage, detail);
        preferences.edit().putString(EVENTS, updated).apply();
    }

    public static synchronized int count() {
        return RoundVideoDiagnosticBuffer.count(preferences().getString(EVENTS, ""));
    }

    public static synchronized void clear() {
        preferences().edit().remove(EVENTS).apply();
    }

    public static synchronized String report() {
        String enhanced = "unavailable", profile = "unavailable";
        try {
            RoundVideoSettings settings = MorokSettings.roundVideo();
            enhanced = Boolean.toString(settings.enhanced);
            profile = settings.profile;
        } catch (RuntimeException ignored) { }
        String events = RoundVideoDiagnosticBuffer.render(preferences().getString(EVENTS, ""));
        return "MOROK round-video diagnostics v1\n"
                + "metadata_only=true\n"
                + "app=" + BuildVars.BUILD_VERSION_STRING + "\n"
                + "android=" + Build.VERSION.SDK_INT + "\n"
                + "device=" + Build.MANUFACTURER + " " + Build.MODEL + "\n"
                + "enhanced=" + enhanced + "\n"
                + "profile=" + profile + "\n"
                + "events=" + count() + "\n\n"
                + (events.isEmpty() ? "No diagnostic events recorded." : events);
    }
}
