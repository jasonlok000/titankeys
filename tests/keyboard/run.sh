#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
sdk=${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK}
kotlin=${KOTLIN_HOME:?Set KOTLIN_HOME to your Kotlin compiler directory}
stdlib=${KOTLIN_STDLIB:-$kotlin/lib/kotlin-stdlib.jar}
android="$sdk/platforms/android-36/android.jar"
work=$(mktemp -d)
device=$("$sdk/platform-tools/adb" shell mktemp -d /data/local/tmp/atlas-keyboard.XXXXXX)
trap 'rm -rf "$work"; "$sdk/platform-tools/adb" shell rm -rf "$device"' EXIT
mkdir "$work/classes"
keyboard=android/kotlin/com/atlas/keyboard
"$kotlin/bin/kotlinc" -language-version 2.2 -no-stdlib -no-reflect -classpath "$stdlib" -d "$work/classes" "$keyboard/Layout.kt"
javac --release 17 -cp "$work/classes:$android:$stdlib" -d "$work/classes" \
  android/kotlin/com/atlas/engine/*.java android/kotlin/org/futo/ml/inference/*.java
"$kotlin/bin/kotlinc" -language-version 2.2 -no-stdlib -no-reflect -classpath "$work/classes:$android:$stdlib" -d "$work/classes" \
  "$keyboard/Field.kt" "$keyboard/Composer.kt" "$keyboard/ConnectionEditor.kt" tests/keyboard/PunctuationChecks.kt
jar cf "$work/classes.jar" -C "$work/classes" .
"$sdk/build-tools/36.0.0/d8" --min-api 34 --lib "$android" --output "$work/checks.zip" "$work/classes.jar" "$stdlib"
"$sdk/platform-tools/adb" push "$work/checks.zip" "$device/"
"$sdk/platform-tools/adb" shell "CLASSPATH=$device/checks.zip app_process /system/bin com.atlas.keyboard.PunctuationChecksKt"
