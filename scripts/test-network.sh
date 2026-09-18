#!/usr/bin/env bash
set -euo pipefail
repo_root=$(cd "$(dirname "$0")/.." && pwd)
test_dir=$(mktemp -d)
trap 'rm -rf "$test_dir"' EXIT
mkdir -p "$test_dir/me/srrapero720/watervision"
# Only the Minecraft mod's logger is stubbed. Both HTTP implementations are real.
cat > "$test_dir/me/srrapero720/watervision/WaterVision.java" <<'JAVA'
package me.srrapero720.watervision;
public final class WaterVision {
    public static final Logger LOGGER = new Logger();
    public static final class Logger {
        public void info(String message, Object... args) {}
        public void warn(String message, Object... args) {}
    }
}
JAVA
javac --release 17 --add-modules jdk.httpserver -d "$test_dir" \
    "$test_dir/me/srrapero720/watervision/WaterVision.java" \
    "$repo_root/src/main/java/me/srrapero720/watervision/client/screens/VisionMediaCache.java" \
    "$repo_root/src/main/java/me/srrapero720/watervision/client/screens/VisionStreamingProxy.java" \
    "$repo_root/tests/network/NetworkRegressionTest.java"
java --add-modules jdk.httpserver -cp "$test_dir" me.srrapero720.watervision.client.screens.NetworkRegressionTest
