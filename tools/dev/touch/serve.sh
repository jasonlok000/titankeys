#!/usr/bin/env bash
# serve.sh start|status|stop: keeps the injector's twins of the touchpad and keyboard alive for a whole session on the phone,
# and touch.sh then plays through them (touchlib.sh: tk_touch_play).
#
# Why: the phone's input reader gives a newly added touchpad its height before it knows the display, and only sets it again at
# its next reconfiguration, such as the screen going off and on. A twin made for each gesture never gets there, so its touches
# land nowhere. One that lives for the session does: start the server, have the owner turn the screen off and on once (this
# tool never does), and `status` says when the twin's heights are right.
#
# start   puts the injector on the phone and starts `tk-inject --serve touchPad TitanKey`, reading "play PATH" lines
# status  whether it runs, and whether the touchpad twin's height is right yet (from dumpsys input)
# stop    lets go of everything and removes the twins
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
    tk_adb shell "rm -f $IN $LOG; touch $IN; nohup sh -c 'tail -f $IN | $TK_PHONE_INJECT --serve touchPad TitanKey' > $LOG 2>&1 &"
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
