#!/bin/bash
# Builds the Chinese data the app ships, android/assets/rime/, from the exact sources pinned below and TitanKeys' schemas
# in native/rime/schemas. Run native/rime/build.sh first (it builds the PC tools this uses), then, in WSL:
#   bash native/rime/data.sh
# The schemas are compiled on the PC (64-bit, little-endian, as the phone is), and only the compiled tables ship: the
# word lists' YAML sources stay out. Where each source comes from and its licence: native/rime/SOURCES.md. Licences, in
# short: rime-cantonese is CC BY 4.0 (its place names ODbL 1.0); the Yale rules (rime-jyutping) and the luna-pinyin, essay
# and prelude data are LGPL-3.0, rebuilt by this script from the sources pinned below; OpenCC's data is Apache-2.0.
set -euo pipefail
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
HERE="$REPO/native/rime"
WORK="${TK_RIME_WORK:-$HOME/build/tk-rime}"
TOOL="$WORK/out-host/rime_tool"
OPENCC_DICT="$WORK/out-host/librime/deps/opencc/src/tools/opencc_dict"
[ -x "$TOOL" ] && [ -x "$OPENCC_DICT" ] || { echo "run native/rime/build.sh first" >&2; exit 1; }

PRELUDE=https://github.com/rime/rime-prelude.git@082425ea0684bca36474415d4a0e8db9b016487e
ESSAY=https://github.com/rime/rime-essay.git@d95eeae056df429ecf993038c325a216f0b28c5b
LUNA=https://github.com/rime/rime-luna-pinyin.git@56b934b099dfbeab842320f13aa8b461a6ab3e42
CANTONESE=https://github.com/rime/rime-cantonese.git@259f0e48bba840c3a2e0d117539e96937f3d89bc

fetch() { # dir url@commit
  local dir="$WORK/src/$1" url="${2%@*}" commit="${2#*@}"
  if [ "$(git -C "$dir" rev-parse HEAD 2>/dev/null)" != "$commit" ]; then
    rm -rf "$dir"; mkdir -p "$dir"
    git -C "$dir" init -q
    git -C "$dir" remote add origin "$url"
    git -C "$dir" fetch -q --depth 1 origin "$commit"
    git -C "$dir" checkout -q FETCH_HEAD
  fi
  test "$(git -C "$dir" rev-parse HEAD)" = "$commit"
}
fetch rime-prelude "$PRELUDE"
fetch rime-essay "$ESSAY"
fetch rime-luna-pinyin "$LUNA"
fetch rime-cantonese "$CANTONESE"
SRC="$WORK/src"
OCC="$SRC/librime/deps/opencc/data"

D="$WORK/data"
rm -rf "$D"; mkdir -p "$D/shared/opencc" "$D/user" "$D/occ"
cd "$D/shared"
cp "$SRC/rime-prelude/key_bindings.yaml" "$SRC/rime-prelude/punctuation.yaml" "$SRC/rime-prelude/symbols.yaml" .
# default.yaml: rime-prelude's, with only TitanKeys' schemas and 8 choices a page.
python3 -I - "$SRC/rime-prelude/default.yaml" default.yaml <<'PY'
import re, sys
s = open(sys.argv[1], encoding='utf-8').read()
s, n = re.subn(r'schema_list:\n(  - schema: .*\n)+',
               'schema_list:\n  - schema: tk_yale\n  - schema: tk_jyutping\n  - schema: tk_pinyin\n', s)
assert n == 1
s, n = re.subn(r'page_size: 5', 'page_size: 8', s)
assert n == 1
open(sys.argv[2], 'w', encoding='utf-8').write(s)
PY
cp "$SRC/rime-essay/essay.txt" .
cp "$SRC/rime-luna-pinyin/luna_pinyin.dict.yaml" "$SRC/rime-luna-pinyin/pinyin.yaml" .
cp "$SRC"/rime-cantonese/jyut6ping3*.dict.yaml "$SRC/rime-cantonese/essay-cantonese.txt" \
  "$SRC/rime-cantonese/symbols_cantonese.yaml" .
