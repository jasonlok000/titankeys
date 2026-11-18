"""The held Sym's switcher and the screen's taps (C's work on main e133d51a), for checks.py --batch switcher.

1. Three languages on, a held Sym: the tiles over E-R, T-Y and U-I, centred within W to O (the screenshot's margins).
2. A resting finger slid across the switcher: one menu_mark per tile it enters, not one per key (Debug's HapticsDump).
3. A tap on the screen on a Sym panel item and on a switcher tile picks it, logs "tap at … on …" and a haptic line
   (TitanKeysTap), and plays one screen_tap; on a bar word: bar_word, no screen_tap.
4. Five languages through InputMethodsSet all stay on; the switcher shows the first four (Settings' own refusal is for hands).
5. Two languages on: a held Sym switches straight to the other, and back.
6. The mark at Q: Chinese with only Yale on shows 中; with Pinyin on too, 耶.

The input methods go on through InputMethodsSet, and each check types in Debug's Space gestures field.
"""
import re
import time

import checks
import guide_checks
import language_checks
import languages
from checks import APP, Stop, adb, play, record

HOLD_SYM = ["key:sym:900:none", "+500"]
# The 1076 x 1200 display: the bar's row, and the middle of key column c (0 is Q).
BAR_Y = 1170


def column_x(column):
    return int((column + .5) * 1076 / 10)


def haptics_since(since):
    """The haptics played since the uptime [since], as (name, result), and the phone's uptime now."""
    answer = adb("shell", "am", "broadcast", "-n", f"{APP}/com.titankeys.keyboard.HapticsDump", "--el", "since", str(since))
    data = answer.split('data="', 1)[-1].rsplit('"', 1)[0]
    lines = [line for line in data.split(";") if line]
    if not lines or not lines[0].startswith("now "):
        raise Stop(f"HapticsDump didn't answer: {answer.strip()[-200:]}")
    now = int(lines[0].split()[1])
    if "feed on" not in lines[0]:
        record("haptic-feed", None, "the Haptics lab's feed is off: open Debug's Haptics lab once so it records")
    return [tuple(line.split()[1:3]) for line in lines[1:]], now


def now():
    return haptics_since(1 << 62)[1]


def setup(on, current):
    language_checks.fresh()
    if not language_checks.turn_on(on, current):
        raise Stop(f"the field didn't start with {current}")
    language_checks.fresh()


def check_centred():
    """Three languages on (English, Russian, Greek), a held Sym: three tiles, two keys each, centred within W to O."""
    setup(["en", "ru", "el"], "en")
    play(*HOLD_SYM, shot="switcher-three", at=1300)
    record("switcher-three", None, "tiles over E-R, T-Y and U-I, equal margins left and right", "switcher-three")
    play("key:sym", "+500")  # Sym closes it with no change


def check_one_mark_per_tile():
    """A resting finger slid from W to O over the switcher's second row (Serbian's two, four keys each): one menu_mark per
    tile it enters, two in all, not one per key."""
    setup(["en", "ru", "sr", "sr-latn"], "en")
    play(*HOLD_SYM, "key:u", "+600")  # SR is over U-I with three languages: a press there opens its second row
    since = now()
    play("rest:0.15,0.125,0.85,0.125:1800", "+400", shot="switcher-marks", at=1200)
    played, _ = haptics_since(since)
    marks = [name for name, result in played if name == "menu_mark"]
    record("switcher-marks", len(marks) == 2, f"{len(marks)} menu_mark across two four-key tiles: {played}", "switcher-marks")
    play("key:sym", "+500")


def tap_lines(action):
    """Runs action (a tap on the screen) with Debug's TitanKeysTap log cleared before, and returns its lines after: "tap at X,Y on
    VIEW" then a haptic line, or "taken by nothing". Places and view kinds only, never text."""
    adb("shell", "setprop", "log.tag.TitanKeysTap", "I")
    adb("logcat", "-c")
    action()
    time.sleep(.8)
    # The text after the tag: logcat's own time holds colons too, so splitting at the first one kept it (2026-11-17's night run
    # read "36:35.213 … I TitanKeysTap: tap at …", and the tap that worked was a NO).
    lines = [line.split("TitanKeysTap:", 1)[-1].strip() for line in adb("logcat", "-d", "-s", "TitanKeysTap").splitlines()
             if "TitanKeysTap:" in line]
    return lines


