#!/usr/bin/env python3
"""The owner's hands-only checks, played by the touch simulator, each with a YES or NO read back from the field where one exists.

Run from a shell that sourced tools/dev/env.sh, with TitanKeys Debug the keyboard. The checks type in TitanKeys Debug's own
Space gestures screen, which the gate brings back when anything else is in front. Every gesture and every setup key goes through
touch.sh or guarded.sh, which check the front window and the focused field first; a STOP ends the whole run. The run holds the
shared phone lock from start to end.

Usage: checks.py [NAME...]   (default: all; see --list)
Screenshots go to $TK_OUT/touch/shots, without the status bar, for what only the eye can judge (the dots, the trail, the pill).
"""
import fcntl
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ElementTree

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.environ.get("TK_APP", "com.titankeys.debug")
SERIAL = os.environ.get("TK_ADB_SERIAL", "")
results = []


class Stop(Exception):
    pass


def adb(*args):
    command = ["adb"] + (["-s", SERIAL] if SERIAL else []) + list(args)
    return subprocess.run(command, capture_output=True, text=True, timeout=60).stdout.replace("\r", "")


def play(*gestures, shot=None, at=0):
    """Plays gestures through touch.sh; with shot, a screenshot at ms after the start goes to shots/NAME.png."""
    command = ["bash", os.path.join(HERE, "touch.sh"), *gestures]
    env = dict(os.environ)
    if shot:
        env.update(TK_TOUCH_SHOT=shot, TK_TOUCH_SHOT_AT=str(at))
    result = subprocess.run(command, capture_output=True, text=True, env=env)
    out = (result.stdout + result.stderr).strip()
    if result.returncode == 1 or "STOP" in out:
        raise Stop(out)
    if result.returncode != 0:
        raise RuntimeError(out)
    time.sleep(.6)


def guarded(*args):
    """An adb input command for setting up the field, after the same checks as a gesture."""
    result = subprocess.run(["bash", os.path.join(HERE, "guarded.sh"), *args], capture_output=True, text=True)
    if result.returncode != 0:
        raise Stop((result.stdout + result.stderr).strip())
    time.sleep(.3)


def keyboard(which):
    """TitanKeys Debug ("debug", after the gate) or release TitanKeys ("release") as the keyboard, read back."""
    result = subprocess.run(["bash", os.path.join(HERE, "ime.sh"), which], capture_output=True, text=True)
    out = (result.stdout + result.stderr).strip()
    if result.returncode != 0 or "WARNING" in out:
        raise Stop(f"keyboard {which}: {out}")


def field():
    """The focused text field of TK_APP, read from the window's hierarchy: its text. The dump is deleted at once."""
    path = "/data/local/tmp/tk-touch-ui.xml"
    adb("shell", "uiautomator", "dump", path)
    xml = adb("shell", "cat", path)
    adb("shell", "rm", "-f", path)
    root = ElementTree.fromstring(xml[xml.find("<"):])
    for node in root.iter("node"):
        a = node.attrib
        if a.get("package") == APP and a.get("focused") == "true" and "EditText" in a.get("class", ""):
            return a.get("text", "")
    raise Stop("no focused field of the Debug build to read back")


def set_text(text):
    """Empties the field and types text into it, with the cursor at its end. Returns what the field then holds."""
    guarded("keycombination", "113", "29")  # Ctrl+A
    guarded("keyevent", "67")
    for chunk in re.findall(r".{1,60}", text.replace(" ", "%s")):
        guarded("text", chunk)
    return field()


def record(name, ok, detail, shot=None):
    verdict = "YES" if ok is True else "NO" if ok is False else "LOOK"
    results.append((name, verdict))
    print(f"{verdict:4} {name}: {detail}" + (f" (shots/{shot}.png)" if shot else ""), flush=True)


# --- The checks. ---

def check_89_flick_end():
    """#89: a flick at the end of a long text deletes the last word, and the dots show while it does."""
    text = " ".join(["the quick brown fox jumps over the lazy dog"] * 5)
    before = set_text(text)
    if before != text:
        return record("89-flick-end", None, f"setup typed {len(before)} characters, not {len(text)}: check autocorrect")
    play("flick", shot="89-flick-dots", at=60)
    after = field()
    record("89-flick-end", after == text[:-len("dog")], f"{len(text)} -> {len(after)} characters", "89-flick-dots")


def check_89_long_word():
    """#89: a flick on a word of 300 letters takes all of it, and nothing before it."""
    text = "start " + "abcdefghij" * 30
    before = set_text(text)
    if before != text:
        return record("89-long-word", None, "setup didn't type the long word whole")
    play("flick")
    after = field()
    record("89-long-word", after == "start ", f"left {len(after)} characters")


