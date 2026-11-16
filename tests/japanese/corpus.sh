#!/usr/bin/env bash
# The Japanese corpus test (tests/japanese/Corpus.kt): Tatoeba's reviewed Japanese sentences, typed as romaji through
# TitanKeys' Mozc path, against the sentence as written and Mozc's own desktop conversion. Needs native/mozc/build.sh's
# mozc_tool and Tatoeba's transcriptions export (https://downloads.tatoeba.org/exports/transcriptions.tar.bz2, CC BY 2.0 FR),
# neither in the repo. Usage: bash tests/japanese/corpus.sh <transcriptions.csv> [sentences] [seed]
set -euo pipefail
cd "$(dirname "$0")/../.."
kotlin=${KOTLIN_HOME:?Set KOTLIN_HOME to your Kotlin compiler directory}
stdlib=${KOTLIN_STDLIB:-$kotlin/lib/kotlin-stdlib.jar}
tool=${TK_MOZC_WORK:-$HOME/build/tk-mozc}/mozc_tool
[ -x "$tool" ] || { echo "run native/mozc/build.sh first: no $tool" >&2; exit 1; }
work=${TK_CORPUS_WORK:-$HOME/build/tk-mozc/corpus}
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
"$kotlin/bin/kotlinc" -nowarn -no-stdlib -no-reflect -classpath "$stdlib" -d "$classes" \
  android/kotlin/com/titankeys/mozc/KanaRomaji.kt tests/japanese/Corpus.kt
java -cp "$classes:$stdlib" com.titankeys.mozc.CorpusKt "$1" "$tool" "$(pwd)/android/assets/mozc/mozc.data" "$work" "${2:-2000}" "${3:-56}"
