"""#98 for checks.py --batch space-middle: Space types the bar's middle word. Each case types or swipes a word in TitanKeys Debug's
own field, takes a screenshot of the bar just before Space, presses Space and reads the field. The bar is drawn by the keyboard,
where nothing can read it, so the eye compares the shot's middle word with the word Space typed (LOOK). Where the word Space must
type is known, the field alone decides YES or NO as well. tests/autocorrect's spaceTypesTheMiddleWord checks the same cases on
the computer."""
import time

import checks
import guide_checks
import language_checks
from checks import play, record

# Before each word, so it isn't a sentence's first (automatic capitals have their own case).
LEAD = "so "


def keys(word):
    """Key presses for a word, as language_checks types them: letters, shift for a capital."""
    return language_checks.gestures(["shift" if c == "^" else c for c in word])


def case(name, gestures, want=None):
    """Plays [gestures] after a fresh "so ", shoots the bar, presses Space and reads what Space left."""
    checks.set_text(LEAD)
    play(*gestures)
    time.sleep(1.2)  # the bar settles: the completions arrive after a short wait
    shot = guide_checks.shot(f"98-{name}")
    play("key:space", "+400")
    text = checks.field()
    typed = text.rstrip().rsplit(" ", 1)[-1]
    ok = None if want is None else typed == want
    record(f"98-{name}", ok, f"Space typed {typed!r}" + (f" (wanted {want!r})" if want else "")
           + ": it must be the shot's middle word", shot)


def check_typed():
    """Typed words: kept, completed, fixed."""
    case("hello", keys("hello"), "hello")
    case("hel", keys("hel"))
    case("teh", keys("teh"), "the")
    case("becase", keys("becase"), "because")


def check_swiped():
    """After a swipe, and a swiped word made longer."""
    case("swiped-hello", ["swipe:hello"], "hello")
    case("swiped-longer", ["swipe:hello", "+300", *keys("s")])
    # #98's case 4: a swiped word cut short and retyped is corrected as a swipe, where Space keeps it as it stands.
    case("swiped-retyped", ["swipe:hello", "+300", "key:del:60:none", "+90", "key:del:60:none", "+90", *keys("p")])


def check_after_fix():
    """The word after a fix, and a fix taken back with Backspace."""
    case("after-fix", [*keys("teh"), "+90", "key:space:60:none", "+300", *keys("recieve")], "receive")
    case("fix-undone", [*keys("teh"), "+90", "key:space:60:none", "+300", "key:del:60:none"], "teh")


def check_apostrophes():
    """The apostrophe words, the other form in the left place."""
    for word in ("its", "dont", "im", "whats"):
        case(f"apostrophe-{word}", keys(word))


def check_capitals():
    """Shift on the first letter, and a fix with it."""
    case("shift-teh", keys("^teh"), "The")
    case("shift-hel", keys("^hel"))


def check_language():
    """Another language: no typo fixes, only words on the bar (Español here)."""
    if not language_checks.turn_on(["en", "es"], "es"):
        return record("98-language", False, "Español didn't become the input method")
    try:
        case("es-hol", keys("hol"))
        case("es-gracias", keys("gracias"), "gracias")
    finally:
        language_checks.turn_on(["en"], "en")


def english():
    language_checks.turn_on(["en"], "en")


BATCH = [english, check_typed, check_swiped, check_after_fix, check_apostrophes, check_capitals, check_language]
HANDS = ["#98 with a contact's name and a saved word: type half of one, look at the bar's middle word, press Space"]


def finish():
    checks.set_text("")