def check_93_flick_then_swipe():
    """#93: a swipe at once after a flick draws its whole trail, and types."""
    set_text("we are going to the")
    play("flick", "+40", "swipe:it", shot="93-trail", at=230)
    after = field()
    record("93-flick-then-swipe", after.rstrip() == "we are going to it", f"field ends with {after[-12:]!r}", "93-trail")


def check_83_pairs():
    """#83: two quick swipes 100 ms apart stay two words."""
    for first, second in [("go", "on"), ("in", "to"), ("is", "it")]:
        set_text("")
        play(f"swipe:{first}", "+100", f"swipe:{second}")
        words = field().split()
        record(f"83-{first}-{second}", words == [first, second], f"read {words}")


def check_swiped_apostrophe():
    """A swiped "its" before "going" becomes "it's"."""
    set_text("Bring a coat, ")
    play("swipe:its")
    play("swipe:going")
    after = field()
    record("swiped-its", "it's going" in after, f"read {after[len('Bring a coat, '):]!r}")


def check_84_pill():
    """#84: after a key press with the finger left resting, lifted or slid, the pill. The eye decides: a screenshot of each."""
    for after in ("rest", "lift", "slide"):
        set_text("")
        play(f"key:f:70:{after}", shot=f"84-{after}", at=300)
        record(f"84-{after}", None, "the pill after the press", f"84-{after}")


def check_sym_fn():
    """#72: Sym pressed and held, and fn held, reach TitanKeys. Needs keymap.sh first. Screenshots of what each opens."""
    for gesture, name in [("key:sym", "sym"), ("key:sym:700", "sym-held"), ("key:fn:700", "fn-held")]:
        play(gesture, shot=name, at=500)
        record(name, None, "what the key opened", name)
        guarded("keyevent", "111")  # Escape closes a panel it opened


def check_sym_log():
    """Why a held Sym opens the panel: TitanKeys Debug logs each Sym it hears (TitanKeysSym, never any text). Prints them."""
    play("key:sym:900:none", shot="sym-log", at=700)
    lines = [line for line in adb("logcat", "-d", "-v", "brief").splitlines() if "TitanKeysSym" in line][-8:]
    record("sym-log", None, "the keyboard's own words about the hold (see the shot for what opened):\n      "
           + "\n      ".join(lines or ["(no TitanKeysSym lines in the log)"]), "sym-log")


CHECKS = {
    "89-flick-end": check_89_flick_end,
    "89-long-word": check_89_long_word,
    "93-flick-then-swipe": check_93_flick_then_swipe,
    "83-pairs": check_83_pairs,
    "swiped-its": check_swiped_apostrophe,
    "84-pill": check_84_pill,
    "72-sym-fn": check_sym_fn,
    "sym-log": check_sym_log,
}


def lock():
    """Holds the shared phone lock for the whole run, as every phone tool does; the tools this starts see TK_PHONE_LOCKED."""
    subprocess.run(["adb", "start-server"], capture_output=True)
    folder = os.path.join(os.environ.get("TK_BUILD", os.path.expanduser("~/build")), "tk-tools")
    os.makedirs(folder, exist_ok=True)
    handle = open(os.path.join(folder, ".lock"), "w")
    try:
        fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError:
        print("waiting for the phone lock (another phone run holds it)", flush=True)
        fcntl.flock(handle, fcntl.LOCK_EX)
    os.environ["TK_PHONE_LOCKED"] = "1"
    # Python opens it so that no program started from here inherits it: the lock goes when this process ends.
    return handle


def main():
    names = sys.argv[1:]
    if names == ["--list"]:
        for name, check in CHECKS.items():
            print(f"{name:20} {check.__doc__.strip().splitlines()[0]}")
        return
    if os.environ.get("TK_PHONE_LOCKED") != "1":
        held = lock()  # noqa: F841 (kept open until the end)
    try:
        for name in names or CHECKS:
            # Debug is the keyboard only for the check itself, and release TitanKeys goes back right after, read back.
            keyboard("debug")
            os.environ["TK_IME_HELD"] = "1"
            try:
                CHECKS[name]()
            finally:
                os.environ.pop("TK_IME_HELD", None)
                keyboard("release")
    except Stop as stop:
        print(f"STOP: the run ended: {stop}", flush=True)
        sys.exit(1)
    passed = sum(1 for _, v in results if v == "YES")
    failed = sum(1 for _, v in results if v == "NO")
    print(f"{passed} YES, {failed} NO, {len(results) - passed - failed} to look at")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
