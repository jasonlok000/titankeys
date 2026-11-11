#!/usr/bin/env bash
# Checks that a failed import of Your words tells a file that is no backup from a damaged backup, which doesn't need Android.
set -euo pipefail
cd "$(dirname "$0")/../.."
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
javac --release 17 -d "$work" android/kotlin/com/atlas/engine/BackupFormat.java tests/backup/BackupFormatChecks.java
java -cp "$work" com.atlas.engine.BackupFormatChecks
