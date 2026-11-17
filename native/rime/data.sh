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
BUILD_PREDICT="$(find "$WORK/out-host" -name build_predict -type f -perm -u+x | head -1)"
[ -x "$TOOL" ] && [ -x "$OPENCC_DICT" ] && [ -n "$BUILD_PREDICT" ] || { echo "run native/rime/build.sh first" >&2; exit 1; }

PRELUDE=https://github.com/rime/rime-prelude.git@082425ea0684bca36474415d4a0e8db9b016487e
ESSAY=https://github.com/rime/rime-essay.git@d95eeae056df429ecf993038c325a216f0b28c5b
LUNA=https://github.com/rime/rime-luna-pinyin.git@56b934b099dfbeab842320f13aa8b461a6ab3e42
CANTONESE=https://github.com/rime/rime-cantonese.git@259f0e48bba840c3a2e0d117539e96937f3d89bc
CANGJIE=https://github.com/rime/rime-cangjie.git@fcb3d7dd94ac1304663d575727d24d8107f6b082
QUICK=https://github.com/rime/rime-quick.git@207d1efcc0dc9574510b61be861544edcf993736
TERRA=https://github.com/rime/rime-terra-pinyin.git@723e51bc266cf9464530c1ddedb856aa18e3da34

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
fetch rime-cangjie "$CANGJIE"
fetch rime-quick "$QUICK"
fetch rime-terra-pinyin "$TERRA"
SRC="$WORK/src"
OCC="$SRC/librime/deps/opencc/data"

D="$WORK/data"
rm -rf "$D"; mkdir -p "$D/shared/opencc" "$D/user" "$D/occ"
cd "$D/shared"
cp "$SRC/rime-prelude/key_bindings.yaml" "$SRC/rime-prelude/punctuation.yaml" "$SRC/rime-prelude/symbols.yaml" .
# default.yaml: rime-prelude's, with only TitanKeys' schemas, and 8 choices a page: the picker's places, E to P.
python3 -I - "$SRC/rime-prelude/default.yaml" default.yaml <<'PY'
import re, sys
s = open(sys.argv[1], encoding='utf-8').read()
s, n = re.subn(r'schema_list:\n(  - schema: .*\n)+',
               'schema_list:\n  - schema: tk_yale\n  - schema: tk_jyutping\n  - schema: tk_pinyin\n  - schema: tk_cangjie\n'
               '  - schema: tk_quick\n  - schema: tk_zhuyin\n', s)
assert n == 1
s, n = re.subn(r'page_size: 5', 'page_size: 8', s)
assert n == 1
open(sys.argv[2], 'w', encoding='utf-8').write(s)
PY
cp "$SRC/rime-essay/essay.txt" .
cp "$SRC/rime-luna-pinyin/luna_pinyin.dict.yaml" "$SRC/rime-luna-pinyin/pinyin.yaml" .
cp "$SRC"/rime-cantonese/jyut6ping3*.dict.yaml "$SRC/rime-cantonese/essay-cantonese.txt" \
  "$SRC/rime-cantonese/symbols_cantonese.yaml" .
