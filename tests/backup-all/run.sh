#!/usr/bin/env bash
# Checks Back up everything's file (#65) and the crash report's text (#74), which don't need Android.
set -euo pipefail
cd "$(dirname "$0")/../.."
sdk=${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK}
kotlin=${KOTLIN_HOME:?Set KOTLIN_HOME to your Kotlin compiler directory}
stdlib=${KOTLIN_STDLIB:-$kotlin/lib/kotlin-stdlib.jar}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
keyboard=android/kotlin/com/titankeys/keyboard
"$kotlin/bin/kotlinc" -nowarn -no-stdlib -no-reflect -classpath "$stdlib:$sdk/platforms/android-36/android.jar" -d "$work" \
  android/kotlin/com/titankeys/connected/Json.kt "$keyboard/BackupFile.kt" "$keyboard/CrashReport.kt" "$keyboard/AppBuild.kt" \
  tests/backup-all/BackupChecks.kt
java -cp "$work:$stdlib:$sdk/platforms/android-36/android.jar" com.titankeys.keyboard.BackupChecksKt
