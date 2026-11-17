#!/usr/bin/env bash
# Builds every input method's next-word table (tools/im/next_words.py) from the Leipzig Corpora Collection's sentences, and
# writes each folder's NOTICE line. tools/im/next_words_corpora.json names the corpus for each input method.
#
#   bash tools/im/next_words_all.sh [Leipzig folder] [ids...]
#
# The Leipzig folder (default ~/build/lang/leipzig) holds <corpus>-sentences.txt, taken from
# https://downloads.wortschatz-leipzig.de/corpora/<corpus>.tar.gz (CC BY 4.0). It stays outside the repository.
# Belarusian needs the word list from claude/m3-be-words (the one before stopped alphabetically at "б").
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
repo=$(cd "$here/../.." && pwd)
leipzig=${1:-$HOME/build/lang/leipzig}
shift || true
im=$repo/android/assets/im
skip=" "
python3 -c "import json,sys; [print(k, v) for k, v in json.load(open(sys.argv[1])).items()]" "$here/next_words_corpora.json" |
while read -r id corpus; do
  if [ $# -gt 0 ] && [[ " $* " != *" $id "* && " $* " != *" ${id%%-*} "* ]]; then continue; fi
  [[ "$skip" == *" $id "* ]] && { echo "$id: skipped"; continue; }
  text=$leipzig/$corpus-sentences.txt
  [ -s "$text" ] || { echo "$id: missing $text"; continue; }
  case $id in
    pt-BR|pt-PT) folder=pt; python3 -I "$here/next_words.py" build pt "$text" --words "$im/pt/words-$id.combined" --out "$im/pt/pairs-$id.bin"; file=pairs-$id.bin ;;
    sr) folder=sr; python3 -I "$here/next_words.py" build sr "$text"; file=pairs.bin
        python3 -I "$here/next_words.py" build sr-latn "$text"
        python3 -I "$here/next_words.py" notice sr-latn "$corpus" pairs.bin "written in Latin letters letter for letter, as its word list" ;;
    *) folder=$id; python3 -I "$here/next_words.py" build "$id" "$text"; file=pairs.bin ;;
  esac
  python3 -I "$here/next_words.py" notice "$folder" "$corpus" "$file" ""
done
