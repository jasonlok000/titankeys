#!/bin/bash
# Builds TitanKeys' Japanese engine from the exact Mozc commit pinned below, in WSL (Linux), from anywhere:
#   bash native/mozc/build.sh
# It makes:
#   android/jniLibs/arm64-v8a/libmozc_tk.so   Mozc with TitanKeys' small JNI layer (native/mozc/tk), only JNI_OnLoad exported
#   android/assets/mozc/mozc.data              Mozc's open-source dictionary and models, built by Mozc's own tools
#   android/assets/mozc.sha256                 the list RimeData's copy checks the data by (MozcEngine)
#   native/mozc/library.sha256                 what the library was built from, checked by tools/checks/mozc.test.ts
#   $WORK/mozc_tool                            the PC tool on the same core, for tests/japanese and the corpus test
# Needs git, curl, python3 3.12+ and a C++ compiler; Mozc's own update_deps.py downloads the rest it pins (the Android NDK
# r29 among them), and bazelisk runs the Bazel Mozc pins (src/.bazeliskrc). Everything goes to $TK_MOZC_WORK
# (~/build/tk-mozc), never into the repo. Sources and licences: native/mozc/SOURCES.md. Mozc is BSD-3-Clause; its data's
# sources are permissive (licences/Mozc-*.txt). Mozc links its own libc++ statically, hidden by tk.map, so the app's
# libc++_shared.so and its NDK don't matter here.
set -euo pipefail
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
HERE="$REPO/native/mozc"
WORK="${TK_MOZC_WORK:-$HOME/build/tk-mozc}"

MOZC=https://github.com/google/mozc.git@a4541df29db443b49f81cf54665873b68fde9efa
BAZELISK_URL=https://github.com/bazelbuild/bazelisk/releases/download/v1.25.0/bazelisk-linux-amd64
BAZELISK_SHA256=fd8fdff418a1758887520fa42da7e6ae39aefc788cf5e7f7bb8db6934d279fc4

mkdir -p "$WORK"
dir="$WORK/mozc" url="${MOZC%@*}" commit="${MOZC#*@}"
if [ "$(git -C "$dir" rev-parse HEAD 2>/dev/null)" != "$commit" ]; then
  rm -rf "$dir"; mkdir -p "$dir"
  git -C "$dir" init -q
  git -C "$dir" remote add origin "$url"
  git -C "$dir" fetch -q --depth 1 origin "$commit"
  git -C "$dir" checkout -q FETCH_HEAD
fi
test "$(git -C "$dir" rev-parse HEAD)" = "$commit"
BAZELISK="$WORK/bazelisk"
[ -x "$BAZELISK" ] || { curl -sSL --fail -o "$BAZELISK" "$BAZELISK_URL"; chmod +x "$BAZELISK"; }
echo "$BAZELISK_SHA256  $BAZELISK" | sha256sum -c --quiet
export BAZELISK_HOME="$WORK/bazelisk-home"
bazel() { (cd "$dir/src" && "$BAZELISK" --output_user_root="$WORK/bazel-root" "$@"); }
# Half the memory and most of the cores at most: a full build once took WSL down with it.
LIMITS=(--local_resources=memory=HOST_RAM*.5 --local_resources=cpu=HOST_CPUS*.75)

# TitanKeys' package in Mozc's tree, and Mozc's own pinned dependencies.
rm -rf "$dir/src/android/tk"; cp -r "$HERE/tk" "$dir/src/android/tk"
(cd "$dir/src" && python3 build_tools/update_deps.py > "$WORK/deps.log" 2>&1) || { tail -20 "$WORK/deps.log"; exit 1; }

# Mozc keeps its session handler visible only to its own android/jni and ios packages; TitanKeys' package beside them is
# allowed in by Bazel's flag, with no change to Mozc's files.
bazel build "${LIMITS[@]}" --config oss_android --config release_build --nocheck_visibility //android/tk:mozc_tk.arm64 > "$WORK/android.log" 2>&1 \
  || { grep -n "ERROR\|error:" "$WORK/android.log" | head -30; exit 1; }
bazel build "${LIMITS[@]}" --config oss_linux --config release_build --nocheck_visibility //android/tk:mozc_tool //data_manager/oss:mozc_dataset_for_oss \
  > "$WORK/linux.log" 2>&1 || { grep -n "ERROR\|error:" "$WORK/linux.log" | head -30; exit 1; }

STRIP="$(ls -d "$dir"/src/third_party/ndk/android-ndk-r29/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip)"
# The arm64 build's own output folder: bazel-bin points at the last build, the PC's.
built="$(ls -t "$dir"/src/bazel-out/arm64-v8a-*/bin/android/tk/libmozc_tk.so 2>/dev/null | head -1)"
[ -n "$built" ] || { echo "no arm64 mozc_tk built" >&2; exit 1; }
mkdir -p "$REPO/android/jniLibs/arm64-v8a"
# Stripped in the work folder, then copied: strip can't set a file's mode on the Windows drive.
"$STRIP" --strip-unneeded -o "$WORK/libmozc_tk.so" "$built"
cp -f "$WORK/libmozc_tk.so" "$REPO/android/jniLibs/arm64-v8a/libmozc_tk.so"
cp -f "$dir/src/bazel-bin/android/tk/mozc_tool" "$WORK/mozc_tool"
chmod u+w "$WORK/mozc_tool"

# Only JNI_OnLoad leaves the library, besides the two section bounds the linker itself makes for protobuf's defaults.
exported="$("$(dirname "$STRIP")/llvm-readelf" --dyn-syms "$REPO/android/jniLibs/arm64-v8a/libmozc_tk.so" \
  | awk '$5 == "GLOBAL" && $7 != "UND" { print $8 }' | grep -v -x -e __start_pb_defaults -e __stop_pb_defaults)"
[ "$exported" = "JNI_OnLoad" ] || { echo "libmozc_tk.so exports more than JNI_OnLoad: $exported" >&2; exit 1; }

OUT="$REPO/android/assets/mozc"
rm -rf "$OUT"; mkdir -p "$OUT"
cp "$dir/src/bazel-bin/data_manager/oss/mozc.data" "$OUT/mozc.data"

cd "$REPO"
(cd android/assets/mozc && sha256sum mozc.data) > android/assets/mozc.sha256
sha256sum native/mozc/tk/BUILD.bazel native/mozc/tk/tk.map native/mozc/tk/tk_core.h native/mozc/tk/tk_core.cc \
  native/mozc/tk/tk_jni.cc android/jniLibs/arm64-v8a/libmozc_tk.so > native/mozc/library.sha256
echo "built android/jniLibs/arm64-v8a/libmozc_tk.so ($(stat -c%s android/jniLibs/arm64-v8a/libmozc_tk.so) bytes)," \
  "android/assets/mozc/mozc.data ($(stat -c%s android/assets/mozc/mozc.data) bytes), $WORK/mozc_tool"
