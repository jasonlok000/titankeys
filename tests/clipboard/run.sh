#!/usr/bin/env bash
# Checks the clipboard history and its page in the Sym panel, which don't need Android.
set -euo pipefail
cd "$(dirname "$0")/../.."
sdk=${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK}
kotlin=${KOTLIN_HOME:?Set KOTLIN_HOME to your Kotlin compiler directory}
stdlib=${KOTLIN_STDLIB:-$kotlin/lib/kotlin-stdlib.jar}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
keyboard=android/kotlin/com/atlas/keyboard
"$kotlin/bin/kotlinc" -nowarn -no-stdlib -no-reflect -classpath "$stdlib:$sdk/platforms/android-36/android.jar" -d "$work" \
  "$keyboard/ClipboardHistory.kt" "$keyboard/Modifiers.kt" "$keyboard/QuickSetting.kt" "$keyboard/Layout.kt" tests/clipboard/ClipboardChecks.kt
java -cp "$work:$stdlib:$sdk/platforms/android-36/android.jar" com.atlas.keyboard.ClipboardChecksKt
