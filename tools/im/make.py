#!/usr/bin/env python3
"""Builds TitanKeys' data-only input methods (docs/languages.md, docs/studies/56-languages.md).

    python3 -I tools/im/make.py <data dir> [id ...]

<data dir> holds the downloaded sources, as the study left them in WSL (~/build/lang/gen):
  aosp-dictionaries/   HeliBoard's word lists (https://codeberg.org/Helium314/aosp-dictionaries)
  cldr/x/package/      CLDR's cldr-misc-full (characters.json per locale)
  cldr-names/package/  CLDR's cldr-localenames-full (languages.json per locale), for each input method's own name
  kcm/                 Android's keyboard_layout_*.kcm files
  frequencywords/      Hermit Dave's FrequencyWords, content/2018/<lang>/<lang>_full.txt (commit 525f9b5)
  leipzig/             Leipzig Corpora Collection words files (<corpus>/<corpus>-words.txt)

It writes, from the repository's root:
  android/assets/im/<id>/im.json, words.combined, NOTICE
  (Chinese input methods: im.json and NOTICE only, without <data dir>'s sources; Rime's data is native/rime's)
  android/assets/im/index.json
  android/src/main/res/xml/input_method.xml and res/values/strings_im.xml (one keyboard-list entry per input method)

With ids, only those input methods are rebuilt; the index and the keyboard list always list every folder there is.
"""
import hashlib
import json
import os
import re
import sys
import unicodedata

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
ASSETS = os.path.join(ROOT, "android", "assets", "im")
RES = os.path.join(ROOT, "android", "src", "main", "res")
MAX_WORDS = 100_000
KEYS = "abcdefghijklmnopqrstuvwxyz"

AOSP = ("AOSP LatinIME's word list ({file}), Apache License 2.0. Copyright The Android Open Source Project. "
        "https://android.googlesource.com/platform/packages/inputmethods/LatinIME/")
LEIPZIG = ("A word list built by HeliBoard's aosp-dictionaries ({file}) from the Leipzig Corpora Collection "
           "(https://wortschatz.uni-leipzig.de/en/download), CC BY 4.0. D. Goldhahn, T. Eckart and U. Quasthoff: Building Large "
           "Monolingual Dictionaries at the Leipzig Corpora Collection: From 100 to 200 Languages. LREC 2012.")

FREQUENCY_WORDS = ("Hermit Dave's FrequencyWords (https://github.com/hermitdave/FrequencyWords), built from OpenSubtitles, via HeliBoard's "
                   "aosp-dictionaries ({file}). Its content is CC BY-SA 4.0.")
AZERBAIJANI = ("aosp-dict-az by Subhan Gadirli (https://github.com/subhangadirli/aosp-dict-az), via HeliBoard's aosp-dictionaries "
               "({file}), CC BY-SA 4.0, with lists from the Leipzig Corpora Collection (CC BY 4.0).")
IRISH = "aosp-gaeilge (https://codeberg.org/goibhniu/aosp-gaeilge), via HeliBoard's aosp-dictionaries ({file}), CC BY 4.0."
BASQUE = "AnySoftKeyboard's Basque word lists, via HeliBoard's aosp-dictionaries ({file}), Apache License 2.0."
NYNORSK = "Norwegian Nynorsk words from Wikipedia, via HeliBoard's aosp-dictionaries ({file}), CC BY-SA 4.0."
SERBIAN_LATIN = ("AOSP LatinIME's Serbian word list ({file}), Apache License 2.0. Copyright The Android Open Source Project. "
                 "https://android.googlesource.com/platform/packages/inputmethods/LatinIME/ . Written in Latin letters by "
                 "tools/im/make.py, letter for letter.")
FREQUENCY_WORDS_OWN = ("Hermit Dave's FrequencyWords (https://github.com/hermitdave/FrequencyWords, {file}), built from "
                       "OpenSubtitles. Its content is CC BY-SA 4.0.")
LEIPZIG_OWN = ("The Leipzig Corpora Collection's {file} (https://wortschatz.uni-leipzig.de/en/download), CC BY 4.0. D. Goldhahn, "
               "T. Eckart and U. Quasthoff: Building Large Monolingual Dictionaries at the Leipzig Corpora Collection: From 100 "
               "to 200 Languages. LREC 2012.")

