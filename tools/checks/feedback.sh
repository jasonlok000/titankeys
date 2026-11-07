#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."

# Exercise the installed app on the Titan 2's 1076 x 1200 display.
for row in 375 500; do
  adb shell am force-stop com.atlas
  adb shell am start -W -n com.atlas/com.vandam.ink.MainActivity \
    --es com.vandam.ink.notification.HREF /settings/feedback >/dev/null
  sleep 2
  adb shell input tap 300 "$row"
  sleep 1
  if ! adb shell dumpsys activity activities | rg -q 'topResumedActivity=.*MessageCompose'; then
    echo "FAIL: feedback row $row did not open the email composer"
    exit 1
  fi
  subject="Atlas bug report"
  if [[ "$row" == 500 ]]; then subject="Atlas idea"; fi
  adb shell uiautomator dump /sdcard/atlas-feedback-check.xml >/dev/null
  draft=$(adb shell cat /sdcard/atlas-feedback-check.xml)
  if ! rg -q "$subject" <<<"$draft" || ! rg -q 'juleshall4@icloud.com' <<<"$draft"; then
    echo "FAIL: feedback row $row opened an incomplete draft"
    exit 1
  fi
  echo "PASS: feedback row $row opened a draft with the expected subject and recipient"
done
adb shell am start -W -n com.atlas/com.vandam.ink.MainActivity \
  --es com.vandam.ink.notification.HREF /settings/feedback >/dev/null
