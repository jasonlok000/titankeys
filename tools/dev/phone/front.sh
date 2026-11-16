#!/usr/bin/env bash
# front.sh: prints the window in front on the phone.
H="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$H/../env.sh"; . "$H/safety.sh"
tk_front
