"""The touchpad's gesture checks for checks.py --batch gestures: in English, #89's flick at a long text's end, on a long word
and held (the long delete, 10 s at most, and its chomper), #93's flick then a swipe, #83's swipe pairs, #84's pill and the
swiped "its" (all in checks.py); then the gestures in every language (owner, 2026-11-17), the languages batch's sets of seven
with English, through Debug's InputMethodsSet:

- a flick at the end of a typed line takes the last word, by the language's own word breaks: the field goes back to what it
  held before that word was typed;
- double Space after a word types the language's own full stop: "。" in Chinese and Japanese, "। " in Hindi, a plain space in
  Thai, ". " elsewhere;
- three Space taps pick one word (a screenshot: the field's text can't show a selection).

Chinese, Japanese and Hindi type their first word from a code, picked with Space. With a code pending, a flick clears the whole
code and leaves the field alone (CODE_FLICK, D's list as of main 3593072b). Chinese deletes one character per flick. Japanese and Thai use word breaks for adjacent words.

TK_GESTURE_ONLY filters the language checks by kind (flick, stop, taps, code-flick) or by kind:input-method (stop:et).
Separate selections with commas. Use --batch gestures-fix to run a selection without the English benchmarks.
"""
import checks
import language_checks
from checks import Stop, play, record

# What double Space after a word leaves at the end of the field, by input method (D, main 3593072b): "。" with no space in
# Chinese and Japanese, "। " in Hindi, a plain second space in Thai (it types no mark), ". " in the rest.
STOPS = {"yue-yale": "。", "yue-jyutping": "。", "zh-pinyin": "。", "zh-cangjie": "。", "zh-quick": "。", "zh-zhuyin": "。",
         "ja": "。", "hi": "। ", "th": "  "}
PLAIN_STOP = ". "
# These dictionaries correct qwe on the first Space. Type a known word to check the committed word and stop together.
STOP_WORDS = {"da": "med", "et": "see"}
# The input methods whose words come from a code, picked with Space; "nihon" picks 日本 in Japanese.
# Cangjie and Quick: qwe picks 抽水. Zhuyin is on the Hsu layout: nehw is ㄋㄧ ㄏㄠ, 你好 (qwe is no syllable there).
CODES = {"yue-yale": "neihou", "yue-jyutping": "neihou", "zh-pinyin": "nihao", "zh-cangjie": "qwe", "zh-quick": "qwe",
         "zh-zhuyin": "nehw", "ja": "nihon", "hi": "namaste"}
# Two known words typed with no space between. Chinese flicks remove a character; Japanese and Thai take a word.
# In Chinese and Japanese the second comes after the first is picked, while next-word suggestions show (no code): a flick
# then clears them and deletes a word as usual.
JOINED = {"yue-yale": ("neihou", "ngodei"), "yue-jyutping": ("neihou", "ngodei"), "zh-pinyin": ("nihao", "women"),
          "zh-cangjie": ("qwe", "qwe"), "zh-quick": ("qwe", "qwe"), "zh-zhuyin": ("nehw", "nehw"),
          "ja": ("watashi", "nihon"), "th": ("gik", "wx")}  # Thai: เรา then ไป, on Kedmanee's plain layer
# A flick with a code pending clears the whole code and leaves the field alone (D): the code each types.
CODE_FLICK = {"yue-yale": "neihou", "yue-jyutping": "neihou", "zh-pinyin": "nihao", "ja": "nihon", "ko": "gksrnr",
              "vi": "tieeng", "hi": "namaste", "zh-cangjie": "qwe", "zh-quick": "qwe", "zh-zhuyin": "nehw"}
# Where the second plain word's keys stay a code (Hindi's transliteration, Vietnamese Telex, Korean's last syllable composing),
# the word flick can't be checked this way: the flick clears the code by design, which code-flick checks (night run,
# 2026-11-20: their "asd" never reached the field, so the check's own guard failed).
CODE_SECOND = {"hi", "vi", "ko"}
# The touchpad ignores touches for 500 ms after steady typing (Contact.TYPING_COOLDOWN_MILLIS, by design): a flick waits this
# long after the last key, in the same gesture script (2026-11-17's night run flicked at 400 ms, and its flicks were NO).
FLICK_AFTER_TYPING = "+800"
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
    """The first word, then a Space: typed, or picked from a code (Space picks and types no space). Returns the field then."""
    if im in CODES:
        play(*keys(CODES[im]), "+500", "key:space", "+500")
    else:
        play(*keys(FIRST), "key:space", "+500")
    return checks.field()


