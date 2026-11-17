#!/usr/bin/env bash
# Word completions after some letters, old context model against the next-word model, from a source tree on the phone,
# CPU only, with the app's own code, holding the phone lock. It adds no input and installs nothing.
# Usage: completion_phone.sh SOURCE_TREE CORPUS OUT_FILE
set -euo pipefail
. "$(dirname "$0")/../../env.sh"
TREE=$1; CORPUS=$2; OUT=$3
BASE=$(cd "$(dirname "$0")/.." && pwd)
sdk=$ANDROID_HOME; kotlin=$TK_BUILD/kotlin-2.1.20/kotlinc; android=$sdk/platforms/android-36/android.jar; stdlib=$kotlin/lib/kotlin-stdlib.jar
PATH=$sdk/platform-tools:$PATH
ort=$(ls "$TREE"/android/libs/onnxruntime-android-*.jar | head -1)
work=$(mktemp -d); trap 'rm -rf "$work"' EXIT
mkdir "$work/classes" "$work/src"
cp "$TREE/android/kotlin/com/titankeys/keyboard/Layout.kt" "$work/src/"
tk_compile Kotlin "$kotlin/bin/kotlinc" -language-version 2.2 -no-stdlib -no-reflect -classpath "$stdlib" -d "$work/classes" "$work/src/Layout.kt"
tk_compile javac javac -nowarn --release 17 -cp "$work/classes:$android:$stdlib:$ort" -d "$work/classes" \
  "$TREE"/android/kotlin/com/titankeys/engine/*.java "$TREE"/android/kotlin/org/futo/ml/inference/*.java "$BASE/tools/CompletionRunner.java"
jar cf "$work/classes.jar" -C "$work/classes" .
tk_compile d8 "$sdk/build-tools/36.0.0/d8" --min-api 34 --lib "$android" --output "$work/c.zip" "$work/classes.jar" "$stdlib" "$ort"
python3 -I -c "
import json, sys
with open(sys.argv[2], 'w', encoding='utf-8') as out:
    for line in open(sys.argv[1], encoding='utf-8'):
        e = json.loads(line)
        if e['first']:
            out.write(json.dumps({k: e[k] for k in ('id', 'set', 'first', 'expected', 'context')}) + '\n')
" "$CORPUS" "$work/in.jsonl"
device=/data/local/tmp/tk-baseline
TK_PHONE_LOCK_WAIT=1800 . "$TK_REPO/tools/phone-lock.sh"
tk_adb push -q "$work/c.zip" "$device/cr.zip" >/dev/null
# Always the tree's own ONNX Runtime, so a cached older runtime on the phone is never tested by mistake.
for so in libonnxruntime.so libonnxruntime4j_jni.so; do
  tk_adb push -q "$TREE/android/jniLibs/arm64-v8a/$so" "$device/lib/" >/dev/null
done
tk_adb shell rm -rf "$device/swipe2"
tk_adb push -q "$TREE/android/assets/swipe2" "$device/" >/dev/null
tk_adb push -q "$work/in.jsonl" "$device/cr-in.jsonl" >/dev/null
tk_adb shell "cd $device && LD_LIBRARY_PATH=$device/lib CLASSPATH=$device/cr.zip app_process -Djava.library.path=$device/lib $device com.titankeys.engine.CompletionRunner $device/models $device/swipe2 $device/cr-in.jsonl $device/cr-out.jsonl" 2>&1 | tr -d '\r' | grep -v '^WARNING' | tail -20 || true
tk_adb pull -q "$device/cr-out.jsonl" "$OUT" >/dev/null
tk_adb shell rm -rf "$device/cr-in.jsonl" "$device/cr-out.jsonl" "$device/cr.zip" "$device/swipe2"
echo "wrote $OUT"
