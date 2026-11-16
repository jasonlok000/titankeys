#!/usr/bin/env bash
# back.sh [PACKAGE]: presses Back only if PACKAGE (default com.titankeys) is the front window and no navigation app runs.
H="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$H/../env.sh"; . "$H/safety.sh"
tk_no_navigation || exit 1
tk_require_front "${1:-com.titankeys}" || exit 1
tk_adb shell input keyevent KEYCODE_BACK; sleep 2
tk_front
