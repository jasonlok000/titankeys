#!/usr/bin/env bash
# Read-only checks before a long unattended run: the phone link, power and screen settings, the keyboard in use,
# the installed build, the link's round trip and free space here. TK_APP picks the package (default the debug build).
. "$(dirname "$0")/../env.sh"
app=${TK_APP:-com.titankeys.debug}
echo "--- phone link"
timeout 15 adb devices -l | sed -n 2,5p | cut -c1-90
state=$(timeout 10 adb ${TK_ADB_SERIAL:+-s "$TK_ADB_SERIAL"} get-state 2>&1)
echo "state: $state"
[ "$state" = device ] || exit 0
echo "--- phone power"
tk_adb shell dumpsys battery | grep -E 'AC powered|USB powered|Wireless powered|level' | tr -s ' ' | tr '\n' ';'; echo
tk_adb shell dumpsys power | grep -m1 mWakefulness= | tr -d ' \r'
echo "stay awake while charging setting: $(tk_adb shell settings get global stay_on_while_plugged_in | tr -d '\r')"
echo "screen timeout ms: $(tk_adb shell settings get system screen_off_timeout | tr -d '\r')"
echo "--- keyboard"
echo "current keyboard: $(tk_adb shell settings get secure default_input_method | tr -d '\r')"
tk_adb shell dumpsys package "$app" | grep -m2 -E 'versionName|lastUpdateTime' | tr -d ' \r' | tr '\n' ';'; echo
echo "--- speed of the link"
start=$(date +%s%N); tk_adb shell true; echo "round trip ms: $(( ($(date +%s%N) - start) / 1000000 ))"
echo "--- free space"
df -h "$HOME" | tail -1 | tr -s ' '
