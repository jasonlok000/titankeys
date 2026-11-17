#!/usr/bin/env python3
"""The owner's hands-only checks, played by the touch simulator, each with a YES or NO read back from the field where one exists.

Run from a shell that sourced tools/dev/env.sh, with TitanKeys Debug the keyboard. The checks type in TitanKeys Debug's own
Space gestures screen, which the gate brings back when anything else is in front. Every gesture and every setup key goes through
touch.sh or guarded.sh, which check the front window and the focused field first; a STOP ends the whole run. The run holds the
shared phone lock from start to end.

Usage: checks.py [NAME...]   (default: all; see --list)
       checks.py --batch NAME  (a whole batch unattended, Debug the keyboard throughout; see BATCHES)
Screenshots go to $TK_OUT/touch/shots, without the status bar, for what only the eye can judge (the dots, the trail, the pill).
"""
import importlib
import os
import tempfile
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ElementTree

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.environ.get("TK_APP", "com.titankeys.debug")
SERIAL = os.environ.get("TK_ADB_SERIAL", "")
results = []
# Each result in full, for a batch's report: (name, verdict, detail, shot).
details = []


class Stop(Exception):
    pass


def adb(*args, timeout=60):
    command = ["adb"] + (["-s", SERIAL] if SERIAL else []) + list(args)
    return subprocess.run(command, capture_output=True, text=True, timeout=timeout).stdout.replace("\r", "")


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
    # Chunked first, then each chunk's spaces as adb's %s: chunking after could split a %s and type it (2026-11-16: "lazy%sdog").
    for chunk in re.findall(r".{1,60}", text, re.S):
        guarded("text", chunk.replace(" ", "%s"))
    return field()


def record(name, ok, detail, shot=None):
    verdict = "YES" if ok is True else "NO" if ok is False else "LOOK"
    results.append((name, verdict))
    details.append((name, verdict, detail, shot))
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


def check_89_long_delete():
    """#89: a flick with the finger left down deletes word by word, the chomper eating a dot for each, for 10 s at most: then
    it stops though the finger stays. 400 words can't all go in 10 s, so some must be left."""
    text = " ".join(["the quick brown fox jumps over the lazy dog"] * 45)
    # Measured from what the field holds after setup: autocorrect may add a few characters (2026-11-16: 1984 for 1979).
    before = set_text(text)
    if len(before) < 1500:
        return record("89-long-delete", None, f"setup typed only {len(before)} characters of {len(text)}")
    play("flick:3.2:90:14000", shot="89-chomper", at=3000)
    after = field()
    gone = len(before) - len(after)
    record("89-long-delete", 0 < gone < len(before) and before.startswith(after),
           f"{gone} of {len(before)} characters deleted in a 14 s hold (stops at 10 s); the chomper is the shot", "89-chomper")


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
    """Why a held Sym opens the panel: TitanKeys Debug logs each Sym it hears (TitanKeysSym, never any text). Prints them.

    This firmware keeps apps' info lines out of the log (log.tag is M). A tag's own property comes first, so log.tag.TitanKeysSym
    lets that one tag through for the check: a debug property, not a setting, gone at the next restart, and cleared here after.
    """
    adb("shell", "setprop", "log.tag.TitanKeysSym", "I")
    if adb("shell", "getprop", "log.tag.TitanKeysSym").strip() != "I":
        return record("sym-log", None, "the shell may not set log.tag.TitanKeysSym: try a W line or the raw log instead")
    try:
        play("key:sym:900:none", shot="sym-log", at=700)
        lines = [line for line in adb("logcat", "-d", "-v", "brief", "-s", "TitanKeysSym").splitlines() if "TitanKeysSym" in line][-8:]
    finally:
        adb("shell", "setprop", "log.tag.TitanKeysSym", "''")
    record("sym-log", None, "the keyboard's own words about the hold (see the shot for what opened):\n      "
           + "\n      ".join(lines or ["(no TitanKeysSym lines in the log)"]), "sym-log")


