#!/usr/bin/env bash
# rec-pull.sh: pulls any recorded pieces left on the phone into $TK_OUT/rec and deletes them from the phone.
H="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$H/../env.sh"
. "$TK_REPO/tools/phone-lock.sh"
D="$TK_OUT/rec"; mkdir -p "$D"
for n in 1 2 3 4 5 6; do
  tk_adb shell ls /sdcard/tk-rec-$n.mp4 >/dev/null 2>&1 || continue
  tk_adb pull -q /sdcard/tk-rec-$n.mp4 "$D/" && tk_adb shell rm /sdcard/tk-rec-$n.mp4
done
