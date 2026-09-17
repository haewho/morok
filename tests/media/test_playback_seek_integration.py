#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[2]
player = (root / "TMessagesProj/src/main/java/org/telegram/ui/Components/AudioPlayerAlert.java").read_text()
controller = (root / "TMessagesProj/src/main/java/org/telegram/messenger/MediaController.java").read_text(errors="replace")
checks = 0


def expect(value, message):
    global checks
    checks += 1
    if not value:
        raise AssertionError(message)


expect("MorokSeekBackward" in player and "MorokSeekForward" in player
       and "PlaybackSeekPolicy.BACKWARD_MILLIS" in player
       and "PlaybackSeekPolicy.FORWARD_MILLIS" in player,
       "The existing player options must expose both reviewed time jumps")
expect("controller.getProgressMs(messageObject)" in player
       and "controller.getDuration()" in player,
       "Jump targets must use live player position and duration")
expect("PlaybackSeekPolicy.targetMillis" in player
       and "if (target >= 0) controller.seekToProgressMs(messageObject, target)" in player,
       "Only validated targets may enter the existing millisecond seek path")
expect("CastSync.seekTo(progressMs)" in controller
       and "NotificationCenter.messagePlayingDidSeek" in controller,
       "The reused seek path must retain Cast synchronization and player notifications")
expect("SendMessagesHelper" not in player[player.index("private void seekBy"):player.index("private ItemOptions buildSleepTimerOptions")],
       "Local time jumps must not invoke a send path")

print(f"Playback seek integration: {checks} checks passed")
