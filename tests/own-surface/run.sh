#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
kotlin=${KOTLIN_HOME:?}
android=${ANDROID_HOME:?}/platforms/android-36/android.jar
# Compile against SDK APIs with the real app and editor ownership checks. Stub the drawing widget.
cat > "$work/Stubs.kt" <<'EOF'
package com.titankeys.keyboard
import android.content.Context
import android.view.View
class Strip(context: Context) : View(context)
class GuideKeyboardView(context: Context) : View(context)
object GuideHooks { const val FIELD = "com.titankeys.guide" }
EOF
"$kotlin/bin/kotlinc" -nowarn -classpath "$android" android/debug/recorder/OwnCapturePolicy.kt \
  android/debug/recorder/OwnSurfaceCapture.kt android/debug/recorder/FeedFocusGuard.kt \
  android/debug/recorder/FeedFocusLease.kt android/kotlin/com/titankeys/keyboard/AppBuild.kt \
  "$work/Stubs.kt" tests/own-surface/PolicyChecks.kt -d "$work/check.jar"
java -cp "$work/check.jar:$kotlin/lib/kotlin-stdlib.jar" com.titankeys.keyboard.PolicyChecksKt
python3 tests/own-surface/protocol.py
python3 tests/own-surface/lease.py
