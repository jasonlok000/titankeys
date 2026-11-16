#!/usr/bin/env bash
# Read-only: the phone's display size, density and font scale, and which TitanKeys builds are installed.
. "$(dirname "$0")/../env.sh"
echo "size:    $(tk_adb shell wm size | tr -d '\r' | tr '\n' ' ')"
echo "density: $(tk_adb shell wm density | tr -d '\r' | tr '\n' ' ')"
echo "font scale: $(tk_adb shell settings get system font_scale | tr -d '\r')"
echo "display zoom base: $(tk_adb shell getprop ro.sf.lcd_density | tr -d '\r')"
tk_adb shell dumpsys window displays | tr -d '\r' | grep -E 'init=|cur=' | head -2 | tr -s ' ' | cut -c1-140
for package in $(tk_adb shell pm list packages | tr -d '\r' | grep -iE 'titankeys|atlas' | sed 's/package://'); do
  echo "$package: $(tk_adb shell dumpsys package "$package" | tr -d '\r' | grep -E 'versionName|lastUpdateTime' | tr -s ' ' | tr '\n' ' ' | cut -c1-120)"
done