def tapped(lines):
    """A tap that reached a view and played its haptic: "tap at … on …", then a haptic line, and never "taken by nothing"."""
    return (any(line.startswith("tap at") and " on " in line for line in lines)
            and any("played" in line or "haptic" in line for line in lines)
            and not any("taken by nothing" in line for line in lines))


def check_screen_taps():
    """C's finger taps (main c8de2255): a tap on a Sym panel item and on a switcher tile picks, logs "tap at … on …" and a haptic
    line, and plays one screen_tap; a tap on a bar word plays bar_word and no screen_tap. TitanKeysTap's lines go in the report."""
    setup(["en", "ru", "el"], "en")
    # The Sym panel with no code: its first item, over W on the panel's top row. It types into the field.
    play("key:sym", "+700")
    before = checks.field()
    since = now()
    lines = tap_lines(lambda: checks.guarded("tap", str(column_x(1)), "970"))
    played, _ = haptics_since(since)
    taps = [name for name, _ in played if name == "screen_tap"]
    picked = checks.field() != before
    record("tap-sym-panel", tapped(lines) and picked and len(taps) == 1,
           f"picked: {picked}; {len(taps)} screen_tap; TitanKeysTap: {lines}")
    checks.guarded("keyevent", "4")
    time.sleep(.6)
    language_checks.fresh()
    # The switcher with three languages: Russian's tile, over T-Y. Picked, Q types й.
    play(*HOLD_SYM)
    since = now()
    lines = tap_lines(lambda: checks.guarded("tap", str(column_x(4.5)), str(BAR_Y)))
    played, _ = haptics_since(since)
    taps = [name for name, _ in played if name == "screen_tap"]
    play("key:q", "+500")
    typed = checks.field()
    record("tap-switcher", tapped(lines) and typed == "й" and len(taps) == 1,
           f"Q after the tap typed {typed!r} (й: Russian picked); {len(taps)} screen_tap; TitanKeysTap: {lines}")
    setup(["en", "ru", "el"], "en")
    # A bar word: English's suggestions after "hel".
    play("key:h", "+90", "key:e", "+90", "key:l", "+600")
    since = now()
    lines = tap_lines(lambda: checks.guarded("tap", "538", str(BAR_Y)))
    played, _ = haptics_since(since)
    names = [name for name, _ in played]
    record("tap-bar-word", "bar_word" in names and "screen_tap" not in names, f"a bar word: {played}; TitanKeysTap: {lines}")
    adb("shell", "setprop", "log.tag.TitanKeysTap", "''")


def check_fifth_language():
    """C's design: InputMethodsSet takes any number, and a setting with more than four keeps them all; the switcher shows the
    first four. Five asked for: all five stay on (the receiver's answer), and a held Sym shows four tiles (the shot)."""
    asked = ["en", "ru", "el", "de", "fr"]
    language_checks.fresh()
    started = language_checks.turn_on(asked, "en")
    answer = adb("shell", "am", "broadcast", "-n", f"{APP}/com.titankeys.keyboard.InputMethodsSet", "--es", "on",
                 ",".join(asked), "--es", "current", "en")
    kept = answer.split("on ", 1)[-1].split(";")[0].split(",") if "on " in answer else []
    record("fifth-language-kept", started and kept == asked, f"asked for {asked}, kept {kept}")
    language_checks.fresh()
    play(*HOLD_SYM, shot="switcher-five", at=1300)
    record("fifth-language-tiles", None, "five on: the switcher shows four tiles, the first four", "switcher-five")
    play("key:sym", "+500")


REFUSAL = "Four languages at a time, tops. Turn one off first."


def screen_dump():
    """The screen's accessibility tree, as uiautomator dumps it (the dump is deleted at once)."""
    path = "/data/local/tmp/tk-touch-ui.xml"
    adb("shell", "uiautomator", "dump", path)
    xml = adb("shell", "cat", path)
    adb("shell", "rm", "-f", path)
    return xml


