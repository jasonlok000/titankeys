"""C's how-tos and the first-use hold hint (main aa523bc5), for checks.py --batch how-to.

1. The Spanish language page exposes its current how-to points through accessibility.
The batch goes before any other that switches to Russian or Spanish in a slot, or they'd have used up the hint.
2. The first switch to Russian: the bar says "Missing a letter? Hold a key to find it." (J, a shot), and a key clears it (J, a
   shot: the bar is drawn, so a dump can't read it).
3. The first switch to Spanish: "Hold a letter for its accents." (J).
4. Neither comes back on a later switch (J).
The hint is said once ever per input method (TypingSettings' hold_hints_shown, in Debug's own data, which run-as can't read). If
Debug has said it before, the bar is rightly empty, so the hint's results are LOOKs, never NOs.
"""
import checks
import guide_checks
import language_checks
import retest_batch
from checks import APP, Stop, adb, play, record

ONCE = "said once ever per input method: an empty bar is right if Debug said it before"


def check_spanish_howto():
    from page_probe import Missing, PageUI, Unobservable
    ui = PageUI(checks)
    retest_batch.own_screen()
    try:
        lines = ui.howto('es')
        record('howto-es', True, f'{len(lines)} current Spanish how-to points found on its language page')
    except Missing as error:
        record('howto-es', False, str(error))
    except Unobservable as error:
        record('howto-es', None, str(error))


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