CHECKS = {
    "89-flick-end": check_89_flick_end,
    "89-long-word": check_89_long_word,
    "89-long-delete": check_89_long_delete,
    "93-flick-then-swipe": check_93_flick_then_swipe,
    "83-pairs": check_83_pairs,
    "swiped-its": check_swiped_apostrophe,
    "84-pill": check_84_pill,
    "72-sym-fn": check_sym_fn,
    "sym-log": check_sym_log,
}


# Named batches: a module whose BATCH is its checks in order, and optional HANDS (what only the owner's hands can check) and
# finish() (a tidy end).
BATCHES = {"chinese-ui": "chinese_checks", "languages": "language_checks", "guide": "guide_batch", "gestures": "gesture_batch",
           "re-tests": "retest_batch", "space-middle": "space_batch", "languages-page": "page_batch",
           "switcher": "switcher_checks", "bar-words": "bar_checks",
           "settings-warnings": "settings_checks", "leak-85": "leak_batch",
           "gestures-fix": "gesture_fix_batch",
           "first-use": "first_use_batch",
           "rime-files": "rime_batch",
           "key-feed": "keyfeed_batch",
           "shift": "shift_checks",
           "delete-animation": "delete_batch",
           "how-to": "howto_batch",
           "review-fixes": "review_batch",
           "interactions": "interactions_batch",
           "pt-spelling": "pt_spelling_batch",
           "worker-lines": "worker_lines_batch",
           "batch2": "batch2_checks",
           "sym-hold": "sym_hold_batch",
           "zh-back": "zh_back_batch",
           "heap": "heap_batch",
           "b-lines": "b_lines_batch"}


def report(name, stamp, hands):
    """One file with every result, its shot and what's left for hands: $TK_OUT/touch/report-NAME-STAMP.md."""
    out = os.path.join(os.environ.get("TK_OUT", os.path.expanduser("~/tk-out")), "touch")
    os.makedirs(out, exist_ok=True)
    path = os.path.join(out, f"report-{name}-{stamp}.md")
    counts = {v: sum(1 for _, each, _, _ in details if each == v) for v in ("YES", "NO", "LOOK")}
    lines = [f"# {name}, {stamp}", "", f"{counts['YES']} YES, {counts['NO']} NO, {counts['LOOK']} to look at", ""]
    for check, verdict, detail, shot in details:
        lines.append(f"- **{verdict}** {check}: {detail}" + (f" (`shots/{shot}.png`)" if shot else ""))
    if hands:
        lines += ["", "For the owner's hands:"] + [f"- {each}" for each in hands]
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    return path


def to_field():
    """Back out of TitanKeys Debug's other screens (Settings, the guide) so the gate can bring its field's screen: an am start
    only brings the task forward, with whatever screen is on top of it (2026-11-16, after About). Never Back over another app."""
    field_screen = os.environ.get("TK_TOUCH_ACTIVITY", "")
    for _ in range(5):
        front = next((line for line in adb("shell", "dumpsys", "window").splitlines() if "mCurrentFocus" in line), "")
        if (field_screen and field_screen in front) or f" {APP}/" not in front:
            return
        adb("shell", "input", "keyevent", "4")
        time.sleep(1.2)


def past_cutoff():
    """True from TK_CUTOFF_EPOCH (a Unix time) on: a night's run stops at the next check, so the owner gets the phone back
    by morning (2026-11-17: 07:30)."""
    cutoff = os.environ.get("TK_CUTOFF_EPOCH")
    return bool(cutoff) and time.time() >= float(cutoff)


# Too hot to test: MediaTek's battery warning in front, or the battery over this (2026-11-17: 51.0 °C at 04:19, the warning
# up from 02:47, and every batch after stopped at its gate). The run waits instead, re-checking every HEAT_STEP_SECONDS.
HEAT_LIMIT_C = 45.0
HEAT_STEP_SECONDS = 300
HEAT_WAIT_STEPS = 12  # 60 minutes


