#!/usr/bin/env bash
# Checks the Rime engine's logic, the Chinese variants and the data copy, which don't need the phone or the native
# library: a stand-in plays Rime.
set -euo pipefail
cd "$(dirname "$0")/../.."
sdk=${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK}
kotlin=${KOTLIN_HOME:?Set KOTLIN_HOME to your Kotlin compiler directory}
stdlib=${KOTLIN_STDLIB:-$kotlin/lib/kotlin-stdlib.jar}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
rime=android/kotlin/com/titankeys/rime
keyboard=android/kotlin/com/titankeys/keyboard
"$kotlin/bin/kotlinc" -nowarn -no-stdlib -no-reflect -classpath "$stdlib:$sdk/platforms/android-36/android.jar" -d "$work" \
  "$rime/RimeCalls.kt" "$rime/ChineseVariant.kt" "$rime/RimeEngine.kt" "$rime/RimeData.kt" "$rime/RimeNative.kt" \
  "$rime/RimeAssets.kt" "$rime/RimeBackup.kt" "$keyboard/InputEngine.kt" "$keyboard/InputMethod.kt" "$keyboard/ChineseSetup.kt" "$keyboard/Chinese.kt" "$keyboard/Layout.kt" "$keyboard/InputMethods.kt" android/kotlin/com/titankeys/connected/Json.kt \
  tests/rime/RimeChecks.kt
java -cp "$work:$stdlib" com.titankeys.rime.RimeChecksKt android/assets/im
