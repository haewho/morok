package org.morok.safety;

import android.app.Activity;

import org.morok.settings.MorokSettings;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;

import java.util.WeakHashMap;

/** Local UI gate before an outgoing private call reaches Telegram's initiateCall path. */
public final class MorokCallConfirmation {
    private static final WeakHashMap<Activity, Boolean> pending = new WeakHashMap<>();

    private MorokCallConfirmation() {}

    /** Returns true when the call was consumed by a confirmation dialog or safely blocked. */
    public static boolean request(Activity activity, String displayName, boolean video, Runnable confirmed) {
        if (!MorokSettings.safety().confirmOutgoingCalls) return false;
        if (activity == null || activity.isFinishing() || confirmed == null) return true;
        synchronized (pending) {
            if (pending.containsKey(activity)) return true;
            pending.put(activity, Boolean.TRUE);
        }
        String target = displayName == null || displayName.trim().isEmpty()
                ? LocaleController.getString(R.string.MorokConfirmCallUnknown) : displayName;
        try {
            AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setTitle(LocaleController.getString(video
                            ? R.string.MorokConfirmVideoCallTitle : R.string.MorokConfirmVoiceCallTitle))
                    .setMessage(LocaleController.formatString(R.string.MorokConfirmCallMessage, target))
                    .setPositiveButton(LocaleController.getString(video
                            ? R.string.MorokStartVideoCall : R.string.MorokStartVoiceCall), (ignored, which) -> {
                        clear(activity);
                        confirmed.run();
                    })
                    .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                    .create();
            dialog.setOnDismissListener(ignored -> clear(activity));
            dialog.show();
        } catch (RuntimeException cannotShow) {
            clear(activity);
        }
        return true;
    }

    private static void clear(Activity activity) {
        synchronized (pending) { pending.remove(activity); }
    }
}
