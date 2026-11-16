#!/usr/bin/env bash
# guarded.sh ARGS...: `adb shell input ARGS`, after touchlib.sh's checks, for setting up a check's field (Ctrl+A, Backspace,
# text). Exits 1 on a STOP.
H="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$H/../env.sh"; . "$H/../phone/safety.sh"; . "$H/touchlib.sh"
tk_touch_lock
tk_touch_ready || exit 1
tk_adb shell input "$@"
