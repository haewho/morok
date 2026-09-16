#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[2]
controller = (root / "TMessagesProj/src/main/java/org/telegram/messenger/MediaController.java").read_text(errors="replace")
store = (root / "TMessagesProj/src/main/java/org/morok/media/MorokPlaybackPositionStore.java").read_text()
user_config = (root / "TMessagesProj/src/main/java/org/telegram/messenger/UserConfig.java").read_text()
checks = 0


def expect(value, message):
    global checks
    checks += 1
    if not value:
        raise AssertionError(message)


expect("MorokPlaybackPositionStore.Scope shouldSavePositionForCurrentAudio" in controller
       and 'getSharedPreferences("media_saved_pos"' not in controller,
       "MediaController must use the MOROK scoped store instead of the ambiguous upstream namespace")
expect("messageObject.getDuration() >= 5 * 60" in controller
       and "messageObject.getDuration() >= 10 * 60" in controller,
       "Existing long voice and long music eligibility thresholds must remain")
expect("messageObject, playbackPositionIdentity(messageObject, name)" in controller
       and "document:" in controller and "file:" in controller,
       "Resume identity must include the current account scope and stable media identity")
expect("MorokPlaybackPositionStore.load(position)" in controller
       and "messageObject.audioProgress = seekToProgressPending = pos" in controller,
       "A validated position must enter the existing pre-playback seek path")
expect("elapsedRealtime() - lastSaveTime >= 5000" in controller
       and "Utilities.globalQueue.postRunnable(() -> MorokPlaybackPositionStore.save(saveFor, value))" in controller,
       "Progress writes must be throttled and remain off the UI thread")
expect("MorokPlaybackPositionStore.clear(completedPosition)" in controller
       and "playbackState == ExoPlayer.STATE_ENDED" in controller,
       "Completed playback must clear its resume position before playlist advance")
music_speed = controller.index("if (Math.abs(currentMusicPlaybackSpeed - 1.0f) > 0.001f)")
music_duration_block = controller.index("messageObject.getDuration() >= 10 * 60")
expect(music_speed > music_duration_block
       and controller[music_duration_block:music_speed].count("}") >= 1,
       "Saved music speed must also apply to tracks shorter than ten minutes")
expect("PREFS_PREFIX + userId" in store and "PlaybackPositionPolicy.storageKey(userId, mediaIdentity)" in store
       and "MAX_ENTRIES" in store and "valid.sort" in store,
       "Storage must be stable-user scoped, hashed and bounded with oldest-entry eviction")
expect("DialogObject.isEncryptedDialog" in store and "TL_message_secret" in store
       and "message.noforwards" in store and "message.ttl" in store
       and "media.ttl_seconds" in store and "isPeerNoForwards" in store
       and "message.id > 0" in store and "object.getDialogId() != 0" in store,
       "Secret, ephemeral and no-forwards media must never receive a persisted position")
expect("LEGACY_PREFS" in store and "deliberately not migrated" in store,
       "Ambiguous legacy positions must not cross into an authenticated account scope")
expect("org.morok.media.MorokPlaybackPositionStore.onLogout(morokLogoutUserId)" in user_config
       and "finally" in user_config,
       "Logout must revoke playback positions even if an earlier private-store cleanup fails")
expect("ConnectionsManager" not in store and "MessagesStorage" not in store
       and "FileLoader" not in store,
       "Position persistence must not touch Telegram storage, downloads or the network")

print(f"Playback position integration: {checks} checks passed")
