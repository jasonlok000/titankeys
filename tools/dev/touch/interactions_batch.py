"""The owner's weird interactions (2026-11-17), chained as real sequences, for checks.py --batch interactions: the bar stuck on
the last language right after a switch, the middle word differing from what Space types (#98), Yale's loading line once its
engine is warm. After every step the report says what the bar showed and what the field holds.

What the bar shows comes from TitanKeys Debug's own log, TitanKeysBar (D's, Debug only, on while log.tag.TitanKeysBar is D):
  bar: IM LEFT | MIDDLE | RIGHT      each time the bar's words change, "-" for an empty place
  bar notice: TEXT                   a line on the bar, as "Warming up Yale…"
BAR and NOTICE below parse them. A Debug without these lines gives a LOOK for each bar check; the field checks still count.

1. Switching, each between en, yue-yale, ja and ko, typing at once after the switch: the two-language toggle (a held Sym),
   the held Sym's switcher with three on (a tile's key), Alt + Sym away to the next keyboard and Debug back. After each, every
   bar line after the first destination bar belongs to that input method. Earlier transition lines are inconclusive.
   A separate immediate Korean r/k probe must commit exactly 가 and one space, regardless of bar predictions.
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


def switch_verdict(lines, new):
    """Only judge bars after the destination first appears; earlier lines belong to the switch itself."""
    bars = [line for line in lines if line[0] == "bar"]
    first = next((i for i, line in enumerate(bars) if line[1] == new), None)
    if first is None:
        return None
    return all(line[1] == new for line in bars[first:])


def judge_switch(name, old, new, typed_prefix):
    lines = bar_lines()
    verdict = switch_verdict(lines, new)
    text = read()
    record(name, verdict, f"{old} to {new}, then {typed_prefix!r}: the bar {shown(lines)}; the field {text!r}"
           + ("; destination bar not observed, transition lines cannot decide" if verdict is None else ""))


def wait_field(expected, attempts=8):
    """Read only our focused test field. Losing focus stops the run, never becomes an app failure."""
    text = checks.field()
    for _ in range(attempts - 1):
        if text == expected:
            break
        time.sleep(.25)
        text = checks.field()
    return text


def check_korean_startup():
    """Switch and type r/k in one guarded feed, then commit exactly one Korean syllable."""
    start(["en", "ko"], "en")
    if checks.field() != "":
        raise Stop("Korean startup needs an empty Debug test field")
    mark()
    # HOLD_SYM normally includes a 500 ms settling pause. Omit it here: these keys test the startup queue.
    play("key:sym:900:none", *keys("rk"))
    # The engine's bar is optional instrumentation. Poll briefly for its arrival before the commit,
    # but never mistake a source-language bar or a raw 'rk' notice for the resulting syllable.
    for _ in range(16):
        if any(line[0] == "bar" and line[1] == "ko" for line in bar_lines()):
            break
        time.sleep(.25)
    play("key:space")
    text = wait_field("가 ")
    record("korean-startup-rk", text == "가 ",
           f"immediate r/k after switching, then one Space: {text!r}; wanted exactly '가 '")


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
        text = read()
        record(f"alt-sym-{im}", switch_verdict(lines, im),
               f"Alt + Sym went to {away or 'nothing'}; Debug back, {prefix!r} at once: the bar {shown(lines)}; the field {text!r}")


def space_types_middle(im, prefix):
    """#98: the bar's middle word after [prefix] is what Space types."""
    start(["en", im] if im != "en" else ["en"], im)
    play(*keys(prefix), "+700")
    bars = [l for l in bar_lines() if l[0] == "bar" and l[1] == im]
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


def mixed(name, on, before, new, prefix, expected_field=None):
    """[before] in on[0], then a held Sym to [new] (two on: straight to it) and its prefix at once."""
    start(on, on[0])
    play(*before)
    mark()
    play(*HOLD_SYM, *keys(prefix), "+700")
    lines = bar_lines()
    text = read()
    verdict = switch_verdict(lines, new)
    if expected_field is not None:
        if text is None:
            raise Stop("the Debug field lost focus after the switch")
        if text != expected_field:
            verdict = False
    record(name, verdict,
           f"then {new}, {prefix!r} at once: the bar {shown(lines)}; the field {text!r}")


def check_mixed():
    mixed("mixed-code-pending", ["yue-yale", "en"], [*keys("neihou"), "+500"], "en", "th", expected_field="th")
    mixed("mixed-flick", ["en", "yue-yale"], [*keys("hello"), "key:space", "+90", *keys("there"), "+800", "flick", "+600"],
          "yue-yale", "ngo")
    mixed("mixed-menu-pick", ["yue-yale", "ja"], [*keys("neihou"), "+500", "swipeup:b", "+700", "key:s", "+500"], "ja", "ka")
    mixed("mixed-sym-panel", ["ja", "ko"], ["key:sym", "+700"], "ko", "rk")


def check_yale_warm():
    """Pinyin typed first, so Rime is open; then the switcher to Yale: no "Warming up" on the bar."""
    start(["en", "yue-yale", "zh-pinyin"], "zh-pinyin")
    play(*keys("nihao"), "+500", "key:space", "+800")
    mark()
    # Two Chinese methods are on, so Q picks the other one within Chinese.
    checks.guarded("tap", str(switcher_checks.column_x(0)), str(switcher_checks.BAR_Y))
    play(*keys("nei"), "+800")
    lines = bar_lines()
    warming = [l[1] for l in lines if l[0] == "notice" and "Warming up" in l[1]]
    verdict = switch_verdict(lines, "yue-yale")
    record("yale-warm", verdict and not warming if verdict is not None else None,
           f"Rime warm from Pinyin, then Yale: notices {warming or 'none'}; the bar {shown(lines)}"
           + ("" if verdict is not None else " (Yale bar not observed)"))


BATCH = [check_korean_startup, check_toggle, check_switcher, check_alt_sym, check_98, check_mixed, check_yale_warm]


def finish():
    """The bar's log off, an empty field, Debug back as the keyboard, and the owner's languages."""
    adb("shell", "setprop", f"log.tag.{TAG}", "''")
    try:
        checks.keyboard("debug")
        checks.set_text("")
    finally:
        adb("shell", "am", "broadcast", "-n", f"{APP}/com.titankeys.keyboard.InputMethodsSet", "--es", "on",
            "en,zh-pinyin,yue-jyutping,yue-yale,af", "--es", "current", "en")
