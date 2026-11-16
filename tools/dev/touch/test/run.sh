#!/usr/bin/env bash
# The touch simulator's offline checks: no phone. Needs a JDK, a C compiler and python3.
set -euo pipefail
cd "$(dirname "$0")/../../../.."
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
javac -d "$work" android/kotlin/com/titankeys/engine/TouchAlignment.java tools/dev/touch/test/AlignmentDump.java
java -cp "$work" com.titankeys.engine.AlignmentDump android/assets/calibration/titan-2-saved.profile > "$work/alignment.txt"
cc -O2 -Wall -Wextra -Werror -o "$work/tk-inject" tools/dev/touch/inject.c
python3 tools/dev/touch/test/test_touch.py "$work/alignment.txt" "$work/tk-inject"
