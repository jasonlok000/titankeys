#!/usr/bin/env bash
# Read-only look at how this phone routes the touchpad: overlay permission, the firmware's touchpad properties and settings,
# input devices and the windows that take input. Changes nothing on the phone.
. "$(dirname "$0")/../env.sh"
echo "--- overlay permission"
for p in com.titankeys com.titankeys.debug; do echo "$p: $(tk_adb shell appops get $p SYSTEM_ALERT_WINDOW | tr -d '\r' | cut -c1-70)"; done
echo "--- firmware properties"
tk_adb shell getprop | grep -iE 'touchpad|scroll_assist|forcescroll|agui|sliding|slide_mode' | tr -d '\r' | cut -c1-110 | head -30
echo "--- firmware settings"
for ns in system secure global; do
  tk_adb shell settings list $ns | grep -iE 'touchpad|scroll|sliding|slide|keyboard_mode|agui' | tr -d '\r' | sed "s/^/$ns: /" | cut -c1-240 | head -24
done
echo "--- input devices"
tk_adb shell getevent -pl 2>/dev/null | tr -d '\r' | grep -E '^add device|name:' | paste - - | tr -s ' ' | cut -c1-90
echo "--- touchpad in the input system"
tk_adb shell dumpsys input | tr -d '\r' | grep -nE 'touchPad|TouchPad' | tr -s ' ' | cut -c1-130 | head -12
echo "--- windows with input, top first"
tk_adb shell dumpsys input | tr -d '\r' | awk '/Windows:/{f=1} f&&/name=/{print}' | grep -oE "name='[^']*'|touchableRegion=[^,]*|ownerUid=[0-9]+|flags=[^,]*" | paste - - - - | tr -s ' ' | cut -c1-190 | head -14
