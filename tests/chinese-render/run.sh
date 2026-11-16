#!/usr/bin/env bash
# Draws Chinese on the bar to PNGs, to look at without the phone: the row (wide choices, a sentence, one-key choices, a hover,
# the English word, Pinyin's mark) and the bigger menu's pages, placed by the app's own rules over its key columns
# (docs/languages.md, "Chinese on the bar"). Chinese needs a CJK font: TK_CJK_FONT, or Windows' Noto Sans HK under WSL.
# Usage: bash tests/chinese-render/run.sh [folder].
set -euo pipefail
cd "$(dirname "$0")/../.."
kotlin=${KOTLIN_HOME:?Set KOTLIN_HOME to your Kotlin compiler directory}
stdlib=${KOTLIN_STDLIB:-$kotlin/lib/kotlin-stdlib.jar}
out=$(realpath -m "${1:-chinese-render}")
cjk=${TK_CJK_FONT:-/mnt/c/Windows/Fonts/NotoSansHK-VF.ttf}
[ -f "$cjk" ] || echo "no CJK font at $cjk: Chinese draws as boxes (set TK_CJK_FONT)" >&2
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
keyboard=android/kotlin/com/titankeys/keyboard
"$kotlin/bin/kotlinc" -nowarn -no-stdlib -no-reflect -classpath "$stdlib" -d "$work" \
  "$keyboard/Chinese.kt" "$keyboard/Layout.kt" "$keyboard/InputMethods.kt" "$keyboard/InputMethod.kt" "$keyboard/KeyAlignment.kt" \
  "$keyboard/KeyGeometry.kt" "$keyboard/StripFit.kt" android/kotlin/com/titankeys/connected/Json.kt tests/chinese-render/ChineseRender.kt
java -Djava.awt.headless=true -cp "$work:$stdlib" com.titankeys.keyboard.ChineseRenderKt "$out" android/src/main/res/font "$cjk"
