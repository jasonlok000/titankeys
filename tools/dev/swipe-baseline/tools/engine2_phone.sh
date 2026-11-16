#!/usr/bin/env bash
# Runs swipe engine 2.0 from a source tree on the phone, CPU only, with the app's own code, holding the phone lock.
# It adds no input, installs nothing and does not touch the keyboard. Usage: engine2_phone.sh SOURCE_TREE CORPUS OUT_FILE [limit]
set -euo pipefail
. "$(dirname "$0")/../../env.sh"
TREE=$1; CORPUS=$2; OUT=$3; LIMIT=${4:-}
BASE=$(cd "$(dirname "$0")/.." && pwd)
sdk=$ANDROID_HOME; kotlin=$TK_BUILD/kotlin-2.1.20/kotlinc; android=$sdk/platforms/android-36/android.jar; stdlib=$kotlin/lib/kotlin-stdlib.jar
PATH=$sdk/platform-tools:$PATH
ort=$(ls "$TREE"/android/libs/onnxruntime-android-*.jar | head -1)
work=$(mktemp -d); trap 'rm -rf "$work"' EXIT
mkdir "$work/classes" "$work/src"
cp "$TREE/android/kotlin/com/titankeys/keyboard/Layout.kt" "$work/src/"
"$kotlin/bin/kotlinc" -language-version 2.2 -no-stdlib -no-reflect -classpath "$stdlib" -d "$work/classes" "$work/src/Layout.kt" 2>/dev/null
javac -nowarn --release 17 -cp "$work/classes:$android:$stdlib:$ort" -d "$work/classes" \
  "$TREE"/android/kotlin/com/titankeys/engine/*.java "$TREE"/android/kotlin/org/futo/ml/inference/*.java "$BASE/tools/Engine2Runner.java" 2>&1 | grep -v '^Note' || true
jar cf "$work/classes.jar" -C "$work/classes" .
"$sdk/build-tools/36.0.0/d8" --min-api 34 --lib "$android" --output "$work/e2.zip" "$work/classes.jar" "$stdlib" "$ort" 2>&1 | grep -iv 'warning' | head -5 || true
python3 -I "$BASE/tools/make_readings.py" "$CORPUS" "$work/readings.jsonl" plain
device=/data/local/tmp/tk-baseline
mkdir -p "$TK_BUILD/tk-tools"; exec 9>"$TK_BUILD/tk-tools/.lock"
flock -w 1800 9 || { echo "phone busy"; exit 3; }
tk_adb push -q "$work/e2.zip" "$device/e2.zip" >/dev/null
# Always the tree's own ONNX Runtime, so a cached older runtime on the phone is never tested by mistake.
for so in libonnxruntime.so libonnxruntime4j_jni.so; do
  tk_adb push -q "$TREE/android/jniLibs/arm64-v8a/$so" "$device/lib/" >/dev/null
done
tk_adb shell rm -rf "$device/swipe2"
tk_adb push -q "$TREE/android/assets/swipe2" "$device/" >/dev/null
tk_adb push -q "$work/readings.jsonl" "$device/e2-in.jsonl" >/dev/null
tk_adb shell "cd $device && FUTO_ONLY=${FUTO_ONLY:-0} LD_LIBRARY_PATH=$device/lib CLASSPATH=$device/e2.zip app_process -Djava.library.path=$device/lib $device com.titankeys.engine.Engine2Runner $device/models $device/swipe2 $device/e2-in.jsonl $device/e2-out.jsonl $LIMIT" 2>&1 | tr -d '\r' | grep -v '^WARNING' | tail -20 || true
tk_adb pull -q "$device/e2-out.jsonl" "$OUT" >/dev/null
tk_adb shell rm -rf "$device/e2-in.jsonl" "$device/e2-out.jsonl" "$device/e2.zip" "$device/swipe2"
echo "wrote $OUT"
