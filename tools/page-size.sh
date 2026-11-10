#!/usr/bin/env bash
# Checks that every native library in an APK or App Bundle supports 16 KB memory pages, as Google Play requires for apps
# that target Android 15 and later (https://developer.android.com/guide/practices/page-sizes): every LOAD segment is
# aligned to 16 KB or more, and a GNU_RELRO segment ends on a 16 KB boundary or where its LOAD segment ends.
# For an APK it also checks that the libraries are stored 16 KB aligned in the file. Usage: page-size.sh <file.apk|file.aab>
set -euo pipefail
file=${1:?Give an APK or AAB}
sdk=${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK}
readelf=$(ls "$sdk"/ndk/*/toolchains/llvm/prebuilt/*/bin/llvm-readelf 2>/dev/null | tail -1)
[[ -x "$readelf" ]] || { echo "llvm-readelf not found: install the Android NDK" >&2; exit 2; }
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
unzip -q -o "$file" '*.so' -d "$work" 2>/dev/null || true
mapfile -t libraries < <(find "$work" -name '*.so' | sort)
[[ ${#libraries[@]} -gt 0 ]] || { echo "No native libraries in $file"; exit 0; }
failed=0
for library in "${libraries[@]}"; do
  name=${library#"$work"/}
  # Program headers: type, offset, virtual address, physical address, file size, memory size, flags, alignment.
  verdict=$("$readelf" -lW "$library" | awk '
    function hex(value) { return strtonum(value) }
    $1 == "LOAD" { load++; align = hex($NF); if (align < 16384) bad = bad " LOAD aligned to " align;
                   start[load] = hex($3); end[load] = hex($3) + hex($6) }
    $1 == "GNU_RELRO" { relro_start = hex($3); relro_end = hex($3) + hex($6) }
    END {
      if (relro_end && relro_end % 16384 != 0) {
        ends_load = 0
        for (i = 1; i <= load; i++) if (relro_start >= start[i] && relro_end == end[i]) ends_load = 1
        if (!ends_load) bad = bad " GNU_RELRO ends off a 16 KB boundary"
      }
      print bad == "" ? "ok" : bad
    }')
  if [[ "$verdict" == ok ]]; then echo "16 KB ok   $name"; else echo "16 KB FAIL $name:$verdict"; failed=1; fi
done
if [[ "$file" == *.apk ]]; then
  zipalign=$(ls "$sdk"/build-tools/*/zipalign 2>/dev/null | sort -V | tail -1)
  if [[ -n "$zipalign" ]] && "$zipalign" -c -P 16 4 "$file" >/dev/null; then echo "zip alignment ok (16 KB for libraries)"
  else echo "zip alignment FAIL: libraries are not stored 16 KB aligned"; failed=1; fi
fi
exit "$failed"
