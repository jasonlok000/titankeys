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
    record("ko-annyeong", None, "안녕하 then ㅅ (C's #98 fix): the row starts with 안녕핫, 안녕하세요 among the rest", "ko-annyeong")
    play("key:space", "+500")
    text = language_checks.checks.field()
    record("ko-annyeong-space", text.rstrip(" ") == "안녕핫", f"Space after 안녕하 + ㅅ typed {text!r}: it writes 핫, 안녕핫")
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


def check_accents():
    """H's accents (no repeat, over W to O): in Spanish a W held 2 s types exactly one "w" (O); in French a held O shows its
    accents over I and O and nothing over P (J), and a held A over W, E and R and nothing over Q (J)."""
    language_checks.fresh()
    if not language_checks.turn_on(["en", "es", "fr"], "es"):
        return record("accents-es", False, "the field didn't start with es")
    language_checks.fresh()
    play("hold:w:2000", "+500")
    text = language_checks.checks.field()
    record("accents-es-w", text == "w", f"W held 2 s in Spanish typed {text!r}, wanted exactly 'w'")
    language_checks.fresh()
    if not language_checks.turn_on(["en", "es", "fr"], "fr"):
        return record("accents-fr", False, "the field didn't start with fr")
    for letter, want in (("o", "the accents over I and O, nothing over P"), ("a", "over W, E and R, nothing over Q")):
        language_checks.fresh()
        play(f"key:{letter}:900:none", "+400", shot=f"accents-fr-{letter}", at=1100)
        record(f"accents-fr-{letter}", None, f"{letter.upper()} held in French: {want}", f"accents-fr-{letter}")
        play("key:del", "+300", "key:del", "+300")


def check_language_settings():
    """H's per-language settings: with a second language on, Settings > Typing reads "Typing in each language: English, …"
    (J), and French's page shows the one-time line and its rows: Fix typos, Capital letter, Space after a sentence, Space after
    , ; :, Double Space (J). The languages go on through InputMethodsSet; Settings is only looked at and tapped through."""
    import retest_batch
    import guide_checks
    language_checks.fresh()
    language_checks.turn_on(["en", "fr"], "en")
    retest_batch.own_screen()
    adb = language_checks.adb
    adb("shell", "am", "start", "-n", f"{language_checks.checks.APP}/com.vandam.ink.MainActivity")
    import time
    time.sleep(3)
    adb("shell", "input", "tap", "952", "166")  # SETTINGS
    time.sleep(2)
    record("settings-typing", None, 'Settings: the Typing row reads "Typing in each language: English, …"',
           guide_checks.shot("settings-typing"))
    row = retest_batch.row_at("Français") or retest_batch.row_at("French")
    if row is None:
        typing = retest_batch.row_at("Typing in each language")
        if typing:
            adb("shell", "input", "tap", str(typing[0]), str(typing[1]))
            time.sleep(2)
            row = retest_batch.row_at("Français") or retest_batch.row_at("French")
    if row:
        adb("shell", "input", "tap", str(row[0]), str(row[1]))
        time.sleep(2)
        record("settings-french", None, "French's page: the one-time line, then Fix typos, Capital letter, Space after a"
               " sentence, Space after , ; :, Double Space", guide_checks.shot("settings-french"))
    else:
        record("settings-french", None, "French's page wasn't found by its name on screen: for the owner's hands")
    for _ in range(4):
        if f" {language_checks.checks.APP}/" in language_checks.languages.front():
            adb("shell", "input", "keyevent", "4")
            time.sleep(1)


BATCH += [check_accents, check_language_settings]


def restarted_hidden(on, current):
    """The input methods on, the keyboard's process restarted by an install of the same APK (never a force stop), Debug's field
    focused and the keyboard hidden, TitanKeysIM's log cleared: the moment before the first touch or key."""
    import os
    import time
    adb = language_checks.adb
    apk = os.environ.get("TK_BATCH_APK", os.path.expanduser("~/build/m1-apks/main-next-debug.apk"))
    language_checks.fresh()
    if not language_checks.turn_on(on, current):
        raise Stop(f"the field didn't start with {current}")
    out = adb("install", "-r", apk)  # through checks.adb, as every phone step here (the run holds the phone lock)
    if "Success" not in out:
        raise Stop(f"the same APK didn't install again: {out.strip()[-80:]}")
    time.sleep(3)
    language_checks.languages.logs_on(True)
    adb("logcat", "-c")
    language_checks.checks.keyboard("debug")
    if not language_checks.languages.in_field():
        raise Stop("not in the Space gestures field after the install")
    language_checks.checks.guarded("keyevent", "4")  # Back hides the keyboard; the field keeps its focus
    time.sleep(1)


