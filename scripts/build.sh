#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
morok_variant="${1:-debug}"
if [[ -z "${JAVA_HOME:-}" && -d '/Applications/Android Studio.app/Contents/jbr/Contents/Home' ]]; then
    export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
fi
if [[ -z "${ANDROID_HOME:-}" && -d "$HOME/Library/Android/sdk" ]]; then
    export ANDROID_HOME="$HOME/Library/Android/sdk"
fi
case "$morok_variant" in
    debug)
        morok_task=':TMessagesProj_App:assembleAfatDebug'
        morok_debug_keystore="$HOME/.android/debug.keystore"
        if [[ ! -f "$morok_debug_keystore" ]]; then
            morok_keytool='keytool'
            if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/keytool" ]]; then
                morok_keytool="$JAVA_HOME/bin/keytool"
            fi
            mkdir -p "$(dirname "$morok_debug_keystore")"
            "$morok_keytool" -genkeypair -keystore "$morok_debug_keystore" \
                -storepass android -alias androiddebugkey -keypass android \
                -dname 'CN=Android Debug,O=Android,C=US' \
                -keyalg RSA -keysize 2048 -validity 10000 >/dev/null 2>&1
            printf '%s\n' 'Generated a host-local Android debug keystore.'
        fi
        ;;
    release) morok_task=':TMessagesProj_App:assembleAfatRelease' ;;
    *) printf '%s\n' 'Usage: scripts/build.sh [debug|release]' >&2; exit 2 ;;
esac
python3 scripts/check_upstream.py
./gradlew morokPreflight "$morok_task" --no-daemon --max-workers=2 \
    -Dorg.gradle.jvmargs='-Xmx3g -XX:MaxMetaspaceSize=768m' \
    -Pandroid.injected.build.abi=arm64-v8a

python3 scripts/report_apk.py "$morok_variant"
