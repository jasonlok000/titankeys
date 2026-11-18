"""The guide's hands-only checks (#66, #67, #68, #69), the word menu's pill (#84) and a look at the right Shift, for checks.py.

Each opens TitanKeys Debug's guide at its slide through android/debug/recorder/GuideStart.kt (only adb can, only in Debug), plays
the slide through the touch feed and the keyboard twin, and reads the guide's own lines back: the guide is made of plain views,
so the window's hierarchy shows its title, instruction and verdict. What only the eye can judge comes as screenshots.
"""
import os
import re
import time
import xml.etree.ElementTree as ElementTree

import checks
from checks import APP, Stop, adb, play, record

GUIDE = "com.titankeys.keyboard.GuideActivity"


def front():
    return next((line for line in adb("shell", "dumpsys", "window").splitlines() if "mCurrentFocus" in line), "")


def texts():
    """Every line of text TitanKeys Debug's window shows now, in order. The dump is deleted at once."""
    path = "/data/local/tmp/tk-touch-ui.xml"
    adb("shell", "uiautomator", "dump", path)
    xml = adb("shell", "cat", path)
    adb("shell", "rm", "-f", path)
    if "<" not in xml:
        return []
    root = ElementTree.fromstring(xml[xml.find("<"):])
    return [n.attrib["text"] for n in root.iter("node") if n.attrib.get("package") == APP and n.attrib.get("text")]


def guide(step):
    """Opens the guide at the slide called step, from TitanKeys Debug's own screen only. Returns its lines."""
    if f" {APP}/" not in front():
        raise Stop(f"the guide opens only from TitanKeys Debug's own screen: front is {front().strip()}")
    answer = adb("shell", "am", "broadcast", "-n", f"{APP}/com.titankeys.keyboard.GuideStart", "--es", "step", step)
    if "guide opened" not in answer:
        raise Stop(f"the guide didn't open at {step}: {answer.strip()[-200:]}")
    time.sleep(3)
    if GUIDE not in front():
        raise Stop(f"the guide isn't in front: {front().strip()}")
    return texts()


def shot(name):
    """A screenshot for an explicit visual check or comparison."""
    return checks.capture(name)


def said(lines, *wanted):
    """The first of the wanted lines the guide shows, or None."""
    # The guide sets its titles in capitals, so the comparison ignores case.
    return next((w for w in wanted if any(w.casefold() in line.casefold() for line in lines)), None)


def leave_guide():
    """Back out of the guide, only while it's in front, so the next check starts from TitanKeys Debug's own screen."""
    for _ in range(3):
        if GUIDE not in front():
            return
        adb("shell", "input", "keyevent", "4")
        time.sleep(1.5)


# --- The checks. ---

def check_66_steer():
    """#66: the cursor slide. Steers left through the feed; the guide's verdict, and where the target sits (screenshots)."""
    lines = guide("steer")
    before = shot("66-steer-before")
    try:
        for keys in (-1.5, -3, -4.5):
            play(f"spacehold:2:{keys}:{int(abs(keys) * 200)}", shot=f"66-steer-{abs(keys)}", at=700)
            time.sleep(1)
            lines = texts()
            if said(lines, "Cursor moved", "No screen harmed"):
                record("66-steer", True, f"landed after a slide of {abs(keys)} keys")
                return record("66-steer-distance", None, "Target distance compared with the pre-slide frame", f"66-steer-{abs(keys)}")
        record("66-steer", False, f"no landing in 1.5 to 4.5 keys; the guide says: {' | '.join(lines[:6])}", before)
    finally:
        leave_guide()


def check_67_selectmore():
    """#67: the triple Space slide. Three taps, the third held: the key names it shows; then a slide right to grab the words."""
    guide("selectmore")
    try:
        play("spacehold:3", shot="67-triple", at=500)
        lines = texts()
        record("67-key-names", None, f"after three taps the guide says: {' | '.join(lines[:6])}", "67-triple")
        play("spacehold:3:3:600", shot="67-grab", at=900)
        time.sleep(1)
        lines = texts()
        record("67-grab", said(lines, "Grabbed", "heart") is not None and "Tap Space three times" not in " ".join(lines),
               f"after three taps and a slide: {' | '.join(lines[:6])}")
        record("67-grab-highlight", None, "The words highlighted during the slide", "67-grab")
    finally:
        leave_guide()


