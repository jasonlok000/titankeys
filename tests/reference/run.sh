#!/usr/bin/env bash
# Checks the steps and passages of the reference app, which don't need Android.
set -euo pipefail
cd "$(dirname "$0")/../.."
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
code=android/debug/reference/java/com/titankeys/reference
javac --release 8 -Xlint:-options -d "$work" "$code/Passages.java" "$code/Prompts.java" tests/reference/ReferenceChecks.java
java -cp "$work" com.titankeys.reference.ReferenceChecks
