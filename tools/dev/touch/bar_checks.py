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
         "Settings > Your words > Saved words: a Spanish word you saved is listed, with \"Español\" under it",
         "Japanese: Shift closes the bigger menu (the keyboard twin's Shift can't be used here)"]


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


def hangul(ch):
    return "가" <= ch <= "힣" or "ㄱ" <= ch <= "ㆎ"


def check_korean_polish():
    """C's Korean polish: 안녕하세요 among the choices for ㅇㅏㄴㄴㅕㅇㅎㅏㅅ (J); Backspace mid-syllable takes one jamo (O); a swipe
    up shows more than the row (J); a letter held mid-syllable writes the syllable, then the symbol, deleting nothing (O)."""
    language_checks.fresh()
    if not language_checks.turn_on(["en", "ko", "ja"], "ko"):
        return record("ko-polish", False, "the field didn't start with ko")
    language_checks.fresh()
    moves = keys("dkssudgkt") + ["+800"]  # ㅇ ㅏ ㄴ ㄴ ㅕ ㅇ ㅎ ㅏ ㅅ
    play(*moves, shot="ko-annyeong", at=len(moves) * 80 + 500)
    record("ko-annyeong", None, "ㅇㅏㄴㄴㅕㅇㅎㅏㅅ typed: 안녕하세요 among the choices", "ko-annyeong")
    language_checks.fresh()
    play(*keys("gks"), "key:del", "+200", "key:space", "+500")  # 한, Backspace, Space
    text = language_checks.checks.field()
    record("ko-backspace", text == "하 ", f"한 then Backspace then Space typed {text!r}, wanted '하 '")
    language_checks.fresh()
    moves = keys("dkssud") + ["+400", "swipeup:b", "+800"]
    play(*moves, "key:sym", "+400", shot="ko-swipe-up", at=len(moves) * 80 + 700)
    record("ko-swipe-up", None, "안녕 then a swipe up: more choices than the row", "ko-swipe-up")
    language_checks.fresh()
    play(*keys("gk"), "hold:s:800", "+500")  # 하, then S held: its symbol
    text = language_checks.checks.field()
    ok = len(text) == 2 and text[0] == "하" and not hangul(text[1])
    record("ko-hold", ok, f"하 then a held S typed {text!r}: the syllable, then S's symbol, nothing deleted")


def check_japanese_menu():
    """Japanese: "kyou" and a swipe up open the menu with 今日 (J); a press picks one (O); Space types the first choice (O)."""
    language_checks.fresh()
    if not language_checks.turn_on(["en", "ko", "ja"], "ja"):
        return record("ja-menu", False, "the field didn't start with ja")
    language_checks.fresh()
    moves = keys("kyou") + ["+400", "swipeup:b", "+800"]
    play(*moves, shot="ja-kyou-menu", at=len(moves) * 80 + 700)
    record("ja-kyou-menu", None, "kyou then a swipe up: the menu, 今日 among its cells", "ja-kyou-menu")
    play("key:s", "+500")  # the cell over S
    picked = language_checks.checks.field()
    record("ja-kyou-pick", bool(picked) and not picked.isascii(), f"a press of S in the menu typed {picked!r}")
    language_checks.fresh()
    play(*keys("kyou"), "+500", "key:space", "+600")
    text = language_checks.checks.field()
    record("ja-kyou-space", bool(text.strip()) and not text.isascii(), f"kyou + Space typed {text!r}: the first choice")


BATCH += [check_korean_polish, check_japanese_menu]


def check_japanese_polish():
    """B's Japanese polish: nihon's choices over W to O (J), Space types 日本, Enter types the romaji, Backspace leaves "niho"
    (O); a flick with a code clears only the code (O); ohayou + Space types おはよう and ございます follows (J), Space types it
    (O); a digit (Alt + W) clears the suggestions (J); "." and "," (Alt + M, Alt + N) type 。 and 、 (O); a swipe up opens the
    menu and a second turns its page (J)."""
    def fresh_ja():
        language_checks.fresh()
        if not language_checks.turn_on(["en", "ko", "ja"], "ja"):
            raise Stop("the field didn't start with ja")
        language_checks.fresh()
    field = language_checks.checks.field
    fresh_ja()
    moves = keys("nihon") + ["+800"]
    play(*moves, "key:space", "+500", shot="ja-nihon-row", at=len(moves) * 80 + 500)
    text = field()
    record("ja-nihon-row", None, "nihon typed: the choices over W to O", "ja-nihon-row")
    record("ja-nihon-space", text == "日本", f"nihon + Space typed {text!r}, wanted '日本'")
    fresh_ja()
    play(*keys("nihon"), "+400", "key:enter", "+500")
    text = field()
    record("ja-nihon-enter", text == "nihon", f"nihon + Enter typed {text!r}, wanted the romaji 'nihon'")
    fresh_ja()
    play(*keys("nihon"), "+400", "key:del", "+300", "key:enter", "+500")
    text = field()
    record("ja-nihon-backspace", text == "niho", f"nihon, Backspace, then Enter for the code typed {text!r}, wanted 'niho'")
    fresh_ja()
    play(*keys("ohayou"), "+500", "key:space", "+500")
    before = field()
    play(*keys("nihon"), "+400", "flick", "+500")
    text = field()
    record("ja-code-flick", text == before, f"{before!r}, a code, then a flick left {text!r}: only the code goes")
    fresh_ja()
    play(*keys("ohayou"), "+500", "key:space", "+900", shot="ja-ohayou-next", at=len(keys("ohayou")) * 80 + 1200)
    first = field()
    play("key:space", "+500")
    second = field()
    record("ja-ohayou", first == "おはよう", f"ohayou + Space typed {first!r}, wanted 'おはよう'", "ja-ohayou-next")
    record("ja-ohayou-next", second.startswith("おはよう") and "ございます" in second,
           f"the bar offered ございます (the shot); Space again typed {second!r}", "ja-ohayou-next")
    fresh_ja()
    play(*keys("ohayou"), "+500", "key:space", "+700", "chord:alt+w", "+600", shot="ja-digit", at=len(keys("ohayou")) * 80 + 1700)
    record("ja-digit", None, f"a digit after a pick (field {field()!r}): the suggestions are gone", "ja-digit")
    fresh_ja()
    play(*keys("nihon"), "+400", "key:space", "+300", "chord:alt+m", "+300", "chord:alt+n", "+500")
    text = field()
    record("ja-marks", text == "日本。、", f"日本, then . and , typed {text!r}, wanted '日本。、'")
    fresh_ja()
    moves = keys("kyou") + ["+400", "swipeup:b", "+700"]
    play(*moves, "swipeup:b", "+700", "key:sym", "+400", shot="ja-menu-page2", at=len(moves) * 80 + 900)
    record("ja-menu-page2", None, "kyou, a swipe up opens the menu, a second turns to its next page (2/N)", "ja-menu-page2")


BATCH += [check_japanese_polish]
