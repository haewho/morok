#!/bin/sh
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
settings_test_dir=$(mktemp -d)
trap 'rm -rf "$settings_test_dir"' EXIT HUP INT TERM
if [ -n "${JAVA_HOME:-}" ]; then
    settings_javac="$JAVA_HOME/bin/javac"
    settings_java="$JAVA_HOME/bin/java"
else
    settings_javac=javac
    settings_java=java
fi
"$settings_javac" --release 8 -d "$settings_test_dir" \
    "$project_dir/TMessagesProj/src/main/java/org/morok/settings/AppearanceSettings.java" \
    "$project_dir/TMessagesProj/src/main/java/org/morok/settings/AppearanceMode.java" \
    "$project_dir/TMessagesProj/src/main/java/org/morok/settings/ArchiveSettings.java" \
    "$project_dir/TMessagesProj/src/main/java/org/morok/settings/InteractionSettings.java" \
    "$project_dir/TMessagesProj/src/main/java/org/morok/settings/PrivacySettings.java" \
    "$project_dir/TMessagesProj/src/main/java/org/morok/settings/RoundVideoSettings.java" \
    "$project_dir/TMessagesProj/src/main/java/org/morok/settings/SafetySettings.java" \
    "$project_dir/TMessagesProj/src/main/java/org/morok/settings/SettingsProfile.java" \
    "$project_dir/TMessagesProj/src/main/java/org/morok/settings/SettingsProfileCodec.java" \
    "$project_dir/TMessagesProj/src/main/java/org/morok/settings/AppProfileState.java" \
    "$project_dir/TMessagesProj/src/main/java/org/morok/settings/AppProfileStateCodec.java" \
    "$project_dir/TMessagesProj/src/main/java/org/morok/settings/AppProfilePresets.java" \
    "$project_dir/TMessagesProj/src/main/java/org/morok/settings/SettingsStore.java" \
    "$project_dir/TMessagesProj/src/main/java/org/morok/settings/SettingsRepository.java" \
    "$project_dir/tests/settings/SettingsRepositoryTest.java"
"$settings_java" -cp "$settings_test_dir" SettingsRepositoryTest
python3 "$project_dir/tests/settings/test_app_profiles_integration.py"
python3 "$project_dir/tests/settings/test_safety_integration.py"
python3 "$project_dir/tests/settings/test_interactions_integration.py"
