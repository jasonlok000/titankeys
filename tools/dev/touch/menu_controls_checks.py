"""Expanded choices controls and Space routing; requires the guarded private runner.

Register BATCH under expanded-menu-controls. Numeric Debug menu metadata proves
navigation; screenshots separately require visual review of the painted icons.
"""
import contextlib
import os
import re
import uuid
import checks
import language_checks
import languages

SEED = "alpha beta gamma delta"
RECEIVER = checks.APP + "/com.titankeys.keyboard.InputMethodsSet"
GESTURES = ("spacehold:1:2:600", "spacehold:2:-2:600", "spacehold:3:2:600")
STATE = re.compile(r"choices page=(\d+) pages=(\d+) previous=([01]) next=([01]) visible=([01]) bounds=(-?\d+),(-?\d+),(\d+),(\d+) row=(\d+) controls=(-?\d+),(-?\d+),(-?\d+),(-?\d+),(-?\d+),(-?\d+)")


def parse_menu(log, marker, pid):
    """Ignore every row before this attempt, foreign processes and malformed state."""
    if marker not in log:
        return None
    found = None
    for line in log.rsplit(marker, 1)[1].splitlines():
        if not re.search(r"TitanKeysMenu\(\s*" + re.escape(str(pid)) + r"\)", line):
            continue
        m = STATE.search(line)
        if not m:
            continue
        v = list(map(int, m.groups()))
        if v[0] < 1 or v[1] < v[0] or v[7] <= 0 or v[8] <= 0 or v[9] <= 0:
            continue
        found = dict(zip(("page", "pages", "previous", "next", "visible", "x", "y", "width", "height", "row", "previous_x", "previous_y", "next_x", "next_y", "close_x", "close_y"), v))
    return found


def cursor_from_dump(dump):
    m = re.search(r"mCursorSelStart=(-?\d+)\s+mCursorSelEnd=(-?\d+)", dump)
    if not m or min(map(int, m.groups())) < 0:
        return None
    return tuple(map(int, m.groups()))


def guard():
    if not os.environ.get("TK_SAFE_RUNNER"):
        raise checks.Stop("menu controls require the guarded private runner")
    if checks.APP != "com.titankeys.debug" or not languages.in_field():
        raise checks.Stop("menu controls require the owned Debug synthetic field")


def snapshot():
    guard()
    text = checks.field()
    raw = checks.adb("shell", "dumpsys", "input_method")
    guard()
    return text, cursor_from_dump(raw)


def mark():
    guard()
    marker = "charter-" + uuid.uuid4().hex
    pid = checks.adb("shell", "pidof", checks.APP).strip()
    if not re.fullmatch(r"[0-9]+", pid):
        raise checks.Stop("Debug process unavailable")
    checks.adb("shell", "log", "-t", "TitanKeysCodex", marker)
    return marker, pid


def observed(attempt):
    guard()
    marker, pid = attempt
    if checks.adb("shell", "pidof", checks.APP).strip() != pid:
        raise checks.Stop("Debug restarted during menu observation")
    log = checks.adb("logcat", "-d", "-t", "1024", "-v", "brief", "-s", "TitanKeysCodex:I", "TitanKeysMenu:I", "*:S")
    return parse_menu(log, marker, pid)


def action(name, moves=(), tap=None):
    attempt = mark()
    if tap:
        checks.guarded("tap", str(tap[0]), str(tap[1]))
    else:
        checks.play(*moves)
    checks.play("+300", shot=name, at=100)
    result = observed(attempt)
    checks.record(name + "-paint", None, "review the actual visible control and page counter in the guarded screenshot", name)
    return result


UNCHECKED = object()

def unchanged(name, before, state_ok=UNCHECKED):
    after = snapshot()
    known = before[1] is not None and after[1] is not None
    if state_ok is not UNCHECKED:
        checks.record(name + "-state", state_ok, "fresh observed menu state compared")
    checks.record(name + "-text", before[0] == after[0], "owned synthetic committed text compared")
    ok = before == after if known else None
    checks.record(name, ok, "owned synthetic text and cursor compared" if known else "cursor metadata unavailable; no position verdict")
    return after


def saved_methods(reply):
    m = re.search(r"on ([a-z0-9,;-]+); typing ([a-z0-9-]+)", reply)
    if not m:
        raise checks.Stop("method settings readback unavailable")
    return m.group(1).split(","), m.group(2)


@contextlib.contextmanager
def methods():
    guard()
    saved = saved_methods(checks.adb("shell", "am", "broadcast", "-n", RECEIVER, "--ez", "read", "true"))
    try:
        yield
    finally:
        guard()
        on, current = saved
        checks.adb("shell", "am", "broadcast", "-n", RECEIVER, "--es", "on", ",".join(on), "--es", "current", current)
        read = saved_methods(checks.adb("shell", "am", "broadcast", "-n", RECEIVER, "--ez", "read", "true"))
        if read != saved:
            raise checks.Stop("method restoration readback differs")


