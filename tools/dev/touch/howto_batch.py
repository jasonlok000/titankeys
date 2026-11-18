"""C's how-tos and the first-use hold hint (main aa523bc5), for checks.py --batch how-to.

1. Settings > Languages > Español shows its how-to, "Hold a letter for its accents: hold N for ñ…": O when the page's dump
   holds the text, else a shot to look at (J).
The batch goes before any other that switches to Russian or Spanish in a slot, or they'd have used up the hint.
2. The first switch to Russian: the bar says "Missing a letter? Hold a key to find it." (J, a shot), and a key clears it (J, a
   shot: the bar is drawn, so a dump can't read it).
3. The first switch to Spanish: "Hold a letter for its accents." (J).
4. Neither comes back on a later switch (J).
The hint is said once ever per input method (TypingSettings' hold_hints_shown, in Debug's own data, which run-as can't read). If
Debug has said it before, the bar is rightly empty, so the hint's results are LOOKs, never NOs.
"""
import time

import checks
import guide_checks
import language_checks
import languages
import retest_batch
from checks import APP, Stop, adb, play, record

HOWTO = "Hold a letter for its accents: hold N for ñ"
ONCE = "said once ever per input method: an empty bar is right if Debug said it before"


def screen_dump():
    path = "/data/local/tmp/tk-touch-ui.xml"
    adb("shell", "uiautomator", "dump", path)
    xml = adb("shell", "cat", path)
    adb("shell", "rm", "-f", path)
    return xml


def check_spanish_howto():
    """Settings > Languages > Español: its how-to. Taps only while TitanKeys Debug is in front, as the languages-page check."""
    def step(*command):
        if f" {APP}/" not in languages.front():
            raise Stop(f"TitanKeys Debug left the front: {languages.front().strip()}")
        adb("shell", "input", *command)
        time.sleep(1.5)
    retest_batch.own_screen()
    adb("shell", "am", "start", "-n", f"{APP}/com.vandam.ink.MainActivity")
    time.sleep(3)
    try:
        retest_batch.open_settings(step)
        x, y = retest_batch.row_at("Languages") or (540, 452)
        step("tap", str(x), str(y))
        row = None
        for _ in range(10):  # by script, Latin's long: scroll down until Español shows
            row = retest_batch.row_at("Español")
            if row:
                break
            step("swipe", "540", "950", "540", "350", "400")
        if not row:
            return record("howto-es", None, "no Español row found on Settings > Languages", guide_checks.shot("howto-es"))
        step("tap", str(row[0]), str(row[1]))
        xml = screen_dump()
        found = HOWTO in xml or HOWTO.replace("ñ", "&#241;") in xml
        # Ink may draw the page's text itself, out of the dump's reach: then the shot says.
        record("howto-es", True if found else None,
               f"the how-to {'read from the dump' if found else 'not in the dump: read the page itself'}",
               None if found else guide_checks.shot("howto-es"))
    finally:
        for _ in range(4):
            if f" {APP}/" in languages.front() and not languages.in_field():
                adb("shell", "input", "keyevent", "4")
                time.sleep(1)


def first_switch(im, line, name):
    """[im] on with English, the field restarted with it: the shot at once shows the hint; a key, and a shot shows it gone."""
    language_checks.fresh()
    if not language_checks.turn_on(["en", "ru", "es"], im):
        raise Stop(f"the field didn't start with {im}")
    shot = guide_checks.shot(f"hint-{name}")
    record(f"hint-{name}", None, f'the bar says "{line}" ({ONCE})', shot)
    play("key:q", "+400", shot=f"hint-{name}-cleared", at=300)
    record(f"hint-{name}-cleared", None, "after a key: the hint gone from the bar", f"hint-{name}-cleared")
    play("key:del", "+300")


def check_hints():
    first_switch("ru", "Missing a letter? Hold a key to find it.", "ru")
    first_switch("es", "Hold a letter for its accents.", "es")
    # A later switch to each: no hint.
    for im in ("ru", "es"):
        language_checks.fresh()
        if not language_checks.turn_on(["en", "ru", "es"], "en") or not language_checks.turn_on(["en", "ru", "es"], im):
            raise Stop(f"the field didn't start with {im}")
        record(f"hint-{im}-again", None, "a later switch: no hint on the bar", guide_checks.shot(f"hint-{im}-again"))


BATCH = [check_spanish_howto, check_hints]


def finish():
    """The owner's languages as they were before the batches: English typing, Chinese's three, Afrikaans."""
    adb("shell", "am", "broadcast", "-n", f"{APP}/com.titankeys.keyboard.InputMethodsSet", "--es", "on",
        "en,zh-pinyin,yue-jyutping,yue-yale,af", "--es", "current", "en")
