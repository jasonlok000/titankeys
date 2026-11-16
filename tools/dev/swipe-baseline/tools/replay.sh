#!/usr/bin/env bash
# Replays the labelled corpus through the current engine on the phone. CPU only: it adds no input and does not
# restart the keyboard. It holds the shared phone lock so it stays in series with other phone work.
# Usage: replay.sh SOURCE_TREE CORPUS OUT_FILE [limit]
set -euo pipefail
. "$(dirname "$0")/../../env.sh"
TREE=$1; CORPUS=$2; OUT=$3; LIMIT=${4:-0}
BASE=$(cd "$(dirname "$0")/.." && pwd)
sdk=$ANDROID_HOME
kotlin=$TK_BUILD/kotlin-2.1.20/kotlinc
android=$sdk/platforms/android-36/android.jar
stdlib=$kotlin/lib/kotlin-stdlib.jar
PATH=$sdk/platform-tools:$PATH
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
mkdir "$work/classes" "$work/src"
ort=$TK_ORT/onnxruntime-android-1.20.0-classes.jar
[ -f "$ort" ] || { echo "no ONNX Runtime jar at $ort: set TK_ORT (tools/dev/env.sh)" >&2; exit 1; }
cp "$TREE/android/kotlin/com/titankeys/keyboard/Layout.kt" "$work/src/"
tk_compile Kotlin "$kotlin/bin/kotlinc" -language-version 2.2 -no-stdlib -no-reflect -classpath "$stdlib" -d "$work/classes" "$work/src/Layout.kt"
tk_compile javac javac -nowarn --release 17 -cp "$work/classes:$android:$stdlib:$ort" -d "$work/classes" \
  "$TREE"/android/kotlin/com/titankeys/engine/*.java "$TREE"/android/kotlin/org/futo/ml/inference/*.java "$BASE/tools/BaselineRunner.java"
jar cf "$work/classes.jar" -C "$work/classes" .
tk_compile d8 "$sdk/build-tools/36.0.0/d8" --min-api 34 --lib "$android" --output "$work/checks.zip" "$work/classes.jar" "$stdlib"
device=/data/local/tmp/tk-baseline
TK_PHONE_LOCK_WAIT=900 . "$TK_REPO/tools/phone-lock.sh"
tk_adb shell mkdir -p "$device/models"
tk_adb push -q "$work/checks.zip" "$device/" >/dev/null
tk_adb shell "ls $device/lib/libswipe_jni.so >/dev/null 2>&1" || tk_adb push -q "$TREE/android/jniLibs/arm64-v8a" "$device/lib" >/dev/null
if ! tk_adb shell "ls $device/models/english.combined >/dev/null 2>&1"; then
  for model in english.combined honorable_sturgeon magic_macaw hungry_jellyfish; do
    tk_adb push -q "$TREE/android/assets/swipe/$model" "$device/models/" >/dev/null
  done
  tk_adb push -q "$TREE/android/assets/autocorrect/en_triples.txt" "$device/models/en_triples.txt" >/dev/null
fi
input=$work/corpus.jsonl
if [ "$LIMIT" != 0 ]; then head -n "$LIMIT" "$CORPUS" > "$input"; else cp "$CORPUS" "$input"; fi
tk_adb push -q "$input" "$device/corpus.jsonl" >/dev/null
tk_adb shell "cd $device && LD_LIBRARY_PATH=$device/lib CLASSPATH=$device/checks.zip app_process -Djava.library.path=$device/lib $device com.titankeys.engine.BaselineRunner $device/models $device/corpus.jsonl $device/out.jsonl" | tr -d '\r' | tail -3
tk_adb pull -q "$device/out.jsonl" "$OUT" >/dev/null
tk_adb shell rm -f "$device/corpus.jsonl" "$device/out.jsonl"
echo "wrote $OUT"
