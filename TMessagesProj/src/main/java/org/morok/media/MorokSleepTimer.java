package org.morok.media;

import android.os.SystemClock;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;

/** Process-local timer for the active music player. It never pauses voice or round-video playback. */
public final class MorokSleepTimer {
    private static long deadlineElapsed;

    private static final Runnable TIMEOUT = new Runnable() {
        @Override
        public void run() {
            long remaining;
            synchronized (MorokSleepTimer.class) {
                remaining = SleepTimerPolicy.remainingMillis(deadlineElapsed, SystemClock.elapsedRealtime());
                if (remaining > 0) {
                    AndroidUtilities.runOnUIThread(this, remaining);
                    return;
                }
                if (deadlineElapsed == 0) return;
                deadlineElapsed = 0;
            }
            MediaController controller = MediaController.getInstance();
            MessageObject playing = controller.getPlayingMessageObject();
            if (playing != null && playing.isMusic() && controller.isPlayingMessage(playing)
                    && !controller.isMessagePaused()) {
                controller.pauseMessage(playing);
            }
        }
    };

    private MorokSleepTimer() { }

    public static synchronized void scheduleMinutes(int minutes) {
        long now = SystemClock.elapsedRealtime();
        long duration = SleepTimerPolicy.durationMillis(minutes);
        deadlineElapsed = SleepTimerPolicy.deadline(now, minutes);
        AndroidUtilities.cancelRunOnUIThread(TIMEOUT);
        AndroidUtilities.runOnUIThread(TIMEOUT, duration);
    }

    public static synchronized void cancel() {
        deadlineElapsed = 0;
        AndroidUtilities.cancelRunOnUIThread(TIMEOUT);
    }

    public static synchronized int remainingMinutes() {
        return SleepTimerPolicy.remainingMinutes(deadlineElapsed, SystemClock.elapsedRealtime());
    }

    public static synchronized boolean isActive() {
        return SleepTimerPolicy.remainingMillis(deadlineElapsed, SystemClock.elapsedRealtime()) > 0;
    }
}