# id: (name, English name, label, locale, word list file, word list notice, extra settings)
LATIN = {
    "es": ("Español", "Spanish", "ES", "es", "main_es", AOSP, {"openingMarks": "¿¡", "apostrophes": False}),
    "fr": ("Français", "French", "FR", "fr", "main_fr", AOSP, {}),
    "de": ("Deutsch", "German", "DE", "de", "main_de__legacy", AOSP, {"apostrophes": False}),
    "pt": ("Português", "Portuguese", "PT", "pt", "main_pt_BR", AOSP, {"apostrophes": False,
           "variants": [("pt-BR", "Brasil", "main_pt_BR", AOSP), ("pt-PT", "Portugal", "main_pt_PT", AOSP)]}),
    "it": ("Italiano", "Italian", "IT", "it", "main_it", AOSP, {}),
    "nl": ("Nederlands", "Dutch", "NL", "nl", "main_nl", AOSP, {}),
    "pl": ("Polski", "Polish", "PL", "pl", "main_pl", AOSP, {"apostrophes": False}),
    "tr": ("Türkçe", "Turkish", "TR", "tr", "main_tr__legacy", AOSP, {}),
    "sv": ("Svenska", "Swedish", "SV", "sv", "main_sv", AOSP, {"apostrophes": False}),
    "da": ("Dansk", "Danish", "DA", "da", "main_da", AOSP, {"apostrophes": False}),
    "nb": ("Norsk bokmål", "Norwegian Bokmål", "NB", "nb", "main_nb", AOSP, {"apostrophes": False}),
    "fi": ("Suomi", "Finnish", "FI", "fi", "main_fi", AOSP, {"apostrophes": False}),
    "cs": ("Čeština", "Czech", "CS", "cs", "main_cs", AOSP, {"apostrophes": False}),
    "ro": ("Română", "Romanian", "RO", "ro", "main_ro", AOSP, {"apostrophes": False}),
    "hu": ("Magyar", "Hungarian", "HU", "hu", "main_hu__experimental", LEIPZIG, {"apostrophes": False}),
    "id": ("Indonesia", "Indonesian", "ID", "id", "main_id__experimental", LEIPZIG, {"apostrophes": False}),
    "hr": ("Hrvatski", "Croatian", "HR", "hr", "main_hr", AOSP, {"apostrophes": False}),
    "sl": ("Slovenščina", "Slovenian", "SL", "sl", "main_sl", AOSP, {"apostrophes": False}),
    "lv": ("Latviešu", "Latvian", "LV", "lv", "main_lv", AOSP, {"apostrophes": False}),
    "lt": ("Lietuvių", "Lithuanian", "LT", "lt", "main_lt", AOSP, {"apostrophes": False}),
    "ca": ("Català", "Catalan", "CA", "ca", "main_ca__experimental", LEIPZIG, {}),
    "gl": ("Galego", "Galician", "GL", "gl", "main_gl__experimental", LEIPZIG, {"apostrophes": False}),
    "et": ("Eesti", "Estonian", "ET", "et", "main_et__experimental", LEIPZIG, {"apostrophes": False}),
    "is": ("Íslenska", "Icelandic", "IS", "is", "main_is__experimental", LEIPZIG, {"apostrophes": False}),
    "af": ("Afrikaans", "Afrikaans", "AF", "af", "main_af__experimental", LEIPZIG, {}),
    "eo": ("Esperanto", "Esperanto", "EO", "eo", "main_eo__experimental", LEIPZIG, {"apostrophes": False}),
    "sk": ("Slovenčina", "Slovak", "SK", "sk", "main_sk__experimental", FREQUENCY_WORDS, {"apostrophes": False}),
    "az": ("Azərbaycan", "Azerbaijani", "AZ", "az", "main_az", AZERBAIJANI, {"apostrophes": False}),
    "ga": ("Gaeilge", "Irish", "GA", "ga", "main_ga", IRISH, {"apostrophes": False}),
    "eu": ("Euskara", "Basque", "EU", "eu", "main_eu", BASQUE, {"apostrophes": False}),
    "nn": ("Norsk nynorsk", "Norwegian Nynorsk", "NN", "nn", "main_nn", NYNORSK, {"apostrophes": False}),
    # HeliBoard's main_bs (ebukva's bs_BA, LGPL-3.0) is a spelling list with made-up counts, so its top 100,000 miss "je".
    "bs": ("Bosanski", "Bosnian", "BS", "bs", "frequencywords/bs_full.txt", FREQUENCY_WORDS_OWN, {"apostrophes": False, "counts": True}),
    # Serbian's own list from AOSP, in Latin letters: HeliBoard's main_sr_ZZ does the same but names no licence.
    "sr-latn": ("Srpski", "Serbian, Latin", "SR", "sr-Latn", "main_sr", SERBIAN_LATIN,
                {"apostrophes": False, "transliterate": "sr"}),
}

# Cyrillic, Greek and Georgian: Android's national layout on the 26 keys, and the letters that layout puts on punctuation keys,
# held on the nearest key of the same row (docs/studies/56-languages.md, "How the letters sit on the Titan's keys").
# Serbian and Macedonian share Android's Serbian layout (QWERTZ: з on Y). Serbian has no ѕ, so its Z types ж, which the layout
# puts on a punctuation key. Macedonian has ѓ and ќ where Serbian has ђ and ћ, as on Macedonian keyboards.
# Kazakh has no Android layout: Russian's, with Kazakh's nine letters held on the Russian letter nearest in sound and shape.
# Georgian has no capitals: Shift types the layout's Shift letter, or the key's own letter.
NATIONAL = {
    "ru": ("Русский", "Russian", "RU", "ru", "main_ru", AOSP, "russian", {"p": "хъ", "l": "жэ", "m": "бю", "t": "ё"}),
    "uk": ("Українська", "Ukrainian", "UK", "uk", "main_uk__experimental", LEIPZIG, "ukrainian", {"p": "хї", "l": "жє", "m": "бю", "u": "ґ"}),
    "be": ("Беларуская", "Belarusian", "BE", "be", "main_be", None, "belarusian", {"p": "х", "l": "жэ", "m": "бю", "t": "ё"}),
    "bg": ("Български", "Bulgarian", "BG", "bg", "main_bg__experimental", LEIPZIG, "bulgarian_phonetic", {"p": "ящ", "m": "ью"}),
    "el": ("Ελληνικά", "Greek", "EL", "el", "main_el__legacy", AOSP, "greek", {}),
    "sr": ("Српски", "Serbian, Cyrillic", "СР", "sr", "main_sr", AOSP, "serbian_and_montenegrin_cyrillic",
           {"p": "шђ", "l": "чћ"}, {"keys": {"z": "ж"}, "own_letters": True}),
    "mk": ("Македонски", "Macedonian", "MK", "mk", "frequencywords/mk_full.txt", FREQUENCY_WORDS_OWN,
           "serbian_and_montenegrin_cyrillic", {"p": "шѓж", "l": "чќ"}, {"counts": True}),
    "kk": ("Қазақ тілі", "Kazakh", "KK", "kk", "main_kk__experimental", LEIPZIG, "russian",
           {"f": "ә", "u": "ғ", "r": "қ", "y": "ң", "j": "ө", "e": "үұ", "h": "һ", "s": "і", "p": "хъ", "l": "жэ", "m": "бю",
            "t": "ё"}, {"layout_note": "with Kazakh's letters held on the Russian letters nearest them"}),
    "ka": ("ქართული", "Georgian", "KA", "ka", "leipzig/kat_newscrawl_2016_1M/kat_newscrawl_2016_1M-words.txt", LEIPZIG_OWN,
           "georgian", {}, {"counts": True, "script": "Geor", "capitalise": False}),
}
BELARUSIAN = ("Belarusian word forms from GrammarDB (https://github.com/Belarus/GrammarDB), via HeliBoard's aosp-dictionaries "
              "({file}), CC BY-SA 4.0.")

