#!/usr/bin/env bash
# Builds TitanKeys for Google Play: a signed Android App Bundle (dist/titankeys-<version>.aab) with the Play version code
# and the build mark that turns off the typing log. See docs/play/build-tasks.md and docs/play/signing.md.
#
# Version codes: the GitHub APK has the odd code in ink.toml, and the Play build the even code after it (2.1.0: 57 on
# GitHub, 58 on Play), so Play always offers its build to GitHub users of the same version.
#
# Signing: Play builds are signed with the upload key, signing/upload.keystore (alias "upload"), with its passwords in
# signing/upload-credentials.env as INK_KEYSTORE_PASSWORD and INK_KEY_PASSWORD. Google signs what users install with the
# GitHub key. --local signs with the GitHub key instead and names the file *-local.aab: for checking a bundle on this
# computer only, never for uploading, as the key of the first upload becomes the upload key.
#
# Needs an Ink SDK whose `ink build` has --bundle (tools/ink-bundle.patch adds it), at ../ink as package.json says.
set -euo pipefail
cd "$(dirname "$0")/.."

local=false
[[ "${1:-}" == "--local" ]] && local=true

if ! ../ink/scripts/ink build --help 2>/dev/null | grep -q -- '--bundle'; then
  echo "This Ink SDK can't build App Bundles. Apply tools/ink-bundle.patch to ../ink first." >&2
  exit 2
fi

code=$(sed -n 's/^version_code = \([0-9]*\)$/\1/p' ink.toml)
version=$(sed -n 's/^version = "\(.*\)"$/\1/p' ink.toml)
if [[ -z "$code" || $((code % 2)) -ne 1 ]]; then
  echo "ink.toml's version_code ($code) must be the GitHub build's odd code; Play takes the even one after it." >&2
  exit 2
fi
play_code=$((code + 1))

if ! $local; then
  [[ -f signing/upload.keystore && -f signing/upload-credentials.env ]] || {
    echo "No upload key: make signing/upload.keystore and signing/upload-credentials.env (docs/play/signing.md, step 3)," >&2
    echo "or pass --local to sign a bundle with the GitHub key for checks on this computer only." >&2
    exit 2
  }
  # Read the passwords here, so release.sh doesn't read the GitHub key's.
  set -a
  source signing/upload-credentials.env
  set +a
  : "${INK_KEYSTORE_PASSWORD:?signing/upload-credentials.env must set INK_KEYSTORE_PASSWORD}"
fi

backup=$(mktemp -d)
files=(ink.toml android/src/main/AndroidManifest.xml)
for file in "${files[@]}"; do mkdir -p "$backup/$(dirname "$file")"; cp "$file" "$backup/$file"; done
restore() {
  for file in "${files[@]}"; do cp "$backup/$file" "$file"; done
  rm -rf "$backup"
}
trap restore EXIT

# macOS and Linux sed disagree on -i, so write through a temporary file.
edit() { local file=$1; shift; sed "$@" "$file" > "$file.tmp"; mv "$file.tmp" "$file"; }

edit ink.toml -e "s/^version_code = .*/version_code = $play_code/"
if ! $local; then
  edit ink.toml -e 's#^keystore = .*#keystore = "signing/upload.keystore"#' -e 's/^key_alias = .*/key_alias = "upload"/'
fi
edit android/src/main/AndroidManifest.xml \
  's#<application\([^>]*\)>#<application\1>\n        <meta-data android:name="com.titankeys.build" android:value="play" />#'
grep -q 'com.titankeys.build' android/src/main/AndroidManifest.xml || { echo "Could not mark the manifest as the Play build" >&2; exit 1; }

bash tools/release.sh --bundle
bundle="dist/titankeys-$version.aab"
[[ -f "$bundle" ]] || { echo "The build made no $bundle" >&2; exit 1; }
if $local; then
  mv "$bundle" "dist/titankeys-$version-local.aab"
  bundle="dist/titankeys-$version-local.aab"
fi
bash tools/page-size.sh "$bundle"
echo "Built $bundle: version $version, Play version code $play_code$($local && echo ', signed with the GitHub key for local checks only')"