cp "$HERE"/schemas/*.yaml .

# OpenCC: the three variants' configs, and only the dictionaries they use, compiled.
cp "$OCC/config/t2s.json" "$OCC/config/t2hk.json" "$OCC/config/t2tw.json" "$HERE/schemas/tk_t2twp.json" \
  "$SRC/rime-cantonese/opencc/t2hkf.json" "$SRC/rime-cantonese/opencc/HKVariantsFull.txt" opencc/
for n in CJK_Compatibility_Ideographs TSPhrases TSCharacters TWVariantsPhrases TWVariants HKVariantsPhrases \
  HKVariants TWPhrases; do cp "$OCC/dictionary/$n.txt" "$D/occ/"; done
(cd "$D/occ" && python3 -I "$OCC/scripts/extract_tofu_risk.py" TSCharacters.txt TSCharactersExt.txt)
for f in "$D"/occ/*.txt; do "$OPENCC_DICT" -i "$f" -o "opencc/$(basename "$f" .txt).ocd2" -f text -t ocd2; done

# Rime records each source file's time in what it compiles, and compiles again when a file is newer. Every file gets one
# fixed time, so two runs give the same bytes, and the app gives its copies the same time (RimeData.SOURCE_TIME), so Rime on
# the phone finds nothing changed.
SOURCE_TIME=1763251200
find "$D/shared" -type f -exec touch -h -d "@$SOURCE_TIME" {} +

# Compile every schema, then keep the compiled tables beside the shared files, as the app copies them.
"$TOOL" deploy "$D/shared" "$D/user" 2> "$D/deploy.log" || { cat "$D/deploy.log"; exit 1; }
mkdir -p "$D/shared/build"
for f in "$D"/user/build/*; do
  case "$(basename "$f")" in *.table.bin|*.reverse.bin|*.prism.bin|*.schema.yaml|default.yaml) cp "$f" "$D/shared/build/";; esac
done
for t in jyut6ping3.table.bin luna_pinyin.table.bin tk_yale.prism.bin tk_jyutping.prism.bin tk_pinyin.prism.bin; do
  test -s "$D/shared/build/$t" || { echo "missing $t" >&2; exit 1; }
done
# The word lists' sources don't ship: Rime uses the compiled tables.
rm -f "$D"/shared/*.dict.yaml "$D"/shared/essay*.txt

# What ships.
OUT="$REPO/android/assets/rime"
rm -rf "$OUT"; mkdir -p "$OUT"
cp -r "$D/shared/." "$OUT/"

# The shipped data, tried as the phone uses it: copied with every file at SOURCE_TIME, as RimeData copies it, and a fresh
# user folder. Rime must use the shipped tables and compile nothing of its own.
rm -rf "$D/phone"; cp -r "$OUT" "$D/phone"
find "$D/phone" -type f -exec touch -h -d "@$SOURCE_TIME" {} +
check() { # schema variant code first-choice
  local got
  rm -rf "$D/try"; mkdir -p "$D/try"
  got="$("$TOOL" try "$D/phone" "$D/try" "$1" "$2" "$3" 1 2>/dev/null | sed 's/^[^:]*: //')"
  [ "$got" = "$4" ] || { echo "FAIL $1 $2 '$3': '$got', expected '$4'" >&2; exit 1; }
  local compiled; compiled="$(ls "$D/try/build" 2>/dev/null | grep -E '\.(bin)$' || true)"
  [ -z "$compiled" ] || { echo "FAIL Rime compiled again on first use: $compiled" >&2; exit 1; }
}
check tk_yale zh_hk "nei hou" 你好
check tk_yale zh_hans "jung man" 中文
check tk_yale zh_hk "deui m jyu" 對唔住
check tk_jyutping zh_hk "gwong dung waa" 廣東話
check tk_jyutping zh_hans "gwong dung waa" 广东话
check tk_jyutping zh_hk "neixxhoux" 你好
check tk_pinyin zh_hans "ruan jian" 软件
check tk_pinyin zh_hk "ruan jian" 軟件
check tk_pinyin zh_tw "ruan jian" 軟體
check tk_pinyin zh_hans "da yin ji" 打印机
check tk_pinyin zh_tw "da yin ji" 印表機

cd "$REPO"
# The list the app copies by (RimeData), and that tools/checks/rime.test.ts checks the shipped data against.
(cd android/assets/rime && find . -type f | LC_ALL=C sort | sed 's#^\./##' | xargs sha256sum) > android/assets/rime.sha256
echo "built android/assets/rime ($(du -sb android/assets/rime | cut -f1) bytes, $(wc -l < android/assets/rime.sha256) files)"
