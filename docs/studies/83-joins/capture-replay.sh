#!/usr/bin/env bash
# Issue #83: reads every labelled gap of the owner's touchpad session three ways on the phone (joined, first alone,
# second alone) with the app's recogniser, then scores the combined vetoes (combine.py) on the logs and the session.
#
# For the phone queue. Run in WSL atlas-build as dev, with the phone connected over adb and awake:
#   bash /mnt/x/Atlas/.claude/worktrees/<this worktree>/docs/studies/83-joins/capture-replay.sh
#
# It's CPU only on the phone: it adds no input, doesn't touch the keyboard or its settings, and waits for the shared
# phone lock like the other replays. About 610 readings; allow 5 to 10 minutes.
# Output: ~/build/joins83/capture/combine-report.txt, numbers only. Paste its contents into the report.
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
repo=$(cd "$here/../../.." && pwd)
base=$repo/tools/dev/swipe-baseline
capture=${CAPTURE:-$HOME/tk-data/joins-capture-20261116-1633/touchpad.txt}
OUT=${OUT:-$HOME/build/joins83}
P=$OUT/private
C=$OUT/capture
py=${PY:-$HOME/build/apostrophe-venv/bin/python3}
for f in attempts.jsonl pairs.jsonl new-rule.txt replay-out.jsonl context-phone-pairs.jsonl; do
  [ -s "$P/$f" ] || { echo "Missing $P/$f: run run.sh, phone-replay.sh and phone_context.py first"; exit 2; }
done
mkdir -p "$C"
# 1. The readings to make (no words: the session has no text).
"$py" -I "$here/combine.py" export "$capture" "$repo" "$C"
# 2. The phone: the recogniser from this tree reads each one.
bash "$base/tools/replay.sh" "$repo" "$C/capture-corpus.jsonl" "$C/capture-replay-out.jsonl"
# 3. The score.
"$py" -I "$here/combine.py" score "$capture" "$repo" "$C" "$P" "$C/capture-replay-out.jsonl" | tee "$C/combine-report.txt"
echo "wrote $C/combine-report.txt"
