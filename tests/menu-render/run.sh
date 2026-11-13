#!/usr/bin/env bash
# Draws the word menu at each step of its ladder, hovered and not, long words, and frames of each animation, to PNGs, with the real
# MenuKeys and the app's own icons, to look at without the phone. Usage: bash tests/menu-render/run.sh [folder]. Not a check.
set -euo pipefail
cd "$(dirname "$0")/../.."
kotlin=${KOTLIN_HOME:?Set KOTLIN_HOME to your Kotlin compiler directory}
stdlib=${KOTLIN_STDLIB:-$kotlin/lib/kotlin-stdlib.jar}
out=$(realpath -m "${1:-menu-render}")
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
keyboard=android/kotlin/com/atlas/keyboard
# The render's icons must still be the strip's.
for line in "Action.Save -> R.drawable.menu_save" "Action.Favourite -> R.drawable.menu_favourite" \
  "Action.Favourited -> R.drawable.menu_favourited" "Action.Unfavourite -> R.drawable.menu_unfavourite" \
  "Action.Forget -> R.drawable.menu_forget" "Action.Unsave -> R.drawable.menu_unsave"; do
  grep -qF "$line" "$keyboard/Strip.kt" || { echo "Strip.kt's icons changed: $line"; exit 1; }
done
"$kotlin/bin/kotlinc" -nowarn -no-stdlib -no-reflect -classpath "$stdlib:${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK}/platforms/android-36/android.jar" -d "$work" \
  "$keyboard/MenuKeys.kt" "$keyboard/StripFit.kt" "$keyboard/MenuMotion.kt" "$keyboard/Layout.kt" "$keyboard/KeyAlignment.kt" "$keyboard/KeyGeometry.kt" tests/menu-render/MenuRender.kt
java -Djava.awt.headless=true -cp "$work:$stdlib" com.atlas.keyboard.MenuRenderKt "$out" android/src/main/res/drawable android/src/main/res/values/strings.xml android/src/main/res/font
