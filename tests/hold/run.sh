#!/usr/bin/env bash
# Checks holding a letter for its symbol, which doesn't need Android or the phone.
set -euo pipefail
cd "$(dirname "$0")/../.."
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
javac --release 17 -d "$work" android/kotlin/com/atlas/engine/HoldEngine.java tests/hold/HoldEngineChecks.java
java -cp "$work" com.atlas.engine.HoldEngineChecks
