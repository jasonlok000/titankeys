#!/usr/bin/env bash
# Checks ink.sh on a small throwaway Ink, no build: two copies with different patches get an Ink each, the clean checkout stays
# clean, node_modules' links follow, an Ink in use is never pruned, and nothing else named ink-* is touched. Runs in WSL.
set -euo pipefail
H="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
export TK_BUILD="$work/build"
mkdir -p "$TK_BUILD"
fail() { echo "FAIL $*"; exit 1; }

# A clean Ink: one tracked file. A patch per branch.
git init -q "$TK_BUILD/ink"
echo "plain" > "$TK_BUILD/ink/core.txt"
git -C "$TK_BUILD/ink" add core.txt
git -C "$TK_BUILD/ink" -c user.name=t -c user.email=t@t commit -qm base
mkdir -p "$TK_BUILD/ink-polish-check"
for b in a b; do
  mkdir -p "$work/tree-$b/tools/ink" "$work/tree-$b/node_modules/ink"
  printf 'diff --git a/core.txt b/core.txt\n--- a/core.txt\n+++ b/core.txt\n@@ -1 +1 @@\n-plain\n+patched %s\n' "$b" \
    > "$work/tree-$b/tools/ink/titankeys.patch"
  ln -s "$TK_BUILD/ink/core.txt" "$work/tree-$b/node_modules/ink/core.txt"
done

. "$H/ink.sh"
for b in a b; do
  copy=$(tk_ink_copy_dir "try-$b" "")
  cp -a "$work/tree-$b/." "$copy/"
  ( tk_ink_use "$copy" 2>/dev/null
    [ "$(cat "$copy/../ink/core.txt")" = "patched $b" ] || fail "copy $b's ../ink doesn't carry its patch"
    [ "$(cat "$copy/node_modules/ink/core.txt")" = "patched $b" ] || fail "copy $b's node_modules link wasn't moved" )
done
[ "$(cat "$TK_BUILD/ink/core.txt")" = plain ] || fail "the clean checkout was patched"
[ "$(ls -d "$TK_BUILD"/ink-*/ | wc -l)" = 3 ] || fail "not one Ink per patch"

# A further patch, as the Play bundle adds, gets an Ink of its own with both, and no other build's Ink changes.
printf 'diff --git a/extra.txt b/extra.txt\nnew file mode 100644\n--- /dev/null\n+++ b/extra.txt\n@@ -0,0 +1 @@\n+bundle\n' \
  > "$work/bundle.patch"
copy=$(tk_ink_copy_dir play "")
cp -a "$work/tree-a/." "$copy/"
( tk_ink_use "$copy" "$work/bundle.patch" 2>/dev/null
  [ "$(cat "$copy/../ink/core.txt")" = "patched a" ] || fail "the bundle's Ink lost the copy's own patch"
  [ "$(cat "$copy/../ink/extra.txt")" = bundle ] || fail "the bundle's Ink doesn't carry the further patch"
  case "$(readlink "$TK_BUILD/copies/play/ink")" in */ink-[0-9a-f]*-[0-9a-f]*) ;; *) fail "the bundle's Ink isn't its own" ;; esac )
[ ! -e "$(readlink "$TK_BUILD/copies/try-a/ink")/extra.txt" ] || fail "the further patch went into another build's Ink"

# Pruning keeps TK_INK_KEEP, never one in use, never another ink-* folder.
( exec 6>"$(ls -d "$TK_BUILD"/ink-[0-9a-f]*/ | head -1 | sed 's#/$##').lock"; flock -s 6
  TK_INK_KEEP=0 tk_ink_prune none
  [ "$(ls -d "$TK_BUILD"/ink-[0-9a-f]*/ | wc -l)" = 1 ] || fail "prune deleted an Ink in use, or kept one nobody holds" )
[ -d "$TK_BUILD/ink-polish-check" ] || fail "prune deleted a folder that isn't its own"
echo "Ink per patch checks passed"
