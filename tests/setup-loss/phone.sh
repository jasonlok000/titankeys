#!/usr/bin/env bash
# A synthetic Accessibility loss. No permission, secure setting, or global setting is changed.
# Run with the phone already on its launcher.
set -euo pipefail
serial=${1:?Use a USB serial from adb devices}
adb=(adb -s "$serial")
app=com.titankeys.debug
focus() { "${adb[@]}" shell dumpsys window | awk '/mCurrentFocus=/ { print; exit }'; }
case "$(focus)" in
  *launcher*) ;;
  *) echo "Open the launcher first."; exit 1 ;;
esac
"${adb[@]}" shell am start -W -n "$app/com.vandam.ink.MainActivity" --ez com.titankeys.debug.setupLoss true >/dev/null
for ((attempt = 0; attempt < 15; attempt++)); do
  if [[ $(focus) == *"$app/"*"GuideActivity"* ]]; then
    if "${adb[@]}" shell uiautomator dump /dev/tty 2>/dev/null | awk '/Let TitanKeys handle the Back key/ { found=1 } END { exit !found }'; then
      echo "The Accessibility repair slide opened. Its button simulates a repair and returns to the app."
      exit 0
    fi
  fi
  sleep 1
done
echo "The Accessibility repair slide did not appear."
exit 1
