"""Main 842717e0's lines (the Master, 2026-11-17 evening), for checks.py --batch evening. Yale in Debug's Space gestures field;
the drawn bar from TitanKeysBar.

1. A tap on a visible starter in the empty field picks it: the field holds that word (O). B, 3140d91f.
2. Space with only predictions on the bar types a plain space in Chinese: 我 picked, then Space gives "我 " (O). B, 3140d91f.
3. Enter types what was picked plus the pending code: 頂你個 picked, fai pending, Enter gives 頂你個fai (O). B, 3140d91f.
4. Korean's how-to in Settings shows no blank jamo (J, a shot). E, 96cbbd01.
5. The owner's MARK in a field whose typing isn't recorded lands in Debug's log (G, 4fe523aa): not automated yet, a LOOK.
"""
import re
import time

import bar_observations as observed
import checks
import chinese_checks
import guide_checks
import interactions_batch as ib
import language_checks
import retest_batch
import switcher_checks
from checks import APP, Stop, adb, play, record


def read():
    try:
        return checks.field()
    except Stop:
        return None


def yale():
    ib.start(["en", "yue-yale"], "yue-yale")


def drawn_starters(raw, marker):
    """Only a drawn Yale prediction row after the setup marker can supply a starter."""
    header = rf"[VDIWEFAS]/({observed.MARK_TAG}|{observed.BAR_TAG})\s*\(\s*\d+\s*\):\s*(.*)"
    events = [match.groups() for line in raw.splitlines() if (match := re.fullmatch(header, line))]
    try:
        start = events.index((observed.MARK_TAG, marker))
    except ValueError:
        return []
    row = None
    for tag, line in events[start + 1:]:
        if tag != observed.BAR_TAG:
            continue
        if line.startswith("choice row: "):
            row = line.removeprefix("choice row: ")
        elif not line.startswith("bar: yue-yale "):
            # Another route or a notice can already have replaced the row.
            row = None
    prefix = "yue-yale predictions "
    if row is None or not row.startswith(prefix):
        return []
    choices = []
    previous_end = 0  # Column 0 is the method mark, and column 9 is the more cue.
    for choice in row.removeprefix(prefix).split(" | "):
        match = re.fullmatch(r"([1-8])-([1-8])=(.+)", choice)
        if not match:
            return []
        first, last = int(match[1]), int(match[2])
        if not previous_end < first <= last:
            return []
        choices.append((first, last, match[3]))
        previous_end = last
    return choices


def starter_target(choices):
    """Pick a point inside a drawn span even at either extreme of Debug's key calibration."""
    # MenuKeys.slot uses KeyGeometry's ten slots around the screen centre.
    # On this 1076 px display, KeyCalibrationActivity allows spacing 108 ±16 px
    # and centre 538 ±40 px (KeyAlignment). Leave the tap strictly inside.
    centre, shift = 538, 40
    pitches = (92, 124)
    targets = []
    for first, last, word in choices:
        left = centre + shift + max((first - 5) * pitch for pitch in pitches)
        right = centre - shift + min((last + 1 - 5) * pitch for pitch in pitches)
        if left + 2 <= right:
            x = (left + right) // 2
            targets.append((x, word))
    return min(targets, key=lambda target: abs(target[0] - centre), default=None)


def check_starter_tap():
    yale()
    # Make the strip leave the idle row so the redraw after the reset is logged
    # even when its starters match those from before this check.
    play(*ib.keys("nei"), "+500")
    try:
        marker, since = observed.mark_attempt()
    except observed.Unobservable as error:
        return record("starter-tap", None, str(error))
    language_checks.fresh()
    if observed.own_field() != "":
        return record("starter-tap", None, "the Yale field was not empty before the tap")
    size = re.findall(r"(?m)^(?:Physical|Override) size: (\d+x\d+)$", adb("shell", "wm", "size"))
    if not size or size[-1] != "1076x1200":
        return record("starter-tap", None, "the 1076 × 1200 tap geometry was unavailable")
    shown = set(re.findall(r"\bmInputShown=(true|false)\b", adb("shell", "dumpsys", "input_method")))
    if shown != {"true"}:
        return record("starter-tap", None, "the keyboard was not confirmed visible before the tap")
    raw = adb("logcat", "-d", "-v", "brief", "-T", since, "-s", observed.MARK_TAG + ":I", ib.TAG + ":D", "*:S")
    target = starter_target(drawn_starters(raw, marker))
    if target is None:
        return record("starter-tap", None, "no fresh drawn Yale starter with an unambiguous tap position")
    x, word = target
    checks.guarded("tap", str(x), str(switcher_checks.BAR_Y))
    time.sleep(.8)
    try:
        text = observed.own_field()
    except Stop as error:
        return record("starter-tap", None, str(error))
    record("starter-tap", text == word, f"a tap at x={x} on the drawn starter {word!r} typed {text!r}")


def check_space_predictions():
    yale()
    play(*ib.keys("ngo"), "+500", "key:space", "+700")
    picked = read()
    play("key:space", "+600")
    text = read()
    record("space-predictions", bool(picked) and text == picked + " ",
           f"{picked!r} picked, predictions on the bar, Space: {text!r} (wanted {picked!r} and a space)")


def check_enter_picked_code():
    yale()
    play(*ib.keys("dingneigo"), "+600", "key:space", "+700")
    picked = read()
    if picked != "頂你個":
        return record("enter-picked-code", None, f"dingneigo and Space typed {picked!r}, not 頂你個: the check needs it")
    play(*ib.keys("fai"), "+500", "key:enter", "+600")
    text = read()
    record("enter-picked-code", text == "頂你個fai", f"頂你個 picked, fai pending, Enter: {text!r} (wanted 頂你個fai)")


def check_korean_howto():
    from page_probe import Missing, PageUI, Unobservable
    retest_batch.own_screen()
    try:
        lines = PageUI(checks).howto('ko')
        record('korean-howto-text', True, f'{len(lines)} current Korean how-to points found')
        record('korean-howto', None, 'Korean page confirmed; visually review drawn jamo for missing glyphs',
               guide_checks.shot('korean-howto'))
    except Missing as error:
        record('korean-howto', False, str(error))
    except Unobservable as error:
        record('korean-howto', None, str(error))


def check_mark():
    record("mark-unrecorded-field", None, "the owner's MARK in a no-suggestions field: not automated yet (needs a pull of "
           "Debug's log through LogPull); for the owner's hands")


BATCH = [check_starter_tap, check_space_predictions, check_enter_picked_code, check_korean_howto, check_mark]


def finish():
    adb("shell", "setprop", f"log.tag.{ib.TAG}", "''")
    try:
        chinese_checks.fresh()
    except Stop:
        pass
    adb("shell", "am", "broadcast", "-n", f"{APP}/com.titankeys.keyboard.InputMethodsSet", "--es", "on",
        "en,zh-pinyin,yue-jyutping,yue-yale,af", "--es", "current", "en")