def flick(im):
    """Check the deletion unit with adjacent words in Chinese, Japanese and Thai."""
    language_checks.fresh()
    if im in JOINED:
        first, second = JOINED[im]
        picked = im != "th"
        play(*keys(first), *(["+500", "key:space"] if picked else []), "+500")
        before = checks.field()
        play(*keys(second), *(["+500", "key:space"] if picked else []), "+500")
        typed = checks.field()
        name = f"flick-suggestions-{im}" if picked else f"flick-{im}"
    else:
        before = first_word(im)
        play(*keys(SECOND), "+400")
        typed = checks.field()
        name = f"flick-{im}"
    play(FLICK_AFTER_TYPING, "flick", "+500")
    after = checks.field()
    # Chinese flicks take one character, even when a pick committed several.
    expected = typed[:-1] if im in ("zh-pinyin", "yue-jyutping", "yue-yale", "zh-cangjie", "zh-quick", "zh-zhuyin") else before
    record(name, bool(before) and typed != before and after == expected,
           f"{before!r} then {typed!r}; the flick left {after!r}, wanted {expected!r}")


def double_space(im):
    """Double Space right after a word: the language's own full stop (D's list), read back. A typed word gets no Space of its
    own first (the night run's "qwe   " made no stop, as a double Space only does right after a word); a picked one's Space
    only picks."""
    language_checks.fresh()
    if im in CODES:
        before = first_word(im)
    else:
        play(*keys(STOP_WORDS.get(im, FIRST)), "+300")
        before = checks.field()
    play("key:space", "+90", "key:space", "+500")
    text = checks.field()
    want = STOPS.get(im, PLAIN_STOP)
    if im in STOP_WORDS:
        ok = before.lower() == STOP_WORDS[im] and text == before + want
        expected = f"wanted {STOP_WORDS[im]!r} followed by {want!r}"
    else:
        # A dictionary may correct the typed keys on the first Space (Belarusian's йцу, Macedonian's љње): only the ending is the
        # gesture's, after something typed (night run, 2026-11-20).
        ok = text.endswith(want) and text[:-len(want)].strip() != ""
        expected = f"wanted it to end {want!r}"
    record(f"stop-{im}", ok, f"after {before!r}, double Space: {text!r}, {expected}")


def three_taps(im):
    """Three Space taps pick one word (by ICU's breaks in Chinese, Japanese and Thai): the screenshot shows the pick."""
    language_checks.fresh()
    if im in JOINED:
        first, second = JOINED[im]
        picked = im != "th"
        play(*keys(first), *(["+500", "key:space"] if picked else []), "+400",
             *keys(second), *(["+500", "key:space"] if picked else []), "+400")
    else:
        first_word(im)
        play(*keys(SECOND), "+400", "key:space", "+400")
    play("space:3", "+600", shot=f"taps-{im}", at=450)
    record(f"taps-{im}", None, "three Space taps: one word picked", f"taps-{im}")


def code_flick(im):
    """A flick with a code pending clears the whole code and leaves the field alone."""
    language_checks.fresh()
    before = first_word(im)
    play(*keys(CODE_FLICK[im]), FLICK_AFTER_TYPING, "flick", "+500", shot=f"code-flick-{im}", at=1000)
    text = checks.field()
    record(f"code-flick-{im}", text == before, f"field {before!r}, a code, then a flick: {text!r}")
    record(f"code-flick-{im}-bar", None, "The flick leaves no code on the bar", f"code-flick-{im}")


def check_every_language():
    """Every input method, in the languages batch's sets with English, each with its flick, double Space and three taps."""
    by_kind = {"flick": flick, "stop": double_space, "taps": three_taps, "code-flick": code_flick}
    only = checks.gesture_selectors()
    unmatched = only.copy()
    plan = []
    for group in language_checks.sets():
        for im in group:
            selected = []
            for kind, check in by_kind.items():
                if kind == "code-flick" and im not in CODE_FLICK:
                    continue
                if kind == "flick" and im in CODE_SECOND:
                    continue
                matches = only.intersection((kind, f"{kind}:{im}"))
                if not only or matches:
                    selected.append(check)
                    unmatched.difference_update(matches)
            if selected:
                plan.append((group, im, selected))
    if unmatched:
        raise Stop(f"TK_GESTURE_ONLY has no matching checks for {', '.join(sorted(unmatched))}")

    for group, im, selected in plan:
        if checks.past_cutoff():
            raise Stop("the morning cutoff came (TK_CUTOFF_EPOCH): the rest of the languages didn't run")
        try:
            if not language_checks.turn_on(["en", *group], im):
                record(f"gestures-{im}", False, f"the field didn't start with {im}")
                continue
            for check in selected:
                check(im)
        except Stop:
            raise
        except (Exception, SystemExit) as error:  # noqa: BLE001 (one language mustn't end the night's run)
            record(f"gestures-{im}", False, f"the check broke: {error!r}")


BATCH = [english, checks.check_89_flick_end, checks.check_89_long_word, checks.check_89_long_delete,
         checks.check_93_flick_then_swipe, checks.check_83_pairs, checks.check_84_pill, checks.check_swiped_apostrophe,
         check_every_language, english]


def finish():
    checks.set_text("")
