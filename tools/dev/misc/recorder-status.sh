#!/usr/bin/env bash
# Reports the recorder's state: what is in front, the keyboard in use, the sessions on the phone and recent crashes.
# Pulls the newest session to $TK_DATA/recordings/latest.jsonl, and screenshots only when the recorder itself is in front.
set -u
. "$(dirname "$0")/../env.sh"
app=${TK_APP:-com.titankeys.debug}
dir=/sdcard/Android/data/$app/files/recordings

focus=$(tk_adb shell dumpsys window | grep -m1 mCurrentFocus | tr -d '\r')
echo "focus: $focus" | cut -c1-110
echo "current keyboard: $(tk_adb shell settings get secure default_input_method | tr -d '\r')"
echo "--- sessions"
tk_adb shell ls -la "$dir" | cut -c1-100
latest=$(tk_adb shell ls -t "$dir" | head -1 | tr -d '\r')
mkdir -p "$TK_DATA/recordings" "$TK_OUT"
[ -n "$latest" ] && tk_adb pull "$dir/$latest" "$TK_DATA/recordings/latest.jsonl" 2>&1 | tail -1 | cut -c1-90
[ -f "$TK_DATA/recordings/latest.jsonl" ] && wc -lc "$TK_DATA/recordings/latest.jsonl"
echo "--- crashes"
tk_adb logcat -d -b crash | tail -20 | cut -c1-170
rm -f "$TK_OUT/recorder.png"
case "$focus" in
  *"$app"/*RecorderActivity*) bash "$(dirname "$0")/../phone/shot.sh" recorder && echo "screenshot: $TK_OUT/shots/recorder.png (status bar cropped)" ;;
  *) echo "recorder not in front, no screenshot" ;;
esac
