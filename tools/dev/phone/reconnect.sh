#!/usr/bin/env bash
# reconnect.sh PORT: connects to the phone on Wireless debugging's current port, then turns on the fixed port 5555 so later
# connections survive. Needs TK_ADB_HOST in tools/dev/local.env. After a phone restart, read the new port from
# Settings > Developer options > Wireless debugging and run this again.
H="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$H/../env.sh"
. "$TK_REPO/tools/phone-lock.sh"
[ -n "$TK_ADB_HOST" ] || { echo "Set TK_ADB_HOST in tools/dev/local.env"; exit 1; }
P="$TK_ADB_HOST:${1:?Wireless debugging port}"
timeout 15 adb connect "$P"
sleep 1
echo "boot: $(timeout 10 adb -s "$P" shell getprop sys.boot_completed | tr -d '\r')  uptime: $(timeout 10 adb -s "$P" shell cat /proc/uptime | cut -d' ' -f1)"
timeout 15 adb -s "$P" tcpip 5555
sleep 4
timeout 15 adb connect "$TK_ADB_HOST:5555"
sleep 1
adb devices
echo "Set TK_ADB_SERIAL=$TK_ADB_HOST:5555 in tools/dev/local.env if it isn't already."
