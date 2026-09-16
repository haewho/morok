#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[2]
timer = (root / "TMessagesProj/src/main/java/org/morok/media/MorokSleepTimer.java").read_text()
player = (root / "TMessagesProj/src/main/java/org/telegram/ui/Components/AudioPlayerAlert.java").read_text()
checks = 0


def expect(value, message):
    global checks
    checks += 1
    if not value:
        raise AssertionError(message)


expect("SystemClock.elapsedRealtime()" in timer and "SleepTimerPolicy.deadline" in timer,
       "Sleep timer must use monotonic elapsed time rather than wall clock")
expect("cancelRunOnUIThread(TIMEOUT)" in timer and "runOnUIThread(TIMEOUT, duration)" in timer,
       "Rescheduling must replace the previous single timeout")
expect("playing.isMusic()" in timer and "controller.isPlayingMessage(playing)" in timer
       and "!controller.isMessagePaused()" in timer and "controller.pauseMessage(playing)" in timer,
       "Expiry must pause only the active unpaused music item")
expect("isVoice" not in timer and "isRoundVideo" not in timer,
       "Sleep timer must not target voice or round-video media")
expect("SharedPreferences" not in timer and "AlarmManager" not in timer
       and "ConnectionsManager" not in timer,
       "Sleep timer remains process-local and cannot create persistence or network side effects")
expect("buildSleepTimerOptions(o)" in player and "SleepTimerPolicy.PRESET_MINUTES" in player
       and "MorokSleepTimer.scheduleMinutes(minutes)" in player,
       "The existing music player options must expose every reviewed timer preset")
expect("MorokSleepTimer.remainingMinutes()" in player and "MorokSleepTimer.cancel()" in player
       and "MorokSleepTimerInfo" in player,
       "Music player menu must show remaining time, disclose scope and allow cancellation")

print(f"Sleep timer integration: {checks} checks passed")
