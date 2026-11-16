# Sourced by every tool in tools/dev. Loads tools/dev/local.env if it exists, then fills in the defaults.
# Python tools read the same variables, so run them from a shell that sourced this file:  . tools/dev/env.sh
TK_DEV="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TK_REPO="$(cd "$TK_DEV/../.." && pwd)"
if [ -f "$TK_DEV/local.env" ]; then set -a; . "$TK_DEV/local.env"; set +a; fi
: "${TK_ADB_SERIAL:=}" "${TK_ADB_HOST:=}"
: "${TK_WSL_DISTRO:=atlas-build}" "${TK_WSL_USER:=dev}"
: "${TK_BUILD:=$HOME/build}" "${TK_ENV_SCRIPT:=$HOME/titankeys-env.sh}"
: "${TK_PLAY_INK:=$TK_BUILD/play/ink}"
: "${TK_OUT:=$HOME/tk-out}"
: "${TK_PRIVATE:=$HOME/tk-private/swipe-baseline}" "${TK_DATA:=$HOME/tk-data}" "${TK_ORT:=$HOME/tk-private/ort}"
: "${TK_GPU_HOST:=}" "${TK_GPU_KEY:=}"
export TK_DEV TK_REPO TK_ADB_SERIAL TK_ADB_HOST TK_WSL_DISTRO TK_WSL_USER TK_BUILD TK_ENV_SCRIPT TK_PLAY_INK TK_OUT \
  TK_PRIVATE TK_DATA TK_ORT TK_GPU_HOST TK_GPU_KEY
[ -f "$TK_ENV_SCRIPT" ] && . "$TK_ENV_SCRIPT"
# adb with the phone picked out, for the phone tools.
tk_adb() { if [ -n "$TK_ADB_SERIAL" ]; then adb -s "$TK_ADB_SERIAL" "$@"; else adb "$@"; fi; }
