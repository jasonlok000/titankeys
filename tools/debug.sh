#!/usr/bin/env bash
# Builds "TitanKeys Debug": a separate app with its own package and pale icon, so it installs next to the
# real TitanKeys without replacing it or touching its saved words. Pass --install to install it over adb.
# This build also adds the sample recorder from android/debug/recorder, which normal TitanKeys never compiles.
set -euo pipefail
cd "$(dirname "$0")/.."

recorder=android/debug/recorder
keyboard=android/kotlin/com/atlas/keyboard
backup=$(mktemp -d)
files=(ink.toml android/src/main/AndroidManifest.xml
  android/src/main/res/mipmap-anydpi-v26/ic_launcher.xml android/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml)
for file in "${files[@]}"; do mkdir -p "$backup/$(dirname "$file")"; cp "$file" "$backup/$file"; done
restore() {
  for file in "${files[@]}"; do cp "$backup/$file" "$file"; done
  rm -f android/src/main/res/values/atlas_debug.xml
  rmdir android/src/main/res/values 2>/dev/null || true
  for file in "$recorder"/*.kt; do rm -f "$keyboard/$(basename "$file")"; done
  rm -rf "$backup"
}
trap restore EXIT

# macOS and Linux sed disagree on -i, so write through a temporary file.
edit() { local file=$1; shift; sed "$@" "$file" > "$file.tmp"; mv "$file.tmp" "$file"; }

edit ink.toml -e 's/^name = .*/name = "TitanKeys Debug"/' -e 's/^package = .*/package = "com.titankeys.debug"/'
edit android/src/main/AndroidManifest.xml 's/android:label="TitanKeys"/android:label="TitanKeys Debug"/'
cp "$recorder"/*.kt "$keyboard/"
awk 'FNR == NR { entry = entry $0 ORS; next } /<\/application>/ { printf "%s", entry } { print }' \
  "$recorder/manifest.xml" android/src/main/AndroidManifest.xml > android/src/main/AndroidManifest.xml.tmp
mv android/src/main/AndroidManifest.xml.tmp android/src/main/AndroidManifest.xml
awk 'FNR == NR { entry = entry $0 ORS; next } /<\/manifest>/ { printf "%s", entry } { print }' \
  "$recorder/instrumentation.xml" android/src/main/AndroidManifest.xml > android/src/main/AndroidManifest.xml.tmp
mv android/src/main/AndroidManifest.xml.tmp android/src/main/AndroidManifest.xml
cp android/debug/ic_launcher.xml android/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
cp android/debug/ic_launcher.xml android/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml
mkdir -p android/src/main/res/values
cp android/debug/colors.xml android/src/main/res/values/atlas_debug.xml

bash tools/release.sh
apk=$(ls -t dist/*.apk | head -1)
echo "Built $apk"
if [[ "${1:-}" == "--install" ]]; then adb install -r "$apk"; fi
