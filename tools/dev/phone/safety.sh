# Safety checks shared by the phone tools. Sourced after ../env.sh. Sourcing it also takes the phone lock
# (tools/phone-lock.sh), held until the tool ends: no two phone runs overlap. Builds use another lock.
. "$TK_REPO/tools/phone-lock.sh"
# The rules every phone tool keeps:
# - Stop if a navigation app is running in the foreground: the phone may be in use in a car.
# - Check the front window before every key or tap, so input never lands in another app.
# - Never force-stop TitanKeys (am force-stop): it turns its Accessibility service off and moves the phone to another keyboard.
# - Crop the status bar off screenshots: it can show notifications.
TK_NAV_APPS="${TK_NAV_APPS:-com.waze com.google.android.apps.maps}"

tk_front() { tk_adb shell dumpsys window | grep -m1 mCurrentFocus | tr -d '\r'; }

tk_no_navigation() {
  local n
  n=$(tk_adb shell dumpsys activity services $TK_NAV_APPS | grep -c "isForeground=true")
  [ "$n" = "0" ] || { echo "STOP: navigation"; return 1; }
}

# tk_require_front PKG: fails unless PKG's window is in front.
tk_require_front() {
  local f
  f=$(tk_front)
  grep -q "$1/" <<<"$f" || { echo "STOP: front is $f"; return 1; }
}
