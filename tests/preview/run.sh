#!/usr/bin/env bash
# Runs the preview check on TitanKeys Debug (com.titankeys.debug), so the real TitanKeys is never touched.
set -euo pipefail
cd "$(dirname "$0")/../.."
# It uses the phone: the maintainer's phone lock, if there is one (tools/phone-lock.sh).
. tools/phone-lock.sh
sdk=${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK}
adb="$sdk/platform-tools/adb"
app=com.titankeys.debug
manifest=android/src/main/AndroidManifest.xml
check=android/kotlin/com/titankeys/keyboard/PreviewChecks.kt
work=$(mktemp -d)
test ! -e "$check"
cp "$manifest" "$work/AndroidManifest.xml"
keyboard=$("$adb" shell settings get secure default_input_method)
restore() {
  cp "$work/AndroidManifest.xml" "$manifest"
  rm -f "$check"
}
trap 'restore; "$adb" shell ime set "$keyboard" >/dev/null; rm -rf "$work"' EXIT
cp tests/preview/PreviewChecks.kt "$check"
python3 - "$app" <<'PY'
import sys
from pathlib import Path
path = Path("android/src/main/AndroidManifest.xml")
text = path.read_text()
assert text.count("<application") == 1, "Could not find the application tag"
path.write_text(text.replace("<application",
    f'<instrumentation android:name="com.titankeys.keyboard.PreviewChecks" android:targetPackage="{sys.argv[1]}" />\n    <application'))
PY
bash tools/debug.sh --install
"$adb" shell ime enable "$app/com.titankeys.keyboard.TitanKeysIme"
"$adb" shell ime set "$app/com.titankeys.keyboard.TitanKeysIme"
passed=true
for attempt in 1 2 3; do
  output=$("$adb" shell am instrument -w "$app/com.titankeys.keyboard.PreviewChecks")
  printf '%s\n' "$output"
  [[ "$output" == *"PASS the playground shows a new swipe after returning from Settings"* ]] || passed=false
done
restore
bash tools/debug.sh --install
[[ "$passed" == true ]]
