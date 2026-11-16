#!/usr/bin/env bash
# Checks a built APK, minified as it ships: every engine an input method names (android/assets/im/index.json) has its class
# and its constructor that takes a Context, which the keyboard makes by name (EngineClasses in InputEngine.kt). R8 can't see
# a class made by name, and once left Korean's and Vietnamese's engines out, so they typed English on the phone.
#
#     bash tests/languages/apk.sh dist/titankeys-debug-2.1.0.apk
#
# Needs the Android SDK's dexdump (build-tools), found under $ANDROID_HOME or ~/android-sdk.
set -euo pipefail
cd "$(dirname "$0")/../.."
apk=${1:?Give the APK to check}
sdk=${ANDROID_HOME:-$HOME/android-sdk}
dexdump=$(ls -d "$sdk"/build-tools/*/dexdump 2>/dev/null | sort -V | tail -1)
[ -x "$dexdump" ] || { echo "No dexdump under $sdk/build-tools" >&2; exit 1; }
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
unzip -q -o "$apk" 'classes*.dex' -d "$work"
for dex in "$work"/classes*.dex; do "$dexdump" "$dex"; done > "$work/dump.txt"
python3 -I - android/assets/im/index.json android/kotlin/com/titankeys/keyboard/InputEngine.kt "$work/dump.txt" <<'EOF'
import json, re, sys
index, source, dump = sys.argv[1:4]
classes = dict(re.findall(r'"(\w+)" to "([\w.]+)"', open(source, encoding="utf-8").read().split("object EngineClasses", 1)[1]))
# dexdump: "  Class descriptor  : 'Lcom/x/Y;'", then its methods, each "      name          : '<init>'" and its "type".
found, current, method = {}, None, None
for line in open(dump, encoding="utf-8", errors="replace"):
    m = re.match(r"\s*Class descriptor\s*:\s*'L([^;]+);'", line)
    if m:
        current = m.group(1).replace("/", ".")
        found.setdefault(current, set())
        continue
    m = re.match(r"\s*name\s*:\s*'([^']+)'", line)
    if m:
        method = m.group(1)
        continue
    m = re.match(r"\s*type\s*:\s*'([^']+)'", line)
    if m and current and method == "<init>":
        found[current].add(m.group(1))
failed = 0
for engine in sorted({m["engine"] for m in json.load(open(index, encoding="utf-8")) if m.get("engine")}):
    name = classes.get(engine)
    if name is None:
        print(f"FAIL {engine}: no class in EngineClasses"); failed += 1
    elif name not in found:
        print(f"FAIL {engine}: {name} isn't in the APK"); failed += 1
    elif "(Landroid/content/Context;)V" not in found[name]:
        print(f"FAIL {engine}: {name} has no constructor that takes a Context"); failed += 1
    else:
        print(f"ok {engine}: {name}")
sys.exit(1 if failed else 0)
EOF
echo "APK engine checks passed"
