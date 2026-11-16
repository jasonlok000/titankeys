# The Ink each build uses, sourced by verify.sh and try.sh after ../env.sh. Runs in WSL.
#
# TitanKeys builds against Ink with tools/ink/titankeys.patch applied, and the patch changes from branch to branch. One shared Ink
# would flip between patches whenever branches with different ones build, and a build could find the other one's. So each
# patch gets its own Ink, $TK_BUILD/ink-<hash>: a copy of the clean checkout $TK_BUILD/ink (with its Rust build cache, so a
# rebuild starts from it) with that patch applied, made once. Each build copy sits in a folder of its own,
# $TK_BUILD/copies/<name>/tk, beside a link "ink" to the Ink for its own patch, so the tree's ../ink is right without a change
# to the tree. $TK_BUILD/ink itself is never patched here.
#
# A build holds a shared lock on its Ink until it ends; only an Ink no build holds is ever deleted, keeping the newest
# TK_INK_KEEP (default 4).
TK_INK_BASE="${TK_INK_BASE:-$TK_BUILD/ink}"

# Sets TK_INK_DIR to the Ink folder for the patch file $1 (none: clean Ink), making it if needed, and holds it (shared) on
# descriptor 6 until this script ends. Not in $(...): the hold would end with that subshell.
tk_ink_for() {
  local patch="$1" hash dir tmp
  if [ -f "$patch" ]; then hash=$(sha256sum < "$patch" | cut -c1-12); else hash=clean; fi
  dir="$TK_BUILD/ink-$hash"
  exec 6>"$dir.lock"
  # Made already: hold it shared at once, so builds of the same patch run side by side. (If it was pruned meanwhile, make it.)
  if [ -f "$dir/.ready" ]; then
    flock -s 6
    [ -f "$dir/.ready" ] && { TK_INK_DIR="$dir"; return 0; }
    flock -u 6
  fi
  flock -x 6
  if [ ! -f "$dir/.ready" ]; then
    echo "making the Ink for patch $hash (once): $dir" >&2
    tmp="$dir.tmp.$$"
    rm -rf "$dir" "$tmp"
    cp -a "$TK_INK_BASE" "$tmp" \
      && git -C "$tmp" checkout -q -- . && git -C "$tmp" clean -fdq \
      && { [ ! -f "$patch" ] || git -C "$tmp" apply "$patch"; } \
      && echo "$hash" > "$tmp/.titankeys-patch" && touch "$tmp/.ready" && mv "$tmp" "$dir" \
      || { rm -rf "$tmp"; flock -u 6; echo "FAIL Ink patch: tools/ink/titankeys.patch doesn't apply to clean Ink" >&2; return 1; }
    tk_ink_prune "$dir"
  fi
  # Shared from here: other builds of the same patch use it too, and nothing deletes it until they end.
  flock -s 6
  TK_INK_DIR="$dir"
}

# Deletes the oldest Inks beyond TK_INK_KEEP, but only those no build holds.
tk_ink_prune() {
  local keep="${TK_INK_KEEP:-4}" old
  # Only this tool's own Inks, ink-<12 hex> or ink-clean: other folders named ink-something are never touched.
  for old in $(ls -dt "$TK_BUILD"/ink-*/ 2>/dev/null | sed 's#/$##' | grep -E "/ink-([0-9a-f]{12}|clean)$" | tail -n +$((keep + 1))); do
    [ "$old" = "$1" ] && continue
    # A build holds it: kept, and that's no failure.
    ( flock -n -x 5 && rm -rf "$old" ) 5>"$old.lock" || true
  done
}

# Points the build copy $1 (in a folder of its own) at the Ink for its own patch: its ../ink, and the links bun made in its
# node_modules into whichever Ink it was installed from.
tk_ink_use() {
  local copy="$1" dir link target rest
  tk_ink_for "$copy/tools/ink/titankeys.patch" || return 1
  dir="$TK_INK_DIR"
  # Built against another Ink: its build outputs (.ink/build, Kotlin's incremental caches with them) no longer fit, and kept they
  # can fail a build with two copies of a moved function. A copy that changes Ink starts that build afresh.
  if [ "$(readlink "$(dirname "$copy")/ink" 2>/dev/null)" != "$dir" ] && [ -d "$copy/.ink/build" ]; then
    rm -rf "$copy/.ink/build"
    echo "new Ink for this copy: its old build outputs cleared" >&2
  fi
  ln -sfn "$dir" "$(dirname "$copy")/ink"
  find "$copy/node_modules" -maxdepth 4 -type l -lname "$TK_BUILD/ink*" 2>/dev/null | while read -r link; do
    target=$(readlink "$link")
    rest="${target#"$TK_BUILD"/}"
    rest="${rest#*/}"
    ln -sfn "$dir/$rest" "$link"
  done
  echo "Ink: $dir" >&2
}

# A build copy's folder of its own, moving an older copy there (to keep its node_modules) and leaving a link at the old path.
tk_ink_copy_dir() {
  local name="$1" old="$2" copy="$TK_BUILD/copies/$1/tk"
  mkdir -p "$TK_BUILD/copies/$name"
  if [ -n "$old" ] && [ -d "$old" ] && [ ! -L "$old" ] && [ ! -e "$copy" ]; then mv "$old" "$copy"; fi
  mkdir -p "$copy"
  [ -n "$old" ] && [ ! -e "$old" ] && ln -sfn "$copy" "$old"
  echo "$copy"
}
