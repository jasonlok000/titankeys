#!/usr/bin/env bash
# Checks which keys the Back service keeps from the app while the Sym panel is open, which doesn't need Android or the phone.
set -euo pipefail
cd "$(dirname "$0")/../.."
sdk=${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK}
kotlin=${KOTLIN_HOME:?Set KOTLIN_HOME to your Kotlin compiler directory}
stdlib=${KOTLIN_STDLIB:-$kotlin/lib/kotlin-stdlib.jar}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
"$kotlin/bin/kotlinc" -nowarn -no-stdlib -no-reflect -classpath "$stdlib:$sdk/platforms/android-36/android.jar" -d "$work" \
  android/kotlin/com/atlas/keyboard/PanelKeyFilter.kt android/kotlin/com/atlas/keyboard/Modifiers.kt \
  tests/panel-keys/PanelKeyFilterChecks.kt
java -cp "$work:$stdlib" com.atlas.keyboard.PanelKeyFilterChecksKt
