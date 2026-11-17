#!/usr/bin/env bash
# A prototype of the Play build's install-time asset packs (docs/play/asset-packs.md): turns a built APK into an App Bundle
# with the big data in packs, and measures what Play would send. It measures, it doesn't ship: the bundle is unsigned, the
# APKs are signed with a throwaway key made here, and nothing touches the release signing or the private Ink. Runs in WSL.
#   bash tools/dev/build/play-packs.sh <app.apk> [work folder]
set -euo pipefail
H="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APK="$(realpath "$1")"
W="${2:-$HOME/build/play-packs/work}"
TOOLS="$HOME/build/play-packs/tools"
BUNDLETOOL_VERSION=1.18.1
BUNDLETOOL_SHA256=675786493983787ffa11550bdb7c0715679a44e1643f3ff980a529e9c822595c
BUILD_TOOLS="$HOME/android-sdk/build-tools/36.0.0"
ANDROID_JAR="$HOME/android-sdk/platforms/android-36/android.jar"
BT="$TOOLS/bundletool-all-$BUNDLETOOL_VERSION.jar"

mkdir -p "$TOOLS"
if ! echo "$BUNDLETOOL_SHA256  $BT" | sha256sum -c --quiet > /dev/null 2>&1; then
  curl -sSL --fail -o "$BT" "https://github.com/google/bundletool/releases/download/$BUNDLETOOL_VERSION/bundletool-all-$BUNDLETOOL_VERSION.jar"
  echo "$BUNDLETOOL_SHA256  $BT" | sha256sum -c --quiet
fi
rm -rf "$W"; mkdir -p "$W"
PACKAGE="$("$BUILD_TOOLS/aapt2" dump packagename "$APK")"
echo "package $PACKAGE, $(stat -c%s "$APK") bytes"

# The APK's manifest and resources in the bundle's protobuf form.
"$BUILD_TOOLS/aapt2" convert --output-format proto -o "$W/proto.apk" "$APK"

# Each pack's manifest: an install-time asset pack of this app, fused into the app on devices without split support.
for pack in $(python3 -I -c "import sys; sys.path.insert(0, '$H'); import importlib.util as u; s = u.spec_from_file_location('p', '$H/play-packs.py'); m = u.module_from_spec(s); s.loader.exec_module(m); print(' '.join(m.PACKS))"); do
  mkdir -p "$W/$pack-manifest"
  cat > "$W/$pack-manifest/source.xml" <<XML
<manifest xmlns:android="http://schemas.android.com/apk/res/android" xmlns:dist="http://schemas.android.com/apk/distribution"
    package="$PACKAGE" split="$pack">
  <dist:module dist:type="asset-pack">
    <dist:fusing dist:include="true" />
    <dist:delivery><dist:install-time /></dist:delivery>
  </dist:module>
</manifest>
XML
  "$BUILD_TOOLS/aapt2" link --proto-format -I "$ANDROID_JAR" --manifest "$W/$pack-manifest/source.xml" -o "$W/$pack-manifest/linked.apk"
  (cd "$W/$pack-manifest" && unzip -q -o linked.apk AndroidManifest.xml)
done

python3 -I "$H/play-packs.py" "$W/proto.apk" "$W"
modules="$(ls "$W"/base.zip "$W"/*_pack.zip | paste -sd, -)"
# The packs' assets compressed, as the APK's are: the app copies Rime's, Mozc's and the swipe models out through a stream
# anyway, and bundletool leaves install-time packs uncompressed unless told, which would double their size on the phone.
cat > "$W/config.json" <<JSON
{ "compression": { "installTimeAssetModuleDefaultCompression": "COMPRESSED" } }
JSON
java -jar "$BT" build-bundle --modules="$modules" --config="$W/config.json" --output="$W/app.aab"
echo "bundle: $(stat -c%s "$W/app.aab") bytes"

# A throwaway key for the measuring APKs only.
keytool -genkeypair -keystore "$W/throwaway.jks" -storepass throwaway -keypass throwaway -alias throwaway -keyalg RSA \
  -keysize 2048 -validity 1 -dname "CN=throwaway" > /dev/null 2>&1
SIGN=(--ks="$W/throwaway.jks" --ks-pass=pass:throwaway --ks-key-alias=throwaway --key-pass=pass:throwaway)

# The Titan 2 Elite: arm64, Android 15, 440 dpi, English.
cat > "$W/titan.json" <<JSON
{ "supportedAbis": ["arm64-v8a"], "supportedLocales": ["en-US"], "screenDensity": 440, "sdkVersion": 35 }
JSON
java -jar "$BT" build-apks --bundle="$W/app.aab" --output="$W/app.apks" "${SIGN[@]}"
java -jar "$BT" build-apks --bundle="$W/app.aab" --output="$W/titan.apks" --device-spec="$W/titan.json" "${SIGN[@]}"
echo "== Play's download sizes for the Titan (bytes, compressed as Play sends them), per module:"
java -jar "$BT" get-size total --apks="$W/app.apks" --device-spec="$W/titan.json" --dimensions=ABI --modules=base
for pack in "$W"/*_pack.zip; do
  name="$(basename "$pack" .zip)"
  echo "-- $name"; java -jar "$BT" get-size total --apks="$W/app.apks" --device-spec="$W/titan.json" --modules="$name"
done
echo "-- everything installed on the Titan"
java -jar "$BT" get-size total --apks="$W/app.apks" --device-spec="$W/titan.json"
echo "== the APKs the Titan would get (an install's set):"
mkdir -p "$W/titan-extract"
java -jar "$BT" extract-apks --apks="$W/titan.apks" --device-spec="$W/titan.json" --output-dir="$W/titan-extract"
ls -l "$W/titan-extract"