def waits_lines():
    return [line.split(":", 1)[-1].strip() for line in language_checks.languages.log_lines() if "TitanKeysIM" in line
            and ("waits" in line or "What waited" in line)]


def check_first_key():
    """C's touch waits (every input method but English): restarted, keyboard hidden, the first input lands in the input method.
    Yale: a touchpad tap on N becomes Yale's code, not a plain n (J: the shot; O: the field stays empty), and TitanKeysIM says
    "A touch waits: the input method is still loading" and "What waited goes to yue-yale" (O). Russian: the first key, Q, types
    й (O), and TitanKeysIM says "Key 45 waits: …" and "What waited goes to ru" (O)."""
    restarted_hidden(["en", "yue-yale"], "yue-yale")
    play("touch:0.65,0.625:80", "+1500", shot="first-touch", at=1200)  # N's middle on the touchpad
    text = language_checks.checks.field()
    lines = waits_lines()
    record("first-touch", text == "", f"a tap on N after the restart left the field {text!r}: Yale's code on the bar (the shot)",
           "first-touch")
    record("first-touch-log", "A touch waits: the input method is still loading" in lines
           and "What waited goes to yue-yale" in lines, f"TitanKeysIM: {lines}")
    restarted_hidden(["en", "ru"], "ru")
    play("key:q", "+1500")
    text = language_checks.checks.field()
    lines = waits_lines()
    record("first-key-ru", text == "й", f"Q after the restart typed {text!r}, wanted 'й'")
    record("first-key-ru-log", "Key 45 waits: the input method is still loading" in lines
           and "What waited goes to ru" in lines, f"TitanKeysIM: {lines}")


def check_sym_then_type():
    """Sym to Yale (two languages on, a held Sym switches) and N at once: the first letter is in the code (J)."""
    language_checks.fresh()
    if not language_checks.turn_on(["en", "yue-yale"], "en"):
        return record("sym-then-type", False, "the field didn't start with en")
    language_checks.fresh()
    play("key:sym:900:none", "+30", "key:n", "+90", "key:e", "+90", "key:i", "+700", shot="sym-then-type", at=1600)
    record("sym-then-type", None, "a held Sym to Yale, then nei at once: the code shows nei, its n included", "sym-then-type")


def check_slips():
    """Belarusian гэ offers гэта (J); Hindi namastr shows नमस्ते on the bar (J) and Space keeps what was typed (O); Vietnamese
    xon shows xin (J). The keyboard's log has no per-key timing, so the slips aren't timed."""
    language_checks.fresh()
    if language_checks.turn_on(["en", "be", "hi", "vi"], "be"):
        language_checks.fresh()
        # г is U; э is L's second accent: L held, then O, the key under it (H's accents over W to O, as French O's over I, O).
        play("key:u", "+90", "hold:l:900", "+300", "key:o", "+800", shot="slip-be", at=2100)
        text = language_checks.checks.field()
        record("slip-be", None if text == "гэ" else False, f"typed {text!r} (wanted гэ): гэта offered on the bar", "slip-be")
    for im, typed, want in (("hi", "namastr", "नमस्ते"), ("vi", "xon", "xin")):
        language_checks.fresh()
        if not language_checks.turn_on(["en", "be", "hi", "vi"], im):
            record(f"slip-{im}", False, f"the field didn't start with {im}")
            continue
        language_checks.fresh()
        moves = keys(typed) + ["+800"]
        play(*moves, shot=f"slip-{im}", at=len(moves) * 80 + 500)
        record(f"slip-{im}", None, f"{typed} typed: {want} on the bar", f"slip-{im}")
        if im == "hi":
            play("key:space", "+500")
            text = language_checks.checks.field()
            record("slip-hi-space", bool(text.strip()) and text.strip() != want,
                   f"Space after namastr typed {text!r}: what was typed, not {want}")


BATCH += [check_first_key, check_sym_then_type, check_slips]
