package org.morok.appearance;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import org.morok.settings.AppearanceSettings;
import org.morok.settings.MorokSettings;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.SvgHelper;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;

import java.util.ArrayList;
import java.util.Date;
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

    /** Applies the device density only to Telegram's ordinary dialog-list row heights. */
    public static int dialogListHeight(int upstreamDp) {
        return MorokSettings.appearance().dialogListHeightDp(upstreamDp);
    }

    /** Keeps the avatar centered while applying a bounded size to ordinary dialog-list rows. */
    public static int dialogListAvatarSize(int upstreamDp) {
        return MorokSettings.appearance().dialogListAvatarSizeDp(upstreamDp);
    }

    /** Applies bounded preview line spacing without changing Telegram text size or content. */
    public static int dialogListLineSpacingExtraDp() {
        return MorokSettings.appearance().dialogListLineSpacingExtraDp();
    }

    /** Adds seconds only where Telegram itself selected the recent-message clock formatter. */
    public static String dialogListDate(long dateSeconds) {
        String upstream = LocaleController.stringForMessageListDate(dateSeconds);
        if (!MorokSettings.appearance().dialogListTimestampSeconds) return upstream;
        try {
            Date date = new Date(dateSeconds * 1000L);
            String clock = LocaleController.getInstance().getFormatterDay().format(date);
            return upstream.equals(clock)
                    ? LocaleController.getInstance().getFormatterDayWithSeconds().format(date) : upstream;
        } catch (RuntimeException ignored) {
            return upstream;
        }
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
        final boolean geometryChanged = !previous.dialogListDensity.equals(settings.dialogListDensity)
                || !previous.dialogListAvatarSize.equals(settings.dialogListAvatarSize);
        final boolean contentChanged = previous.dialogListTimestampSeconds
                != settings.dialogListTimestampSeconds
                || !previous.dialogListLineSpacing.equals(settings.dialogListLineSpacing);
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
        if (activity != null) refreshTree(activity.getWindow().getDecorView(), geometryChanged, contentChanged);
    }

    private static synchronized ArrayList<BlurredBackgroundDrawable> drawableSnapshot() {
        return new ArrayList<>(drawables.keySet());
    }

    private static void refreshTree(View view, boolean requestLayout, boolean rebuildContent) {
        if (rebuildContent && view instanceof DialogCell) {
            ((DialogCell) view).refreshMorokDialogListContent();
        }
        view.invalidate();
        if (requestLayout) view.requestLayout();
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                refreshTree(group.getChildAt(i), requestLayout, rebuildContent);
            }
        }
    }
}
