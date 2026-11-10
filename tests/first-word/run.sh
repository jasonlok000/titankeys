#!/usr/bin/env bash
# Checks the reward for words that start a sentence, with the real word counts and recorded swipes. It doesn't need the phone.
set -euo pipefail
cd "$(dirname "$0")/../.."
sdk=${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK}
kotlin=${KOTLIN_HOME:?Set KOTLIN_HOME to your Kotlin compiler directory}
stdlib=${KOTLIN_STDLIB:-$kotlin/lib/kotlin-stdlib.jar}
android=$sdk/platforms/android-36/android.jar
# ONNX Runtime classes, which the engine needs to compile.
ort=android/libs/onnxruntime-android-1.20.0.jar
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
root=android/kotlin
"$kotlin/bin/kotlinc" -nowarn -no-stdlib -no-reflect -classpath "$stdlib" -d "$work" "$root/com/atlas/keyboard/Layout.kt"
javac --release 17 -nowarn -cp "$work:$stdlib:$android:$ort" -d "$work" \
  $(find "$root/com/atlas/engine" "$root/org" -name '*.java') tests/first-word/FirstWordChecks.java
java -cp "$work:$stdlib:$android:$ort" com.atlas.engine.FirstWordChecks
