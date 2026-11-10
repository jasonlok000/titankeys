#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
sdk=${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK}
kotlin=${KOTLIN_HOME:?Set KOTLIN_HOME to your Kotlin compiler directory}
stdlib=${KOTLIN_STDLIB:-$kotlin/lib/kotlin-stdlib.jar}
android="$sdk/platforms/android-36/android.jar"
# ONNX Runtime classes, which the engine needs to compile.
ort=android/libs/onnxruntime-android-1.20.0.jar
adb="$sdk/platform-tools/adb"
work=$(mktemp -d)
device=$("$adb" shell mktemp -d /data/local/tmp/atlas-swipe.XXXXXX)
trap 'rm -rf "$work"; "$adb" shell rm -rf "$device"' EXIT
mkdir "$work/classes"
"$kotlin/bin/kotlinc" -language-version 2.2 -no-stdlib -no-reflect -classpath "$stdlib" -d "$work/classes" android/kotlin/com/atlas/keyboard/Layout.kt
javac --release 17 -cp "$work/classes:$android:$stdlib:$ort" -d "$work/classes" \
  android/kotlin/com/atlas/engine/*.java android/kotlin/org/futo/ml/inference/*.java tests/swipe/SavedWordSwipeChecks.java tests/swipe/ContextSwipeChecks.java
jar cf "$work/classes.jar" -C "$work/classes" .
"$sdk/build-tools/36.0.0/d8" --min-api 34 --lib "$android" --output "$work/checks.zip" "$work/classes.jar" "$stdlib"
"$adb" push "$work/checks.zip" "$device/"
"$adb" push android/jniLibs/arm64-v8a "$device/lib"
"$adb" shell mkdir "$device/models"
for model in english.combined honorable_sturgeon magic_macaw hungry_jellyfish; do
  "$adb" push "android/assets/swipe/$model" "$device/models/"
done
"$adb" shell "cd $device && LD_LIBRARY_PATH=$device/lib CLASSPATH=$device/checks.zip app_process -Djava.library.path=$device/lib $device com.atlas.engine.SavedWordSwipeChecks $device/models"
"$adb" push android/assets/swipe/hungry_jellyfish/vocab.txt "$device/models/vocab.txt"
"$adb" push android/assets/autocorrect/en_triples.txt "$device/models/en_triples.txt"
"$adb" shell "cd $device && LD_LIBRARY_PATH=$device/lib CLASSPATH=$device/checks.zip app_process -Djava.library.path=$device/lib $device com.atlas.engine.ContextSwipeChecks $device/models"
