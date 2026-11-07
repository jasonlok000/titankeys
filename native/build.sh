#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
NDK_SYSROOT="${ANDROID_NDK_SYSROOT:-$PWD/build/toolchains/android-ndk-r27c/toolchains/llvm/prebuilt/darwin-x86_64/sysroot}"
CXX="${ANDROID_CXX:-$NDK_SYSROOT/../bin/aarch64-linux-android26-clang++}"
FUTO_SOURCE="$PWD/build/upstream-swipe"
FUTO_BUILD="$FUTO_SOURCE/build-android-arm64-v8a"
ADAPTER_SOURCE="$PWD/build/native-adapter"
if [ ! -d "$NDK_SYSROOT" ]; then
  echo "Android NDK sysroot missing. Run bash tools/setup-native.sh or set ANDROID_NDK_SYSROOT." >&2
  exit 1
fi
if [ ! -f "$FUTO_BUILD/libswipe_decoder.a" ]; then
  echo "FUTO static library missing. Run bash native/build-engine.sh first." >&2
  exit 1
fi
test "$(git -C "$FUTO_SOURCE" rev-parse HEAD)" = 1b13f2c85d6b347f6ea3fbc4b3aaf01fce42429a
mkdir -p "$ADAPTER_SOURCE/jni"
git -C "$FUTO_SOURCE" show HEAD:jni/swipe_jni.cpp > "$ADAPTER_SOURCE/jni/swipe_jni.cpp"
patch -s -d "$ADAPTER_SOURCE" -p1 < native/swipe-jni.patch
jni_names=()
for method in Init Recognize PredictNext GetLastTiming GetScoring HasDecoder HasLm SetMode SetCaseMapping Destroy PinCores; do
  jni_names+=("-DJava_org_futo_ml_inference_SwipeDecoder_native${method}=Java_org_futo_ml_inference_SwipeDecoder_native${method}Safe")
done
"$CXX" --target=aarch64-linux-android26 --sysroot="$NDK_SYSROOT" -std=c++17 -O2 -fPIC -fvisibility=hidden \
  -ffile-prefix-map="$PWD"=. -mno-outline-atomics -shared \
  -isystem "$NDK_SYSROOT/usr/include/c++/v1" -I native/futo/include -I "$FUTO_SOURCE/include" \
  "${jni_names[@]}" native/personal_dictionary.cpp "$ADAPTER_SOURCE/jni/swipe_jni.cpp" \
  -L android/jniLibs/arm64-v8a -L "$FUTO_BUILD" -L "$NDK_SYSROOT/usr/lib/aarch64-linux-android/26" \
  -Wl,--no-undefined,-soname,libpersonal_dictionary.so,-z,max-page-size=16384,-z,relro,-z,now \
  -lswipe_jni -lswipe_decoder -lc++_shared -lc -lm -ldl -llog -o android/jniLibs/arm64-v8a/libpersonal_dictionary.so

shasum -a 256 native/personal_dictionary.cpp native/swipe-jni.patch native/futo/include/swipe_decoder/trie.hpp native/futo/include/swipe_decoder/itrie.h android/jniLibs/arm64-v8a/libswipe_jni.so android/jniLibs/arm64-v8a/libc++_shared.so android/jniLibs/arm64-v8a/libpersonal_dictionary.so > native/bridge.sha256
