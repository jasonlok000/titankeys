#!/usr/bin/env bash
# Issue #83: reads every swipe pair that could join on the phone, three ways (joined, first alone, second alone), with
# the app's recogniser, and scores the new joining rule against what the owner kept.
#
# For the phone tester. Run in WSL atlas-build as dev, with the phone connected over adb and awake:
#   bash /mnt/x/Atlas/.claude/worktrees/<this worktree>/docs/studies/83-joins/phone-replay.sh
#
# It's CPU only on the phone: it adds no input, doesn't touch the keyboard or its settings, and waits for the shared
# phone lock (~/build/tk-tools/.phone.lock) like the other replays. It runs about 1,950 readings; allow 10 to 20 minutes.
# The report also runs Composer's 150 ms check of both readings on every pair within 150 ms ("check joins ..."), as
# before #83, now, and with a stricter lead of 2, so it shows whether the check glues quick separate words.
# It leaves the runner's models in /data/local/tmp/tk-baseline, as the baseline replays do.
# Output: ~/build/joins83/replay-report.json, numbers only. Paste that file's contents into the report.
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
repo=$(cd "$here/../../.." && pwd)
# The repo's own copy of the replay tools (#82), which follows the renamed packages; the old .dev copy doesn't.
base=$repo/tools/dev/swipe-baseline
OUT=${OUT:-$HOME/build/joins83}
P=$OUT/private
for f in attempts.jsonl pairs.jsonl live-rule.txt new-rule.txt; do
  [ -s "$P/$f" ] || { echo "Missing $P/$f: run docs/studies/83-joins/run.sh first"; exit 2; }
done
# 1. The readings to make. Private: it holds the words kept.
python3 -I "$here/replay_pairs.py" export "$P/attempts.jsonl" "$P/pairs.jsonl" "$P/live-rule.txt" "$P/new-rule.txt" "$P/replay-corpus.jsonl"
# 2. The phone: the recogniser from this tree reads each one.
bash "$base/tools/replay.sh" "$repo" "$P/replay-corpus.jsonl" "$P/replay-out.jsonl"
# 3. The score.
python3 -I "$here/replay_pairs.py" score "$P/replay-corpus.jsonl" "$P/replay-out.jsonl" "$P/pairs.jsonl" "$P/live-rule.txt" \
  "$P/new-rule.txt" "$OUT/replay-report.json"
echo "wrote $OUT/replay-report.json"
