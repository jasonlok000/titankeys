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

bun run notes
exec bun run build "$@"