CLDR_NOTICE = "Letters and accents: Unicode CLDR 49, exemplar characters. Unicode License v3. Copyright Unicode, Inc."
KCM_NOTICE = "Key layout: Android's {file} (platform/frameworks/base, InputDevices), Apache License 2.0."


def cldr_name(data, locale):
    """CLDR's name for [locale]'s language in that language (its autonym), as it would start a list: capitalised."""
    path = os.path.join(data, "cldr-names", "package", "main", locale, "languages.json")
    names = json.load(open(path, encoding="utf-8"))["main"][locale]["localeDisplayNames"]["languages"]
    name = names.get(locale) or names[locale.split("-")[0]]
    # Title case, not upper case: Georgian has no capitals in running text, and its upper case is another alphabet.
    return name[:1].title() + name[1:]


def cldr_letters(data, locale):
    path = os.path.join(data, "cldr", "x", "package", "main", locale, "characters.json")
    chars = json.load(open(path, encoding="utf-8"))["main"][locale]["characters"]
    return parse_set(chars.get("exemplarCharacters", "")), parse_set(chars.get("auxiliary", ""))


def parse_set(text):
    text = text.strip()
    if text.startswith("["):
        text = text[1:-1]
    out = []
    for token in re.findall(r"\{[^}]*\}|\\u[0-9a-fA-F]{4}|\\.|.", text):
        if token.startswith("{"):
            out.append(token[1:-1])
        elif token.startswith("\\u"):
            out.append(chr(int(token[2:], 16)))
        elif token.startswith("\\"):
            out.append(token[1:])
        elif not token.isspace():
            out.append(token)
    result, i = [], 0
    while i < len(out):
        if i + 2 < len(out) and out[i + 1] == "-" and len(out[i]) == 1 and len(out[i + 2]) == 1:
            result += [chr(c) for c in range(ord(out[i]), ord(out[i + 2]) + 1)]
            i += 3
        else:
            result.append(out[i])
            i += 1
    return [c for c in result if len(c) == 1 and c.isalpha()]


def read_words(data, name):
    path = os.path.join(data, "aosp-dictionaries", "wordlists", name + ".combined")
    head, words = "", []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if line.startswith("dictionary="):
                head = line
                continue
            if not line.startswith(" word=") and not (line.startswith("word=")):
                continue
            fields = [x.strip() for x in line.strip().split(",")]
            word = next((x[5:] for x in fields if x.startswith("word=")), "")
            if not word or "not_a_word=true" in fields or "possibly_offensive=true" in fields:
                continue
            freq = next((x[2:] for x in fields if x.startswith("f=")), "0")
            words.append((word, int(freq) if freq.isdigit() else 0))
    seen, unique = set(), []
    for word, freq in sorted(words, key=lambda w: -w[1]):
        if word not in seen:
            seen.add(word)
            unique.append((word, freq))
    return head, unique[:MAX_WORDS]


def read_counts(data, name, locale, letters):
    """A plain list of words and counts (FrequencyWords: "word count"; Leipzig: "id<TAB>word<TAB>count"), as AOSP's
    frequencies: HeliBoard's scale, the log of the count from 15 to 254. Only words made of the language's letters."""
    allowed = set(letters) | {c.upper() for c in letters}
    counts = {}
    with open(os.path.join(data, name), encoding="utf-8") as f:
        for line in f:
            parts = line.rstrip("\n").split("\t") if "\t" in line else line.split()
            if len(parts) < 2:
                continue
            word, count = parts[-2], parts[-1]
            if not count.isdigit() or not word or any(c not in allowed for c in word):
                continue
            counts[word] = counts.get(word, 0) + int(count)
    ranked = sorted(counts.items(), key=lambda w: -w[1])[:MAX_WORDS]
    import math
    low, high = math.log(max(ranked[-1][1], 1)), math.log(ranked[0][1])
    span = max(high - low, 1)
    words = [(w, int((math.log(n) - low) * (254 - 15) / span + 15)) for w, n in ranked]
    return "dictionary=main:%s,locale=%s,description=%s" % (locale, locale, locale), words


SERBIAN_LATIN_LETTERS = dict(zip("абвгдђежзијклмнопрстћуфхцчш", "abvgdđežzijklmnoprstćufhcčš"))
SERBIAN_LATIN_LETTERS.update({"љ": "lj", "њ": "nj", "џ": "dž"})


def serbian_latin(word):
    """Serbian Cyrillic in Latin letters, one for one: љ њ џ are lj nj dž, and Lj, Nj, Dž at a capital's start, LJ in capitals."""
    out = []
    for i, ch in enumerate(word):
        low = ch.lower()
        latin = SERBIAN_LATIN_LETTERS.get(low)
        if latin is None:
            return None
        if ch != low:
            latin = latin.upper() if word.isupper() and len(word) > 1 else latin[0].upper() + latin[1:]
        out.append(latin)
    return "".join(out)


def transliterated(words):
    best = {}
    for word, freq in words:
        latin = serbian_latin(word)
        if latin and freq > best.get(latin, -1):
            best[latin] = freq
    return sorted(best.items(), key=lambda w: -w[1])


def write_words(folder, head, words, file="words.combined"):
    with open(os.path.join(folder, file), "w", encoding="utf-8", newline="\n") as f:
        f.write((head or "dictionary=main") + "\n")
        for word, freq in words:
            f.write(" word=%s,f=%d\n" % (word, freq))


def common_letters(words, locale_lower):
    counts = {}
    for word, _ in words[:50_000]:
        for ch in locale_lower(word):
            if ch.isalpha():
                counts[ch] = counts.get(ch, 0) + 1
    return {ch for ch, n in counts.items() if n >= 20}


