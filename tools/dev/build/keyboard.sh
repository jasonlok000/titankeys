#!/usr/bin/env bash
# Android drops the current keyboard when its app is reinstalled, so select TitanKeys Debug again.
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/../env.sh"
keyboard=com.titankeys.debug/com.atlas.keyboard.AtlasIme
tk_adb shell ime enable "$keyboard" || exit 1
tk_adb shell ime set "$keyboard" || exit 1
current=$(tk_adb shell settings get secure default_input_method | tr -d '\r')
[ "$current" = "$keyboard" ]
