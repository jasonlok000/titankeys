#!/usr/bin/env bash
# rec-start.sh: records the phone's screen in 170 s pieces until rec-stop.sh runs (or 6 pieces, about 17 min), and logs the
# front window every half second, so only the stretches with TitanKeys in front are ever looked at (see rec-frames.py).
# Writes to $TK_OUT/rec. Pieces are pulled off the phone and deleted there at the end.
H="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$H/../env.sh"; . "$H/safety.sh"
D="$TK_OUT/rec"; mkdir -p "$D"
rm -f "$D/STOP"; : > "$D/focus.log"; : > "$D/pieces.log"
( while [ ! -f "$D/STOP" ]; do
    echo "$(date +%s%3N) $(tk_front | grep -oE '[a-z0-9_.]+/[A-Za-z0-9_.$]+')" >> "$D/focus.log"
    sleep 0.4
  done ) &
for n in 1 2 3 4 5 6; do
  [ -f "$D/STOP" ] && break
  echo "$n $(date +%s%3N)" >> "$D/pieces.log"
  tk_adb shell screenrecord --time-limit 170 --bit-rate 12000000 /sdcard/tk-rec-$n.mp4
done
touch "$D/STOP"; wait
bash "$H/rec-pull.sh"
ls -la "$D"
