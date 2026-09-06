#!/bin/sh
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
proxy_route_test_dir=$(mktemp -d)
trap 'rm -rf "$proxy_route_test_dir"' EXIT HUP INT TERM
if [ -n "${JAVA_HOME:-}" ]; then
    proxy_route_javac="$JAVA_HOME/bin/javac"
    proxy_route_java="$JAVA_HOME/bin/java"
else
    proxy_route_javac=javac
    proxy_route_java=java
fi
"$proxy_route_javac" --release 8 -Xlint:-options -d "$proxy_route_test_dir" \
    "$project_dir/TMessagesProj/src/main/java/org/morok/proxy/ProxyRouteTransaction.java" \
    "$project_dir/tests/proxy_route/ProxyRouteTransactionTest.java"
"$proxy_route_java" -cp "$proxy_route_test_dir" ProxyRouteTransactionTest
