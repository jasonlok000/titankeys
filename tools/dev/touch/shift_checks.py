"""The Shift checks that needed the owner's hands, for checks.py --batch shift: Thai's Shift layer, Georgian's Shift + W, and
Shift opening and closing the bigger menu in Chinese and Japanese.

The keyboard twin's Shift can't be used on this phone (it takes the field's focus), so this batch plays its keys through TitanKeys
Debug's key feed instead (TK_KEYFEED=1, android/debug/recorder/KeyFeed.kt): the keys go into the keyboard's own key handling,
Shift's meta state and all, without a twin. Each check turns its input method on with English through InputMethodsSet
(language_checks.turn_on), in Debug's Space gestures field, types, and reads the field back; the menu's opening and closing is
read from the keyboard's own TitanKeysChoices lines, and a screenshot shows it.
"""
import json
import os
import time

import checks
import language_checks
import languages
from checks import adb, play, record

CHOICES_TAG = "TitanKeysChoices"


def setup():
    os.environ["TK_KEYFEED"] = "1"
    adb("shell", "setprop", f"log.tag.{CHOICES_TAG}", "I")
    languages.logs_on(True)


def finish():
    os.environ.pop("TK_KEYFEED", None)
    adb("shell", "setprop", f"log.tag.{CHOICES_TAG}", "''")
    try:
        # The owner's languages as they were before the batches (2026-11-16), as the other batches' finish puts them back.
        language_checks.turn_on(["en", "zh-pinyin", "yue-jyutping", "yue-yale", "af"], "en")
    finally:
        languages.logs_on(False)


def start(im):
    """Its input method on with English and typing, in an empty field."""
    setup()  # in every check, so a check that runs without the first still plays its keys through the key feed
    if not language_checks.turn_on(["en", im], im):
        raise checks.Stop(f"the field didn't start with {im}")
    language_checks.fresh()


def thai_keys(text):
    """The gestures that type [text] on Thai's Kedmanee: a key's own letter, Shift + the key for its Shift letter, the key held
    for its third letter, as Thai's im.json has them."""
    with open(os.path.join(languages.REPO, "android/assets/im/th/im.json"), encoding="utf-8") as f:
        keys = json.load(f)["keys"]
    where = {}
    for key, letters in keys.items():
        for layer, letter in enumerate(letters):
            where.setdefault(letter, (key, layer))
    out = []
    for letter in text:
        key, layer = where[letter]
        out += [{0: f"key:{key}:60:none", 1: f"chord:shift+{key}", 2: f"key:{key}:700:none"}[layer], "+120"]
    return out


def choices_lines():
    return [line for line in adb("logcat", "-d", "-v", "brief", "-s", CHOICES_TAG).splitlines() if "bigger menu" in line]


def check_thai_shift():
    """Thai: Shift + Q types ๐, its Shift letter (Q alone is ๆ)."""
    start("th")
    play("chord:shift+q", "+300")
    text = checks.field()
    record("shift-thai-q", text == "๐", f"Shift + Q typed {text!r}, wanted '๐'")


def check_thai_phrase():
    """Thai: ฉันไปโรงเรียน (ฉ and โ on Shift, ง held) and Space: the bar shows next words after โรงเรียน (บ้าน ที่ ใน), not
    starters (C's Thai fix, d73a1873)."""
    start("th")
    moves = thai_keys("ฉันไปโรงเรียน")
    # One call plays at most 15 s: the phrase in two.
    half = len(moves) // 2 // 2 * 2
    play(*moves[:half])
    play(*moves[half:], "key:space:60:none", "+700", shot="shift-thai-phrase", at=len(moves[half:]) * 90 + 500)
    text = checks.field()
    record("shift-thai-phrase", text == "ฉันไปโรงเรียน ", f"typed {text!r}, wanted 'ฉันไปโรงเรียน '; the bar after it: next "
           "words after โรงเรียน (บ้าน ที่ ใน), not starters (สวัสดี ขอบคุณ ฉัน)", "shift-thai-phrase")


def check_georgian_shift():
    """Georgian: Shift + W types ჭ, and the field keeps its focus."""
    start("ka")
    before = languages.front()
    play("chord:shift+w", "+300")
    text = checks.field()
    record("shift-georgian-w", text == "ჭ" and languages.front() == before,
           f"Shift + W typed {text!r}, wanted 'ჭ'; the window in front {'stayed' if languages.front() == before else 'changed'}")


def menu_by_shift(im, code, name):
    """With a code pending, Shift opens the bigger menu and Shift again closes it, by the keyboard's own lines and a screenshot."""
    start(im)
    adb("logcat", "-c")
    moves = language_checks.gestures(list(code))
    play(*moves, "+400", "chord:shift", "+600", shot=f"shift-{name}-open", at=len(moves) * 80 + 800)
    opened = choices_lines()
    play("chord:shift", "+400")
    lines = choices_lines()
    record(f"shift-{name}-menu", any("opens" in l for l in opened) and any("closes" in l for l in lines[len(opened):]),
           f"Shift: {' | '.join(l.split(':', 1)[-1].strip() for l in lines) or 'no menu lines'}", f"shift-{name}-open")
    play(*[g for _ in range(len(code) + 2) for g in ("key:del", "+60")])


def check_chinese_menu():
    """Chinese (Yale): with neihou pending, Shift opens the bigger menu, and Shift again closes it."""
    menu_by_shift("yue-yale", "neihou", "yale")


def check_japanese_menu():
    """Japanese: with nihongo pending, Shift opens the bigger menu, and Shift again closes it."""
    menu_by_shift("ja", "nihongo", "japanese")


BATCH = [check_thai_shift, check_thai_phrase, check_georgian_shift, check_chinese_menu, check_japanese_menu]
HANDS = []
