#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."

# Check the installed build on a connected Android device.
adb shell am start -W -n com.atlas/com.vandam.ink.MainActivity \
  --es com.vandam.ink.notification.HREF /settings/about >/dev/null
sleep 2
if ! adb shell dumpsys activity activities | rg -q 'topResumedActivity=.*com.atlas/com.vandam.ink.MainActivity'; then
  echo "FAIL: opening About closed Atlas"
  exit 1
fi
echo "PASS: About opened and Atlas stayed in the foreground"