def select_method(current):
    guard()
    on = ["en", "zh-pinyin", "yue-jyutping"]
    reply = checks.adb("shell", "am", "broadcast", "-n", RECEIVER, "--es", "on", ",".join(on), "--es", "current", current)
    if saved_methods(reply) != (on, current):
        return False
    # Existing harness restart path, while the owned synthetic editor stays in
    # front. No input is sent while release is selected and no log is cleared.
    checks.keyboard("release")
    checks.keyboard("debug")
    guard()
    reply = checks.adb("shell", "am", "broadcast", "-n", RECEIVER, "--ez", "read", "true")
    return saved_methods(reply) == (on, current)


def prepare(expanded=True, switcher=False):
    guard()
    # Restart the owned field's input connection before clearing: a prior open
    # menu must not consume the setup's Delete/Ctrl+A as menu commands.
    if not select_method("en"):
        raise checks.Stop("English setup did not start")
    language_checks.fresh()
    if checks.set_text(SEED) != SEED:
        raise checks.Stop("synthetic setup text differed")
    # Keep a real editor cursor well away from both bounds.
    for _ in range(8):
        checks.guarded("keyevent", "21")
    if switcher:
        before = snapshot()
        action("menu-switcher-open", ["key:sym:900:none", "+500"])
        return before, None
    if not select_method("zh-pinyin"):
        raise checks.Stop("Pinyin setup did not start")
    before = snapshot()
    checks.play("key:s", "key:h", "key:i", "+500")
    if not expanded:
        checks.play("+300", shot="menu-compact-before", at=100)
        return before, None
    state = action("menu-expanded-open", ["key:shift", "+500"])
    if not state or not state["visible"] or state["page"] != 1 or not state["next"]:
        checks.record("menu-overflow", None, "expanded multi-page state not measured; skip dependent navigation")
        return before, None
    return before, state


def point(state, column, row):
    key = {(9, 1): "previous", (9, 2): "next", (8, 2): "close"}[(column, row)]
    x, y = state[key + "_x"], state[key + "_y"]
    if not state["visible"] or not (state["x"] <= x < state["x"] + state["width"] and
                                    state["y"] <= y < state["y"] + state["height"]):
        raise checks.Stop("observed menu control is outside its visible bounds")
    return x, y


def check_edges():
    with methods():
        before, first = prepare()
        if not first:
            return
        state = action("menu-first-previous", ["key:del", "+400"])
        unchanged("menu-first-edge", before, bool(state and state["page"] == 1 and not state["previous"]) if state else None)
        last = first
        for index in range(32):
            if not last["next"]:
                break
            new = action("menu-page-" + str(index + 2), ["key:enter", "+400"])
            if not new:
                checks.record("menu-last-edge", None, "fresh page state missing; stop paging")
                return
            if new["page"] != last["page"] + 1:
                checks.record("menu-page-progress", False, "observed next page did not advance")
                return
            unchanged("menu-page-editor-" + str(index), before)
            last = new
        if last["next"]:
            checks.record("menu-last-edge", None, "terminal page not reached in 32 transitions")
            return
        state = action("menu-last-next", ["key:enter", "+400"])
        unchanged("menu-last-edge", before, bool(state and state["page"] == last["page"] and not state["next"]) if state else None)
        state = action("menu-last-back", ["key:del", "+400"])
        unchanged("menu-last-back", before, bool(state and state["page"] == last["page"] - 1 and state["next"]) if state else None)
        state = action("menu-key-close", ["key:sym", "+400"])
        unchanged("menu-key-close", before, not bool(state["visible"]) if state else None)


def check_screen_controls():
    with methods():
        before, state = prepare()
        if not state:
            return
        for name, column, row, want in (("next", 9, 2, 2), ("previous", 9, 1, 1), ("close", 8, 2, 0)):
            state = action("menu-tap-" + name, tap=point(state, column, row))
            if not state:
                checks.record("menu-tap-" + name, None, "fresh menu state unavailable after tap")
                return
            ok = state["page"] == want if want else not state["visible"]
            unchanged("menu-tap-" + name, before, ok)


def check_space_routing():
    with methods():
        for mode in ("expanded", "switcher", "compact"):
            for index, gesture in enumerate(GESTURES):
                before, state = prepare(expanded=mode == "expanded", switcher=mode == "switcher")
                if mode == "expanded" and not state:
                    continue
                name = "menu-space-" + mode + "-" + str(index + 1)
                action(name, [gesture, "+400"])
                after = snapshot()
                known = before[1] is not None and after[1] is not None
                if mode == "expanded":
                    checks.record(name, before == after if known else None, "expanded menu must preserve owned text and selection")
                else:
                    # These canvases have no numeric visibility probe. Screenshots must confirm the precondition.
                    checks.record(name, None, mode + " visible-state review required; text_equal=" + str(before[0] == after[0]) + "; cursor_changed=" + str(known and before[1] != after[1]))


BATCH = [check_edges, check_screen_controls, check_space_routing]
