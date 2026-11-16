#!/usr/bin/env bash
# Builds the Play bundle (.aab) from a checkout, with Julian's private Ink SDK. Runs in WSL.
# Usage: play-bundle.sh [checkout] [--local]   (default checkout: the repo this script sits in)
# The private Ink is not public and never goes in this repo. It is expected at $TK_PLAY_INK (default ~/build/play/ink),
# patched with tools/ink-bundle.patch. The bundle is built in a throwaway copy at $TK_BUILD/play/tk.
set -uo pipefail
H="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$H/../env.sh"
export KOTLIN_HOME="$TK_BUILD/kotlin-2.1.20/kotlinc"
export PATH="$HOME/android-sdk/build-tools/36.0.0:$PATH"
INK="$TK_PLAY_INK"
B="$TK_BUILD/play/tk"
W="${1:-$TK_REPO}"
SEED="${TK_SEED:-$TK_BUILD/titankeys}"
[ -d "$INK/packages/ink" ] || { echo "The private Ink SDK isn't at $INK. Set TK_PLAY_INK in tools/dev/local.env."; exit 1; }
# Ink's ignored node_modules come from the shared public checkout, read only.
cd "$TK_BUILD/ink" || exit 1
for dir in $(git status --short --ignored | awk '$1 == "!!" && $2 ~ /node_modules\/$/ {print $2}'); do
  [ -e "$INK/$dir" ] || { mkdir -p "$INK/$(dirname "$dir")"; ln -s "$TK_BUILD/ink/$dir" "$INK/${dir%/}"; }
done
mkdir -p "$TK_BUILD/tk-tools"
exec 9>"$TK_BUILD/tk-tools/.lock"
flock 9
mkdir -p "$B"
for f in node_modules .ink signing; do [ -e "$B/$f" ] || cp -a "$SEED/$f" "$B/"; done
find "$B" -mindepth 1 -maxdepth 1 ! -name node_modules ! -name .ink ! -name signing ! -name dist -exec rm -rf {} +
tar -C "$W" --exclude=.git --exclude=node_modules --exclude=dist --exclude=.ink --exclude=signing --exclude=tools/dev/local.env -cf - . | tar -xf - -C "$B"
# The app's Ink package must be the private SDK's, or Ink refuses to build.
rm -rf "$B/node_modules/ink" && ln -s "$INK/packages/ink" "$B/node_modules/ink"
cd "$B" || exit 1
python3 "$H/compat.py"
time bash tools/play.sh ${2:-}
echo "play exit $?"
ls -l dist/*.aab
