#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

# CI can provide credentials directly; local builds use the ignored signing folder.
if [[ -z "${INK_KEYSTORE_PASSWORD:-}" && -f signing/credentials.env ]]; then
  source signing/credentials.env
fi
: "${INK_KEYSTORE_PASSWORD:?Set INK_KEYSTORE_PASSWORD or restore signing/credentials.env}"
export INK_KEYSTORE_PASSWORD
export INK_KEY_PASSWORD="${INK_KEY_PASSWORD:-$INK_KEYSTORE_PASSWORD}"

# The build uses the Ink checkout beside TitanKeys (../ink), which needs tools/ink/titankeys.patch. Without it a release still
# builds, but loses the accent, autoCapitalise, the press haptic and the Light Phone receiver's removal (release re-audit,
# 2026-11-13). The check passes only when the whole patch is in.
patch="$PWD/tools/ink/titankeys.patch"
if [[ -f "$patch" ]] && ! git -C ../ink apply --reverse --check "$patch" 2>/dev/null; then
  echo "../ink doesn't carry tools/ink/titankeys.patch. Apply it, then build again:" >&2
  echo "  cd ../ink && git checkout -- . && git apply \"$patch\"" >&2
  exit 1
fi

bun run notes
exec bun run build "$@"
