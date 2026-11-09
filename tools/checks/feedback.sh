#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
# APP=com.titankeys.debug checks the debug build instead.
app="${APP:-com.titankeys}"

# Exercise the installed app on the Titan 2's 1076 x 1200 display.
for row in 375 500; do
  adb shell am force-stop "$app"
  adb shell am start -W -n "$app"/com.vandam.ink.MainActivity \
    --es com.vandam.ink.notification.HREF /settings/feedback >/dev/null
  sleep 2
  adb shell input tap 300 "$row"
  sleep 2
  if adb shell dumpsys activity activities | rg -q "topResumedActivity=.*$app/"; then
    echo "FAIL: feedback row $row did not open the browser"
    exit 1
  fi
  echo "PASS: feedback row $row left the app for the browser"
done
adb shell am start -W -n "$app"/com.vandam.ink.MainActivity \
  --es com.vandam.ink.notification.HREF /settings/feedback >/dev/null
