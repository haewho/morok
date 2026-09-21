#!/usr/bin/env python3
"""Source invariants for device-wide dialog-list geometry and live relayout."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java"

appearance = (JAVA / "org/morok/settings/AppearanceSettings.java").read_text()
repository = (JAVA / "org/morok/settings/SettingsRepository.java").read_text()
codec = (JAVA / "org/morok/settings/SettingsProfileCodec.java").read_text()
policy = (JAVA / "org/morok/appearance/MorokAppearance.java").read_text()
cell = (JAVA / "org/telegram/ui/Cells/DialogCell.java").read_text()
screen = (JAVA / "org/morok/ui/MorokSettingsActivity.java").read_text()
transfer = (JAVA / "org/morok/ui/MorokSettingsTransferActivity.java").read_text()
profiles = (JAVA / "org/morok/settings/AppProfilePresets.java").read_text()

assert 'DENSITY_COMPACT = "compact"' in appearance
assert 'DENSITY_STANDARD = "standard"' in appearance
assert 'DENSITY_COMFORTABLE = "comfortable"' in appearance
assert 'return -8' in appearance and 'return 8' in appearance
assert 'AVATAR_SMALL = "small"' in appearance and 'AVATAR_LARGE = "large"' in appearance
assert 'dialogListAvatarSizeOffsetDp' in appearance and 'return -4' in appearance and 'return 4' in appearance
assert 'appearance.dialog_list_density' in repository and 'appearance.dialog_list_avatar_size' in repository
assert 'appearance.dialog_list_timestamp_seconds' in repository and 'SCHEMA_VERSION = 17' in repository
assert 'FORMAT_VERSION = 4' in codec and 'version < 1 || version > FORMAT_VERSION' in codec
assert 'version == 1 ? AppearanceSettings.DENSITY_STANDARD' in codec
assert 'version < 3 ? AppearanceSettings.AVATAR_STANDARD' in codec
assert 'version >= 4 && booleanValue(values, TIMESTAMP_SECONDS)' in codec
assert 'geometryChanged' in policy and 'timestampChanged' in policy and 'view.requestLayout()' in policy
assert 'dialogListHeightDp(upstreamDp)' in policy and 'dialogListAvatarSizeDp(upstreamDp)' in policy
assert 'getFormatterDayWithSeconds().format(date)' in policy
assert 'Math.max(44, Math.min(60' in appearance
assert 'dialogListAvatarSizeOffsetDp() / 2' in appearance
assert 'usesMorokDialogListGeometry()' in cell
assert 'MorokAppearance.dialogListHeight(heightDp)' in cell
assert 'setDialogAvatarRect(avatarLeft, avatarTop, 56)' in cell
assert 'setDialogAvatarRect(avatarLeft, avatarTop, 52)' in cell
assert 'MorokAppearance.dialogListAvatarSize(48)' in cell
assert 'refreshMorokDialogListTimestamp()' in cell and 'forceMorokTimestampLayout' in cell
assert 'MorokAppearance.dialogListDate(date)' in cell
assert 'DIALOGS_TYPE_DEFAULT' in cell and 'DIALOGS_TYPE_FOLDER1' in cell and 'DIALOGS_TYPE_FOLDER2' in cell
assert 'DIALOG_DENSITY' in screen and 'showDialogDensity(context)' in screen
assert 'DIALOG_AVATAR_SIZE' in screen and 'showDialogAvatarSize(context)' in screen
assert 'DIALOG_TIMESTAMP_SECONDS' in screen and 'withDialogListTimestampSeconds' in screen
assert 'MorokDialogListDensity' in transfer and 'MorokDialogListAvatarSize' in transfer
assert 'MorokDialogListTimestampSeconds' in transfer
assert 'withDialogListDensity(current.settings.appearance.dialogListDensity)' in profiles
assert 'withDialogListAvatarSize(current.settings.appearance.dialogListAvatarSize)' in profiles
assert 'withDialogListTimestampSeconds(' in profiles

print("PASS: dialog-list density, avatars and recent timestamps are live, transferable and list-limited")
