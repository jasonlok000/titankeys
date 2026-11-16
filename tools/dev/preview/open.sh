#!/usr/bin/env bash
# Opens a preview page in Chrome on the phone, through adb reverse to server.py on this machine.
# Usage: open.sh "swipe.html?pick=5" [port]. Start server.py first. Stops if a navigation app is running.
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/../env.sh"
. "$TK_DEV/phone/safety.sh"
port="${2:-8765}"
curl -s -o /dev/null -w "server %{http_code}\n" "http://localhost:$port/${1%%\?*}"
tk_no_navigation || exit 1
tk_adb reverse "tcp:$port" "tcp:$port" >/dev/null
tk_adb shell input keyevent KEYCODE_WAKEUP
url="http://localhost:$port/$1"
tk_adb shell am start -a android.intent.action.VIEW -d "'$url'" -p com.android.chrome >/dev/null
sleep 3
tk_front | grep -q "com.android.chrome" || { echo "STOP: Chrome is not in front"; exit 2; }
echo "opened $1"
echo "Screenshots of Chrome can show other tabs: crop or delete them before sharing."
