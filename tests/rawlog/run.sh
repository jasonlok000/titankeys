#!/usr/bin/env bash
# Checks the parts of the raw input log that don't need Android: the field rule, the line format, the files and the queue.
set -euo pipefail
cd "$(dirname "$0")/../.."
sdk=${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK}
kotlin=${KOTLIN_HOME:?Set KOTLIN_HOME to your Kotlin compiler directory}
stdlib=${KOTLIN_STDLIB:-$kotlin/lib/kotlin-stdlib.jar}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
keyboard=android/kotlin/com/atlas/keyboard
"$kotlin/bin/kotlinc" -nowarn -no-stdlib -no-reflect -classpath "$stdlib:$sdk/platforms/android-36/android.jar" -d "$work" \
  "$keyboard/Field.kt" "$keyboard/RawLogGate.kt" "$keyboard/RawLogLine.kt" "$keyboard/RawLogStore.kt" "$keyboard/RawLogWriter.kt" tests/rawlog/RawLogChecks.kt
java -cp "$work:$stdlib" com.atlas.keyboard.RawLogChecksKt
