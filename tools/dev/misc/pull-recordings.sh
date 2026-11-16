#!/usr/bin/env bash
# Copies the debug build's recorder sessions from the phone into $TK_DATA/recordings. Read-only on the phone.
# Usage: pull-recordings.sh [PREFIX]   PREFIX keeps only names starting with it (session-, sentences-, speed-, delete-, benchmark-).
# Sessions can hold typed text, so they stay in $TK_DATA and never go in the repo. TK_APP picks the package (default the debug build).
set -u
. "$(dirname "$0")/../env.sh"
app=${TK_APP:-com.titankeys.debug}
prefix=${1:-}
dir=/sdcard/Android/data/$app/files/recordings
out=$TK_DATA/recordings
mkdir -p "$out"
for name in $(tk_adb shell ls "$dir" | tr -d '\r' | grep "^$prefix"); do
  tk_adb pull "$dir/$name" "$out/$name" 2>&1 | tail -1 | cut -c1-90
done
ls -la "$out" | tail -n +2 | awk '{print $5, $NF}'
