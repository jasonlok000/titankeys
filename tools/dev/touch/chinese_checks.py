"""The Chinese bar's phone checks (#56, docs/languages.md "Chinese on the bar"), for checks.py --batch chinese-ui.

Each check types a Yale code in TitanKeys Debug's Space gestures field through the keyboard twin and the touch feed, reads the field
back, and takes a screenshot for what only the eye can judge: where the code, the mark and the choices sit. Rime learns from every
pick, so the choices' order moves during a run: a pick is judged by the screenshot taken just before it, which shows the row it
picked from, and the field it typed.

The design as of main 99fb0dcd (owner, 2026-11-17): Space, a tap on the screen and a flick up pick from the row; no Shift, no
hold, no P. While a code is typed, Sym opens the bigger menu and Sym again closes it, and the orange ⋯ at P opens it on a tap;
with no code, Sym is the Sym panel. Enter types the code as letters.
"""
import os
import time

import checks
import gestures
import guide_checks
import languages
from checks import Stop, play, record

YALE = "yue-yale"
# Every "before" ends in a pause of at least 500 ms; the screenshot starts this long before its end.
SHOT_EARLY = 450
CODE = "neihou"
# A pending code goes key by key: Backspace takes one letter off.
CLEAR_CODE = [g for _ in range(20) for g in ("key:del", "+60")]
# What these checks can't do on this phone, for the owner to try by hand.
HANDS = ["Georgian (ქართული on in Settings > Languages): Shift + W types ჭ (the keyboard twin's Shift can't be used here)"]
# The more cue at P on the bar, on the 1076 x 1200 display: P's column, the bar's middle.
MORE_CUE = (1022, 1170)
FIFTH = (592, 1170)


def duration(*moves):
    """How long the gestures take, in ms, as the touch simulator plays them."""
    keys = os.path.join(os.environ.get("TK_OUT", os.path.expanduser("~/tk-out")), "touch", "keys.txt")

    class Args:
        # The phone's own codes (keymap.sh), as touch.sh plays them: Sym and fn have no Linux code.
        keymap = keys if os.path.exists(keys) else None
    Args.gestures = list(moves)
    return gestures.build(Args).t


def code(*after):
    """The code typed key by key, a pause, then the gestures after it."""
    return [f"key:{letter}" for letter in CODE] + ["+600", *after]


def fresh():
    """No code pending and an empty field, so one check's leftovers never reach the next, in Debug's Space gestures field (the
    guide's and the playground's type English only)."""
    guide_checks.leave_guide()
    if not languages.in_field():
        raise Stop(f"not in the Space gestures field: {languages.front().strip()}")
    play(*CLEAR_CODE)
    checks.set_text("")


def read():
    """The field's text, or None when it lost its focus."""
    try:
        return checks.field()
    except Stop:
        return None


def shot_then(name, before, after):
    """Plays before + after, with a screenshot in the pause that ends before: the state the next gesture acts on. A screenshot
    takes a few hundred ms of its own, so it starts SHOT_EARLY ms before the pause ends (the shakedown's were all late)."""
    play(*before, *after, shot=name, at=max(0, int(duration(*before)) - SHOT_EARLY))
    time.sleep(.5)
    return read()


def cjk(text):
    """Chinese characters only. A Space after a whole pick types a space of its own, so one at the end is fine."""
    text = (text or "").rstrip(" ")
    return bool(text) and all("　" <= ch <= "鿿" or "豈" <= ch <= "￯" or ord(ch) >= 0x20000 for ch in text)


def check_yale():
    """Yale is the input method, picked in Android's list with Alt+Sym, read back from Android."""
    ok = languages.switch_to(YALE, languages.subtype_ids())
    record("zh-yale", ok, "Yale is typing" if ok else "Alt+Sym didn't reach Yale: is it on in Settings > Languages?")
    if not ok:
        raise Stop("Yale isn't on")


def check_row():
    """The code floats above the bar's left edge (bar-sized, a soft orange glow), the mark at Q, the choices over W to O, and
    P's ⋯ in orange."""
    fresh()
    text = shot_then("zh-row", code("+400"), [])
    record("zh-row", text == "", f"field {text!r} while the code is pending (empty is right); the bar is the shot: the code"
           " bar-sized with an orange glow, 耶 at Q, ⋯ orange", "zh-row")


