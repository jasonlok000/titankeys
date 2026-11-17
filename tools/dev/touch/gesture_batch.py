"""The touchpad's gesture checks for checks.py --batch gestures: in English, #89's flick at a long text's end, on a long word
and held (the long delete, 10 s at most, and its chomper), #93's flick then a swipe, #83's swipe pairs, #84's pill and the
swiped "its" (all in checks.py); then the gestures in every language (owner, 2026-11-17), the languages batch's sets of seven
with English, through Debug's InputMethodsSet:

- a flick at the end of a typed line takes the last word, by the language's own word breaks: the field goes back to what it
  held before that word was typed;
- double Space after a word types the language's own full stop: 。 in Chinese and Japanese, । in Hindi, none in Thai, "."
  elsewhere;
- three Space taps pick one word (a screenshot: the field's text can't show a selection).

Chinese and Japanese type their first word from a code, picked with Space. A flick with a code pending (it clears the whole
code) comes with D's build: CODE_FLICK lists the input methods it runs in once that's on main.
"""
import checks
import language_checks
from checks import Stop, play, record

# The full stop double Space types, by input method; "." for the rest.
STOPS = {"yue-yale": "。", "yue-jyutping": "。", "zh-pinyin": "。", "ja": "。", "hi": "।", "th": ""}
# Codes for the input methods that type through one, picked with Space.
CODES = {"yue-yale": "nei", "yue-jyutping": "nei", "zh-pinyin": "ni", "ja": "nihon"}
# Where a flick with a code pending clears the code: on once D's build is on main (2026-11-17).
CODE_FLICK = []
# Judged by the screenshot: words without spaces, split by a dictionary.
LOOK_FLICK = {"th"}
# Two plain words' keys for the rest: the first is typed with a Space after it, the second is what the flick takes.
FIRST, SECOND = "qwe", "asd"


def english():
    """English types, whatever the last batch left on, in Debug's Space gestures field."""
    language_checks.guide_checks.leave_guide()
    if not language_checks.languages.in_field():
        raise Stop(f"not in the Space gestures field: {language_checks.languages.front().strip()}")
    language_checks.turn_on(["en"], "en")


def keys(word):
    return [g for letter in word for g in (f"key:{letter}:60:none", "+90")]


def first_word(im):
    """The first word, then a Space: typed, or picked from a code. Returns what the field holds then."""
    if im in CODES:
        play(*keys(CODES[im]), "+500", "key:space", "+500")
    else:
        play(*keys(FIRST), "key:space", "+500")
    return checks.field()


def flick(im):
    language_checks.fresh()
    before = first_word(im)
    play(*keys(CODES.get(im, SECOND)), "+400")
    typed = checks.field()
    play("flick", "+500", shot=f"flick-{im}", at=200)
    after = checks.field()
    if im in CODES or im in LOOK_FLICK:
        # A pending code isn't in the field (the flick before D's build may take the word before it instead); Thai's breaks come
        # from a dictionary, which made-up keys don't follow.
        return record(f"flick-{im}", None, f"{before!r} + a code, flick: {after!r} (the shot shows the bar)", f"flick-{im}")
    record(f"flick-{im}", after == before, f"{before!r} then {typed!r}; the flick left {after!r}", f"flick-{im}")


def double_space(im):
    language_checks.fresh()
    first_word(im)
    play("key:space", "+90", "key:space", "+500")
    text = checks.field().rstrip(" ")
    stop = STOPS.get(im, ".")
    ok = (not text.endswith((".", "。", "।"))) if stop == "" else text.endswith(stop)
    record(f"stop-{im}", ok, f"double Space after a word: {text!r}, the stop wanted {stop or 'none'!r}")


def three_taps(im):
    language_checks.fresh()
    first_word(im)
    play(*keys(CODES.get(im, SECOND)), "+400", "key:space", "+400")
    play("space:3", "+600", shot=f"taps-{im}", at=450)
    record(f"taps-{im}", None, "three Space taps: one word picked", f"taps-{im}")


def code_flick(im):
    language_checks.fresh()
    play(*keys(CODES[im]), "+400", "flick", "+500", shot=f"code-flick-{im}", at=600)
    text = checks.field()
    record(f"code-flick-{im}", text == "", f"a flick with the code pending left the field {text!r}; the bar is the shot",
           f"code-flick-{im}")


def check_every_language():
    """Every input method, in the languages batch's sets with English, each with its flick, double Space and three taps."""
    for group in language_checks.sets():
        for im in group:
            try:
                if not language_checks.turn_on(["en", *group], im):
                    record(f"gestures-{im}", False, f"the field didn't start with {im}")
                    continue
                flick(im)
                double_space(im)
                three_taps(im)
                if im in CODE_FLICK:
                    code_flick(im)
            except Stop:
                raise
            except (Exception, SystemExit) as error:  # noqa: BLE001 (one language mustn't end the night's run)
                record(f"gestures-{im}", False, f"the check broke: {error!r}")


BATCH = [english, checks.check_89_flick_end, checks.check_89_long_word, checks.check_89_long_delete,
         checks.check_93_flick_then_swipe, checks.check_83_pairs, checks.check_84_pill, checks.check_swiped_apostrophe,
         check_every_language, english]


def finish():
    checks.set_text("")
