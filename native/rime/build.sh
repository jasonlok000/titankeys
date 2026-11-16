#!/bin/bash
# Builds TitanKeys' Rime library, android/jniLibs/arm64-v8a/librime_tk.so, and the PC tool data.sh uses, from the exact
# sources pinned below. Run it in WSL (Linux) from anywhere:  bash native/rime/build.sh
# Needs git, cmake 3.18+, clang, curl, and the Android NDK r27c (sdkmanager "ndk;27.2.12479018"), the NDK the app's
# libc++_shared.so comes from, so Rime shares it. Sources and build trees go to $TK_RIME_WORK (~/build/tk-rime), never
# into the repo. Where each source comes from and its licence: native/rime/SOURCES.md. Licences, in short: librime is
# BSD-3-Clause and OpenCC Apache-2.0, with BSD, MIT and Boost libraries (licences/Rime-*.txt). Nothing built here is LGPL:
# the LGPL-3.0 parts are data, which data.sh builds.
set -euo pipefail
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
HERE="$REPO/native/rime"
WORK="${TK_RIME_WORK:-$HOME/build/tk-rime}"
NDK="${ANDROID_NDK:-$HOME/android-sdk/ndk/27.2.12479018}"
JOBS="${JOBS:-8}"

# The pinned sources: Trime's own pins at Trime 2e2b4d5 (2026-11-12). librime's submodules (OpenCC, leveldb,
# marisa-trie, yaml-cpp, spdlog) are pinned by librime's commit.
LIBRIME=https://github.com/rime/librime.git@bb56876890ba60f446fa2640c244b1794b965eda
SNAPPY=https://github.com/google/snappy.git@9c28114a38866f6deeaa826db918293bc28ae410
TRIME=https://github.com/osfans/trime.git@2e2b4d5394297745bf42ff6260bb547a271f378a
BOOST_URL=https://github.com/boostorg/boost/releases/download/boost-1.92.0/boost-1.92.0-cmake.tar.xz
BOOST_SHA256=9bed76128d4e46755dbe818487788c6fceb6f72b378f4daa49b7e1e600d9088d
# NDK r27c's libc++_shared.so, the one in android/jniLibs.
LIBCXX_SHA256=f9992c4ba6b7c5a716e3a202fceb1ce029d6a2b0605838ac6b3219f489dd7970

grep -q "Pkg.Revision = 27.2.12479018" "$NDK/source.properties" || { echo "NDK r27c not found at $NDK" >&2; exit 1; }

fetch() { # dir url@commit
  local dir="$WORK/src/$1" url="${2%@*}" commit="${2#*@}"
  if [ "$(git -C "$dir" rev-parse HEAD 2>/dev/null)" != "$commit" ]; then
    rm -rf "$dir"; mkdir -p "$dir"
    git -C "$dir" init -q
    git -C "$dir" remote add origin "$url"
    git -C "$dir" fetch -q --depth 1 origin "$commit"
    git -C "$dir" checkout -q FETCH_HEAD
  fi
  git -C "$dir" submodule -q update --init --recursive --depth 1
  test "$(git -C "$dir" rev-parse HEAD)" = "$commit"
}
mkdir -p "$WORK/src"
fetch librime "$LIBRIME"
fetch snappy "$SNAPPY"
fetch trime "$TRIME"
[ -f "$WORK/src/boost-1.92.0.tar.xz" ] || curl -sSL --fail -o "$WORK/src/boost-1.92.0.tar.xz" "$BOOST_URL"
echo "$BOOST_SHA256  $WORK/src/boost-1.92.0.tar.xz" | sha256sum -c --quiet

# The tree CMake builds: TitanKeys' CMakeLists.txt and sources, Trime's cmake/ helpers, and the fetched sources.
TREE="$WORK/tree"
rm -rf "$TREE"; mkdir -p "$TREE"
cp "$HERE/CMakeLists.txt" "$HERE/rime_jni.cc" "$HERE/rime_tool.cc" "$TREE/"
cp -r "$WORK/src/trime/app/src/main/jni/cmake" "$TREE/cmake"
ln -s "$WORK/src/librime" "$TREE/librime"
ln -s "$WORK/src/snappy" "$TREE/snappy"
ln -s "$WORK/src/boost-1.92.0.tar.xz" "$TREE/boost-1.92.0.tar.xz"
# Trime's Boost.cmake looks for the archive in the folder CMake runs from.
cd "$TREE"

# The app's library: arm64, Release, the app's minSdk, the shared C++ runtime, 16 KB pages (CMakeLists.txt).
cmake -S "$TREE" -B "$WORK/out-android" -DCMAKE_BUILD_TYPE=Release -DTK_RIME_SOURCES="$WORK/src" \
  -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=android-34 -DANDROID_STL=c++_shared > "$WORK/out-android.log" 2>&1 \
  || { tail -40 "$WORK/out-android.log"; exit 1; }
cmake --build "$WORK/out-android" -j"$JOBS" --target rime_tk >> "$WORK/out-android.log" 2>&1 \
  || { grep -n "error" "$WORK/out-android.log" | head -30; exit 1; }

BIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
OUT="$REPO/android/jniLibs/arm64-v8a"
# Stripped in the Linux tree, then copied: llvm-strip can't set a file's mode on a Windows drive.
# The NDK's toolchain adds a build id, a hash that changes with the build folder: it goes, so any folder gives the same bytes.
"$BIN/llvm-strip" --strip-unneeded --remove-section=.note.gnu.build-id -o "$WORK/librime_tk.so" "$WORK/out-android/librime_tk.so"
cat "$WORK/librime_tk.so" > "$OUT/librime_tk.so"
# Only Android's own libraries and the app's libc++_shared.so.
needed="$("$BIN/llvm-readelf" -d "$OUT/librime_tk.so" | sed -n 's/.*Shared library: \[\(.*\)\]/\1/p' | sort | tr '\n' ' ')"
test "$needed" = "libc++_shared.so libc.so libdl.so liblog.so libm.so " || { echo "unexpected libraries: $needed" >&2; exit 1; }
echo "$LIBCXX_SHA256  $OUT/libc++_shared.so" | sha256sum -c --quiet
# Every loaded segment on 16 KB pages. (Read into a variable: grep -q in a pipe can cut readelf off, which pipefail fails.)
loads="$("$BIN/llvm-readelf" -lW "$OUT/librime_tk.so" | grep " LOAD ")"
[ -n "$loads" ] && ! grep -qv "0x4000$" <<< "$loads" || { echo "not 16 KB pages" >&2; exit 1; }

# The PC tool, from the same sources: it compiles the data (data.sh) and tries codes.
cmake -S "$TREE" -B "$WORK/out-host" -DCMAKE_BUILD_TYPE=Release -DTK_RIME_SOURCES="$WORK/src" \
  -DCMAKE_C_COMPILER=clang -DCMAKE_CXX_COMPILER=clang++ > "$WORK/out-host.log" 2>&1 \
  || { tail -40 "$WORK/out-host.log"; exit 1; }
cmake --build "$WORK/out-host" -j"$JOBS" --target rime_tool opencc_dict >> "$WORK/out-host.log" 2>&1 \
  || { grep -n "error" "$WORK/out-host.log" | head -30; exit 1; }

cd "$REPO"
sha256sum native/rime/CMakeLists.txt native/rime/rime_jni.cc android/jniLibs/arm64-v8a/librime_tk.so > native/rime/library.sha256
echo "built android/jniLibs/arm64-v8a/librime_tk.so ($(stat -c%s android/jniLibs/arm64-v8a/librime_tk.so) bytes)"