def lower(locale):
    if locale in ("tr", "az"):
        return lambda w: w.replace("I", "ı").replace("İ", "i").lower()
    return str.lower


SPECIAL_BASE = {"ß": "s", "ø": "o", "æ": "a", "œ": "o", "ł": "l", "đ": "d", "þ": "t", "ð": "d", "ı": "i", "ŋ": "n", "ə": "e"}


def latin_accents(letters):
    """The letters beyond a to z, each on its base letter's key, in the order given."""
    accents = {}
    for ch in letters:
        if ch in KEYS or not ch.isalpha():
            continue
        base = unicodedata.normalize("NFD", ch)[0]
        if base not in KEYS:
            base = SPECIAL_BASE.get(ch)
        if base:
            accents.setdefault(base, [])
            if ch not in accents[base]:
                accents[base].append(ch)
    return {k: "".join(v) for k, v in sorted(accents.items())}


def read_kcm(data, name):
    """The base letter on each of the 26 letter keys in an Android layout, and what Shift gives where it isn't the capital."""
    text = open(os.path.join(data, "kcm", "keyboard_layout_%s.kcm" % name), encoding="utf-8").read()
    keys = {}
    for m in re.finditer(r"key\s+([A-Z])\s*\{([^}]*)\}", text):
        body = m.group(2)

        def prop(names):
            for line in body.splitlines():
                left, _, right = line.partition(":")
                if set(x.strip() for x in left.split(",")) & names:
                    v = right.strip()
                    if v.startswith("'"):
                        v = v[1:-1]
                        v = re.sub(r"\\u([0-9a-fA-F]{4})", lambda x: chr(int(x.group(1), 16)), v).replace("\\'", "'").replace("\\\\", "\\")
                        return v
            return None

        base = prop({"base"})
        shift = prop({"shift", "shift, capslock"}) or prop({"shift"})
        if base:
            keys[m.group(1).lower()] = (base, shift)
    return keys


def notice(lines):
    return "\n\n".join(lines) + "\n"


def subtype_id(ident):
    """A fixed id for Android's keyboard list, so the user's choices survive updates: the first 4 bytes of the id's SHA-1."""
    value = int.from_bytes(hashlib.sha1(("titankeys.im." + ident).encode()).digest()[:4], "big")
    return value - (1 << 32) if value >= 1 << 31 else value


def build_latin(data, ident):
    name, english, label, locale, file, source, extra = LATIN[ident]
    folder = os.path.join(ASSETS, ident)
    os.makedirs(folder, exist_ok=True)
    main, aux = cldr_letters(data, locale)
    head, words = read_counts(data, file, locale, main + aux) if extra.get("counts") else read_words(data, file)
    if extra.get("transliterate") == "sr":
        words = transliterated(words)
        head = "dictionary=main:sr_Latn,locale=sr_Latn,description=Srpski"
    common = common_letters(words, lower(locale))
    # CLDR's letters first, in its order, then any other letter the word list uses often (é in Afrikaans).
    ordered = main + sorted(c for c in common if c not in main)
    accents = latin_accents(ordered)
    variants = None
    notices = [CLDR_NOTICE]
    if "variants" in extra:
        variants = []
        for vid, vname, vfile, vsource in extra["variants"]:
            vhead, vwords = read_words(data, vfile)
            write_words(folder, vhead, vwords, "words-%s.combined" % vid)
            variants.append({"id": vid, "name": vname, "words": "words-%s.combined" % vid})
            notices.append("%s (%s): %s" % (english, vname, vsource.format(file=vfile + ".combined")))
        words_file = variants[0]["words"]
    else:
        write_words(folder, head, words)
        words_file = "words.combined"
        file = os.path.basename(file) if extra.get("counts") else file + ".combined"
        notices.append("Words: " + source.format(file=file))
    rules = {"capitalise": True, "sentenceEnd": ".?!", "openingMarks": extra.get("openingMarks", ""),
             "apostrophes": extra.get("apostrophes", True)}
    write_im(folder, {"id": ident, "name": name, "nameEnglish": english, "label": label, "locale": locale, "script": "Latn",
                      "keys": "qwerty", "accents": accents, "words": words_file, "variants": variants, "rules": rules,
                      "engine": None, "source": {"letters": "CLDR 49 exemplarCharacters",
                                                                     "words": file if "." in file else file + ".combined"}})
    open(os.path.join(folder, "NOTICE"), "w", encoding="utf-8", newline="\n").write(notice(notices))


# Chinese through Rime (native/rime/SOURCES.md): no word list of their own here, since Rime's tables are in
# android/assets/rime. Yale first, the owner's input method. F (docs/words-m1.md).
# id: (name, English name, label, locale, Rime schema, sources)
CHINESE = {
    "yue-yale": ("耶魯拼音", "Cantonese, Yale", "耶", "yue", "tk_yale",
                 "rime-jyutping's Yale spelling rules (LGPL-3.0) on rime-cantonese's word list (CC BY 4.0)"),
    "yue-jyutping": ("粵拼", "Cantonese, Jyutping", "粵", "yue", "tk_jyutping", "rime-cantonese (CC BY 4.0)"),
    "zh-pinyin": ("漢語拼音", "Mandarin, Pinyin", "拼", "zh", "tk_pinyin",
                  "rime-luna-pinyin and rime-essay (LGPL-3.0)"),
}

CHINESE_NOTICE = ("Typed with Rime (librime, BSD-3-Clause) and OpenCC (Apache License 2.0). The words and spelling rules: "
                  "{source}. OpenCC's conversion dictionaries: Apache License 2.0. Their versions and licences: "
                  "native/rime/SOURCES.md and licences/Rime-*.txt.")


