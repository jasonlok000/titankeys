#!/usr/bin/env bash
# rec-stop.sh: ends the recording rec-start.sh is making.
H="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$H/../env.sh"
. "$TK_REPO/tools/phone-lock.sh"
mkdir -p "$TK_OUT/rec"; touch "$TK_OUT/rec/STOP"
tk_adb shell pkill -INT screenrecord; echo stopped
