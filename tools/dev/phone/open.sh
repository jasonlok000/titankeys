#!/usr/bin/env bash
# open.sh PACKAGE [ACTIVITY]: wakes the phone and opens the app's launcher activity, or ACTIVITY (pkg/.Name). Prints the front window.
H="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$H/../env.sh"; . "$H/safety.sh"
tk_no_navigation || exit 1
tk_adb shell input keyevent KEYCODE_WAKEUP
act=${2:-$(tk_adb shell cmd package resolve-activity --brief "$1" | tail -1 | tr -d '\r')}
echo "launch $act"
tk_adb shell am start -n "$act" >/dev/null
sleep 3
tk_front