def build_chinese(data, ident):
    name, english, label, locale, schema, source = CHINESE[ident]
    folder = os.path.join(ASSETS, ident)
    os.makedirs(folder, exist_ok=True)
    rules = {"capitalise": False, "sentenceEnd": "。？！.?!", "openingMarks": "", "apostrophes": False}
    write_im(folder, {"id": ident, "name": name, "nameEnglish": english, "label": label, "locale": locale, "script": "Hani",
                      "keys": "qwerty", "accents": {}, "words": None, "variants": None, "rules": rules,
                      "engine": {"rime": schema}, "source": {"words": source}})
    open(os.path.join(folder, "NOTICE"), "w", encoding="utf-8", newline="\n").write(
        notice([CHINESE_NOTICE.format(source=source)]))

# Japanese through Mozc (native/mozc/SOURCES.md): no word list here, since Mozc's data is android/assets/mozc. Its mark on the
# bar is あ, as Japanese keyboards show it. F (docs/words-m1.md).
# id: (name, English name, label, locale)
JAPANESE = {"ja": ("日本語", "Japanese", "あ", "ja")}

JAPANESE_NOTICE = ("Typed with Mozc (BSD-3-Clause, Google) and its open-source dictionary (IPAdic, Okinawa dictionary, "
                   "Japanese Usage Dictionary and others, under their own permissive licences). Their versions and licences: "
                   "native/mozc/SOURCES.md and licences/Mozc-*.txt.")


def build_japanese(data, ident):
    name, english, label, locale = JAPANESE[ident]
    folder = os.path.join(ASSETS, ident)
    os.makedirs(folder, exist_ok=True)
    rules = {"capitalise": False, "sentenceEnd": "。？！.?!", "openingMarks": "", "apostrophes": False}
    write_im(folder, {"id": ident, "name": name, "nameEnglish": english, "label": label, "locale": locale, "script": "Jpan",
                      "keys": "qwerty", "accents": {}, "words": None, "variants": None, "rules": rules,
                      "engine": {"mozc": "oss"}, "source": {"words": "Mozc's open-source dictionary"}})
    open(os.path.join(folder, "NOTICE"), "w", encoding="utf-8", newline="\n").write(notice([JAPANESE_NOTICE]))

# Korean and Vietnamese through TitanKeys' own engines (android/kotlin/com/titankeys/keyboard/Hangul.kt and Telex.kt).
# Korean has no capitals and uses Shift for its tense consonants, so no sentence capitals.
# id: (name, English name, label, locale, script, engine, word list file or None, word list notice, notice for the typing)
ENGINES = {
    "ko": ("한국어", "Korean", "한", "ko", "Kore", "hangul", None, None,
           "Typed with TitanKeys' Hangul engine (GPL-3.0) on the Korean standard 2-set layout (KS X 5002): Hangul syllables "
           "built from their letters as Unicode composes them."),
    "vi": ("Tiếng Việt", "Vietnamese", "VI", "vi", "Latn", "telex", "main_vi__experimental", LEIPZIG,
           "Typed with TitanKeys' Telex engine (GPL-3.0)."),
}


def build_engine(data, ident):
    name, english, label, locale, script, engine, file, source, typing = ENGINES[ident]
    folder = os.path.join(ASSETS, ident)
    os.makedirs(folder, exist_ok=True)
    notices = [typing]
    if file:
        head, words = read_words(data, file)
        write_words(folder, head, words)
        notices = [CLDR_NOTICE, typing, "Words: " + source.format(file=file + ".combined")]
    rules = {"capitalise": ident != "ko", "sentenceEnd": ".?!", "openingMarks": "", "apostrophes": False}
    write_im(folder, {"id": ident, "name": name, "nameEnglish": english, "label": label, "locale": locale, "script": script,
                      "keys": "qwerty", "accents": {}, "words": "words.combined" if file else None, "variants": None,
                      "rules": rules, "engine": engine,
                      "source": {"keys": engine, "words": file + ".combined" if file else None}})
    open(os.path.join(folder, "NOTICE"), "w", encoding="utf-8", newline="\n").write(notice(notices))


def build_national(data, ident):
    name, english, label, locale, file, source, layout, holds, *more = NATIONAL[ident]
    extra = more[0] if more else {}
    folder = os.path.join(ASSETS, ident)
    os.makedirs(folder, exist_ok=True)
    main, _ = cldr_letters(data, locale)
    head, words = read_counts(data, file, locale, main) if extra.get("counts") else read_words(data, file)
    if extra.get("own_letters"):
        # AOSP's Serbian list has a few words in Latin letters ("a" among them), which its Cyrillic keys can't type.
        own = set(main)
        words = [(w, f) for w, f in words if all(c in own for c in w.lower())]
    kcm = read_kcm(data, layout)
    for k, letter in extra.get("keys", {}).items():
        kcm[k] = (letter, letter.upper())
    missing = [k for k in KEYS if k not in kcm]
    if missing:
        raise SystemExit("%s: %s has no letter for %s" % (ident, layout, " ".join(missing)))
    keys = {}
    for k in KEYS:
        base, shift = kcm[k]
        # Shift's own letter only where it's a letter and not the capital: Greek's W gives a dead key with Shift, which isn't kept.
        own = shift and shift != base.upper() and shift.isalpha()
        keys[k] = "%s/%s" % (base, shift) if own else base
    typed = {kcm[k][0] for k in KEYS}
    accents = {k: list(v) for k, v in holds.items()}
    common = common_letters(words, str.lower)
    by_base = {kcm[k][0]: k for k in KEYS}
    # Letters with a mark go on their base letter's key (Greek's tonos); anything else must be in the holds above.
    for ch in main + sorted(c for c in common if c not in main):
        if ch in typed or any(ch in v for v in accents.values()):
            continue
        base = unicodedata.normalize("NFD", ch)[0]
        key = by_base.get(base)
        if key:
            accents.setdefault(key, []).append(ch)
    accents = {k: "".join(v) for k, v in sorted(accents.items())}
    write_words(folder, head, words)
    shown = os.path.basename(file) if extra.get("counts") else file + ".combined"
    source_line = (source or BELARUSIAN).format(file=shown)
    rules = {"capitalise": extra.get("capitalise", True), "sentenceEnd": ".?!;" if ident == "el" else ".?!", "openingMarks": "",
             "apostrophes": ident in ("uk", "be")}
    layout_line = KCM_NOTICE.format(file="keyboard_layout_%s.kcm" % layout)
    if extra.get("layout_note") or extra.get("keys") or ident == "mk":
        changes = extra.get("layout_note") or ("with Macedonian's ѓ and ќ in place of ђ and ћ" if ident == "mk"
                                               else "with ж on Z in place of ѕ")
        layout_line = layout_line[:-1] + ", " + changes + "."
    write_im(folder, {"id": ident, "name": name, "nameEnglish": english, "label": label, "locale": locale,
                      "script": extra.get("script", "Grek" if ident == "el" else "Cyrl"), "keys": keys, "accents": accents,
                      "words": "words.combined", "variants": None, "rules": rules, "engine": None,
                      "source": {"letters": "CLDR 49 exemplarCharacters", "keys": "keyboard_layout_%s.kcm" % layout,
                                 "words": shown}})
    open(os.path.join(folder, "NOTICE"), "w", encoding="utf-8", newline="\n").write(
        notice([CLDR_NOTICE, layout_line, "Words: " + source_line]))


