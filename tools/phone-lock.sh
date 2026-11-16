# Sourced by every script that talks to the phone: it holds the phone lock for the rest of that script, so two phone runs never
# overlap (two sets of keys, taps or installs at once would trip each other, or worse). Builds take a lock of their own,
# $TK_BUILD/tk-tools/.lock, and never this one, so a long phone run doesn't hold up a build, nor a build a phone run.
#
# The lock is the maintainer's: $TK_PHONE_LOCK, by default ~/build/tk-tools/.phone.lock. Where that folder doesn't exist, as on a
# contributor's computer, there is nothing to wait for, and this does nothing.
# - A script started by one that holds it (TK_PHONE_LOCKED=1) doesn't take it again, which would wait for itself.
# - TK_PHONE_LOCK_WAIT=SECONDS waits at most that long, then says "phone busy" and the script ends with 3.
# - It's held on file descriptor 7 until the script ends; nothing needs to give it back.
tk_phone_lock() {
  [ "${TK_PHONE_LOCKED:-}" = 1 ] && return 0
  local lock="${TK_PHONE_LOCK:-${TK_BUILD:-$HOME/build}/tk-tools/.phone.lock}"
  [ -d "$(dirname "$lock")" ] || return 0
  # The adb server starts first, with no lock open: one started while a lock (this or the build lock) is open would inherit
  # it, and hold it after this script ends.
  command -v adb >/dev/null 2>&1 && adb start-server >/dev/null 2>&1 7>&- 8>&- 9>&-
  exec 7>"$lock"
  if ! flock -n 7; then
    echo "waiting for the phone: another phone run holds $lock" >&2
    if [ -n "${TK_PHONE_LOCK_WAIT:-}" ]; then
      flock -w "$TK_PHONE_LOCK_WAIT" 7 || { echo "phone busy"; return 3; }
    else
      flock 7
    fi
  fi
  export TK_PHONE_LOCKED=1
}
tk_phone_lock || exit 3
