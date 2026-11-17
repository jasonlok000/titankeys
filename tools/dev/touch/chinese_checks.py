"""The Chinese bar's phone checks (#56, docs/languages.md "Chinese on the bar"), for checks.py --batch chinese-ui.

Each check types a Yale code in TitanKeys Debug's Space gestures field through the keyboard twin and the touch feed, reads the field
back, and takes a screenshot for what only the eye can judge: where the code, the mark and the choices sit. Rime learns from every
pick, so the choices' order moves during a run: a pick is judged by the screenshot taken just before it, which shows the row it
picked from, and the field it typed.

The design as of B's merge (main 0da40b8a): Space, a tap on the screen and a swipe up on the touchpad; a swipe up (or Shift, or
a tap on the orange ⋯) opens the bigger menu and Shift or Sym closes it; Sym with no code is the Sym panel; Enter types the code
as letters. The keyboard twin's Shift can't be used on this phone (it takes the field's focus), so the checks open the menu by a
swipe up or ⋯ and close it with Sym, and Shift's own part is for the owner's hands.
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
HANDS = ["Georgian (ქართული on in Settings > Languages): Shift + W types ჭ (the keyboard twin's Shift can't be used here)",
         "Chinese: Shift with a code pending opens the bigger menu, and Shift again closes it"]
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
    """Yale and Jyutping on with English, Yale typing (Debug's InputMethodsSet), read back from the keyboard's own log."""
    import language_checks
    fresh()
    ok = language_checks.turn_on(["en", YALE, "yue-jyutping"], YALE)
    record("zh-yale", ok, "the field started with Yale" if ok else "the field didn't start with Yale")
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


def check_swipe_up():
    """A swipe up from B opens the bigger menu with a code pending, in Yale (nei), Japanese (nihon), Vietnamese (ta) and Hindi
    (nam), types nothing, and Sym closes it; in Korean nothing opens. A short flick up (a third of a row) opens nothing."""
    import language_checks
    for im, typed in (("yue-yale", "nei"), ("ja", "nihon"), ("vi", "ta"), ("hi", "nam"), ("ko", "gk")):
        fresh()
        if not language_checks.turn_on(["en", im], im):
            record(f"swipe-up-{im}", False, f"the field didn't start with {im}")
            continue
        fresh()
        text = shot_then(f"swipe-up-{im}", [f"key:{k}" for k in typed] + ["+400", "swipeup:b", "+600"],
                         ["key:sym", "+500", *CLEAR_CODE])
        want = "nothing opens (Korean has no menu)" if im == "ko" else "the bigger menu over three rows"
        record(f"swipe-up-{im}", None, f"{typed} then a swipe up: {want}; the field after Sym and clearing: {text!r}",
               f"swipe-up-{im}")
    language_checks.turn_on(["en", YALE, "yue-jyutping"], YALE)
    fresh()
    shot_then("swipe-up-short", code("swipeup:g:0.3:60", "+600"), [*CLEAR_CODE])
    record("swipe-up-short", None, "a short flick up (0.3 rows in 60 ms): nothing opens, the row stays", "swipe-up-short")


def check_menu():
    """The bigger menu, opened by a swipe up with a code pending: Enter turns to the next page and Backspace back; a press
    picks; a finger tap on a cell picks and logs (TitanKeysTap); Sym closes it. A tap on the orange ⋯ at P opens it too."""
    import switcher_checks
    fresh()
    opened = code("swipeup:b", "+600")
    close = ["key:sym", "+400", *CLEAR_CODE]
    shot_then("zh-menu-1", opened, close)
    record("zh-menu-open", None, "a swipe up with a code: the menu over three rows, the page at P", "zh-menu-1")
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
    shot_then("zh-menu-cell", opened, [])
    # The cell over W on the menu's top row, as the Sym panel's (y 970 on the 1076 x 1200 display).
    lines = switcher_checks.tap_lines(lambda: checks.guarded("tap", str(switcher_checks.column_x(1)), "970"))
    play("key:space", "+300")
    text = read()
    record("zh-menu-tap-cell", switcher_checks.tapped(lines) and cjk(text),
           f"a finger tap on the top row's first cell typed {text!r} (the shot's cell over W); TitanKeysTap: {lines}",
           "zh-menu-cell")
    fresh()
    shot_then("zh-menu-close", opened + ["key:sym", "+600"], [])
    record("zh-menu-close", None, "Sym closed the menu and kept the code: the row is back", "zh-menu-close")
    fresh()
    play(*code("+400"))
    checks.guarded("tap", str(MORE_CUE[0]), str(MORE_CUE[1]))
    time.sleep(.8)
    record("zh-menu-tap", None, "a tap on ⋯ opened the menu", guide_checks.shot("zh-menu-tap"))
    play(*close)
    checks.adb("shell", "setprop", "log.tag.TitanKeysTap", "''")


def check_yale_codes():
    """Yale's j/z and n/l (B): "lei" + Space types 你, and "zungman" + Space types 中文."""
    import language_checks
    language_checks.turn_on(["en", YALE, "yue-jyutping"], YALE)
    for typed, want in (("lei", "你"), ("zungman", "中文")):
        fresh()
        text = shot_then(f"yale-{typed}", [f"key:{k}" for k in typed] + ["+500"], ["key:space", "+400"])
        record(f"yale-{typed}", (text or "").strip() == want, f"{typed} + Space typed {text!r}, wanted {want!r}", f"yale-{typed}")


def check_shape_codes():
    """Cangjie (hqi + Space types 我) and Zhuyin on the Hsu layout (ne, ㄋㄧ, + Space types one character), each with starters
    on an empty field first."""
    import language_checks
    for im, typed, want in (("zh-cangjie", "hqi", "我"), ("zh-zhuyin", "ne", None)):
        fresh()
        if not language_checks.turn_on(["en", im], im):
            record(f"code-{im}", False, f"the field didn't start with {im}")
            continue
        fresh()
        play("+800", shot=f"starters-{im}", at=500)
        record(f"starters-{im}", None, f"{im}, empty field: starters on the bar", f"starters-{im}")
        text = shot_then(f"code-{im}", [f"key:{k}" for k in typed] + ["+500"], ["key:space", "+400"])
        ok = (text or "").strip() == want if want else (cjk(text) and len((text or "").strip()) == 1)
        record(f"code-{im}", ok, f"{typed} + Space typed {text!r}, wanted {want or 'one character'!r}", f"code-{im}")
    language_checks.turn_on(["en", YALE, "yue-jyutping"], YALE)


def check_sym_panel():
    """With no code typed, Sym is the Sym panel, as in every language."""
    fresh()
    play("key:sym", "+700", shot="zh-sym-panel", at=int(duration("key:sym", "+700")) - SHOT_EARLY)
    record("zh-sym-panel", None, "Sym with no code: the Sym panel", "zh-sym-panel")
    checks.guarded("keyevent", "4")  # Back closes the panel
    time.sleep(.8)


def suggestions(name, method, keys, then):
    """A code, Space for its first choice, then the bar's next-word suggestions (a shot), and Space again: the first of them."""
    import language_checks
    fresh()
    language_checks.turn_on(["en", YALE, "yue-jyutping", "zh-pinyin"], method)
    fresh()
    first = shot_then(f"{name}-after", [f"key:{k}" for k in keys] + ["+500", "key:space", "+800"], [])
    second = shot_then(f"{name}-next", ["key:space", "+800"], [])
    record(name, None if cjk(second) and len((second or "").strip()) > len((first or "").strip()) else False,
           f"{''.join(keys)} + Space typed {first!r}; Space again typed {second!r}: {then}", f"{name}-after")


def check_suggestions():
    """Chinese suggestions with nothing typed (main 7a75c4bb): after a pick, the bar offers what comes next, and Space types
    the first. Pinyin runs in the Characters setting the phone has (Hong Kong): the receiver can't set Simplified."""
    suggestions("zh-next-yale", YALE, "nei", "the bar after the pick shows 嘅 哋 唔…, and Space types 嘅")
    suggestions("zh-next-pinyin", "zh-pinyin", "wo", "the bar after the pick shows 为/為 母亲/母親…, and Space types the first")
    import language_checks
    language_checks.turn_on(["en", YALE, "yue-jyutping"], YALE)


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


BATCH = [check_yale, check_row, check_space, check_tap, check_yale_codes, check_swipe_up, check_menu, check_sym_panel, check_enter,
         check_comma, check_english, check_shape_codes,
         check_suggestions]


def finish():
    """No code left pending and the field empty, for whatever comes next."""
    try:
        fresh()
    except Stop:
        pass