def check_space():
    """Space types the first choice."""
    fresh()
    text = shot_then("zh-space", code(), ["key:space", "+300"])
    record("zh-space", cjk(text), f"typed {text!r}; the row it picked from is the shot", "zh-space")


def check_tap():
    """A tap on the screen on the fifth choice picks it (the column over Y, where one-key choices put the fifth)."""
    fresh()
    shot_then("zh-tap", code("+400"), [])
    checks.guarded("tap", str(FIFTH[0]), str(FIFTH[1]))
    time.sleep(.6)
    play("key:space", "+300")
    text = read()
    record("zh-tap", None if cjk(text) else False, f"typed {text!r}: it should start with the shot's choice over Y", "zh-tap")


def check_flick():
    """A flick up from the key under the fourth choice (G, the second row's key in its column) picks it."""
    fresh()
    text = shot_then("zh-flick", code(), ["flickup:g", "+300", "key:space", "+300"])
    record("zh-flick", None if cjk(text) else False, f"typed {text!r}: it should start with the shot's fourth choice", "zh-flick")


def check_menu():
    """While a code is typed, Sym opens the bigger menu; Enter turns to the next page and Backspace back; a press picks; Sym again
    closes it. A tap on the orange ⋯ at P opens it too."""
    fresh()
    opened = code("key:sym", "+600")
    close = ["key:sym", "+400", *CLEAR_CODE]
    shot_then("zh-menu-1", opened, close)
    record("zh-menu-open", None, "Sym with a code: the menu over three rows, ✕ at Sym, ↓ at Enter, the page at P", "zh-menu-1")
    fresh()
    shot_then("zh-menu-2", opened + ["key:enter", "+600"], close)
    record("zh-menu-next", None, "Enter: the second page (2/N at P, ↑ at Backspace)", "zh-menu-2")
    fresh()
    shot_then("zh-menu-back", opened + ["key:enter", "+500", "key:del", "+600"], close)
    record("zh-menu-back", None, "Backspace: the first page again (1/N)", "zh-menu-back")
    fresh()
    text = shot_then("zh-menu-pick", opened, ["key:s", "+400", "key:space", "+300"])
    record("zh-menu-pick", None if cjk(text) else False, f"typed {text!r}: it should start with the shot's choice over S", "zh-menu-pick")
    fresh()
    shot_then("zh-menu-close", opened + ["key:sym", "+600"], [])
    record("zh-menu-close", None, "Sym again closed the menu and kept the code: the row is back", "zh-menu-close")
    fresh()
    play(*code("+400"))
    checks.guarded("tap", str(MORE_CUE[0]), str(MORE_CUE[1]))
    time.sleep(.8)
    record("zh-menu-tap", None, "a tap on ⋯ opened the menu", guide_checks.shot("zh-menu-tap"))
    play(*close)


def check_sym_panel():
    """With no code typed, Sym is the Sym panel, as in every language."""
    fresh()
    play("key:sym", "+700", shot="zh-sym-panel", at=int(duration("key:sym", "+700")) - SHOT_EARLY)
    record("zh-sym-panel", None, "Sym with no code: the Sym panel", "zh-sym-panel")
    checks.guarded("keyevent", "4")  # Back closes the panel
    time.sleep(.8)


def check_enter():
    """Enter types the code as letters."""
    fresh()
    text = shot_then("zh-enter", code(), ["key:enter", "+400"])
    record("zh-enter", text == CODE, f"typed {text!r}, wanted {CODE!r}", "zh-enter")


def check_comma():
    """A comma with a code pending (Alt+N on this phone) types the first choice, then "，"."""
    fresh()
    text = shot_then("zh-comma", code(), ["chord:alt+n", "+400"])
    ok = bool(text) and text.endswith("，") and cjk(text[:-1])
    record("zh-comma", ok, f"typed {text!r}: the shot's first choice, then ，", "zh-comma")


def check_english():
    """ "hello" is offered as an English word, second; "man" isn't."""
    for word in ("hello", "man"):
        fresh()
        shot_then(f"zh-{word}", [f"key:{letter}" for letter in word] + ["+600"], [])
        want = "hello second, never first" if word == "hello" else "no English word"
        record(f"zh-{word}", None, f"{word}: {want}", f"zh-{word}")


BATCH = [check_yale, check_row, check_space, check_tap, check_flick, check_menu, check_sym_panel, check_enter, check_comma, check_english]


def finish():
    """No code left pending and the field empty, for whatever comes next."""
    try:
        fresh()
    except Stop:
        pass
