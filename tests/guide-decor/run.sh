#!/usr/bin/env bash
# Draws every guide slide's decoration with the real GuideDecor and checks docs/design-v2.md §7's rules on it: nothing inside a
# keep-out rect or its 8 dp, at most 12 shapes, no layer stronger than its level, exact loops, and under 2 ms a frame.
# Usage: bash tests/guide-decor/run.sh [folder]. The folder gets sheet.png, sheet-bright.png and a PNG for each slide.
set -euo pipefail
cd "$(dirname "$0")/../.."
sdk=${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK}
kotlin=${KOTLIN_HOME:?Set KOTLIN_HOME to your Kotlin compiler directory}
stdlib=${KOTLIN_STDLIB:-$kotlin/lib/kotlin-stdlib.jar}
out=$(realpath -m "${1:-$(mktemp -d)/guide-decor}")
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
keyboard=android/kotlin/com/atlas/keyboard
# The decoration's colours must still be the guide's.
for line in "const val ORANGE = 0xFFEF804E.toInt()" "const val CREAM = 0xFFFFF1E6.toInt()" "const val INK = 0xFF160A04.toInt()"; do
  grep -qF "$line" "$keyboard/GuideKeyboardView.kt" || { echo "GuideKeyboardView.kt's colours changed: $line"; exit 1; }
  grep -qF "$line" "$keyboard/GuideDecor.kt" || { echo "GuideDecor.kt's colours changed: $line"; exit 1; }
done
# The stand-in Canvas, Paint and Path draw with Java2D. They come before Android on the class path, so GuideDecor runs on them.
mkdir -p "$work/fake" "$work/classes"
javac -nowarn -d "$work/fake" tests/guide-decor/fake/android/graphics/*.java
"$kotlin/bin/kotlinc" -nowarn -no-stdlib -no-reflect -classpath "$work/fake:$stdlib:$sdk/platforms/android-36/android.jar" -d "$work/classes" \
  "$keyboard/GuideDecor.kt" tests/guide-decor/GuideDecorRender.kt
java -Djava.awt.headless=true -cp "$work/fake:$work/classes:$stdlib" com.atlas.keyboard.GuideDecorRenderKt "$out" android/src/main/res/font
