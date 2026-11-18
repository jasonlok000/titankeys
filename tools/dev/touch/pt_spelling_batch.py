"""Portuguese's two spellings (C's variants, claude/m3-im-variants f3721299, not on main when this was written), for checks.py
--batch pt-spelling. Input method pt: Brasil (pt-BR, the default) or Portugal (pt-PT), picked in Settings > Languages >
Português > Spelling, kept as typing_settings' input_method_variants ("pt=pt-PT"). Debug has no receiver for it, so the pick
goes through TitanKeys' own screen, with taps only while Debug is in front.

Words only one list has (the Master diffed them): PT projecto, actividade; BR projeto.
1. Portugal: "proje" offers projecto, "activi" offers actividade (O from D's TitanKeysBar line, else J by the shot).
2. Brasil: "proje" offers projeto, never projecto (O, the same way).
Both picks run, and Brasil is put back after. C's variants are on main since 561eb1de.
"""
import time

import checks
import guide_checks
import interactions_batch
import language_checks
import languages
import retest_batch
from checks import APP, Stop, adb, play, record

NAMES = {"pt-PT": "Portugal", "pt-BR": "Brasil"}


def step(*command):
    if f" {APP}/" not in languages.front():
        raise Stop(f"TitanKeys Debug left the front: {languages.front().strip()}")
    adb("shell", "input", *command)
    time.sleep(1.5)


def pick(variant):
    """Settings > Languages > Português > Spelling > Portugal or Brasil. True if each row was found and tapped."""
    retest_batch.own_screen()
    adb("shell", "am", "start", "-n", f"{APP}/com.vandam.ink.MainActivity")
    time.sleep(3)
    try:
        retest_batch.open_settings(step)
        x, y = retest_batch.row_at("Languages") or (540, 452)
        step("tap", str(x), str(y))
        for label in ("Português", "Spelling", NAMES[variant]):
            row = None
            for _ in range(10):
                row = retest_batch.row_at(label)
                if row:
                    break
                step("swipe", "540", "950", "540", "350", "400")
            if not row:
                guide_checks.shot(f"pt-no-{label.lower()}")
                return False
            step("tap", str(row[0]), str(row[1]))
        return True
    finally:
        for _ in range(5):
            if f" {APP}/" in languages.front() and not languages.in_field():
                adb("shell", "input", "keyevent", "4")
                time.sleep(1)


def offers(prefix, name, ok_if, detail):
    """Read the words after a prefix. If the log missed the row, keep it for visual comparison."""
    interactions_batch.start(["en", "pt"], "pt")
    play(*interactions_batch.keys(prefix), "+800")
    bars = [line for line in interactions_batch.bar_lines() if line[0] == "bar"]
    if bars:
        words = bars[-1][2]
        record(name, ok_if(words), f"{detail}: the bar {' | '.join(words)}")
    else:
        record(name, None, f"{detail}: compare the words on the bar", guide_checks.shot(name))
    language_checks.fresh()


def check_portugal():
    if not pick("pt-PT"):
        return record("pt-PT-picked", None, "no Português > Spelling > Portugal found: the dump has no Ink row text yet (a harness LOOK until E labels the rows)")
    offers("proje", "pt-PT-projecto", lambda w: "projecto" in w, "Portugal, proje")
    offers("activi", "pt-PT-actividade", lambda w: "actividade" in w, "Portugal, activi")


def check_brasil():
    picked = pick("pt-BR")
    offers("proje", "pt-BR-projeto", lambda w: "projeto" in w and "projecto" not in w,
           f"Brasil{'' if picked else ' (the default: no Spelling row to pick it)'}, proje")


BATCH = [check_portugal, check_brasil]


def finish():
    """Brasil, the default, back; the bar's log off; the owner's languages."""
    try:
        pick("pt-BR")
    finally:
        adb("shell", "setprop", f"log.tag.{interactions_batch.TAG}", "''")
        adb("shell", "am", "broadcast", "-n", f"{APP}/com.titankeys.keyboard.InputMethodsSet", "--es", "on",
            "en,zh-pinyin,yue-jyutping,yue-yale,af", "--es", "current", "en")
