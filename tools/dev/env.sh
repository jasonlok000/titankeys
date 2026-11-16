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
: "${TK_PRIVATE:=$HOME/tk-private/swipe-baseline}" "${TK_DATA:=$HOME/tk-data}"
# ONNX Runtime's Android classes, which the phone tools compile against: the first known place that has the jar. The main
# checkout's .dev/swipe-baseline/ort is found through git, so a worktree finds it too.
if [ -z "${TK_ORT:-}" ]; then
  # A worktree's .git names the main checkout in Windows' spelling, which git in WSL can't follow: its path does too.
  tk_main=$(git -C "$TK_REPO" rev-parse --path-format=absolute --git-common-dir 2>/dev/null) && tk_main=${tk_main%/.git}
  case "$TK_REPO" in */.claude/worktrees/*) tk_worktree_main=${TK_REPO%%/.claude/worktrees/*} ;; esac
  for tk_place in "$HOME/tk-private/ort" "$TK_REPO/.dev/swipe-baseline/ort" ${tk_main:+"$tk_main/.dev/swipe-baseline/ort"} \
      ${tk_worktree_main:+"$tk_worktree_main/.dev/swipe-baseline/ort"}; do
    if [ -f "$tk_place/onnxruntime-android-1.20.0-classes.jar" ]; then TK_ORT=$tk_place; break; fi
  done
  : "${TK_ORT:=$HOME/tk-private/ort}"
  unset tk_main tk_worktree_main tk_place
fi
: "${TK_GPU_HOST:=}" "${TK_GPU_KEY:=}"
export TK_DEV TK_REPO TK_ADB_SERIAL TK_ADB_HOST TK_WSL_DISTRO TK_WSL_USER TK_BUILD TK_ENV_SCRIPT TK_PLAY_INK TK_OUT \
  TK_PRIVATE TK_DATA TK_ORT TK_GPU_HOST TK_GPU_KEY
[ -f "$TK_ENV_SCRIPT" ] && . "$TK_ENV_SCRIPT"
# adb with the phone picked out, for the phone tools.
tk_adb() { if [ -n "$TK_ADB_SERIAL" ]; then adb -s "$TK_ADB_SERIAL" "$@"; else adb "$@"; fi; }

# A compile step for a phone tool: its output without the routine notes, and on a failure the errors and an end to the
# script, before anything reaches the phone. Usage: tk_compile WHAT COMMAND...
tk_compile() {
  local what=$1 log
  shift
  log=$(mktemp)
  if "$@" >"$log" 2>&1; then
    grep -v -i -E '^Note|warning' "$log" | head -20 || true
    rm -f "$log"
    return 0
  fi
  grep -v '^Note' "$log" | head -40 >&2 || true
  rm -f "$log"
  echo "$what failed: stopped before the phone" >&2
  exit 1
}
