#!/usr/bin/env bash
# serve.sh start|status|stop: keeps the injector's twin of the keyboard alive for a whole session on the phone, and touch.sh
# then plays its keys through it (touchlib.sh: tk_serve_play), so the twin isn't added and removed for every gesture.
# Never a twin of the touchpad: the Titan 2's input reader sets a touchpad's height from globals that a new twin would set to 0
# for the real touchpad too (inject.c, open_twin). Touches go through TitanKeys Debug's touch feed.
#
# start   puts the injector on the phone and starts `tk-inject --serve TitanKey`, reading "play PATH" lines
# status  whether it runs, and the touchpads Android knows (only the real one should be there)
# stop    lets go of everything and removes the twin
H="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$H/../env.sh"; . "$H/../phone/safety.sh"; . "$H/touchlib.sh"
tk_touch_lock
IN=/data/local/tmp/tk-serve.in
LOG=/data/local/tmp/tk-serve.log

running() { [ -n "$(tk_adb shell 'pidof tk-inject' | tr -d '\r')" ]; }

twin_height() {
  tk_adb shell dumpsys input | tr -d '\r' | awk '/^  Device [0-9]+: touchPad/{d=$0} /Y: source=TOUCHPAD/ && d {print d " " $0; d=""}'
}

case "${1:-}" in
  start)
    running && { echo "already serving"; exit 0; }
    tk_touch_install || exit 3
    tk_adb shell "rm -f $IN $LOG; touch $IN; nohup sh -c 'tail -f $IN | $TK_PHONE_INJECT --serve TitanKey' > $LOG 2>&1 &"
    sleep 2.5
    tk_adb shell cat "$LOG" | tr -d '\r'
    mkdir -p "$TK_TOUCH_DIR"; : > "$TK_TOUCH_DIR/serving"
    twin_height
    echo "The twin's height is right once its Y range reads like the real touchpad's (0 to about 749), after the screen goes off and on."
    ;;
  status)
    running && echo "serving" || echo "not serving"
    twin_height
    ;;
  stop)
    tk_adb shell "echo quit >> $IN; sleep 1; pkill -f 'tail -f $IN'; pkill tk-inject; rm -f $IN $LOG /data/local/tmp/tk-serve-*.tki"
    rm -f "$TK_TOUCH_DIR/serving"
    running && echo "still running" || echo "stopped"
    ;;
  *) echo "usage: serve.sh start|status|stop"; exit 2 ;;
esac