def check_68_hunts():
    """#68: the ∞ and 🤡 hunts. The guide times them from the hunt line's appearance (startHunt, at the slide's hunt stage), so the
    stopwatch starts there too, and what the guide says is read just before and after 10, 20 and 30 s, with screenshots."""
    for step, reach, name in [("symgroups", ["key:sym", "+400", "key:enter"], "68-infinity"), ("clown", ["key:sym", "+400", "key:sym"], "68-clown")]:
        guide(step)
        try:
            play(*reach)
            # Wait for the hunt line itself ("Find ∞…", "Find 🤡…"): the guide's clock starts with it.
            start, waited = None, time.monotonic()
            while time.monotonic() - waited < 8:
                if said(texts(), "Find ∞", "Find 🤡", "Swipe left or right"):
                    start = time.monotonic()
                    break
                time.sleep(.3)
            if start is None:
                record(name, None, f"the hunt line didn't show: {' | '.join(texts()[:5])}")
                continue
            timeline = []
            for second in (2, 9, 12, 19, 22, 29, 32):
                time.sleep(max(0.0, start + second - time.monotonic()))
                lines = texts()
                shot(f"{name}-{second}s")
                timeline.append(f"{second} s: {' | '.join(lines[:6])}")
            record(name, None, "from the hunt line on (the guide's rule: the count and mockery at 10 s, the hint at 20, the skip at "
                   "30):\n      " + "\n      ".join(timeline), f"{name}-32s")
        finally:
            leave_guide()


def check_69_clip():
    """#69's second paste: the clipboard slide. Sym, A, then each clip's key until the nice one pastes; the guide's done line is
    "No, seriously. I mean it." (a rude clip is taken back out at once, so a wrong key costs nothing)."""
    guide("clipboard")
    keys = ["R", "I", "F", "K", "C"]  # GuideSteps.CLIP_FIELD_KEYS: a key in each clip's field
    seen = []
    try:
        for _ in range(16):
            lines = texts()
            seen.append(" | ".join(lines[:3]))
            if said(lines, "No, seriously. I mean it."):
                return record("69-clip", True, f"the guide says \"No, seriously. I mean it.\" after {len(seen)} steps")
            if said(lines, "Press Sym."):
                play("key:sym")
            elif said(lines, "Press A for the clipboard"):
                play("key:a")
            elif said(lines, "Rest on each clip") and keys:
                play(f"key:{keys.pop(0).lower()}:70:lift")
            else:
                play("key:sym")
            time.sleep(.8)
        record("69-clip", False, "no nice paste: " + " / ".join(seen[-4:]))
    finally:
        leave_guide()


def check_69_paste():
    """#69: the paste slide. fn held and V pressed on the real keyboard: the guide's verdict."""
    guide("paste")
    try:
        play("chord:fn+v:120")
        time.sleep(1)
        lines = texts()
        record("69-paste", said(lines, "Pasted") is not None, f"the guide says: {' | '.join(lines[:6])}")
    finally:
        leave_guide()


def check_84_menu():
    """#84: the word menu's pill after a press on I/K with the finger left resting, lifted or slid. The eye decides."""
    for after in ("rest", "lift", "slide"):
        lines = guide("menu")
        try:
            # "Tap TitanKeys on the bar.": the bar's middle word, as a screen tap on the keyboard's own window.
            if GUIDE not in front():
                raise Stop("the guide left the front")
            adb("shell", "input", "tap", "540", os.environ.get("TK_BAR_Y", "1169"))
            time.sleep(1.2)
            opened = texts()
            play(f"key:i:70:{after}", shot=f"84-menu-{after}", at=350)
            record(f"84-menu-{after}", None, f"menu step: {said(opened, 'Press I/K to save it.') or 'not reached: ' + ' | '.join(opened[:4])}",
                   f"84-menu-{after}")
        finally:
            leave_guide()


def check_shift():
    """Why the right Shift loses the field: focus, the input connection and Android's activity events around one Shift+W."""
    def state():
        ime = adb("shell", "dumpsys", "input_method")
        editor = re.search(r"mCurrentEditorInfo.*?packageName=(\S+)", ime, re.S)
        return f"front {front().strip()[-50:]}; editor {editor.group(1) if editor else None}"
    im = os.environ.get("TK_SHIFT_IM")
    if im:
        import languages
        if not (languages.in_field() and languages.switch_to(im, languages.subtype_ids())):
            return record("shift-w", None, f"couldn't switch to {im}: is it on?")
        languages.in_field()
    adb("logcat", "-b", "events", "-c")
    before = state()
    text_before = checks.field()
    play("chord:shift+w:120")
    time.sleep(1)
    after = state()
    try:
        gained = repr(checks.field()[len(text_before):]) if checks.field().startswith(text_before) else "the text changed"
    except Stop as stop:
        gained = f"no field: {stop}"
    events = [line for line in adb("logcat", "-b", "events", "-d").splitlines()
              if re.search(r"wm_|am_|input_focus", line) and "titankeys" in line][-10:]
    record(f"shift-w{'-' + im if im else ''}", None, f"before: {before}\n      after: {after}\n      field gained: {gained}\n      events:\n        " + "\n        ".join(events or ["(none)"]))


CHECKS = {
    "66-steer": check_66_steer,
    "67-selectmore": check_67_selectmore,
    "68-hunts": check_68_hunts,
    "69-paste": check_69_paste,
    "69-clip": check_69_clip,
    "84-menu": check_84_menu,
    "shift-w": check_shift,
}
