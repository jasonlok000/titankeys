#!/usr/bin/env bash
# Saved words and a boost for them (owner's request, Nov 10): runs SavedRunner on the phone, CPU only, holding the phone lock.
# It adds no input, installs nothing and does not touch the keyboard.
# Usage: saved_phone.sh SOURCE_TREE CORPUS OUT_FILE [SAVED_TSV] [BOOSTS]   (no SAVED_TSV: nothing saved, as today)
set -euo pipefail
. "$(dirname "$0")/../../env.sh"
TREE=$1; CORPUS=$2; OUT=$3; SAVED=${4:-}; BOOSTS=${5:-0}
BASE=$(cd "$(dirname "$0")/.." && pwd)
sdk=$ANDROID_HOME; kotlin=$TK_BUILD/kotlin-2.1.20/kotlinc; android=$sdk/platforms/android-36/android.jar; stdlib=$kotlin/lib/kotlin-stdlib.jar
PATH=$sdk/platform-tools:$PATH
ort=$(ls "$TREE"/android/libs/onnxruntime-android-*.jar | head -1)
work=$(mktemp -d); trap 'rm -rf "$work"' EXIT
mkdir "$work/classes" "$work/src"
cp "$TREE/android/kotlin/com/titankeys/keyboard/Layout.kt" "$work/src/"
tk_compile Kotlin "$kotlin/bin/kotlinc" -language-version 2.2 -no-stdlib -no-reflect -classpath "$stdlib" -d "$work/classes" "$work/src/Layout.kt"
tk_compile javac javac -nowarn --release 17 -cp "$work/classes:$android:$stdlib:$ort" -d "$work/classes" \
  "$TREE"/android/kotlin/com/titankeys/engine/*.java "$TREE"/android/kotlin/org/futo/ml/inference/*.java "$BASE/tools/SavedRunner.java"
jar cf "$work/classes.jar" -C "$work/classes" .
tk_compile d8 "$sdk/build-tools/36.0.0/d8" --min-api 34 --lib "$android" --output "$work/e2.zip" "$work/classes.jar" "$stdlib" "$ort"
python3 -I "$BASE/tools/make_readings.py" "$CORPUS" "$work/readings.jsonl" plain
device=/data/local/tmp/tk-baseline
TK_PHONE_LOCK_WAIT=1800 . "$TK_REPO/tools/phone-lock.sh"
tk_adb push -q "$work/e2.zip" "$device/e2.zip" >/dev/null
# Always the tree's own ONNX Runtime, so a cached older runtime on the phone is never tested by mistake.
for so in libonnxruntime.so libonnxruntime4j_jni.so; do
  tk_adb push -q "$TREE/android/jniLibs/arm64-v8a/$so" "$device/lib/" >/dev/null
done
tk_adb shell rm -rf "$device/swipe2"
tk_adb push -q "$TREE/android/assets/swipe2" "$device/" >/dev/null
tk_adb push -q "$work/readings.jsonl" "$device/e2-in.jsonl" >/dev/null
saved_arg=""
if [ -n "$SAVED" ]; then tk_adb push -q "$SAVED" "$device/saved.tsv" >/dev/null; saved_arg="$device/saved.tsv"; fi
tk_adb shell "cd $device && LD_LIBRARY_PATH=$device/lib CLASSPATH=$device/e2.zip app_process -Djava.library.path=$device/lib $device com.titankeys.engine.SavedRunner $device/models $device/swipe2 $device/e2-in.jsonl $device/e2-out.jsonl '$saved_arg' $BOOSTS" 2>&1 | tr -d '\r' | grep -v '^WARNING' | tail -20 || true
tk_adb pull -q "$device/e2-out.jsonl" "$OUT" >/dev/null
tk_adb shell rm -rf "$device/e2-in.jsonl" "$device/e2-out.jsonl" "$device/e2.zip" "$device/swipe2" "$device/saved.tsv"
echo "wrote $OUT"
