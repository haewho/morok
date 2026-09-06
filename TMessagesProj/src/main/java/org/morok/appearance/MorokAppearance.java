package org.morok.appearance;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import org.morok.settings.AppearanceSettings;
import org.morok.settings.MorokSettings;
import org.telegram.messenger.SvgHelper;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;

import java.util.ArrayList;
import java.util.WeakHashMap;

/** Live appearance policy. All mutations and drawable registration run on the UI thread. */
public final class MorokAppearance {
    private static final WeakHashMap<BlurredBackgroundDrawable, Boolean> drawables = new WeakHashMap<>();

    private MorokAppearance() {}

    public static boolean opaqueSurfaces() {
        return !MorokSettings.appearance().liquidGlass;
    }

    public static boolean reducedEffects() {
        return MorokSettings.appearance().reducedEffects;
    }

    public static synchronized void register(BlurredBackgroundDrawable drawable) {
        drawables.put(drawable, Boolean.TRUE);
    }

    public static void apply(AppearanceSettings settings, Activity activity) {
        AppearanceSettings previous = MorokSettings.appearance();
        MorokSettings.setAppearance(settings);
        refreshAfterImport(previous, settings, activity);
    }

    /** Refreshes render caches after an already-persisted settings-profile import. Runs on the UI thread. */
    public static void refreshAfterImport(AppearanceSettings previous, AppearanceSettings settings, Activity activity) {
        final boolean animationChanged = previous.reducedEffects != settings.reducedEffects;
        // Refresh existing drawables; no Activity/Fragment recreation or draft/scroll reset.
        for (BlurredBackgroundDrawable drawable : drawableSnapshot()) {
            if (drawable != null) {
                drawable.updateColors();
                drawable.invalidateSelf();
            }
        }
        if (animationChanged) {
            AnimatedEmojiDrawable.updateAll();
            SvgHelper.SvgDrawable.updateLiteValues();
            Theme.reloadWallpaper(true);
        }
        if (activity != null) invalidateTree(activity.getWindow().getDecorView());
    }

    private static synchronized ArrayList<BlurredBackgroundDrawable> drawableSnapshot() {
        return new ArrayList<>(drawables.keySet());
    }

    private static void invalidateTree(View view) {
        view.invalidate();
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) invalidateTree(group.getChildAt(i));
        }
    }
}
