#!/usr/bin/env bash
# Runs one command on a checkout in its own throwaway build copy, beside the shared Ink, without the verify lock, so several
# people or agents can test at once. The checkout itself is never edited. Runs in WSL.
# Usage: try.sh <checkout path> <command...>
# Example: bash tools/dev/build/try.sh "$PWD" bash tests/words/run.sh
# For the full check, use verify.sh build.
H="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$H/../env.sh"
export KOTLIN_HOME="$TK_BUILD/kotlin-2.1.20/kotlinc"
export PATH="$H:$HOME/android-sdk/build-tools/36.0.0:$PATH"
W="${1:?checkout path}"; shift
SEED="${TK_SEED:-$TK_BUILD/titankeys}"
S="$TK_BUILD/tk-tools"
mkdir -p "$S"
# At most TK_TRY_SLOTS runs at once (default 4, for 15 GB of RAM): take a free slot, or wait for one.
slots="${TK_TRY_SLOTS:-4}"
for i in $(seq 1 "$slots"); do
  exec 8>"$S/.try-$i.lock"
  flock -n 8 && break
  [ "$i" = "$slots" ] && { echo "waiting for a free test slot"; flock 8; }
done
. "$H/ink.sh"
# The copy, in a folder of its own beside the Ink for its patch (ink.sh); an older $TK_BUILD/tk-try-<name> moves there.
B=$(tk_ink_copy_dir "try-$(basename "$W")" "$TK_BUILD/tk-try-$(basename "$W")")
for f in node_modules .ink signing; do [ -e "$B/$f" ] || cp -a "$SEED/$f" "$B/"; done
find "$B" -mindepth 1 -maxdepth 1 ! -name node_modules ! -name .ink ! -name signing ! -name dist -exec rm -rf {} +
tar -C "$W" --exclude=.git --exclude=node_modules --exclude=dist --exclude=.ink --exclude=signing --exclude=tools/dev/local.env -cf - . | tar -xf - -C "$B"
cd "$B" || exit 1
python3 "$H/compat.py" > /dev/null || { echo "FAIL public Ink edits"; exit 1; }
# The Ink for this tree's own patch as ../ink, as verify.sh has: try.sh used to build against whatever the shared Ink had.
tk_ink_use "$B" || exit 1
"$@"
