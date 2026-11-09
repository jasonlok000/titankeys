#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
# APP=com.titankeys.debug checks the debug build instead.
app="${APP:-com.titankeys}"

# Check the installed build on a connected Android device.
adb shell am start -W -n "$app"/com.vandam.ink.MainActivity \
  --es com.vandam.ink.notification.HREF /settings/about >/dev/null
sleep 2
if ! adb shell dumpsys activity activities | rg -q "topResumedActivity=.*$app/com.vandam.ink.MainActivity"; then
  echo "FAIL: opening About closed TitanKeys"
  exit 1
fi
echo "PASS: About opened and TitanKeys stayed in the foreground"
