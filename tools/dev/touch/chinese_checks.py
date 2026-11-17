"""The Chinese bar's phone checks (#56, docs/languages.md "Chinese on the bar"), for checks.py --batch chinese-ui.

Each check types a Yale code in TitanKeys Debug's Space gestures field through the keyboard twin and the touch feed, reads the field
back, and takes a screenshot for what only the eye can judge: where the code, the mark and the choices sit. Rime learns from every
pick, so the choices' order moves during a run: a pick is judged by the screenshot taken just before it, which shows the row it
picked from, and the field it typed.

The keyboard twin's Shift can't be used here: on the Titan 2 Elite any Shift from it hides the keyboard and takes the field's focus,
in English and with the release keyboard too (2026-11-16). The checks that need Shift are listed for the owner's hands instead.
"""
import os
import time

import checks
import gestures
import languages
from checks import Stop, play, record

YALE = "yue-yale"
# Every "before" ends in a pause of at least 500 ms; the screenshot starts this long before its end.
SHOT_EARLY = 450
CODE = "neihou"
# A pending code goes key by key: Backspace takes one letter off.
CLEAR_CODE = [g for _ in range(20) for g in ("key:del", "+60")]
# What these checks can't do on this phone, for the owner to try by hand.
HANDS = [
    "Shift + the key under the third choice (R, or F) picks the third choice",
    "Georgian (ქართული on in Settings > Languages): Shift + W types ჭ",
]


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
    """No code pending and an empty field, so one check's leftovers never reach the next."""
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
    """The code floats above the bar's left edge, the mark at Q, the choices over W to O, P's "more" cue."""
    fresh()
    text = shot_then("zh-row", code("+400"), [])
    record("zh-row", text == "", f"field {text!r} while the code is pending (empty is right); the bar is the shot", "zh-row")


def check_space():
    """Space types the first choice."""
    fresh()
    text = shot_then("zh-space", code(), ["key:space", "+300"])
    record("zh-space", cjk(text), f"typed {text!r}; the row it picked from is the shot", "zh-space")


def check_hold():
    """Holding the key under the second choice (E) picks it; Space then types whatever of the code is left."""
    fresh()
    text = shot_then("zh-hold", code(), ["hold:e:800", "+300", "key:space", "+300"])
    record("zh-hold", None if cjk(text) else False, f"typed {text!r}: it should start with the shot's second choice", "zh-hold")


def check_flick():
    """A flick up from the key under the fourth choice (G, the second row's key in its column) picks it."""
    fresh()
    text = shot_then("zh-flick", code(), ["flickup:g", "+300", "key:space", "+300"])
    record("zh-flick", None if cjk(text) else False, f"typed {text!r}: it should start with the shot's fourth choice", "zh-flick")


def check_menu():
    """Holding P opens the bigger menu; Enter turns to the next page and Backspace back; a press picks; Sym closes it."""
    fresh()
    held = code("hold:p:800", "+600")
    shot_then("zh-menu-1", held, ["key:sym", "+300", *CLEAR_CODE])
    record("zh-menu-open", None, "the menu over three rows, ✕ at Sym, ↓ at Enter, the page at P", "zh-menu-1")
    fresh()
    shot_then("zh-menu-2", held + ["key:enter", "+600"], ["key:sym", "+300", *CLEAR_CODE])
    record("zh-menu-next", None, "Enter: the second page (2/N at P, ↑ at Backspace)", "zh-menu-2")
    fresh()
    shot_then("zh-menu-back", held + ["key:enter", "+500", "key:del", "+600"], ["key:sym", "+300", *CLEAR_CODE])
    record("zh-menu-back", None, "Backspace: the first page again (1/N)", "zh-menu-back")
    fresh()
    text = shot_then("zh-menu-pick", held, ["key:s", "+400", "key:space", "+300"])
    record("zh-menu-pick", None if cjk(text) else False, f"typed {text!r}: it should start with the shot's choice over S", "zh-menu-pick")
    fresh()
    shot_then("zh-menu-sym", held + ["key:sym", "+600"], [])
    record("zh-menu-sym", None, "Sym closed the menu and kept the code: the row is back", "zh-menu-sym")


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


BATCH = [check_yale, check_row, check_space, check_hold, check_flick, check_menu, check_enter, check_comma, check_english]


def finish():
    """No code left pending and the field empty, for whatever comes next."""
    try:
        fresh()
    except Stop:
        pass
