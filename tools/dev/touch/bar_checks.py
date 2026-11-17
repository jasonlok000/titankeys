"""C's bar words (main c8de2255), for checks.py --batch bar-words: with an empty field, the bar shows starters in the language's
own words; after a word and Space, it shows next words. Spanish, French and Korean (Korean's word list is new), each on with
English through InputMethodsSet, in Debug's Space gestures field. A password field's empty bar is for the owner's hands:
Debug's own screens have no password field.
"""
import language_checks
from checks import Stop, play, record

# A word in each, as its keys: Korean's 한국 is g k s r n r.
WORDS = {"es": "hola", "fr": "bonjour", "ko": "gksrnr"}
HANDS = ["A password field: the bar stays empty (no starters, no next words)",
         "Spanish: block a word from its menu, and it never shows on the bar again",
         "Settings > Your words > Saved words: a Spanish word you saved is listed, with \"Español\" under it"]


def keys(word):
    return [g for letter in word for g in (f"key:{letter}:60:none", "+90")]


def check_bar_words():
    """Starters with an empty field, then next words after a word and Space, in Spanish, French and Korean."""
    for im, word in WORDS.items():
        language_checks.fresh()
        if not language_checks.turn_on(["en", im], im):
            record(f"bar-{im}", False, f"the field didn't start with {im}")
            continue
        language_checks.fresh()
        play("+800", shot=f"bar-{im}-empty", at=500)
        want = " (De, La, En)" if im == "es" else ""
        record(f"bar-{im}-starters", None, f"{im}, empty field: the bar shows starters{want}", f"bar-{im}-empty")
        moves = keys(word) + ["key:space", "+900"]
        play(*moves, shot=f"bar-{im}-next", at=len(moves) * 80 + 600)
        record(f"bar-{im}-next", None, f"{im}, after {word!r} and Space: the bar shows next words", f"bar-{im}-next")


def finish():
    """The owner's languages as they were before the batches: English typing, Chinese's three, Afrikaans."""
    language_checks.adb("shell", "am", "broadcast", "-n", f"{language_checks.checks.APP}/com.titankeys.keyboard.InputMethodsSet",
                        "--es", "on", "en,zh-pinyin,yue-jyutping,yue-yale,af", "--es", "current", "en")


def check_learned_starter():
    """C's word checks (main, 2026-11-17): "hola" + Space ending two sentences, then a new sentence: Hola comes first on the bar."""
    language_checks.fresh()
    if not language_checks.turn_on(["en", "es"], "es"):
        return record("bar-es-learned", False, "the field didn't start with es")
    language_checks.fresh()
    sentence = keys("hola") + ["key:space", "+90", "key:space", "+500"]  # double Space: the full stop
    play(*sentence, *sentence, "+800", shot="bar-es-learned", at=len(sentence) * 2 * 80 + 900)
    record("bar-es-learned", None, "after \"hola\" ended two sentences, the new sentence's bar has Hola first",
           "bar-es-learned")


def check_korean_choices():
    """Korean: ㅎㅏ (G, K) pending, the bar's choices include 하고 and a word starting 한."""
    language_checks.fresh()
    if not language_checks.turn_on(["en", "ko"], "ko"):
        return record("bar-ko-choices", False, "the field didn't start with ko")
    language_checks.fresh()
    play("key:g", "+90", "key:k", "+800", shot="bar-ko-choices", at=600)
    record("bar-ko-choices", None, "하 typed: the choices include 하고 and a 한 word", "bar-ko-choices")


def check_corrections():
    """D's corrections (main 070198f1): in Spanish, "cafe" + Space types "café " (O), and a neighbour-key slip, "hpla", shows
    "hola" on the bar (J). In Greek, Space never fixes a slip: the field keeps what was typed (O), and the fix is only offered
    on the bar (J)."""
    language_checks.fresh()
    if not language_checks.turn_on(["en", "es", "el"], "es"):
        return record("fix-es", False, "the field didn't start with es")
    language_checks.fresh()
    play(*keys("cafe"), "key:space", "+600")
    text = language_checks.checks.field()
    record("fix-es-cafe", text == "café ", f"cafe + Space typed {text!r}, wanted 'café '")
    language_checks.fresh()
    moves = keys("hpla") + ["+800"]
    play(*moves, shot="fix-es-hpla", at=len(moves) * 80 + 500)
    record("fix-es-hpla", None, "hpla typed: the bar shows hola", "fix-es-hpla")
    language_checks.fresh()
    if not language_checks.turn_on(["en", "es", "el"], "el"):
        return record("fix-el", False, "the field didn't start with el")
    language_checks.fresh()
    slip = "kslhmera"  # καλημέρα's keys (k a l h m e r a) with A slipped to its neighbour S
    typed = language_checks.expected("el", list(slip))
    moves = keys(slip) + ["+800"]
    play(*moves, shot="fix-el-bar", at=len(moves) * 80 + 500)
    play("key:space", "+600")
    text = language_checks.checks.field()
    record("fix-el-space", text == typed + " ", f"{slip} + Space typed {text!r}, wanted it as typed, {typed + ' '!r}")
    record("fix-el-bar", None, f"{typed} typed: the bar offers the fix (καλημέρα), Space didn't take it", "fix-el-bar")


BATCH = [check_bar_words, check_learned_starter, check_korean_choices, check_corrections]
