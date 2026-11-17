"""The touchpad's gesture checks for checks.py --batch gestures, in English: #89's flick at a long text's end and on a long word,
#93's flick then a swipe, #83's swipe pairs, #84's pill and the swiped "its" (all in checks.py)."""
import checks
import language_checks


def english():
    """English types, whatever the last batch left on."""
    language_checks.turn_on(["en"], "en")


BATCH = [english, checks.check_89_flick_end, checks.check_89_long_word, checks.check_93_flick_then_swipe, checks.check_83_pairs,
         checks.check_84_pill, checks.check_swiped_apostrophe]


def finish():
    checks.set_text("")
