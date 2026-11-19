"""C's KeyFeed (Debug-only, DUMP-protected): keys into the keyboard by broadcast, Shift included, which the keyboard twin can't
send on this phone (its Shift takes the field's focus). The proof, first in a slot, for checks.py --batch key-feed:
- Thai on, "D 0 SHIFT_RIGHT;D 60 Q;U 140 Q;U 200 SHIFT_RIGHT": the field is ๐ (O), it keeps its focus (O), and TitanKeysDebug
  says "key feed: D 60 Q m129 taken by the keyboard" (O).
- Georgian on, the same with W: the field is ჭ (O).
"""
import time

import checks
import language_checks
from checks import APP, Stop, adb, record

RECEIVER = f"{APP}/com.titankeys.keyboard.KeyFeed"


def feed(keys):
    return adb("shell", "am", "broadcast", "-n", RECEIVER, "--es", "keys", f"'{keys}'")  # quoted: the phone's shell splits on spaces and ";" (2026-11-17: "bad keys")


def debug_lines():
    return [line.split("TitanKeysDebug:", 1)[-1].strip() for line in adb("logcat", "-d", "-s", "TitanKeysDebug").splitlines()
            if "TitanKeysDebug:" in line]


def focused_text():
    """The field's text, or None when it lost its focus."""
    try:
        return checks.field()
    except Stop:
        return None


def check_key_feed():
    adb("shell", "setprop", "log.tag.TitanKeysDebug", "D")  # a debug property, cleared at the end
    try:
        for im, keys, want in (("th", "D 0 SHIFT_RIGHT;D 60 Q;U 140 Q;U 200 SHIFT_RIGHT", "๐"),
                               ("ka", "D 0 SHIFT_RIGHT;D 60 W;U 140 W;U 200 SHIFT_RIGHT", "ჭ")):
            language_checks.fresh()
            if not language_checks.turn_on(["en", "th", "ka"], im):
                record(f"key-feed-{im}", False, f"the field didn't start with {im}")
                continue
            if language_checks.fresh() != "":
                record(f"key-feed-{im}", None, "the test field still contained text after setup; the chord wasn't sent")
                continue
            adb("logcat", "-c")
            answer = feed(keys)
            time.sleep(1)
            text = focused_text()
            lines = debug_lines()
            record(f"key-feed-{im}", text == want, f"Shift + {keys.split(';')[1][-1]} through KeyFeed typed {text!r}, wanted {want!r}")
            record(f"key-feed-{im}-focus", text is not None, "the field kept its focus" if text is not None else "the field lost its focus")
            if im == "th":
                record("key-feed-log", "key feed: D 60 Q m129 taken by the keyboard" in lines,
                       f"TitanKeysDebug: {lines}; the broadcast answered {answer.strip()[-160:]}")
    finally:
        adb("shell", "setprop", "log.tag.TitanKeysDebug", "''")


def finish():
    """The owner's languages as they were before the batches: English typing, Chinese's three, Afrikaans."""
    adb("shell", "am", "broadcast", "-n", f"{APP}/com.titankeys.keyboard.InputMethodsSet", "--es", "on",
        "en,zh-pinyin,yue-jyutping,yue-yale,af", "--es", "current", "en")


BATCH = [check_key_feed]
