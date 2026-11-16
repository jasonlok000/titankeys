#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
# APP=com.titankeys.debug checks the debug build instead.
app="${APP:-com.titankeys}"

# Send feedback's two rows open the phone's mail app (#74). The mail apps are the ones that answer a mailto: link.
mail_apps=$(adb shell cmd package query-activities --brief -a android.intent.action.SENDTO -d mailto: | tr -d '\r' \
  | rg -o '^\s*[A-Za-z0-9_.]+/' | tr -d ' /' | sort -u)
if [ -z "$mail_apps" ]; then
  echo "FAIL: this phone has no mail app, so Send feedback can only show the address"
  exit 1
fi

# Exercise the installed app on the Titan 2's 1076 x 1200 display.
# No force-stop: it turns TitanKeys' Accessibility service off. The page link opens the page over whatever was there.
for row in 375 500; do
  adb shell am start -W -n "$app"/com.vandam.ink.MainActivity \
    --es com.vandam.ink.notification.HREF /settings/feedback >/dev/null
  sleep 2
  adb shell input tap 300 "$row"
  sleep 3
  top=$(adb shell dumpsys activity activities | rg -o "topResumedActivity=\S+ \S+ ([A-Za-z0-9_.]+)/" -r '$1' | head -n 1)
  if ! printf '%s\n' "$mail_apps" | rg -qx "$top"; then
    echo "FAIL: feedback row $row opened ${top:-nothing}, not a mail app"
    exit 1
  fi
  echo "PASS: feedback row $row opened the mail app ($top)"
  # Leave the draft unsent.
  adb shell input keyevent KEYCODE_BACK
  sleep 1
done
adb shell am start -W -n "$app"/com.vandam.ink.MainActivity \
  --es com.vandam.ink.notification.HREF /settings/feedback >/dev/null
