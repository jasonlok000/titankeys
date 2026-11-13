#!/usr/bin/env bash
# Draws the word menu of ladder v4 to PNGs, to look at without the phone: every rung at rest and hovered, frames of each of the six
# effects, the hover before and after each press, and a favourite's quiet hearts. The word and its effects are the real MenuScene,
# and the buttons are drawn with the real MenuKeys and SteadyKey, Strip's sizes and the app's own icons. It also checks, in pixels,
# that a pill covers the buttons to its left, that Save's book is opaque once shut, and that every rung's word is solid in its own
# colour, the status, with each preview 60% of the way to the next, accents on and off. It draws the rungs with accents off too,
# and the guide's light under its word. Usage: bash tests/menu-render/run.sh [folder].
set -euo pipefail
cd "$(dirname "$0")/../.."
kotlin=${KOTLIN_HOME:?Set KOTLIN_HOME to your Kotlin compiler directory}
stdlib=${KOTLIN_STDLIB:-$kotlin/lib/kotlin-stdlib.jar}
out=$(realpath -m "${1:-menu-render}")
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
keyboard=android/kotlin/com/atlas/keyboard
# The render's icons and sizes must still be the strip's.
for line in "Action.Save -> R.drawable.menu_save" "Action.Favourite -> R.drawable.menu_favourite" \
  "Action.Favourited -> R.drawable.menu_favourited" "Action.Unfavourite -> R.drawable.menu_unfavourite" \
  "Action.Unsave -> R.drawable.menu_unsave" "Action.Block -> R.drawable.menu_block" "Action.Unblock -> R.drawable.menu_unblock" \
  "private const val MENU_ICON_DP = 22" "private const val MENU_PILL_DP = 42" "private const val MENU_PILL_TEXT = .88f" \
  "private const val MENU_PILL_LEFT = 18" "private const val MENU_PILL_RIGHT = 11" "private const val MENU_PILL_GAP = 12" \
  "private const val MENU_PILL_FADE = 20" "private const val MENU_WORD_GAP = 8" "private const val LIT_DP = 2f" \
  "private const val LIT_GAP_DP = 3f"; do
  grep -qF "$line" "$keyboard/Strip.kt" || { echo "Strip.kt's menu icons or sizes changed: $line"; exit 1; }
done
"$kotlin/bin/kotlinc" -nowarn -no-stdlib -no-reflect -classpath "$stdlib:${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK}/platforms/android-36/android.jar" -d "$work" \
  "$keyboard/MenuKeys.kt" "$keyboard/StripFit.kt" "$keyboard/MenuMotion.kt" "$keyboard/Layout.kt" "$keyboard/KeyAlignment.kt" "$keyboard/KeyGeometry.kt" \
  "$keyboard/SteadyKey.kt" "$keyboard/MenuPath.kt" "$keyboard/MenuScene.kt" "$keyboard/QuickSetting.kt" "$keyboard/FixedScale.kt" "$keyboard/Accent.kt" \
  tests/word-menu/MenuBar.kt tests/word-menu/QuickSettingsPage.kt tests/menu-render/AwtMenuCanvas.kt tests/menu-render/MenuRender.kt
java -Djava.awt.headless=true -cp "$work:$stdlib" com.atlas.keyboard.MenuRenderKt "$out" android/src/main/res/drawable android/src/main/res/values/strings.xml android/src/main/res/font
