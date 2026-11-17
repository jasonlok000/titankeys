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
import guide_checks
import languages
from checks import Stop, adb, play, record

RECEIVER = f"{checks.APP}/com.titankeys.keyboard.InputMethodsSet"
# How many languages go on with English at a time, each with all its input methods: seven, as Settings kept eight on at most
# (2026-11-16). The switcher shows four languages once C's cap is on main (owner, 2026-11-17): TK_LANG_SET_SIZE=3 then.
SET_SIZE = int(os.environ.get("TK_LANG_SET_SIZE", "7"))
MAX_ON = int(os.environ.get("TK_LANG_MAX_ON", "7"))


def sets():
    """The input methods other than English, in index order, in sets of SET_SIZE languages: a language's input methods (Chinese's
    three, Serbian's two) always go on together, as the switcher shows them under one language."""
    with open(os.path.join(languages.REPO, "android/assets/im/index.json"), encoding="utf-8") as f:
        entries = [entry for entry in json.load(f) if entry["id"] != "en"]
    start = os.environ.get("TK_LANG_FROM")
    if start in [entry["id"] for entry in entries]:
        entries = entries[[entry["id"] for entry in entries].index(start):]
    by_language = {}
    for entry in entries:
        by_language.setdefault(entry.get("language", {}).get("id", entry["id"]), []).append(entry["id"])
    # Also at most MAX_ON input methods besides English: Settings dropped the ninth on (2026-11-16), and whether it counts
    # languages or input methods wasn't seen.
    out, current, count = [], [], 0
    for group in by_language.values():
        if current and (count == SET_SIZE or len(current) + len(group) > MAX_ON):
            out.append(current)
            current, count = [], 0
        current += group
        count += 1
    return out + ([current] if current else [])
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
    # Hindi types by sound (jquery.ime's rules), so its keys aren't a table to read back against.
    "hi": ("n a m a s t e", "Hindi by sound: नमस्ते in the field"),
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
            # A key's letters as "a/A", or as a list (newer im.json files): what it types plain comes first either way.
            value = value[0] if isinstance(value, list) else value.split("/")[0]
            out += value
    return out


def turn_on(on, current):
    """Sets the input methods on and the one typing, then starts the field anew so the keyboard reads them: release TitanKeys,
    then Debug again (Android's own entry doesn't move the keyboard; 2026-11-16's first run). The keyboard's own log line says
    which input method the field started with."""
    languages.logs_on(True)
    out = adb("shell", "am", "broadcast", "-n", RECEIVER, "--es", "on", ",".join(on), "--es", "current", current)
    if "typing " + current not in out:
        raise Stop(f"InputMethodsSet didn't take {current}: {out.strip()[-200:]}")
    # The log from here on only: counting lines drifted as the buffer rolled (lt, nn and ja, 2026-11-16).
    adb("logcat", "-c")
    checks.keyboard("release")
    checks.keyboard("debug")
    for _ in range(10):
        if any(f"field start: {current} (" in line for line in languages.log_lines()):
            return True
        time.sleep(.5)
    return False


def gestures(keys):
    out = []
    for key in keys:
        out += ([f"chord:{key}"] if "+" in key else [f"key:{key}:60:none"]) + ["+90"]
    return out[:-1]


def fresh():
    """Debug's Space gestures field in front (the guide's and the playground's type English only), no code, empty."""
    guide_checks.leave_guide()
    if not languages.in_field():
        raise Stop(f"not in the Space gestures field: {languages.front().strip()}")
    play(*[g for _ in range(20) for g in ("key:del", "+60")])
    checks.set_text("")


def log_since(seen):
    lines = languages.log_lines()[seen:]
    said = [line.split(":", 1)[-1].strip() for line in lines if "TitanKeysIM" in line][-2:]
    return " | ".join(said) or "nothing about input methods"


def one(im, on):
    seen = len(languages.log_lines())
    guide_checks.leave_guide()
    if not languages.in_field():
        raise Stop(f"not in the Space gestures field: {languages.front().strip()}")
    started = turn_on(on, im)
    seen = 0  # turn_on cleared the log
    if not started:
        return record(f"lang-{im}", False, f"the field didn't start with {im}; the log: {log_since(seen)}")
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
    """Every input method, SET_SIZE languages at a time with English, each typing in turn. TK_LANG_FROM=ID starts at that input
    method, to go on after a run that ended part way."""
    for group in sets():
        for im in group:
            try:
                one(im, ["en", *group])
            except Stop:
                raise
            except (Exception, SystemExit) as error:  # noqa: BLE001 (one input method mustn't end the night's run)
                record(f"lang-{im}", False, f"the check broke: {error!r}")


def out_of_memory():
    """How many out-of-memory crashes of TitanKeys Debug Android's crash log holds now."""
    crashes = adb("logcat", "-d", "-b", "crash")
    return sum(1 for line in crashes.splitlines() if "OutOfMemoryError" in line)


def setup():
    global crashes_before
    crashes_before = out_of_memory()
    if not languages.logs_on(True):
        record("lang-log", None, "the shell couldn't let TitanKeysIM through the log: the lines will say nothing")


crashes_before = 0


def finish():
    """No out-of-memory crash during the run (#85's input methods: one came after 18 switches, 2026-11-16), and LeakCheck's own
    report; then English alone again, as most people have it, and the log tags cleared."""
    crashes = out_of_memory() - crashes_before
    record("lang-memory", crashes == 0, f"{crashes} out-of-memory crashes during the run")
    answer = adb("shell", "am", "broadcast", "-n", f"{checks.APP}/com.titankeys.keyboard.LeakCheck")
    record("lang-leakcheck", None, "LeakCheck: " + answer.strip().split("data=")[-1].strip('"')[:400])
    try:
        turn_on(["en"], "en")
        fresh()
    finally:
        languages.logs_on(False)


BATCH = [setup, check_all]
