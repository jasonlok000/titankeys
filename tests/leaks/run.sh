#!/usr/bin/env bash
# Checks tools/leak-paths.py on a JVM heap dump with known leaks (LeakFixture.java): it names the static list and the listener
# that hold a destroyed GuideActivity, skips one held only weakly and the open one, and prints no string's contents.
set -euo pipefail
cd "$(dirname "$0")/../.."
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
javac -d "$work" tests/leaks/LeakFixture.java
java -cp "$work" com.titankeys.keyboard.LeakFixture "$work/heap.hprof"
out=$(python3 tools/leak-paths.py "$work/heap.hprof")
all=$(python3 tools/leak-paths.py "$work/heap.hprof" --all)
echo "$out"
fail() { echo "FAIL: $1"; exit 1; }
grep -q "^2 destroyed" <<<"$out" || fail "two destroyed guides"
grep -q "class com.titankeys.keyboard.Registry" <<<"$out" || fail "the root is Registry's class"
grep -q "static listeners" <<<"$out" || fail "the static list is named"
grep -q "held only by weak references" <<<"$out" || fail "the weakly held one is free"
grep -q "^3 GuideActivity\|^3 com.titankeys.keyboard.GuideActivity" <<<"$all" || fail "--all counts the open one"
grep -q "SECRET" <<<"$out$all" && fail "a string's contents were printed"
echo "PASS: leak paths"
