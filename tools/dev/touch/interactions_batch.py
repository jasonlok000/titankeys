"""The owner's weird interactions (2026-11-17), chained as real sequences, for checks.py --batch interactions: the bar stuck on
the last language right after a switch, the middle word differing from what Space types (#98), Yale's loading line once its
engine is warm. After every step the report says what the bar showed and what the field holds.

What the bar shows comes from TitanKeys Debug's own log, TitanKeysBar (D's, Debug only, on while log.tag.TitanKeysBar is D):
  bar: IM LEFT | MIDDLE | RIGHT      each time the bar's words change, "-" for an empty place
  bar notice: TEXT                   a line on the bar, as "Warming up Yale…"
BAR and NOTICE below parse them. A Debug without these lines gives a LOOK for each bar check; the field checks still count.

1. Switching, each between en, yue-yale, ja and ko, typing at once after the switch: the two-language toggle (a held Sym),
   the held Sym's switcher with three on (a tile's key), Alt + Sym away to the next keyboard and Debug back. After each, every
   bar line belongs to the new input method, or the bar is empty: never the old one's (O).
2. #98 in each: for short prefixes, the bar's middle word is exactly what Space types (O).
3. Mixed, then a switch: a code pending, a flick, a pick from the bigger menu, the Sym panel open. The bar after it is the new
   input method's (O), with the field and a shot.
4. Yale's loading line: Pinyin typing first, so Rime is warm; a switch to Yale shows no "Warming up" (O).
"""
import re
import time

import checks
import language_checks
import switcher_checks
from checks import APP, Stop, adb, play, record

BAR = re.compile(r"bar: (\S+) (.*?) \| (.*?) \| (.*)$")
NOTICE = re.compile(r"bar notice: (.*)$")
TAG = "TitanKeysBar"
HOLD_SYM = switcher_checks.HOLD_SYM
TILE_KEYS = ["e", "t", "u"]  # the held Sym's tiles with three on: over E-R, T-Y and U-I, in the order they're on
# Short prefixes in each, typed key by key (Korean on its two-set layout: dk 아, rk 가, sk 나).
PREFIXES = {"en": ["di", "th", "wh"], "yue-yale": ["nei", "ngo", "hai"], "ja": ["ni", "ka", "wa"], "ko": ["dk", "rk", "sk"]}
PAIRS = [("en", "yue-yale"), ("yue-yale", "ja"), ("ja", "ko"), ("ko", "en")]
ALL = ["en", "yue-yale", "ja", "ko"]


def keys(word):
    return [g for letter in word for g in (f"key:{letter}:60:none", "+90")]


def bar_lines():
    """TitanKeysBar's lines since the log was last cleared: ("bar", im, [left, middle, right]) or ("notice", text)."""
    out = []
    for line in adb("logcat", "-d", "-s", TAG).splitlines():
        if f"{TAG}:" not in line:
            continue
        text = line.split(f"{TAG}:", 1)[-1].strip()
        if m := BAR.match(text):
            out.append(("bar", m.group(1), [w.strip() for w in m.group(2, 3, 4)]))
        elif m := NOTICE.match(text):
            out.append(("notice", m.group(1)))
    return out


def shown(lines):
    """The bar lines, short, for the report."""
    return [f"{l[1]} {' | '.join(l[2])}" if l[0] == "bar" else f"notice {l[1]}" for l in lines][-6:] or "no bar lines"


def mark():
    adb("logcat", "-c")


def start(on, current):
    language_checks.fresh()
    if not language_checks.turn_on(on, current):
        raise Stop(f"the field didn't start with {current}")
    language_checks.fresh()
    adb("shell", "setprop", f"log.tag.{TAG}", "D")
    mark()


def read():
    try:
        return checks.field()
    except Stop:
        return None


def judge_switch(name, old, new, typed_prefix):
    """After a switch from [old] to [new] and the prefix typed at once: every bar line is [new]'s, never [old]'s."""
    lines = bar_lines()
    bars = [l for l in lines if l[0] == "bar"]
    text = read()
    if not bars:
        return record(name, None, f"no bar lines ({TAG} missing from this Debug?); the field {text!r}")
    wrong = [l for l in bars if l[1] != new]
    record(name, not wrong, f"{old} to {new}, then {typed_prefix!r} at once: the bar {shown(lines)}; the field {text!r}"
           + (f"; {len(wrong)} bar line(s) not {new}'s" if wrong else ""))


def check_toggle():
    """Two on: a held Sym switches straight to the other; the new one's prefix typed at once."""
    for old, new in PAIRS:
        start([old, new], old)
        prefix = PREFIXES[new][0]
        play(*HOLD_SYM, *keys(prefix), "+700")
        judge_switch(f"toggle-{old}-{new}", old, new, prefix)


