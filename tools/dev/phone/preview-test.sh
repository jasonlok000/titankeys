#!/usr/bin/env bash
# preview-test.sh: runs the swipe preview test (tests/preview) on the phone from the last verify.sh build copy, holding the
# build lock so nothing else uses the phone, then reinstalls that build's APK and selects the keyboard again.
# Run verify.sh build first. The test force-stops the app, so turn TitanKeys' Accessibility back on afterwards.
H="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$H/../env.sh"
export KOTLIN_HOME="$TK_BUILD/kotlin-2.1.20/kotlinc"
export PATH="$TK_DEV/build:$HOME/android-sdk/build-tools/36.0.0:$PATH"
[ -n "$TK_ADB_SERIAL" ] && export ANDROID_SERIAL="$TK_ADB_SERIAL"
exec 9>"$TK_BUILD/tk-tools/.lock"
flock -w 900 9 || { echo "phone busy"; exit 3; }
B="${TK_VERIFY_COPY:-$TK_BUILD/tk-overnight}"
cd "$B" || exit 1
# The build copy holds the tree of the last build with the public Ink edits made; take the test from this checkout.
cp "$TK_REPO/tests/preview/PreviewChecks.kt" tests/preview/PreviewChecks.kt
cp "$TK_REPO/tests/preview/run.sh" tests/preview/run.sh
timeout 900 bash tests/preview/run.sh 2>&1 | grep -E "PASS|FAIL|preview:|error|Error" | cut -c1-200 | tail -20
echo "exit ${PIPESTATUS[0]}"
apk=$(ls -t dist/titankeys-debug-*.apk | head -1)
adb install -r "$apk" | tail -1
bash "$TK_DEV/build/keyboard.sh" >/dev/null 2>&1
echo "version: $(adb shell dumpsys package com.titankeys.debug | grep -m1 versionName | tr -d ' \r')"
