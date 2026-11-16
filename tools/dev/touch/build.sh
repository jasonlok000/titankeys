#!/usr/bin/env bash
# Builds tk-inject for the phone (arm64) with the NDK, into $TK_OUT/touch/tk-inject. Runs in WSL.
set -euo pipefail
H="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$H/../env.sh"
clang=$(ls "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME (titankeys-env.sh)}"/toolchains/llvm/prebuilt/*/bin/aarch64-linux-android34-clang | head -1)
mkdir -p "$TK_OUT/touch"
"$clang" -O2 -Wall -Wextra -Werror -static-libgcc -o "$TK_OUT/touch/tk-inject" "$H/inject.c"
echo "$TK_OUT/touch/tk-inject"
