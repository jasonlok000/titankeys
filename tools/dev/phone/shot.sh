#!/usr/bin/env bash
# shot.sh NAME: screenshot of the phone with the status bar cropped off, and everything from the keyboard window down, so no
# notification or clipboard text is kept. Writes $TK_OUT/shots/NAME.png (full size) and NAME-half.png, and prints the crop.
# Needs python3 with Pillow.
H="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$H/../env.sh"; . "$H/safety.sh"
name="${1:?name}"
D="$TK_OUT/shots"; mkdir -p "$D"
tk_no_navigation || exit 1
tk_adb exec-out screencap -p > "$D/raw-$name.png"
win=$(tk_adb shell dumpsys window)
sb=$(grep -m1 -oE "statusBars.*frame=\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]" <<<"$win" | grep -oE "\]\[[0-9]+,[0-9]+\]" | grep -oE ",[0-9]+" | tr -d ,)
ime=$(grep -m1 -E "type=ime frame.*visible=true" <<<"$win" | grep -oE "frame=\[[0-9]+,[0-9]+\]" | grep -oE ",[0-9]+" | tr -d ,)
vis=$(tk_adb shell dumpsys input_method | grep -m1 -oE "mInputShown=(true|false)")
echo "statusbar_bottom=$sb ime_top=$ime $vis"
# An uncropped screenshot is never kept: if the crop fails (no Pillow), the raw file goes.
python3 "$H/crop.py" "$D/raw-$name.png" "${sb:-40}" "${ime:-0}" "${vis:-mInputShown=false}" \
  || { rm -f "$D/raw-$name.png"; echo "Crop failed, raw screenshot deleted. Install Pillow: sudo apt install python3-pil"; exit 1; }