# Thai: Kedmanee (TIS 820-2538), read from xkeyboard-config's symbols/th "basic" (xkb/ in the data dir, with xorgproto's
# keysymdef.h for the characters). The Titan's 26 letter keys keep Kedmanee's letters and Shift letters where a PC keyboard
# has them. The 21 keys the Titan doesn't have go on a third layer: holding a letter key types the missing key's letter,
# with Shift its Shift letter. The number row goes over Q to P (and on to S and D); the rest sit on the rows below, in
# order.
THAI_LETTER_KEYS = dict(zip(KEYS, ["AC01", "AB05", "AB03", "AC03", "AD03", "AC04", "AC05", "AC06", "AD08", "AC07", "AC08",
                                   "AC09", "AB07", "AB06", "AD09", "AD10", "AD01", "AD04", "AC02", "AD05", "AD07", "AB04",
                                   "AD02", "AB02", "AD06", "AB01"]))
THAI_THIRD = {"q": "AE01", "w": "AE02", "e": "AE03", "r": "AE04", "t": "AE05", "y": "AE06", "u": "AE07", "i": "AE08",
              "o": "AE09", "p": "AE10", "s": "AE11", "d": "AE12", "a": "BKSL", "f": "AD11", "g": "AD12", "h": "AC10",
              "j": "AC11", "z": "TLDE", "b": "AB08", "n": "AB09", "m": "AB10"}
THAI_NOTICE = ("Key layout: Kedmanee (Thai Industrial Standard TIS 820-2538), as xkeyboard-config's symbols/th gives it "
               "(https://gitlab.freedesktop.org/xkeyboard-config/xkeyboard-config), with the keys the Titan doesn't have held "
               "on its letter keys.")
THAI_WORDS = ("Words: PyThaiNLP's corpus (https://github.com/PyThaiNLP/pythainlp), via HeliBoard's aosp-dictionaries "
              "(main_th.combined), CC0 1.0.")


def read_xkb(data, layout="basic", file="symbols-th"):
    """Each xkb key's characters in [layout] (base, Shift), from the keysyms' Unicode code points in keysymdef.h."""
    codes = {}
    for line in open(os.path.join(data, "xkb", "keysymdef.h"), encoding="utf-8"):
        m = re.match(r"#define XK_(\w+)\s+0x[0-9a-f]+\s*/\*[ (]*U\+([0-9A-F]{4,6})", line)
        if m:
            codes[m.group(1)] = chr(int(m.group(2), 16))
    text = open(os.path.join(data, "xkb", file), encoding="utf-8").read()
    block = re.search(r'xkb_symbols "%s" \{(.*?)\n\};' % layout, text, re.S).group(1)
    keys = {}
    for m in re.finditer(r"key <(\w+)>\s*\{\[\s*([^\]]+)\]\}", block):
        keys[m.group(1)] = [xkb_char(codes, name.strip()) for name in m.group(2).split(",")]
    return keys


def xkb_char(codes, name):
    """A keysym's character: by its name in keysymdef.h, or U05F3 as a code point; none is nothing."""
    if name == "none":
        return ""
    if re.fullmatch(r"U[0-9A-Fa-f]{4,6}", name):
        return chr(int(name[1:], 16))
    return codes[name]


def thai_keys(data):
    xkb = read_xkb(data)
    keys = {}
    for k in KEYS:
        base, shift = xkb[THAI_LETTER_KEYS[k]]
        third = xkb[THAI_THIRD[k]] if k in THAI_THIRD else []
        keys[k] = [base, shift] + third
    return keys


def build_thai(data, ident):
    folder = os.path.join(ASSETS, ident)
    os.makedirs(folder, exist_ok=True)
    head, words = read_words(data, "main_th")
    write_words(folder, head, words)
    write_im(folder, {"id": ident, "name": cldr_name(data, "th"), "nameEnglish": "Thai", "label": "TH", "locale": "th",
                      "script": "Thai", "keys": thai_keys(data), "accents": {}, "words": "words.combined", "variants": None,
                      "rules": {"capitalise": False, "sentenceEnd": ".?!", "openingMarks": "", "apostrophes": False,
                                "wordBreak": True},
                      "engine": None,
                      "source": {"letters": "Kedmanee (TIS 820-2538)", "keys": "xkeyboard-config symbols/th basic",
                                 "words": "main_th.combined"}})
    open(os.path.join(folder, "NOTICE"), "w", encoding="utf-8", newline="\n").write(notice([THAI_NOTICE, THAI_WORDS]))


