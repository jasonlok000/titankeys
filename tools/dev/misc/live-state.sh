#!/usr/bin/env bash
# Read-only snapshot of the keyboard's state on the phone: keyboards, focus, the input method service, the keyboard's windows
# and recent keyboard log lines. Changes nothing on the phone.
. "$(dirname "$0")/../env.sh"
echo "--- replay or instrumentation running on the phone"
tk_adb shell ps -A -o PID,STIME,TIME,ARGS | grep -E 'com\.titankeys|com\.atlas|instrument' | grep -v grep | cut -c1-90
echo "--- keyboards"
echo "current: $(tk_adb shell settings get secure default_input_method | tr -d '\r')"
tk_adb shell ime list -s | tr -d '\r' | tr '\n' ' '; echo
echo "--- focus"
tk_adb shell dumpsys window | grep -E 'mCurrentFocus|mFocusedApp' | tr -s ' ' | cut -c1-120
echo "--- input method service"
tk_adb shell dumpsys input_method | grep -E 'mCurMethodId|mCurId=|mInputShown|mVisibleBound|mShowRequested|mImeWindowVis|mCurFocusedWindowSoftInputMode|mCurClient|mBoundToMethod|mHaveConnection|packageName=|inputType=|imeOptions=' | tr -s ' ' | cut -c1-130 | head -24
echo "--- keyboard window"
tk_adb shell dumpsys window windows | grep -E 'Window #.*(InputMethod|TitanKeysInputMethodTouchpad)' | tr -s ' ' | cut -c1-110
tk_adb shell dumpsys window windows | awk '/Window #.*u0 InputMethod/{f=1;c=0} f{c++; if ($0 ~ /isVisible|mHasSurface|mViewVisibility|isOnScreen|mDrawState|mAttrs=/) print; if (c>70) f=0}' | tr -s ' ' | cut -c1-140 | head -12
echo "--- recent log (keyboard related)"
tk_adb logcat -d -t 1500 | grep -E 'TitanKeys|TouchpadSwipe|com\.titankeys|FATAL|ANR ' | grep -vE 'mali_platform|CoreBackPreview' | tail -28 | cut -c1-170
