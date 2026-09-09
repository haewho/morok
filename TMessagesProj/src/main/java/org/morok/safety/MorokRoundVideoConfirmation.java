package org.morok.safety;

import org.morok.settings.MorokSettings;

/** Maps Telegram's immediate round-video send action to its existing preview action when requested. */
public final class MorokRoundVideoConfirmation {
    private static final int SEND_IMMEDIATELY = 1;
    private static final int OPEN_PREVIEW = 3;

    private MorokRoundVideoConfirmation() {}

    public static int guardedState(int state) {
        return state == SEND_IMMEDIATELY && MorokSettings.safety().confirmRoundVideos
                ? OPEN_PREVIEW : state;
    }
}
