#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

# A git worktree has no signing folder, so it uses the one in the main checkout.
main=$(cd "$(git rev-parse --git-common-dir)/.." && pwd)
[[ ! -e signing && -d "$main/signing" ]] && ln -s "$main/signing" signing

# CI can provide credentials directly; local builds use the ignored signing folder.
if [[ -z "${INK_KEYSTORE_PASSWORD:-}" && -f signing/credentials.env ]]; then
  source signing/credentials.env
fi
: "${INK_KEYSTORE_PASSWORD:?Set INK_KEYSTORE_PASSWORD or restore signing/credentials.env}"
export INK_KEYSTORE_PASSWORD
export INK_KEY_PASSWORD="${INK_KEY_PASSWORD:-$INK_KEYSTORE_PASSWORD}"

bun run notes
# Add the "Bug fixes" line, as the GitHub release does, then put the notes back after the build.
cp release-notes.txt release-notes.txt.bak
trap 'mv release-notes.txt.bak release-notes.txt' EXIT
bun .github/ink-release.mjs bug-fixes
bun run build "$@"
