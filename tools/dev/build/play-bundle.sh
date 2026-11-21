#!/usr/bin/env bash
# Builds the Play bundle (.aab) from a checkout. Runs in WSL.
# Usage: play-bundle.sh [checkout] [--local]   (default checkout: the repo this script sits in)
# The bundle needs an Ink with both tools/ink/titankeys.patch and tools/ink-bundle.patch (`ink build --bundle`). ink.sh makes
# one from the clean Ink for that pair of patches, keyed by both, so no Ink another build uses is ever changed. The bundle is
# built in a throwaway copy, $TK_BUILD/copies/play/tk, or TK_PLAY_COPY=<path> (in a folder of its own, as ink.sh needs).
set -uo pipefail
H="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$H/../env.sh"
export KOTLIN_HOME="$TK_BUILD/kotlin-2.1.20/kotlinc"
export PATH="$HOME/android-sdk/build-tools/36.0.0:$PATH"
W="${1:-$TK_REPO}"
SEED="${TK_SEED:-$TK_BUILD/titankeys}"
. "$H/ink.sh"
[ -f "$W/tools/ink-bundle.patch" ] || { echo "$W has no tools/ink-bundle.patch"; exit 1; }
mkdir -p "$TK_BUILD/tk-tools"
exec 9>"$TK_BUILD/tk-tools/.lock"
flock 9
B="${TK_PLAY_COPY:-$(tk_ink_copy_dir play "")}"
mkdir -p "$B"
for f in node_modules .ink signing; do [ -e "$B/$f" ] || cp -a "$SEED/$f" "$B/"; done
find "$B" -mindepth 1 -maxdepth 1 ! -name node_modules ! -name .ink ! -name signing ! -name dist -exec rm -rf {} +
tar -C "$W" --exclude=.git --exclude=node_modules --exclude=dist --exclude=.ink --exclude=signing --exclude=tools/dev/local.env -cf - . | tar -xf - -C "$B"
cd "$B" || exit 1
python3 "$H/compat.py" || { echo "FAIL public Ink edits"; exit 1; }
tk_ink_use "$B" "$B/tools/ink-bundle.patch" || exit 1
# The app's Ink package must be the one in this Ink, or Ink refuses to build.
rm -rf "$B/node_modules/ink" && ln -s "$TK_INK_DIR/packages/ink" "$B/node_modules/ink"
time bash tools/play.sh ${2:-}
status=$?
echo "play exit $status"
ls -l dist/*.aab 2>/dev/null
exit "$status"
