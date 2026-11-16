#!/usr/bin/env bash
# Times ONNX models on the phone with ONNX Runtime, CPU only, holding the shared phone lock. Adds no input and does not
# touch the keyboard. The files go to /data/local/tmp/tk-ort and the models are removed afterwards.
# Usage: ort_bench.sh ORT_AAR_DIR ck|lm MODEL.onnx [mode] [ck|lm MODEL.onnx [mode] ...]
# mode: plain (1, 2 and 4 threads), or noarena, noprepack, noarena-noprepack (4 threads)
set -euo pipefail
. "$(dirname "$0")/../../env.sh"
ORT=$1; shift
BASE=$(cd "$(dirname "$0")/.." && pwd)
sdk=$ANDROID_HOME
android=$sdk/platforms/android-36/android.jar
PATH=$sdk/platform-tools:$PATH
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
mkdir "$work/classes"
tk_compile javac javac -nowarn --release 17 -cp "$ORT/classes.jar:$android" -d "$work/classes" "$BASE/tools/OrtBench.java"
jar cf "$work/bench.jar" -C "$work/classes" .
tk_compile d8 "$sdk/build-tools/36.0.0/d8" --min-api 34 --lib "$android" --output "$work/bench.zip" "$work/bench.jar" "$ORT/classes.jar"
device=/data/local/tmp/tk-ort
TK_PHONE_LOCK_WAIT=900 . "$TK_REPO/tools/phone-lock.sh"
tk_adb shell mkdir -p "$device/lib"
tk_adb push -q "$work/bench.zip" "$device/" >/dev/null
tk_adb shell "ls $device/lib/libonnxruntime.so >/dev/null 2>&1" || tk_adb push -q "$ORT/jni/arm64-v8a/." "$device/lib/" >/dev/null
while [ $# -ge 2 ]; do
  kind=$1; model=$2; shift 2
  mode=plain
  case "${1:-}" in plain|default|noarena|noprepack|noarena-noprepack|nopattern) mode=$1; shift ;; esac
  name=$(basename "$(dirname "$model")")-$(basename "$model")
  tk_adb shell "ls $device/$name >/dev/null 2>&1" || tk_adb push -q "$model" "$device/$name" >/dev/null
  echo "== $kind $name $mode"
  tk_adb shell "cd $device && LD_LIBRARY_PATH=$device/lib CLASSPATH=$device/bench.zip app_process -Djava.library.path=$device/lib $device OrtBench $kind $device/$name $mode" 2>&1 | tr -d '\r' | grep -v '^WARNING' || true
done
tk_adb shell "rm -f $device/*.onnx"
