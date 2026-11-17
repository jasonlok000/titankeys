"""Every input method on the phone (#56), for checks.py --batch languages: each turned on in turn, its keys typed and read back.

The input methods go on eight at a time, as a person might have them, through TitanKeys Debug's InputMethodsSet receiver (only adb,
only Debug), which sets Settings > Languages and the one typing; Android's keyboard entry follows, and the keyboard with it. Each
one then types a few keys in Debug's Space gestures field, and the text is read back against the keys in its im.json. The
keyboard's own log line about the input method (TitanKeysIM: ids and counts, never text) goes with each result.

Engines have tests of their own: Korean's syllables, Vietnamese's tones, Japanese (Mozc: romaji in, kanji on the bar, Space picks)
and Chinese, whose bar has its own batch (chinese-ui). Georgian's Shift+W needs the owner's hands: the keyboard twin's Shift
can't be used on this phone.
"""
import json
import os
import time

import checks
import languages
from checks import Stop, adb, play, record

RECEIVER = f"{checks.APP}/com.titankeys.keyboard.InputMethodsSet"
SET_SIZE = 8
# Letters most layouts move, so a wrong layout shows; no Space, so no fix runs on them.
PLAIN = "qwertyuiop"
# Engines and the scripts whose keys aren't one letter each: their own keys and what they must type.
OWN = {
    "ko": ("g k s r n r d j space", "한국어 "),
    "vi": ("t i e e n g s space", "tiếng "),
    "sr": ("q y z space", "љзж "),
    "kk": ("q space", "й "),
    "ka": ("q space", "ქ "),
}
# Typed but judged by the screenshot: what the bar shows decides.
LOOK = {
    "ja": ("n i h o n g o", "Mozc: にほんご as the code, kanji (日本語) among the choices on the bar"),
    "yue-yale": ("n e i h o u", "Yale: choices over W to O (the chinese-ui batch checks the rest)"),
    "yue-jyutping": ("n e i h o u", "Jyutping: choices over W to O"),
    "zh-pinyin": ("n i h a o", "Pinyin: 你好 among the choices"),
}
# Space after a code picks: Japanese's first choice, read back.
PICKS = {"ja": "n i h o n g o"}
HANDS = ["Georgian: Shift + W types ჭ (the keyboard twin's Shift can't be used on this phone)"]


def all_ids():
    with open(os.path.join(languages.REPO, "android/assets/im/index.json"), encoding="utf-8") as f:
        return [entry["id"] for entry in json.load(f)]


def expected(im, keys):
    table = languages.im_json(im).get("keys") or {}
    out = ""
    for key in keys:
        if key == "space":
            out += " "
        else:
            value = table.get(key, key) if isinstance(table, dict) else key
            out += value.split("/")[0]
    return out


def turn_on(on, current):
    """Sets the input methods on and the one typing, then waits for Android's entry and the keyboard to follow."""
    out = adb("shell", "am", "broadcast", "-n", RECEIVER, "--es", "on", ",".join(on), "--es", "current", current)
    if "typing " + current not in out:
        raise Stop(f"InputMethodsSet didn't take {current}: {out.strip()[-200:]}")
    ids = languages.subtype_ids()
    for _ in range(10):
        if ids.get(languages.current_subtype()) == current:
            return True
        time.sleep(.5)
    # The keyboard reads the setting at the next field start: Debug again, read back.
    checks.keyboard("debug")
    return ids.get(languages.current_subtype()) == current


def gestures(keys):
    out = []
    for key in keys:
        out += ([f"chord:{key}"] if "+" in key else [f"key:{key}:60:none"]) + ["+90"]
    return out[:-1]


def fresh():
    play(*[g for _ in range(20) for g in ("key:del", "+60")])
    checks.set_text("")


def log_since(seen):
    lines = languages.log_lines()[seen:]
    said = [line.split(":", 1)[-1].strip() for line in lines if "TitanKeysIM" in line][-2:]
    return " | ".join(said) or "nothing about input methods"


def one(im, on):
    seen = len(languages.log_lines())
    if not turn_on(on, im):
        return record(f"lang-{im}", False, f"Android's entry didn't follow to {im}; the log: {log_since(seen)}")
    fresh()
    if im in LOOK:
        keys, what = LOOK[im]
        moves = gestures(keys.split())
        play(*moves, "+600", shot=f"lang-{im}", at=len(moves) * 80 + 500)
        if im in PICKS:
            play("key:space", "+400")
            text = checks.field()
            record(f"lang-{im}-space", bool(text) and text.isascii() is False, f"Space typed {text!r}: the bar's first choice")
            fresh()
        return record(f"lang-{im}", None, f"{what}; the log: {log_since(seen)}", f"lang-{im}")
    keys, want = OWN.get(im, (" ".join(PLAIN), None))
    keys = keys.split()
    want = want if want is not None else expected(im, keys)
    play(*gestures(keys), "+400", shot=f"lang-{im}", at=len(keys) * 160 + 300)
    text = checks.field()
    record(f"lang-{im}", text == want, f"typed {text!r}, wanted {want!r}; the log: {log_since(seen)}", f"lang-{im}")


def check_all():
    """Every input method, eight at a time with English, each typing in turn."""
    ids = [im for im in all_ids() if im != "en"]
    for start in range(0, len(ids), SET_SIZE):
        group = ids[start:start + SET_SIZE]
        for im in group:
            try:
                one(im, ["en", *group])
            except Stop:
                raise
            except (Exception, SystemExit) as error:  # noqa: BLE001 (one input method mustn't end the night's run)
                record(f"lang-{im}", False, f"the check broke: {error!r}")


def setup():
    if not languages.logs_on(True):
        record("lang-log", None, "the shell couldn't let TitanKeysIM through the log: the lines will say nothing")


def finish():
    """English alone again, as most people have it, and the log tags cleared."""
    try:
        turn_on(["en"], "en")
        fresh()
    finally:
        languages.logs_on(False)


BATCH = [setup, check_all]
