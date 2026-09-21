#!/usr/bin/env python3
"""Source invariants for device-wide dialog-list density and live relayout."""
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
assert 'appearance.dialog_list_density' in repository and 'SCHEMA_VERSION = 15' in repository
assert 'FORMAT_VERSION = 2' in codec and 'version != 1' in codec
assert 'version == 1 ? AppearanceSettings.DENSITY_STANDARD' in codec
assert 'densityChanged' in policy and 'view.requestLayout()' in policy
assert 'usesMorokDialogListDensity()' in cell
assert 'MorokAppearance.dialogListHeight(heightDp)' in cell
assert 'DIALOGS_TYPE_DEFAULT' in cell and 'DIALOGS_TYPE_FOLDER1' in cell and 'DIALOGS_TYPE_FOLDER2' in cell
assert 'DIALOG_DENSITY' in screen and 'showDialogDensity(context)' in screen
assert 'MorokDialogListDensity' in transfer
assert 'withDialogListDensity(current.settings.appearance.dialogListDensity)' in profiles

print("PASS: dialog-list density is bounded, live, transferable and limited to ordinary dialog lists")