def phone_heat():
    """The battery's temperature in °C (None if unreadable) and whether MediaTek's battery warning is in front."""
    found = re.search(r"temperature: (\d+)", adb("shell", "dumpsys", "battery"))
    front = next((line for line in adb("shell", "dumpsys", "window").splitlines() if "mCurrentFocus" in line), "")
    return (int(found.group(1)) / 10 if found else None), "com.mediatek.batterywarning" in front


def wait_cool():
    """Waits while the phone is too hot or warns, up to an hour, saying the temperature each time; Stop if it never cools."""
    for step in range(HEAT_WAIT_STEPS + 1):
        celsius, warning = phone_heat()
        if not warning and (celsius is None or celsius <= HEAT_LIMIT_C):
            if step:
                print(f"{time.strftime('%T')} the phone is fine again: {celsius} °C", flush=True)
            return
        print(f"{time.strftime('%T')} the phone is too hot to test: {celsius} °C, the battery warning "
              f"{'in front' if warning else 'not shown'}; waiting ({step * HEAT_STEP_SECONDS // 60} of "
              f"{HEAT_WAIT_STEPS * HEAT_STEP_SECONDS // 60} min)", flush=True)
        if step == HEAT_WAIT_STEPS or past_cutoff():
            break
        time.sleep(HEAT_STEP_SECONDS)
    raise Stop(f"the phone stayed too hot or kept its battery warning ({celsius} °C)")


# The crash guard (the Master, 2026-11-17, for night runs): after each check, a new FATAL of TitanKeys Debug in logcat's crash buffer
# ends the batch with "STOP: crash", its trace retraced with the build's kept mapping (debug.sh writes <apk>.mapping-<id8>.txt), and
# Debug's own crash dialog, if it's in front, gets one Back. The runner then goes on with the next batch.
MAPPINGS = os.environ.get("TK_MAPPING_DIR", os.path.expanduser("~/build/m1-apks"))


def crash_blocks():
    """TitanKeys Debug's FATAL EXCEPTION blocks in the crash buffer, oldest first, each as its lines."""
    blocks, block = [], None
    for line in adb("logcat", "-d", "-b", "crash", "-v", "time").splitlines():
        if "FATAL EXCEPTION" in line:
            block = [line]
            blocks.append(block)
        elif block is not None and "AndroidRuntime" in line:
            block.append(line)
        else:
            block = None
    return [b for b in blocks if any(f"Process: {APP}," in line for line in b)]


def retraced(block):
    """The block's frames, retraced with the kept mapping its r8-map-id names, or as they are when there's none."""
    raw = "\n".join(line.split("): ", 1)[-1] for line in block)
    found = re.search(r"r8-map-id-([0-9a-f]{8})", raw)
    mapping = None
    if found and os.path.isdir(MAPPINGS):
        mapping = next((os.path.join(MAPPINGS, f) for f in sorted(os.listdir(MAPPINGS)) if f"mapping-{found.group(1)}" in f), None)
    retrace = os.path.expanduser("~/android-sdk/cmdline-tools/latest/bin/retrace")
    if not mapping or not os.path.exists(retrace):
        return raw + f"\n(not retraced: {'no mapping for ' + found.group(1) if found else 'no map id'})"
    with tempfile.NamedTemporaryFile("w", suffix=".txt", delete=False) as handle:
        handle.write(raw)
    try:
        return subprocess.run([retrace, mapping, handle.name], capture_output=True, text=True, timeout=300).stdout
    finally:
        os.unlink(handle.name)


