#!/usr/bin/env bash
# Cangjie, Quick and Zhuyin on a public text (tests/rime/codes.py): each character of Tatoeba's Cantonese sentences typed by
# its code through native/rime/build.sh's rime_tool, with the shipped data. Needs data.sh's sources and Tatoeba's export
# (https://downloads.tatoeba.org/exports/per_language/yue/yue_sentences.tsv.bz2, CC BY 2.0 FR), neither in the repo.
# Usage: bash tests/rime/codes.sh <yue_sentences.tsv> [characters]
set -euo pipefail
cd "$(dirname "$0")/../.."
work=${TK_RIME_WORK:-$HOME/build/tk-rime}
python3 -I tests/rime/codes.py "$work/out-host/rime_tool" android/assets/rime "$work/src" "$1" "${2:-600}"
