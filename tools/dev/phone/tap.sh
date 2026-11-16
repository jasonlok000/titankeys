#!/usr/bin/env bash
# tap.sh X Y [PACKAGE]: taps the screen only if PACKAGE (default com.titankeys) is the front window. Prints the front window after.
H="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$H/../env.sh"; . "$H/safety.sh"
tk_no_navigation || exit 1
tk_require_front "${3:-com.titankeys}" || exit 1
tk_adb shell input tap "$1" "$2"; sleep 2
tk_front