def check_switcher():
    """Three on: the held Sym's switcher, a tile's key, and the new one's prefix at once."""
    for on in (["en", "yue-yale", "ja"], ["en", "ja", "ko"], ["yue-yale", "ko", "en"]):
        for index in (1, 2):
            start(on, on[0])
            new = on[index]
            prefix = PREFIXES[new][1]
            play(*HOLD_SYM, f"key:{TILE_KEYS[index]}", "+500", *keys(prefix), "+700")
            judge_switch(f"switcher-{on[0]}-{new}", on[0], new, prefix)


def check_alt_sym():
    """Alt + Sym: the next keyboard (away from Debug), then Debug back; the prefix at once, in the input method it had."""
    for im in ALL:
        start(["en", im] if im != "en" else ["en", "ja"], im)
        play("chord:alt+sym", "+1500")
        away = adb("shell", "settings", "get", "secure", "default_input_method").strip()
        checks.keyboard("debug")
        language_checks.languages.in_field()
        mark()
        prefix = PREFIXES[im][2]
        play(*keys(prefix), "+700")
        lines = bar_lines()
        bars = [l for l in lines if l[0] == "bar"]
        text = read()
        record(f"alt-sym-{im}", (not [l for l in bars if l[1] != im]) if bars else None,
               f"Alt + Sym went to {away or 'nothing'}; Debug back, {prefix!r} at once: the bar {shown(lines)}; the field {text!r}")


def space_types_middle(im, prefix):
    """#98: the bar's middle word after [prefix] is what Space types."""
    start(["en", im] if im != "en" else ["en"], im)
    play(*keys(prefix), "+700")
    bars = [l for l in bar_lines() if l[0] == "bar"]
    middle = bars[-1][2][1] if bars else None
    play("key:space", "+600")
    text = read()
    name = f"98-{im}-{prefix}"
    if middle in (None, "", "-"):
        return record(name, None, f"{prefix!r}: no middle word logged; Space typed {text!r}")
    typed = (text or "").rstrip(" ")
    record(name, typed == middle, f"{prefix!r}: middle {middle!r}, Space typed {text!r}")


def check_98():
    for im in ALL:
        for prefix in PREFIXES[im]:
            space_types_middle(im, prefix)


def mixed(name, on, before, new, prefix, shot_at=None):
    """[before] in on[0], then a held Sym to [new] (two on: straight to it) and its prefix at once."""
    start(on, on[0])
    play(*before)
    mark()
    play(*HOLD_SYM, *keys(prefix), "+700", shot=name, at=shot_at or 1500)
    lines = bar_lines()
    bars = [l for l in lines if l[0] == "bar"]
    text = read()
    record(name, (not [l for l in bars if l[1] != new]) if bars else None,
           f"then {new}, {prefix!r} at once: the bar {shown(lines)}; the field {text!r}", name)


def check_mixed():
    mixed("mixed-code-pending", ["yue-yale", "en"], [*keys("neihou"), "+500"], "en", "th")
    mixed("mixed-flick", ["en", "yue-yale"], [*keys("hello"), "key:space", "+90", *keys("there"), "+800", "flick", "+600"],
          "yue-yale", "ngo")
    mixed("mixed-menu-pick", ["yue-yale", "ja"], [*keys("neihou"), "+500", "swipeup:b", "+700", "key:s", "+500"], "ja", "ka")
    mixed("mixed-sym-panel", ["ja", "ko"], ["key:sym", "+700"], "ko", "rk")


def check_yale_warm():
    """Pinyin typed first, so Rime is open; then the switcher to Yale: no "Warming up" on the bar."""
    start(["en", "yue-yale", "zh-pinyin"], "zh-pinyin")
    play(*keys("nihao"), "+500", "key:space", "+800")
    mark()
    play(*HOLD_SYM, f"key:{TILE_KEYS[1]}", "+500", *keys("nei"), "+800", shot="yale-warm", at=1600)
    lines = bar_lines()
    warming = [l[1] for l in lines if l[0] == "notice" and "Warming up" in l[1]]
    bars = [l for l in lines if l[0] == "bar"]
    record("yale-warm", (not warming) if lines else None,
           f"Rime warm from Pinyin, then Yale: notices {warming or 'none'}; the bar {shown(lines)}"
           + ("" if bars else f" ({TAG} missing?)"), "yale-warm")


BATCH = [check_toggle, check_switcher, check_alt_sym, check_98, check_mixed, check_yale_warm]


def finish():
    """The bar's log off, an empty field, Debug back as the keyboard, and the owner's languages."""
    adb("shell", "setprop", f"log.tag.{TAG}", "''")
    try:
        checks.keyboard("debug")
        checks.set_text("")
    finally:
        adb("shell", "am", "broadcast", "-n", f"{APP}/com.titankeys.keyboard.InputMethodsSet", "--es", "on",
            "en,zh-pinyin,yue-jyutping,yue-yale,af", "--es", "current", "en")
