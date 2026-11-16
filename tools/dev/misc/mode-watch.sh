#!/usr/bin/env bash
# Read-only: logs the firmware's touchpad mode each time the front app changes, to the screen and $TK_OUT/modes.txt.
# Usage: mode-watch.sh [MINUTES]   default 15. It polls adb once a second from here; nothing is started on the phone.
. "$(dirname "$0")/../env.sh"
minutes=${1:-15}
mkdir -p "$TK_OUT"
out=$TK_OUT/modes.txt
: > "$out"
last=""
deadline=$(( $(date +%s) + minutes * 60 ))
while [ "$(date +%s)" -lt "$deadline" ]; do
  focus=$(tk_adb shell dumpsys window | grep -m1 mCurrentFocus | tr -d '\r' | sed -E 's/.*u0 //; s/[/}].*//')
  mode=$(tk_adb shell getprop persist.sys.touchpad_event_mode | tr -d '\r')
  scroll=$(tk_adb shell getprop persist.sys.touchpad_forcescroll | tr -d '\r')
  state="$focus forcescroll=$scroll mode=$mode"
  if [ "$state" != "$last" ]; then echo "$(date +%H:%M:%S) $state" | tee -a "$out"; last=$state; fi
  sleep 1
done
echo "watch finished"
