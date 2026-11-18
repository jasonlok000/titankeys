#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
kotlin=${KOTLIN_HOME:?}
android=${ANDROID_HOME:?}/platforms/android-36/android.jar
recorder=android/debug/recorder
cat > "$work/Activities.kt" <<'EOF'
package com.titankeys.keyboard
import android.app.Activity
class SpaceGestureActivity : Activity()
class GuideActivity : Activity()
object GuideHooks { const val FIELD = "com.titankeys.guide" }
EOF
"$kotlin/bin/kotlinc" -nowarn -classpath "$android" "$recorder/TestScreenLease.kt" \
  "$recorder/FeedFocusGuard.kt" "$recorder/FeedFocusLease.kt" \
  android/kotlin/com/titankeys/keyboard/AppBuild.kt "$work/Activities.kt" -d "$work/android.jar"
python3 tests/test-screen-lease/check.py "$work/LeaseCheck.kt"
"$kotlin/bin/kotlinc" "$work/LeaseCheck.kt" -d "$work/check.jar"
java -cp "$work/check.jar:$kotlin/lib/kotlin-stdlib.jar" LeaseCheckKt
