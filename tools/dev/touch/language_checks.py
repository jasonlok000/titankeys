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
import re
import time

import checks
import guide_checks
import languages
import own_editor
from checks import Stop, adb, play, record

RECEIVER = f"{checks.APP}/com.titankeys.keyboard.InputMethodsSet"
# How many languages go on with English at a time, each with all its input methods: seven, as Settings kept eight on at most
# (2026-11-16). Since C's cap (main e133d51a, owner 2026-11-17) the switcher shows four: three with English, the default.
SET_SIZE = int(os.environ.get("TK_LANG_SET_SIZE", "3"))
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
    "zh-cangjie": ("h q i space", "我"),
    "zh-quick": ("h q i space", "我"),
    "zh-zhuyin": ("n e enter", "ne"),  # Enter commits the pending Hsu code; chinese-ui checks its Space pick.
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
# Georgian's Shift + W is the shift batch's (shift_checks.py, through the key feed).
HANDS = []


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


def saved_methods():
    answer = adb("shell", "am", "broadcast", "-n", RECEIVER, "--ez", "read", "true")
    match = re.search(r'data="on ([a-z0-9,-]+); typing ([a-z0-9-]+)"', answer)
    return (match[1].split(","), match[2]) if match else ([], None)


def field_in_front():
    return languages.front().strip().endswith(f" u0 {checks.APP}/{languages.FIELD_ACTIVITY}}}")


def active_method(on, ids):
    """A live selection is only confirmed by both the saved method and Android's current Debug subtype."""
    if not field_in_front():
        return None
    debug = os.environ.get("TK_DEBUG_IME", f"{checks.APP}/com.titankeys.keyboard.TitanKeysIme")
    if adb("shell", "settings", "get", "secure", "default_input_method").strip() != debug:
        return None
    saved, current = saved_methods()
    return current if saved == on and ids.get(languages.current_subtype()) == current else None


def method_groups(on):
    with open(os.path.join(languages.REPO, "android/assets/im/index.json"), encoding="utf-8") as file:
        grouping = {entry["id"]: entry.get("language", {}).get("id", entry["id"]) for entry in json.load(file)}
    groups = {}
    for im in on:
        groups.setdefault(grouping.get(im, im), []).append(im)
    return [methods[:4] for methods in list(groups.values())[:4]]


def pick_key(index, count):
    """The first key over the tile, from SwitchOverlay.columns: the owner's layout of 2026-11-22 (#101)."""
    starts = {1: [1], 2: [1, 4], 3: [1, 3, 5], 4: [0, 2, 4, 6]}
    return "qwertyuiop"[starts[count][index]]


def switch_live(on, target):
    """Use the guarded switcher within the existing set. Give up on any unverified transition."""
    ids = languages.subtype_ids()
    current = active_method(on, ids)
    if current is None:
        return False
    if current == target:
        return True
    groups = method_groups(on)
    wanted = next((group for group in groups if target in group), None)
    if wanted is None or not any(current in group for group in groups):
        return False

    def observe(predicate):
        for attempt in range(8):
            method = active_method(on, ids)
            if predicate(method):
                return method
            if attempt < 7:
                time.sleep(.25)
        return None

    held = ["key:sym:900:none", "+500"]
    if len(groups) == 2 and current in wanted:
        # Q changes methods within the current language. Held Sym goes to the other language.
        checks.guarded("tap", "54", "1170")
        if len(wanted) > 2:
            play(f"key:{pick_key(wanted.index(target), len(wanted))}", "+500")
    else:
        moves = held
        if len(groups) > 2:
            moves += [f"key:{pick_key(groups.index(wanted), len(groups))}", "+500"]
        if len(wanted) > 1:
            moves += [f"key:{pick_key(wanted.index(target), len(wanted))}", "+500"]
        play(*moves)
    return observe(lambda method: method == target) == target


def turn_on(on, current):
    """Use the live switcher within a set. Restart input in the same field when the saved set changes."""
    def regain_field():
        if not field_in_front():
            checks.keyboard("debug")  # The existing guard waits for the owner before restoring the test field.
            if not field_in_front():
                raise Stop("the Space gestures field could not be brought back")

    regain_field()
    languages.logs_on(True)
    if switch_live(on, current):
        return True
    regain_field()
    out = adb("shell", "am", "broadcast", "-n", RECEIVER, "--es", "on", ",".join(on), "--es", "current", current)
    if "typing " + current not in out:
        raise Stop(f"InputMethodsSet didn't take {current}: {out.strip()[-200:]}")
    own_editor.restart()
    ids = languages.subtype_ids()
    for _ in range(10):
        # A same-method restart may write no log line. Verify what Settings and Android report now.
        if active_method(on, ids) == current:
            return True
        time.sleep(.5)
    return False


def gestures(keys):
    out = []
    for key in keys:
        out += ([f"chord:{key}"] if "+" in key else [f"key:{key}:60:none"]) + ["+90"]
    return out[:-1]


def fresh():
    """Clear Debug's Space gestures field and return the text the editor reports after the clear."""
    guide_checks.leave_guide()
    if not languages.in_field():
        raise Stop(f"not in the Space gestures field: {languages.front().strip()}")
    play(*[g for _ in range(20) for g in ("key:del", "+60")])
    return checks.set_text("")


def log_since(seen):
    lines = languages.log_lines()
    if len(lines) > seen:
        lines = lines[seen:]
    said = [line.split(":", 1)[-1].strip() for line in lines if "TitanKeysIM" in line][-2:]
    return " | ".join(said) or "nothing about input methods"


def one(im, on):
    seen = len(languages.log_lines())
    guide_checks.leave_guide()
    if not languages.in_field():
        raise Stop(f"not in the Space gestures field: {languages.front().strip()}")
    started = turn_on(on, im)
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
    play(*gestures(keys), "+400")
    text = checks.field()
    record(f"lang-{im}", text == want, f"typed {text!r}, wanted {want!r}; the log: {log_since(seen)}")


def check_all():
    """Every input method, SET_SIZE languages at a time with English, each typing in turn. TK_LANG_FROM=ID starts at that input
    method, to go on after a run that ended part way."""
    for group in sets():
        for im in group:
            if checks.past_cutoff():
                raise Stop("the morning cutoff came (TK_CUTOFF_EPOCH): the rest of the input methods didn't run")
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