def crash_check(seen):
    """If Debug crashed since [seen] blocks: the trace saved, the dialog cleared if it's Debug's, and a Stop."""
    blocks = crash_blocks()
    if len(blocks) <= seen:
        return
    out = os.path.join(os.environ.get("TK_OUT", os.path.expanduser("~/tk-out")), "touch", f"crash-{time.strftime('%Y%m%d-%H%M%S')}.txt")
    with open(out, "w", encoding="utf-8") as handle:
        handle.write(retraced(blocks[-1]))
    time.sleep(1)
    front = next((line for line in adb("shell", "dumpsys", "window").splitlines() if "mCurrentFocus" in line), "")
    # Only Debug's own crash dialog ("Application Error: com.titankeys.debug"), never another app's window.
    if APP in front and any(word in front for word in ("Application Error", "Application Not Responding")):
        adb("shell", "input", "keyevent", "4")
    raise Stop(f"crash: TitanKeys Debug died ({blocks[-1][2].split('): ', 1)[-1][:160] if len(blocks[-1]) > 2 else 'see the trace'});"
               f" the trace, retraced, in {out}")


def run_batch(name):
    """Runs a batch on its own: Debug is the keyboard from the start to the end, and release TitanKeys comes back only then.
    The gates still run before every gesture. A check that breaks is a NO and the batch goes on; a STOP ends it. The report is
    written either way."""
    sys.path.insert(0, HERE)
    module = importlib.import_module(BATCHES[name])
    stamp = time.strftime("%Y%m%d-%H%M%S")
    stopped = None
    try:
        # Inside the try: a gate that fails here still gets its report and release back (2026-11-16, after a crash dialog).
        wait_cool()  # before the first gate, which would stop on the battery warning
        to_field()
        keyboard("debug")
        os.environ["TK_IME_HELD"] = "1"
        celsius, warning = phone_heat()
        print(f"{time.strftime('%T')} battery at the batch's start: {celsius} °C, warning {'in front' if warning else 'not shown'}",
              flush=True)
        crashes = len(crash_blocks())
        for check in module.BATCH:
            if past_cutoff():
                raise Stop("the morning cutoff came (TK_CUTOFF_EPOCH): the rest of the batch didn't run")
            wait_cool()
            try:
                check()
            except Stop:
                raise
            except (Exception, SystemExit) as error:  # noqa: BLE001 (one broken check mustn't end the night's run)
                record(check.__name__, False, f"the check broke: {error!r}")
            crash_check(crashes)
        if hasattr(module, "finish"):
            module.finish()
    except Stop as stop:
        stopped = stop
        record("batch", None, f"STOP: {stop}" if str(stop).startswith("crash") else f"STOP, the run ended: {stop}")
    finally:
        os.environ.pop("TK_IME_HELD", None)
        # A night of batches keeps Debug from the first to the last (TK_BATCH_KEEP_DEBUG=1); the runner sets release back after.
        if os.environ.get("TK_BATCH_KEEP_DEBUG") != "1":
            keyboard("release")
        path = report(name, stamp, getattr(module, "HANDS", []))
        print(f"report: {path}", flush=True)
    return stopped


def lock():
    """Holds the phone lock for the whole run (tools/phone_lock.py); the tools this starts see TK_PHONE_LOCKED."""
    sys.path.insert(0, os.path.join(HERE, "../.."))
    import phone_lock
    phone_lock.hold()


def main():
    # The guide's checks live in guide_checks.py, which uses this module's helpers: the same module, not a second copy of it.
    sys.modules.setdefault("checks", sys.modules[__name__])
    import guide_checks
    CHECKS.update(guide_checks.CHECKS)
    names = sys.argv[1:]
    if names[:1] == ["--batch"]:
        if len(names) != 2 or names[1] not in BATCHES:
            sys.exit(f"--batch takes one of: {', '.join(BATCHES)}")
        if os.environ.get("TK_PHONE_LOCKED") != "1":
            held = lock()  # noqa: F841 (kept open until the end)
        stopped = run_batch(names[1])
        failed = sum(1 for _, v in results if v == "NO")
        print(f"{sum(1 for _, v in results if v == 'YES')} YES, {failed} NO, {len(results)} in all")
        sys.exit(1 if stopped or failed else 0)
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
