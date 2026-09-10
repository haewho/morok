#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java"

manifest = (JAVA / "org/morok/update/SignedUpdateManifest.java").read_text()
manager = (JAVA / "org/morok/update/MorokUpdateManager.java").read_text()
screen = (JAVA / "org/morok/ui/MorokUpdateActivity.java").read_text()
settings = (JAVA / "org/morok/ui/MorokSettingsActivity.java").read_text()
build_vars = (JAVA / "org/telegram/messenger/BuildVars.java").read_text()
provider = (ROOT / "TMessagesProj/src/main/res/xml/provider_paths.xml").read_text()
application = (JAVA / "org/telegram/messenger/ApplicationLoader.java").read_text()

assert '"MOROK-SIGNED-UPDATE-1"' in manifest and '"MOROK-UPDATE-1"' in manifest
for field in ("version_code", "package", "abi", "issued", "expires", "size", "sha256",
              "certificate_sha256", "url", "telegram_base", "commit", "changelog_b64"):
    assert field in manifest
assert "SHA256withRSA" in manifest and "bitLength() < 2048" in manifest
assert "versionCode < minimumVersion" in manifest and "Update version reused" in manifest
assert '"https".equals(parsedUrl.getScheme())' in manifest
assert "expires - issued > 31L * 86400" in manifest

assert 'open("morok-update-trust.properties")' in manager
assert 'property.matches("endpoint\\\\.[12]")' in manager
assert manager.count("setInstanceFollowRedirects(false)") == 2
assert "selected.sha256.equals" in manager and "total != selected.size" in manager
assert "singleSignerDigest(own)" in manager and "singleSignerDigest(archive)" in manager
assert "selected.packageName.equals(archive.packageName)" in manager
assert "versionCode(archive) != selected.versionCode" in manager
assert "ACTION_MANAGE_UNKNOWN_APP_SOURCES" in manager
assert "FileProvider.getUriForFile" in manager and "FLAG_GRANT_READ_URI_PERMISSION" in manager
assert "getCacheDir(), \"morok-updates\"" in manager
assert "output.getFD().sync()" in manager
assert "check(Callback callback)" in manager and "download(Callback callback)" in manager

assert "manager.check(this::rebuild)" in screen and "manager.download(this::rebuild)" in screen
assert "manager.install(getParentActivity())" in screen
assert "new MorokUpdateActivity(currentAccount)" in settings
assert '<cache-path name="morok_updates" path="morok-updates/"/>' in provider
assert "CHECK_UPDATES = false" in build_vars
assert "MorokUpdateManager" not in application

for forbidden in ("http://", "updates.example", "github.com/haewho/morok/releases"):
    assert forbidden not in manager

print("PASS: MOROK updater is explicit, signed, bounded, same-certificate and installer-confirmed")