# Cangjie 5 and Quick 5 (rime-cangjie's dictionary, which Quick builds on), and Zhuyin's dictionary (rime-terra-pinyin).
cp "$SRC"/rime-cangjie/cangjie5*.dict.yaml "$SRC"/rime-quick/quick5*.dict.yaml "$SRC/rime-terra-pinyin/terra_pinyin.dict.yaml" .
# TitanKeys' few changes to the Cantonese word weights (cantonese-weights.tsv says why): a word's weight replaced, or the
# word added at the end, in the file's order.
awk -F'\t' -v OFS='\t' '
  NR == FNR { if ($0 !~ /^#/ && NF == 2) { weight[$1] = $2; order[++n] = $1 }; next }
  ($1 in weight) { $2 = weight[$1]; seen[$1] = 1 }
  { print }
  END { for (i = 1; i <= n; i++) if (!(order[i] in seen)) print order[i], weight[order[i]] }
' "$HERE/cantonese-weights.tsv" essay-cantonese.txt > essay-cantonese.new
mv essay-cantonese.new essay-cantonese.txt
cp "$HERE"/schemas/*.yaml .

# OpenCC: the three variants' configs, and only the dictionaries they use, compiled.
cp "$OCC/config/t2s.json" "$OCC/config/t2hk.json" "$OCC/config/t2tw.json" "$HERE/schemas/tk_t2twp.json" \
  "$SRC/rime-cantonese/opencc/t2hkf.json" "$SRC/rime-cantonese/opencc/HKVariantsFull.txt" opencc/
for n in CJK_Compatibility_Ideographs TSPhrases TSCharacters TWVariantsPhrases TWVariants HKVariantsPhrases \
  HKVariants TWPhrases; do cp "$OCC/dictionary/$n.txt" "$D/occ/"; done
(cd "$D/occ" && python3 -I "$OCC/scripts/extract_tofu_risk.py" TSCharacters.txt TSCharactersExt.txt)
for f in "$D"/occ/*.txt; do "$OPENCC_DICT" -i "$f" -o "opencc/$(basename "$f" .txt).ocd2" -f text -t ocd2; done

# The next-word suggestions (librime-predict, #56): native/rime/predict.py's tables, compiled by the plugin's own tool.
# Cantonese's for Yale and Jyutping, Mandarin's for Pinyin (each schema's predictor/db).
for lang in yue cmn; do "$BUILD_PREDICT" "predict-$lang.db" < "$HERE/predict/$lang.txt" 2> "$D/predict-$lang.log" \
  || { cat "$D/predict-$lang.log"; exit 1; }; done

# Rime records each source file's time in what it compiles. Every file gets one fixed time, so two runs give the same bytes.
# On the phone Rime never deploys (rime_jni.cc), so the copies' own times don't matter.
SOURCE_TIME=1763251200
find "$D/shared" -type f -exec touch -h -d "@$SOURCE_TIME" {} +

# Compile every schema, then keep the compiled tables beside the shared files, as the app copies them.
"$TOOL" deploy "$D/shared" "$D/user" 2> "$D/deploy.log" || { cat "$D/deploy.log"; exit 1; }
mkdir -p "$D/shared/build"
for f in "$D"/user/build/*; do
  case "$(basename "$f")" in *.table.bin|*.reverse.bin|*.prism.bin|*.schema.yaml|default.yaml) cp "$f" "$D/shared/build/";; esac
done
for t in jyut6ping3.table.bin luna_pinyin.table.bin cangjie5.table.bin quick5.table.bin terra_pinyin.table.bin tk_yale.prism.bin tk_jyutping.prism.bin tk_pinyin.prism.bin tk_cangjie.prism.bin tk_quick.prism.bin tk_zhuyin.prism.bin; do
  test -s "$D/shared/build/$t" || { echo "missing $t" >&2; exit 1; }
done
# The word lists' sources don't ship: Rime uses the compiled tables.
rm -f "$D"/shared/*.dict.yaml "$D"/shared/essay*.txt

# What ships.
OUT="$REPO/android/assets/rime"
rm -rf "$OUT"; mkdir -p "$OUT"
cp -r "$D/shared/." "$OUT/"

# The shipped data, tried as the phone uses it: copied with new file times, as RimeData copies it, opened as the app opens
# Rime (rime_tool does as rime_jni.cc), with a fresh user folder. Rime must use the shipped tables and compile nothing.
rm -rf "$D/phone"; cp -r "$OUT" "$D/phone"
check() { # schema variant code first-choice
  local got
  rm -rf "$D/try"; mkdir -p "$D/try"
  got="$("$TOOL" try "$D/phone" "$D/try" "$1" "$2" "$3" 1 2>/dev/null | sed 's/^[^:]*: //')"
  [ "$got" = "$4" ] || { echo "FAIL $1 $2 '$3': '$got', expected '$4'" >&2; exit 1; }
  local compiled; compiled="$(ls "$D/try/build" 2>/dev/null || true)"
  [ -z "$compiled" ] || { echo "FAIL Rime compiled again on first use: $compiled" >&2; exit 1; }
}
check tk_yale zh_hk "nei hou" 你好
check tk_yale zh_hans "jung man" 中文
check tk_yale zh_hk "deui m jyu" 對唔住
check tk_yale zh_hk "yut yu" 粵語
check tk_yale zh_hk "ngo dei" 我哋
# Yale's interchangeable initials: z for j, n and l for each other. 你 stays first for nei, and ng stays ng.
check tk_yale zh_hk "nei" 你
check tk_yale zh_hk "lei" 你
check tk_yale zh_hk "lei hou" 你好
check tk_yale zh_hk "zung man" 中文
check tk_yale zh_hk "jung man" 中文
check tk_yale zh_hk "ngo" 我
# Jyutping keeps its own initials: lei isn't 你 there.
rm -rf "$D/try"; mkdir -p "$D/try"
[ "$("$TOOL" try "$D/phone" "$D/try" tk_jyutping zh_hk lei 1 2>/dev/null | sed 's/^[^:]*: //')" != 你 ] \
  || { echo "FAIL Jyutping lei gives 你: the fuzzy initials reached Jyutping" >&2; exit 1; }
check tk_jyutping zh_hk "jyut jyu" 粵語
check tk_jyutping zh_hk "ngo dei" 我哋
check tk_jyutping zh_hk "gwong dung waa" 廣東話
check tk_jyutping zh_hans "gwong dung waa" 广东话
check tk_jyutping zh_hk "neixxhoux" 你好
check tk_pinyin zh_hans "ruan jian" 软件
check tk_pinyin zh_hk "ruan jian" 軟件
check tk_pinyin zh_tw "ruan jian" 軟體
check tk_pinyin zh_hans "da yin ji" 打印机
check tk_pinyin zh_tw "da yin ji" 印表機
# Cangjie 5: 我 is hqi, 倉 oiar; Quick 5 the first and last letters (我 hi, after 的); Zhuyin on Hsu (你好 ㄋㄧˇ ㄏㄠˇ).
check tk_cangjie zh_hk "hqi" 我
check tk_cangjie zh_hk "oiar" 倉
check tk_cangjie zh_hans "oiar" 仓
check tk_quick zh_hk "hqi" 我
check tk_quick zh_tw "oiar" 倉
check tk_zhuyin zh_tw "nefhwf" 你好
check tk_zhuyin zh_tw "nehw" 你好
check tk_zhuyin zh_tw "tidxm" 臺灣
check tk_zhuyin zh_hans "jxlxn" 中文
# A whole phrase typed without spaces, then Space once, as the owner types (2026-11-17: "diuneiloumo"): Space takes all of it,
# leaving nothing behind.
phrase() { # schema variant code committed
  local got
  rm -rf "$D/try"; mkdir -p "$D/try"
  got="$("$TOOL" space "$D/phone" "$D/try" "$1" "$2" "$3" 2>/dev/null)"
  [ "$got" = "$4|" ] || { echo "FAIL $1 $2 '$3' then Space: '$got', expected '$4|'" >&2; exit 1; }
}
phrase tk_yale zh_hk diuneiloumou 屌你老母
phrase tk_yale zh_hk ngodeigamyatheuiyamcha 我哋今日去飲茶
phrase tk_yale zh_hk ngodeiyatchaiheuisikfaan 我哋一齊去食飯
phrase tk_jyutping zh_hk ngohaihoenggongjan 我係香港人
phrase tk_pinyin zh_hans womenyiqiquchifanba 我们一起去吃饭吧

# 対, the Japanese form of 對, is off deui's first page in every variant.
for v in zh_hk zh_tw zh_hans; do
  rm -rf "$D/try"; mkdir -p "$D/try"
  page="$("$TOOL" try "$D/phone" "$D/try" tk_yale "$v" "deui" 8 2>/dev/null)"
  [[ "$page" != *対* ]] || { echo "FAIL 対 on deui's first page, $v: $page" >&2; exit 1; }
done

# The next-word suggestions (#56): after a commit Rime offers the table's next words, in the variant; Space picks the first;
# Escape (the keyboard's way to clear them) leaves none, and so does a fresh session, with nothing committed yet.
next() { # schema variant code key step expected-choices
  local got
  rm -rf "$D/try"; mkdir -p "$D/try"
  got="$("$TOOL" next "$D/phone" "$D/try" "$1" "$2" "$3" "$4" 2>/dev/null | grep "^$5 " | sed 's/^[^:]*: *//' | cut -d' ' -f1-3)"
  [ "$got" = "$6" ] || { echo "FAIL next $1 $2 '$3' $4, $5: '$got', expected '$6'" >&2; exit 1; }
}
next tk_yale zh_hk "nei" esc space "嘅 哋 唔"
next tk_yale zh_hk "nei" esc esc ""
next tk_jyutping zh_hk "nei" esc space "嘅 哋 唔"
# A letter starts a new code: its own choices, not the suggestions.
next tk_pinyin zh_hans "wo" h h "和 好 还"
next tk_pinyin zh_hk "wo" esc space "為 母親 什麼都"
next tk_pinyin zh_hans "wo" esc space "为 母亲 什么都"
next tk_pinyin zh_tw "wo" esc space "為 母親 什麼都"
# Cangjie and Quick suggest from Cantonese's table, Zhuyin from Mandarin's.
next tk_cangjie zh_hk "hqi" reset space "哋 喺 嘅"
next tk_quick zh_hk "hqi" reset space "哋 喺 嘅"
next tk_zhuyin zh_tw "nehw" reset space "何塞 幸福 幾次"
rm -rf "$D/try"; mkdir -p "$D/try"
fresh="$("$TOOL" try "$D/phone" "$D/try" tk_yale zh_hk "" 8 2>/dev/null | sed 's/^[^:]*://')"
[ -z "$fresh" ] || { echo "FAIL suggestions with nothing committed: $fresh" >&2; exit 1; }

# Learned words in Back up everything (RimeBackup): a word learned on one phone goes out in Rime's snapshot, and a fresh
# phone merges that snapshot in from sync/restored/ with its own name, titankeys, as the app sets it.
for u in one two; do
  rm -rf "$D/$u"; mkdir -p "$D/$u"; printf 'installation_id: "titankeys"\n' > "$D/$u/installation.yaml"
done
learned="$("$TOOL" try "$D/phone" "$D/one" tk_yale zh_hk "nei hou" 2 2>/dev/null | sed 's/^[^:]*: //' | cut -d' ' -f2)"
[ -n "$learned" ] || { echo "FAIL no second choice for nei hou" >&2; exit 1; }
"$TOOL" learn "$D/phone" "$D/one" tk_yale "nei hou" 1 2>/dev/null
"$TOOL" sync "$D/phone" "$D/one" 2>/dev/null
snapshot="$D/one/sync/titankeys/jyut6ping3.userdb.txt"
grep -q "$learned" "$snapshot" || { echo "FAIL the snapshot hasn't the learned word $learned" >&2; exit 1; }
mkdir -p "$D/two/sync/restored"; cp "$snapshot" "$D/two/sync/restored/"
# As the app does: the schema opens its dictionary first, since Rime merges only into a dictionary the phone has.
"$TOOL" try "$D/phone" "$D/two" tk_yale zh_hk "nei" 1 > /dev/null 2>&1
"$TOOL" sync "$D/phone" "$D/two" 2>/dev/null
grep -q "$learned" "$D/two/sync/titankeys/jyut6ping3.userdb.txt" \
  || { echo "FAIL a restored snapshot didn't merge in" >&2; exit 1; }

cd "$REPO"
# The list the app copies by (RimeData), and that tools/checks/rime.test.ts checks the shipped data against.
(cd android/assets/rime && find . -type f | LC_ALL=C sort | sed 's#^\./##' | xargs sha256sum) > android/assets/rime.sha256
echo "built android/assets/rime ($(du -sb android/assets/rime | cut -f1) bytes, $(wc -l < android/assets/rime.sha256) files)"
