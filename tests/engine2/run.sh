#!/usr/bin/env bash
# Checks swipe engine 2.0's tokenizer, word search, stroke features and ranker. It doesn't need the phone.
set -euo pipefail
cd "$(dirname "$0")/../.."
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
engine=android/kotlin/com/atlas/engine
javac --release 17 -nowarn -d "$work" "$engine/ByteBpe.java" "$engine/CtcWords.java" "$engine/Swipe.java" "$engine/SwipeRanker.java" \
  android/kotlin/org/futo/ml/inference/SwipeDecoder.java tests/engine2/Engine2Checks.java
java -cp "$work" com.atlas.engine.Engine2Checks
