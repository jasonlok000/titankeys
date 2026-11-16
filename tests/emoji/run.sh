#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
javac --release 17 -d "$work" android/kotlin/com/titankeys/engine/EmojiPages.java tests/emoji/EmojiPagesChecks.java
java -cp "$work" com.titankeys.engine.EmojiPagesChecks android/assets/emoji.txt
