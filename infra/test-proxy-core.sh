#!/bin/sh
set -eu
morok_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
morok_tmp=$(mktemp -d)
trap 'rm -rf "$morok_tmp"' EXIT HUP INT TERM
morok_javac=${MOROK_JAVAC:-javac}
morok_java=${MOROK_JAVA:-java}
"$morok_javac" -encoding UTF-8 -d "$morok_tmp" \
    "$morok_root/TMessagesProj/src/main/java/org/morok/proxy/ProxyNode.java" \
    "$morok_root/TMessagesProj/src/main/java/org/morok/proxy/SignedProxyPool.java" \
    "$morok_root/TMessagesProj/src/main/java/org/morok/proxy/ProxyRetryPolicy.java" \
    "$morok_root/infra/tests/ProxyCoreTest.java"
"$morok_java" -cp "$morok_tmp" ProxyCoreTest
