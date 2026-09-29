#!/bin/sh
# Build the local standalone package using externally provisioned runtime assets.
set -eu
PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
export ANDROIDPLAY_AUTH_ASSETS_DIR="${ANDROIDPLAY_AUTH_ASSETS_DIR:-$HOME/Library/Application Support/AndroidPlay/runtime-assets}"
if [ -z "${JAVA_HOME:-}" ] && [ -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]; then
    export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
fi
cd "$PROJECT_DIR"
./gradlew :mobile:assembleStandaloneDebug "$@"
mkdir -p build
cp mobile/build/outputs/apk/debug/mobile-debug.apk build/AndroidPlay.apk
printf '%s\n' "已生成：$PROJECT_DIR/build/AndroidPlay.apk"