def switches(xml):
    """Each switch on the screen: its middle and whether it's on."""
    out = []
    for node in re.findall(r"<node [^>]*>", xml):
        if 'class="android.widget.Switch"' not in node and 'checkable="true"' not in node:
            continue
        bounds = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
        if bounds:
            x0, y0, x1, y1 = (int(v) for v in bounds.groups())
            out.append(((x0 + x1) // 2, (y0 + y1) // 2, 'checked="true"' in node))
    return out


def row_switch(xml, label):
    """The switch on the row of the last view whose text is [label] (on a language's page the first is its title, the last its
    input method's row): the one whose middle is nearest that row's, or None."""
    rows = re.findall(r'text="' + re.escape(label) + r'"[^>]*bounds="\[\d+,(\d+)\]\[\d+,(\d+)\]"', xml)
    found = switches(xml)
    if not rows or not found:
        return None
    middle = (int(rows[-1][0]) + int(rows[-1][1])) // 2
    return min(found, key=lambda each: abs(each[1] - middle))


def check_fifth_language_refused():
    """Four enabled languages: a fifth toggle stays off in UI and saved state."""
    import retest_batch
    from page_probe import Missing, PageUI, Unobservable, assets, shown_name
    retest_batch.own_screen()
    ui = PageUI(checks)
    wanted = ['en', 'ru', 'el', 'de']
    try:
        with ui.methods(wanted, 'en'):
            wording, _, method = assets()
            label = shown_name(method('af'))
            ui.open_language(label)
            ui.find(wording['page']['languagesFull'])
            toggle = ui.find(label, 'toggle')
            if toggle.get('checked') != 'false':
                raise Missing('Fifth language toggle already appears enabled')
            if toggle.get('enabled', 'true') == 'true':
                ui.tap(toggle)
            if ui.find(label, 'toggle').get('checked') != 'false' or ui.state() != (wanted, 'en'):
                raise Missing('Fifth language changed the four-language selection')
            ui.find(wording['page']['languagesFull'])
            record('fifth-language-refused', True, 'Limit note visible; fifth stays off in UI and saved state',
                   guide_checks.shot('fifth-language-refused'))
    except Missing as error:
        record('fifth-language-refused', False, str(error))
    except Unobservable as error:
        record('fifth-language-refused', None, str(error))


def check_two_toggle():
    """Two languages on (English, Russian): a held Sym switches straight to the other, with no overlay, and back."""
    setup(["en", "ru"], "en")
    play(*HOLD_SYM, "key:q", "+500")
    first = checks.field()
    play("key:del", "+300", *HOLD_SYM, "key:q", "+500")
    second = checks.field()
    record("two-toggle", first == "й" and second == "q", f"held Sym then Q: {first!r}; again: {second!r} (wanted й, then q)")


def check_q_mark():
    """C's mark at Q (main 2ef3bf2d): Chinese with only Yale on shows 中, its language; with Pinyin on too, Yale's own 耶."""
    for on, want in ((["en", "yue-yale"], "中"), (["en", "yue-yale", "zh-pinyin"], "耶")):
        setup(on, "yue-yale")
        play("+300", shot=f"q-mark-{len(on)}", at=100)
        record(f"q-mark-{'-'.join(on[1:])}", None, f"{', '.join(on)} on, Yale typing: {want} at Q", f"q-mark-{len(on)}")


BATCH = [check_centred, check_one_mark_per_tile, check_screen_taps, check_fifth_language, check_fifth_language_refused,
         check_two_toggle, check_q_mark]
HANDS = ["A tap on a clipboard item (a clip saved first): one screen_tap",
         "A tap on a cell in Chinese's bigger menu picks it and logs (after B's merge)"]


def finish():
    """The owner's languages as they were before the batches (2026-11-16): English typing, Chinese's three, Afrikaans."""
    adb("shell", "am", "broadcast", "-n", f"{APP}/com.titankeys.keyboard.InputMethodsSet", "--es", "on",
        "en,zh-pinyin,yue-jyutping,yue-yale,af", "--es", "current", "en")
