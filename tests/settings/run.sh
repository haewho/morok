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
    "$project_dir/TMessagesProj/src/main/java/org/morok/settings/PrivacySettings.java" \
    "$project_dir/TMessagesProj/src/main/java/org/morok/settings/SettingsStore.java" \
    "$project_dir/TMessagesProj/src/main/java/org/morok/settings/SettingsRepository.java" \
    "$project_dir/tests/settings/SettingsRepositoryTest.java"
"$settings_java" -cp "$settings_test_dir" SettingsRepositoryTest