# Hindi: Wikimedia's jquery.ime transliteration rules (jquery.ime/hi-transliteration.js in the data dir, GPL-3.0), run by
# the keyboard's Rules engine, and FrequencyWords' Hindi words.
HINDI_NOTICE = ("Typing: Wikimedia's jquery.ime, hi-transliteration (https://github.com/wikimedia/jquery.ime, "
                "rules/hi/hi-transliteration.js) by Junaid P V, GNU GPL 3.0.")


def js_values(text, start):
    """The JavaScript literal at [start] in [text]: strings, lists of them and nested lists, with comments between."""
    i = start

    def skip():
        nonlocal i
        while i < len(text):
            if text[i] in " \t\r\n,":
                i += 1
            elif text.startswith("//", i):
                i = text.index("\n", i)
            elif text.startswith("/*", i):
                i = text.index("*/", i) + 2
            else:
                return

    def value():
        nonlocal i
        skip()
        if text[i] == "[":
            i += 1
            out = []
            while True:
                skip()
                if text[i] == "]":
                    i += 1
                    return out
                out.append(value())
        quote = text[i]
        if quote not in "'\"":
            raise SystemExit("hi-transliteration.js: unexpected %r at %d" % (text[i], i))
        i += 1
        out = []
        while text[i] != quote:
            c = text[i]
            if c == "\\":
                n = text[i + 1]
                i += 2
                if n == "u":
                    out.append(chr(int(text[i:i + 4], 16)))
                    i += 4
                else:
                    out.append({"n": "\n", "r": "\r", "t": "\t", "0": "\0"}.get(n, n))
                continue
            out.append(c)
            i += 1
        i += 1
        return "".join(out)

    return value()


def read_jquery_ime(data, file):
    text = open(os.path.join(data, "jquery.ime", file), encoding="utf-8").read()
    number = lambda key: int(re.search(r"\b%s:\s*(\d+)" % key, text).group(1))
    patterns = js_values(text, text.index("[", text.index("patterns:")))
    return {"maxKeyLength": number("maxKeyLength"), "contextLength": number("contextLength"), "patterns": patterns}


