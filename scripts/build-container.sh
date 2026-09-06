#!/usr/bin/env bash
set -euo pipefail
# /source is mounted read-only; all build work is disposable inside the container.
mkdir -p /workspace /artifacts
tar -C /source --exclude='./artifacts' --exclude='./.gradle' --exclude='./TMessagesProj/build' --exclude='./TMessagesProj_App/build' \
    --exclude='./buildSrc/build' --exclude='./TMessagesProj/lib/jlatexmath/jlatexmath/build' \
    --exclude='./TMessagesProj/.cxx' -cf - . | tar -C /workspace -xf -
cd /workspace
./scripts/check.sh
./scripts/build.sh "${MOROK_BUILD_VARIANT:-debug}"
cp artifacts/*.apk artifacts/*.sha256 artifacts/build-report.json /artifacts/
