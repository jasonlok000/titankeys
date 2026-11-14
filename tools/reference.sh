#!/usr/bin/env bash
# Builds "TitanKeys Reference", the separate app in android/debug/reference, into dist/titankeys-reference.apk.
# Nothing else compiles that folder, so it is never part of normal TitanKeys or TitanKeys Debug.
set -euo pipefail
cd "$(dirname "$0")/.."

# CI can provide credentials directly; local builds use the ignored signing folder.
if [[ -z "${INK_KEYSTORE_PASSWORD:-}" && -f signing/credentials.env ]]; then
  source signing/credentials.env
fi
: "${INK_KEYSTORE_PASSWORD:?Set INK_KEYSTORE_PASSWORD or restore signing/credentials.env}"
export INK_KEYSTORE_PASSWORD
export INK_KEY_PASSWORD="${INK_KEY_PASSWORD:-$INK_KEYSTORE_PASSWORD}"

sdk=${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK}
tools=$sdk/build-tools/36.0.0
platform=$sdk/platforms/android-36/android.jar
app=android/debug/reference
code=com/titankeys/reference
output=dist/titankeys-reference.apk
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
mkdir -p dist "$work/classes" "$work/dex"

"$tools/aapt2" compile --dir "$app/res" -o "$work/resources.zip"
"$tools/aapt2" compile android/src/main/res/drawable/atlas_icon_foreground.xml -o "$work"
"$tools/aapt2" link -I "$platform" --manifest "$app/AndroidManifest.xml" -o "$work/unsigned.apk" \
  "$work/resources.zip" "$work/drawable_atlas_icon_foreground.xml.flat"

# android.jar has no LambdaMetafactory that javac can compile a lambda against. d8 replaces these calls anyway.
javac -Xlint:-options -source 8 -target 8 -bootclasspath "$tools/core-lambda-stubs.jar:$platform" \
  -d "$work/classes" "$app/java/$code"/*.java
"$tools/d8" --release --min-api 34 --lib "$platform" --output "$work/dex" "$work/classes/$code"/*.class

# aapt2 packs only resources. aapt adds the code, and stores the name as it is given here.
(cd "$work/dex" && "$tools/aapt" add "$work/unsigned.apk" classes.dex > /dev/null)
"$tools/zipalign" -f -p 4 "$work/unsigned.apk" "$work/aligned.apk"

# The env: form reads each password from the environment, so it is never on a command line or in the output.
"$tools/apksigner" sign --ks signing/nightkeys-release.p12 --ks-key-alias nightkeys \
  --ks-pass env:INK_KEYSTORE_PASSWORD --key-pass env:INK_KEY_PASSWORD \
  --v4-signing-enabled false --out "$output" "$work/aligned.apk"
"$tools/apksigner" verify "$output"
echo "Built $output"