def build_hindi(data, ident):
    folder = os.path.join(ASSETS, ident)
    os.makedirs(folder, exist_ok=True)
    rules = read_jquery_ime(data, "hi-transliteration.js")
    with open(os.path.join(folder, "rules.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(rules, f, ensure_ascii=False, indent=0)
        f.write("\n")
    # Devanagari's letters and signs (CLDR's exemplars leave out the vowel signs, which aren't alphabetic to Python), not its
    # digits or dandas.
    letters = [chr(c) for c in range(0x0900, 0x0980) if not 0x0964 <= c <= 0x096F and unicodedata.category(chr(c))[0] in "LM"]
    file = "frequencywords/hi_full.txt"
    head, words = read_counts(data, file, "hi", letters)
    write_words(folder, head, words)
    write_im(folder, {"id": ident, "name": cldr_name(data, "hi"), "nameEnglish": "Hindi", "label": "HI", "locale": "hi",
                      "script": "Deva", "keys": "qwerty", "accents": {}, "words": "words.combined", "variants": None,
                      # Shift letters are their own sounds (namaH, maChiyoM), so no sentence capitals.
                      "rules": {"capitalise": False, "sentenceEnd": ".?!।", "openingMarks": "", "apostrophes": False},
                      "engine": {"rules": "rules.json"},
                      "source": {"keys": "jquery.ime hi-transliteration", "words": os.path.basename(file)}})
    open(os.path.join(folder, "NOTICE"), "w", encoding="utf-8", newline="\n").write(
        notice([CLDR_NOTICE, HINDI_NOTICE, "Words: " + FREQUENCY_WORDS_OWN.format(file="hi_full.txt")]))


# Hebrew: SI-1452, read from xkeyboard-config's symbols/il "basic" (xkb/ in the data dir). Its letters sit on the Titan's
# letter keys where a PC has them; Shift types the same letter, as Hebrew has no capitals (the standard's Shift gives Latin
# capitals). Three letters are on keys the Titan lacks: ת (the comma key) goes on Q in place of /, and the final forms ף and
# ץ (; and .) are held on their own letters, P and M; / is held on Q.
HEBREW_THIRD = {"q": "/", "p": "ף", "m": "ץ"}
HEBREW_ON_Q = "ת"
HEBREW_NOTICE = ("Key layout: SI-1452, as xkeyboard-config's symbols/il gives it "
                 "(https://gitlab.freedesktop.org/xkeyboard-config/xkeyboard-config), with ת on Q and the final ף and ץ held "
                 "on P and M.")


def hebrew_keys(data):
    xkb = read_xkb(data, file="symbols-il")
    keys = {}
    for k in KEYS:
        base = xkb[THAI_LETTER_KEYS[k]][0]
        if k == "q":
            base = HEBREW_ON_Q
        held = HEBREW_THIRD.get(k)
        keys[k] = [base, base, held, held] if held else base
    return keys


def build_hebrew(data, ident):
    folder = os.path.join(ASSETS, ident)
    os.makedirs(folder, exist_ok=True)
    file = "frequencywords/he_full.txt"
    letters = [chr(c) for c in range(0x05D0, 0x05EB)] + ["'"]
    head, words = read_counts(data, file, "he", letters)
    write_words(folder, head, words)
    write_im(folder, {"id": ident, "name": cldr_name(data, "he"), "nameEnglish": "Hebrew", "label": "HE", "locale": "he",
                      "script": "Hebr", "keys": hebrew_keys(data), "accents": {}, "words": "words.combined", "variants": None,
                      # Geresh, typed as ', is part of a word (צ'יפס).
                      "rules": {"capitalise": False, "sentenceEnd": ".?!", "openingMarks": "", "apostrophes": True},
                      "engine": None,
                      "source": {"keys": "xkeyboard-config symbols/il basic", "words": os.path.basename(file)}})
    open(os.path.join(folder, "NOTICE"), "w", encoding="utf-8", newline="\n").write(
        notice([CLDR_NOTICE, HEBREW_NOTICE, "Words: " + FREQUENCY_WORDS_OWN.format(file="he_full.txt")]))


# Input methods built by a function of their own.
OWN = {"th": build_thai, "hi": build_hindi, "he": build_hebrew}

# The data folder, for CLDR's names as each input method is written.
DATA = None


def write_im(folder, im):
    # Every input method of a language is named as CLDR names it in that language, accents included; Chinese input methods are
    # named for their spelling (Pinyin, Jyutping, Yale), not the language.
    if im["script"] != "Hani":
        cldr = cldr_name(DATA, im["locale"])
        if im["name"] != cldr:
            sys.exit("%s: its name %r isn't CLDR's %r" % (im["id"], im["name"], cldr))
        im["source"] = {"name": "CLDR 49 localeDisplayNames: " + cldr, **im["source"]}
    with open(os.path.join(folder, "im.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(im, f, ensure_ascii=False, indent=2)
        f.write("\n")


# Languages with more than one input method (owner, 2026-11-16: languages, then input methods, the Gboard way). Every other
# input method is a language of its own, under its own name. A language's name is CLDR's for it in that language.
LANGUAGES = {
    "sr": {"name": "Српски", "nameEnglish": "Serbian", "label": "SR", "script": "Cyrl", "methods": ["sr", "sr-latn"]},
    "zh": {"name": "中文", "nameEnglish": "Chinese", "label": "中", "script": "Hani",
           "methods": ["yue-yale", "yue-jyutping", "zh-pinyin"]},
}
# Every language is marked experimental in the app but Chinese (owner, 2026-11-16); English is the keyboard's own.
NOT_EXPERIMENTAL = {"zh"}


def language_of(im):
    """The language input method [im] belongs to, as the index gives it to Settings and the switcher."""
    for ident, language in LANGUAGES.items():
        if im["id"] in language["methods"]:
            info = {key: language[key] for key in ("name", "nameEnglish", "label", "script")}
            return {"id": ident, **info, "experimental": ident not in NOT_EXPERIMENTAL}
    return {"id": im["id"], "name": im["name"], "nameEnglish": im["nameEnglish"], "label": im["label"], "script": im["script"],
            "experimental": im["id"] not in NOT_EXPERIMENTAL}


def write_index_and_list():
    entries = []
    for ident in sorted(os.listdir(ASSETS)):
        path = os.path.join(ASSETS, ident, "im.json")
        if not os.path.isfile(path):
            continue
        im = json.load(open(path, encoding="utf-8"))
        engine = im.get("engine")
        entries.append({"id": im["id"], "name": im["name"], "nameEnglish": im["nameEnglish"], "label": im["label"],
                        "locale": im["locale"], "script": im["script"],
                        "engine": (next(iter(engine)) if isinstance(engine, dict) else engine), "language": language_of(im)})
    order = list(LATIN) + list(NATIONAL) + list(OWN) + list(ENGINES) + list(CHINESE) + list(JAPANESE)
    entries.sort(key=lambda e: (order.index(e["id"]) if e["id"] in order else len(order), e["id"]))
    with open(os.path.join(ASSETS, "index.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(entries, f, ensure_ascii=False, indent=1)
        f.write("\n")
    # Android's keyboard list: English first, then every input method, each with a fixed id.
    strings = ['<?xml version="1.0" encoding="utf-8"?>',
               "<!-- Written by tools/im/make.py: one keyboard-list name per input method. Don't edit by hand. -->",
               "<resources>", '    <string name="im_en" translatable="false">English</string>']
    subtypes = [subtype("en", "en_US", "en-US")]
    for e in entries:
        res = "im_" + e["id"].replace("-", "_")
        strings.append('    <string name="%s" translatable="false">%s</string>' % (res, escape(e["name"])))
        subtypes.append(subtype(e["id"], e["locale"].replace("-", "_"), e["locale"]))
    strings.append("</resources>")
    open(os.path.join(RES, "values", "strings_im.xml"), "w", encoding="utf-8", newline="\n").write("\n".join(strings) + "\n")
    xml = ['<!-- One entry per input method, written by tools/im/make.py from android/assets/im (docs/languages.md). -->',
           '<input-method xmlns:android="http://schemas.android.com/apk/res/android"',
           '    android:settingsActivity="com.vandam.ink.MainActivity"',
           '    android:suppressesSpellChecker="true">'] + subtypes + ["</input-method>"]
    open(os.path.join(RES, "xml", "input_method.xml"), "w", encoding="utf-8", newline="\n").write("\n".join(xml) + "\n")
    return entries


def subtype(ident, locale, tag):
    return ('    <subtype android:label="@string/im_%s" android:imeSubtypeLocale="%s" android:languageTag="%s"\n'
            '        android:imeSubtypeMode="keyboard" android:imeSubtypeExtraValue="im=%s" android:subtypeId="%d" />'
            % (ident.replace("-", "_"), locale, tag, ident, subtype_id(ident)))


def escape(text):
    return text.replace("&", "&amp;").replace("<", "&lt;").replace("'", "\\'")


def main():
    global DATA
    data = DATA = sys.argv[1]
    wanted = sys.argv[2:] or list(LATIN) + list(NATIONAL) + list(ENGINES) + list(CHINESE) + list(JAPANESE) + list(OWN)
    os.makedirs(ASSETS, exist_ok=True)
    for ident in wanted:
        if ident in LATIN:
            build_latin(data, ident)
        elif ident in NATIONAL:
            build_national(data, ident)
        elif ident in ENGINES:
            build_engine(data, ident)
        elif ident in CHINESE:
            build_chinese(data, ident)
        elif ident in OWN:
            OWN[ident](data, ident)
        elif ident in JAPANESE:
            build_japanese(data, ident)
        else:
            raise SystemExit("unknown input method " + ident)
        print("built", ident)
    entries = write_index_and_list()
    print("index:", len(entries), "input methods")


if __name__ == "__main__":
    main()
