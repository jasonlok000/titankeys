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
    "af": ("Afrikaans", "Afrikaans", "AF", "af", "main_af__experimental", LEIPZIG, {"extra_accents": "èôû"}),
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
    "ru": ("Русский", "Russian", "РУ", "ru", "main_ru", AOSP, "russian", {"p": "хъ", "l": "жэ", "m": "бю", "t": "ё"}),
    "uk": ("Українська", "Ukrainian", "УК", "uk", "main_uk__experimental", LEIPZIG, "ukrainian", {"p": "хї'", "l": "жє", "m": "бю", "u": "ґ"}),
    # GrammarDB's 4 million forms have one frequency, so they're ranked by Leipzig's Belarusian news (J's find, 2026-11-17).
    "be": ("Беларуская", "Belarusian", "БЕ", "be", "main_be", None, "belarusian", {"p": "х'", "l": "эж", "m": "бю", "t": "ё"},
           {"rank_by": "leipzig/bel_newscrawl_2015_1M/bel_newscrawl_2015_1M-sentences.txt"}),
    "bg": ("Български", "Bulgarian", "БГ", "bg", "main_bg__experimental", LEIPZIG, "bulgarian_phonetic", {"p": "ящ", "m": "юь", "i": "ѝ"}),
    "el": ("Ελληνικά", "Greek", "ΕΛ", "el", "main_el__legacy", AOSP, "greek", {}),
    "sr": ("Српски", "Serbian, Cyrillic", "СР", "sr", "main_sr", AOSP, "serbian_and_montenegrin_cyrillic",
           {"p": "шђ", "l": "чћ"}, {"keys": {"z": "ж"}, "own_letters": True}),
    "mk": ("Македонски", "Macedonian", "МК", "mk", "frequencywords/mk_full.txt", FREQUENCY_WORDS_OWN,
           "serbian_and_montenegrin_cyrillic", {"p": "шжѓ", "l": "чќ", "i": "ѝ", "e": "ѐ"}, {"counts": True}),
    "kk": ("Қазақ тілі", "Kazakh", "ҚА", "kk", "main_kk__experimental", LEIPZIG, "russian",
           {"f": "ә", "u": "ғ", "r": "қ", "y": "ң", "j": "ө", "e": "ұү", "h": "һ", "s": "і", "p": "хъ", "l": "жэ", "m": "бю",
            "t": "ё"}, {"layout_note": "with Kazakh's letters held on the Russian letters nearest them"}),
    "ka": ("ქართული", "Georgian", "ქა", "ka", "leipzig/kat_newscrawl_2016_1M/kat_newscrawl_2016_1M-words.txt", LEIPZIG_OWN,
           "georgian", {}, {"counts": True, "script": "Geor", "capitalise": False}),
}
BELARUSIAN = ("Belarusian word forms from GrammarDB (https://github.com/Belarus/GrammarDB), via HeliBoard's aosp-dictionaries "
              "({file}), CC BY-SA 4.0. Ranked by how often each form appears in the Leipzig Corpora Collection's "
              "bel_newscrawl_2015_1M (https://wortschatz.uni-leipzig.de/en/download), CC BY 4.0. D. Goldhahn, T. Eckart and "
              "U. Quasthoff: Building Large Monolingual Dictionaries at the Leipzig Corpora Collection: From 100 to 200 Languages. "
              "LREC 2012.")

CLDR_NOTICE = "Letters and accents: Unicode CLDR 49, exemplar characters. Unicode License v3. Copyright Unicode, Inc."

# Lists whose own frequencies left junk on top (foreign and unaccented words, as Slovak's est and jane), so corrections fixed
# few slips or too often wrongly: each is ranked by how often its words appear in the Leipzig Corpora Collection's sentences
# for that language, and words the corpus never uses are left out (tests/corrections; owner's go via the Master, 2026-11-17).
# The sentences files are the ones the next-word tables use, in <data dir>/leipzig/.
RANK_BY = {
    "af": "afr_mixed_2019_1M", "bs": "bos_wikipedia_2021_300K", "ca": "cat_wikipedia_2021_1M", "el": "ell_news_2024_1M",
    "ga": "gle_newscrawl_2014_300K", "gl": "glg_wikipedia_2021_300K", "he": "heb_wikipedia_2021_1M",
    "is": "isl_newscrawl_2011_1M", "lv": "lav_newscrawl_2016_1M", "nn": "nno_wikipedia_2021_300K",
    "sk": "slk_newscrawl_2016_1M",
    # The second pass (2026-11-17): each of these fixed more slips ranked this way, with Space's wrong changes still at 0.5% or
    # under; the others fixed fewer, and keep their own frequencies.
    "cs": "ces_news_2024_1M", "eo": "epo_newscrawl_2017_1M", "es": "spa_news_2024_1M", "et": "est_newscrawl_2017_1M",
    "id": "ind_news_2024_1M", "kk": "kaz_newscrawl_2016_1M", "lt": "lit_news_2020_1M", "mk": "mkd_wikipedia_2021_300K",
    "pl": "pol_news_2024_1M", "ru": "rus_news_2024_1M", "tr": "tur_news_2024_1M",
    # The third (2026-11-17), measured mid-sentence: their own lists' rare forms (Ukrainian's "ставмося") made Space pick wrong
    # words 1.0% of the time or more; ranked, Ukrainian went from 3.0% to 0.3%, and the others under 1.0% but Serbian (1.2%).
    "bg": "bul_news_2022_1M", "hr": "hrv_wikipedia_2021_1M", "hu": "hun_news_2024_1M", "ro": "ron_news_2024_1M",
    "sr": "srp_wikipedia_2021_1M", "uk": "ukr_news_2024_1M",
}
# Ranked lists whose held accents still come from their own list: letters its words use that the corpus doesn't.
OWN_ACCENTS = {"tr"}
LEIPZIG_RANK = ("Ranked by how often each word appears in the Leipzig Corpora Collection's {corpus} "
                "(https://wortschatz.uni-leipzig.de/en/download), CC BY 4.0, and only the words it uses kept. D. Goldhahn, T. Eckart "
                "and U. Quasthoff: Building Large Monolingual Dictionaries at the Leipzig Corpora Collection: From 100 to 200 "
                "Languages. LREC 2012.")


def reranked(data, ident, words, notices):
    """[words] ranked by [RANK_BY]'s corpus for [ident], with its credit added to [notices]; as they are for other lists."""
    corpus = RANK_BY.get(ident)
    if not corpus:
        return words
    notices.append(LEIPZIG_RANK.format(corpus=corpus))
    return ranked_by_corpus(data, words, "leipzig/%s-sentences.txt" % corpus)
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


def read_words(data, name, limit=MAX_WORDS):
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
    return head, unique if limit is None else unique[:limit]


APOSTROPHES = str.maketrans({"’": "'", "ʼ": "'"})
CORPUS_WORD = re.compile(r"[^\W\d_]+(?:'[^\W\d_]+)*")


def ranked_by_corpus(data, words, sentences):
    """[words] (GrammarDB's forms, all f=100 in alphabetical order, or a list whose own frequencies are poor: [RANK_BY]),
    ranked by how often each appears in a Leipzig sentences file ("id<TAB>sentence"): the [MAX_WORDS] most used, on HeliBoard's scale as
    [read_counts]. A capital at a sentence's start counts for the word in lower case. Only the list's forms count, so the
    corpus's own spellings (Belarusian news in Taraškievica) never become words."""
    counts = {}
    with open(os.path.join(data, sentences), encoding="utf-8") as f:
        for line in f:
            text = line.split("\t", 1)[-1].translate(APOSTROPHES)
            for token in CORPUS_WORD.findall(text):
                counts[token] = counts.get(token, 0) + 1
    seen = []
    for word, _ in words:
        key = word.translate(APOSTROPHES)
        n = counts.get(key, 0)
        if key.islower():
            n += counts.get(key[0].upper() + key[1:], 0)
        if n:
            seen.append((word, n))
    ranked = sorted(seen, key=lambda w: -w[1])[:MAX_WORDS]
    return scaled(ranked)


def scaled(ranked):
    """Counts, most first, as AOSP's frequencies: HeliBoard's scale, the log of the count from 15 to 254."""
    import math
    low, high = math.log(max(ranked[-1][1], 1)), math.log(ranked[0][1])
    span = max(high - low, 1)
    return [(w, int((math.log(n) - low) * (254 - 15) / span + 15)) for w, n in ranked]


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
    words = scaled(sorted(counts.items(), key=lambda w: -w[1])[:MAX_WORDS])
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


# Paragraphs of a NOTICE that other steps write, kept when an input method is built again: J's next words
# (tools/im/next_words.py) and the starters ([write_starters]).
KEPT_NOTICES = ("Next words", "Starters: ")


def write_notice(folder, lines):
    """Writes [folder]'s NOTICE from [lines], keeping the paragraphs other steps added ([KEPT_NOTICES])."""
    path = os.path.join(folder, "NOTICE")
    kept = []
    if os.path.isfile(path):
        kept = [p for p in open(path, encoding="utf-8").read().strip("\n").split("\n\n") if p.startswith(KEPT_NOTICES)]
    open(path, "w", encoding="utf-8", newline="\n").write(notice(list(lines) + kept))


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
    ranking = []
    own = words
    words = reranked(data, ident, words, ranking)
    if extra.get("transliterate") == "sr":
        words = transliterated(words)
        head = "dictionary=main:sr_Latn,locale=sr_Latn,description=Srpski"
    # Turkish keeps the î its own list's loanwords use, which the news corpus never does (Master, 2026-11-17).
    common = common_letters(own if ident in OWN_ACCENTS else words, lower(locale))
    # CLDR's letters first, in its order, then any other letter the word list uses often (é in Afrikaans).
    ordered = main + sorted(c for c in common if c not in main)
    accents = latin_accents([*ordered, *extra.get("extra_accents", "")])
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
        notices += ranking
    rules = {"capitalise": True, "sentenceEnd": ".?!", "openingMarks": extra.get("openingMarks", ""),
             "apostrophes": extra.get("apostrophes", True)}
    write_im(folder, {"id": ident, "name": name, "nameEnglish": english, "label": label, "locale": locale, "script": "Latn",
                      "keys": "qwerty", "accents": accents, "words": words_file, "variants": variants, "rules": rules,
                      "engine": None, "source": {"letters": "CLDR 49 exemplarCharacters",
                                                                     "words": file if "." in file else file + ".combined"}})
    write_notice(folder, notices)


# Chinese through Rime (native/rime/SOURCES.md): no word list of their own here, since Rime's tables are in
# android/assets/rime. Yale first, the owner's input method. F (docs/words-m1.md).
# id: (name, English name, label, locale, Rime schema, sources)
CHINESE = {
    # F: draft. The words.hk and grammar credits in Yale's and Jyutping's (#56).
    "yue-yale": ("耶魯拼音", "Cantonese, Yale", "耶", "yue", "tk_yale",
                 "rime-jyutping's Yale spelling rules (LGPL-3.0) on rime-cantonese's word list (CC BY 4.0)"
                 ", with words.hk's word counts (public domain) and TitanKeys' grammar model, counted from Cantonese Wikipedia (CC BY-SA 4.0, and so is the model), HKCanCor (CC BY 4.0) and Common Voice (CC0)"),
    "yue-jyutping": ("粵拼", "Cantonese, Jyutping", "粵", "yue", "tk_jyutping",
                     "rime-cantonese (CC BY 4.0)"
                     ", with words.hk's word counts (public domain) and TitanKeys' grammar model, counted from Cantonese Wikipedia (CC BY-SA 4.0, and so is the model), HKCanCor (CC BY 4.0) and Common Voice (CC0)"),
    "zh-pinyin": ("漢語拼音", "Mandarin, Pinyin", "拼", "zh", "tk_pinyin",
                  "rime-luna-pinyin and rime-essay (LGPL-3.0)"
                  ", with lotem's Mandarin grammar model (rime-octagram-data, LGPL-3.0)"),
    # Hong Kong's standard shape-based input methods, and Taiwan's Zhuyin on the Hsu layout, which fits the Titan's 26 letters
    # (the standard 大千 layout needs the number row): owner and Orchestrator, 2026-11-17.
    "zh-cangjie": ("倉頡", "Chinese, Cangjie", "倉", "zh", "tk_cangjie",
                   "rime-cangjie's Cangjie 5 (LGPL-3.0; its dictionary, from 倉頡之友's 五倉世紀, marked GPL) and rime-essay (LGPL-3.0)"),
    "zh-quick": ("速成", "Chinese, Quick", "速", "zh", "tk_quick",
                 "rime-quick's Quick 5 (LGPL-3.0) on rime-cangjie's dictionary (marked GPL) and rime-essay (LGPL-3.0)"),
    "zh-zhuyin": ("注音 · 許氏", "Mandarin, Zhuyin (Hsu layout)", "注", "zh", "tk_zhuyin",
                  "rime-bopomofo's Zhuyin spelling rules on rime-terra-pinyin's word list (LGPL-3.0, refined from Chewing's)"
                  ", with lotem's Mandarin grammar model (rime-octagram-data, LGPL-3.0)"),
}

CHINESE_NOTICE = ("Typed with Rime (librime, BSD-3-Clause) and OpenCC (Apache License 2.0). The words and spelling rules: "
                  "{source}. OpenCC's conversion dictionaries: Apache License 2.0. Their versions and licences: "
                  "native/rime/SOURCES.md and licences/Rime-*.txt.")


def build_chinese(data, ident):
    name, english, label, locale, schema, source = CHINESE[ident]
    folder = os.path.join(ASSETS, ident)
    os.makedirs(folder, exist_ok=True)
    rules = {"capitalise": False, "sentenceEnd": "。？！.?!", "openingMarks": "", "apostrophes": False, "fullStop": "。"}
    write_im(folder, {"id": ident, "name": name, "nameEnglish": english, "label": label, "locale": locale, "script": "Hani",
                      "keys": "qwerty", "accents": {}, "words": None, "variants": None, "rules": rules,
                      "engine": {"rime": schema}, "source": {"words": source}})
    write_notice(folder, [CHINESE_NOTICE.format(source=source)])

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
    rules = {"capitalise": False, "sentenceEnd": "。？！.?!", "openingMarks": "", "apostrophes": False, "fullStop": "。"}
    write_im(folder, {"id": ident, "name": name, "nameEnglish": english, "label": label, "locale": locale, "script": "Jpan",
                      "keys": "qwerty", "accents": {}, "words": None, "variants": None, "rules": rules,
                      "engine": {"mozc": "oss"}, "source": {"words": "Mozc's open-source dictionary"}})
    write_notice(folder, [JAPANESE_NOTICE])

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
    if ident == "ko":
        # Korean's words, for the choices while a word is typed (하 → 하고, 한국) and the bar: the Leipzig Corpora Collection's
        # 2022 news, whole Hangul syllables only.
        file = KOREAN_WORDS
        hangul = [chr(c) for c in range(0xAC00, 0xD7A4)]
        head, words = read_counts(data, file, "ko", hangul)
        write_words(folder, head, words)
        notices = [typing, "Words: " + LEIPZIG_OWN.format(file=os.path.basename(file))]
    rules = {"capitalise": ident != "ko", "sentenceEnd": ".?!", "openingMarks": "", "apostrophes": False}
    write_im(folder, {"id": ident, "name": name, "nameEnglish": english, "label": label, "locale": locale, "script": script,
                      "keys": "qwerty", "accents": {}, "words": "words.combined" if file else None, "variants": None,
                      "rules": rules, "engine": engine,
                      "source": {"keys": engine, "words": (os.path.basename(file) if ident == "ko" else file + ".combined") if file else None}})
    write_notice(folder, notices)


def build_national(data, ident):
    name, english, label, locale, file, source, layout, holds, *more = NATIONAL[ident]
    extra = more[0] if more else {}
    folder = os.path.join(ASSETS, ident)
    os.makedirs(folder, exist_ok=True)
    main, _ = cldr_letters(data, locale)
    if extra.get("rank_by"):
        head, words = read_words(data, file, limit=None)
        words = ranked_by_corpus(data, words, extra["rank_by"])
    else:
        head, words = read_counts(data, file, locale, main) if extra.get("counts") else read_words(data, file)
    ranking = []
    words = reranked(data, ident, words, ranking)
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
        own = shift and shift != base.upper() and (shift.isalpha() or (ident, k, shift) == ("el", "q", ":"))
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
    write_notice(folder, [CLDR_NOTICE, layout_line, "Words: " + source_line] + ranking)


# Korean's word list (Leipzig Corpora Collection, CC BY 4.0), in the data dir.
KOREAN_WORDS = "leipzig/kor_news_2022_1M/kor_news_2022_1M-words.txt"

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
    write_im(folder, {"id": ident, "name": cldr_name(data, "th"), "nameEnglish": "Thai", "label": "ไทย", "locale": "th",
                      "script": "Thai", "keys": thai_keys(data), "accents": {}, "words": "words.combined", "variants": None,
                      "rules": {"capitalise": False, "sentenceEnd": ".?!", "openingMarks": "", "apostrophes": False,
                                "wordBreak": True, "fullStop": ""},
                      "engine": None,
                      "source": {"letters": "Kedmanee (TIS 820-2538)", "keys": "xkeyboard-config symbols/th basic",
                                 "words": "main_th.combined"}})
    write_notice(folder, [THAI_NOTICE, THAI_WORDS])


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
    write_im(folder, {"id": ident, "name": cldr_name(data, "hi"), "nameEnglish": "Hindi", "label": "हि", "locale": "hi",
                      "script": "Deva", "keys": "qwerty", "accents": {}, "words": "words.combined", "variants": None,
                      # Shift letters are their own sounds (namaH, maChiyoM), so no sentence capitals.
                      "rules": {"capitalise": False, "sentenceEnd": ".?!।", "openingMarks": "", "apostrophes": False, "fullStop": "। "},
                      "engine": {"rules": "rules.json"},
                      "source": {"keys": "jquery.ime hi-transliteration", "words": os.path.basename(file)}})
    write_notice(folder, [CLDR_NOTICE, HINDI_NOTICE, "Words: " + FREQUENCY_WORDS_OWN.format(file="hi_full.txt")])


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
    ranking = []
    words = reranked(data, ident, words, ranking)
    write_words(folder, head, words)
    write_im(folder, {"id": ident, "name": cldr_name(data, "he"), "nameEnglish": "Hebrew", "label": "עב", "locale": "he",
                      "script": "Hebr", "keys": hebrew_keys(data), "accents": {}, "words": "words.combined", "variants": None,
                      # Geresh, typed as ', is part of a word (צ'יפס).
                      "rules": {"capitalise": False, "sentenceEnd": ".?!", "openingMarks": "", "apostrophes": True},
                      "engine": None,
                      "source": {"keys": "xkeyboard-config symbols/il basic", "words": os.path.basename(file)}})
    write_notice(folder, [CLDR_NOTICE, HEBREW_NOTICE, "Words: " + FREQUENCY_WORDS_OWN.format(file="he_full.txt")] + ranking)


# The bar offers starters at the beginning of a sentence and as a fallback after a word.
# Chinese starters come from Rime's essays, with a Simplified form from OpenCC.
STARTERS = 12
STARTER_SOURCES = {
    "yue-yale": ("../src/rime-cantonese/essay-cantonese.txt", "essay"),
    "yue-jyutping": ("../src/rime-cantonese/essay-cantonese.txt", "essay"),
    "zh-pinyin": ("../src/rime-essay/essay.txt", "essay"),
    # As their predictors: Cangjie and Quick Rime's Cantonese (predict-yue), Zhuyin its Mandarin (predict-cmn).
    "zh-cangjie": ("../src/rime-cantonese/essay-cantonese.txt", "essay"),
    "zh-quick": ("../src/rime-cantonese/essay-cantonese.txt", "essay"),
    "zh-zhuyin": ("../src/rime-essay/essay.txt", "essay"),
}
# Sentence-initial words from the Leipzig Corpora Collection (CC BY 4.0), restricted to the input method's own list.
STARTER_CORPORA = {
    "af": "afr_mixed_2019_1M", "az": "aze_wikipedia_2021_1M", "be": "bel_newscrawl_2015_1M", "bg": "bul_news_2022_1M",
    "bs": "bos_wikipedia_2021_300K", "ca": "cat_wikipedia_2021_1M", "cs": "ces_news_2024_1M", "da": "dan_newscrawl_2023_1M",
    "de": "deu_news_2024_1M", "el": "ell_news_2024_1M", "eo": "epo_newscrawl_2017_1M", "es": "spa_news_2024_1M",
    "et": "est_newscrawl_2017_1M", "eu": "eus_wikipedia_2021_300K", "fi": "fin_news_2022_1M", "fr": "fra_news_2024_1M",
    "ga": "gle_newscrawl_2014_300K", "gl": "glg_wikipedia_2021_300K",
    "hr": "hrv_wikipedia_2021_1M", "hu": "hun_news_2024_1M", "is": "isl_newscrawl_2011_1M",
    "it": "ita_news_2024_1M", "kk": "kaz_newscrawl_2016_1M",
    "lt": "lit_news_2020_1M", "lv": "lav_newscrawl_2016_1M", "mk": "mkd_wikipedia_2021_300K", "nb": "nob_newscrawl_2019_1M",
    "nl": "nld_news_2024_1M", "nn": "nno_wikipedia_2021_300K", "pl": "pol_news_2024_1M", "pt": "por-br_newscrawl_2011_1M",
    "ro": "ron_news_2024_1M", "ru": "rus_news_2024_1M", "sk": "slk_newscrawl_2016_1M", "sl": "slv_wikipedia_2021_1M",
    "sr": "srp_wikipedia_2021_1M", "sr-latn": "srp_wikipedia_2021_1M", "sv": "swe_news_2023_1M", "tr": "tur_news_2024_1M",
    "uk": "ukr_news_2024_1M", "vi": "vie_news_2022_1M",
}
STARTER_CREDIT = ("Starters: the words that most often begin a sentence in the Leipzig Corpora Collection's {corpus} "
                  "(https://wortschatz.uni-leipzig.de/en/download), CC BY 4.0.")
# Greetings, replies and other common message openers. The reviewed corpora favour news or encyclopaedia prose.
# Thai's two polite replies can stand alone. Japanese dictionary costs rank readings rather than sentence openers.
FIXED_STARTERS = {
    "he": ["שלום", "תודה", "אני", "כן", "לא", "מה", "יש", "אפשר", "היום", "מחר", "עכשיו", "בטח"],
    "hi": ["नमस्ते", "हाँ", "मैं", "नहीं", "आप", "हम", "क्या", "आज", "कल", "अभी", "ठीक", "धन्यवाद"],
    "id": ["halo", "iya", "aku", "saya", "kamu", "kita", "apa", "boleh", "sudah", "belum", "besok", "makasih"],
    "ja": ["私", "今日", "はい", "これ", "それ", "何", "今", "また", "でも", "ありがとう", "そう", "明日"],
    "ka": ["გამარჯობა", "კი", "მადლობა", "არა", "მე", "შენ", "როგორ", "რა", "ახლა", "დღეს", "ხვალ", "კარგი"],
    "ko": ["안녕하세요", "네", "알겠습니다", "감사합니다", "아니요", "좋아요", "괜찮아요", "지금", "오늘", "내일", "혹시", "저는"],
    "th": ["สวัสดี", "ครับ", "ค่ะ", "ขอบคุณ", "ได้", "ใช่", "ผม", "เรา", "คุณ", "วันนี้", "พรุ่งนี้", "ตอนนี้"],
}


def is_word(word):
    return any(unicodedata.category(c)[0] == "L" for c in word) and not any(c.isdigit() or c in ".,!?;:\"()" for c in word)


def top_words(path, kind):
    """The commonest words of a frequency source, commonest first."""
    if kind == "list":
        words = []
        for line in open(path, encoding="utf-8"):
            # The shipped lists write " word=", some of HeliBoard's sources (Slovak's, Nynorsk's) "word=".
            if line.lstrip(" ").startswith("word="):
                fields = line.strip().split(",")
                word = fields[0][5:]
                freq = next((int(f[2:]) for f in fields if f.startswith("f=") and f[2:].isdigit()), 0)
                if "not_a_word=true" not in fields and "possibly_offensive=true" not in fields:
                    words.append((word, freq))
        return [w for w, _ in sorted(words, key=lambda w: -w[1])]
    if kind == "counts":
        pairs = []
        for line in open(path, encoding="utf-8"):
            parts = line.split()
            if len(parts) == 2 and parts[1].isdigit():
                pairs.append((parts[0], int(parts[1])))
        return [w for w, _ in sorted(pairs, key=lambda w: -w[1])]
    if kind == "essay":
        pairs = []
        for line in open(path, encoding="utf-8"):
            word, _, freq = line.rstrip("\n").partition("\t")
            if freq.isdigit():
                pairs.append((word, int(freq)))
        return [w for w, _ in sorted(pairs, key=lambda w: -w[1])]
    if kind == "mozc":
        # Mozc's dictionary: reading, left id, right id, cost, word; a lower cost is commoner. Only words that start a sentence
        # well, by their part of speech in id.def: pronouns, interjections, adverbs and common nouns, not verb forms or particles.
        starts = set()
        for line in open(os.path.join(path, "id.def"), encoding="utf-8"):
            number, _, pos = line.rstrip("\n").partition(" ")
            if pos.startswith(("名詞,代名詞", "感動詞", "名詞,一般", "副詞")):
                starts.add(number)
        best = {}
        for name in sorted(os.listdir(path)):
            if not re.match(r"dictionary\d+\.txt$", name):
                continue
            for line in open(os.path.join(path, name), encoding="utf-8"):
                parts = line.rstrip("\n").split("\t")
                if len(parts) == 5 and parts[3].isdigit() and parts[1] in starts:
                    best[parts[4]] = min(best.get(parts[4], 1 << 30), int(parts[3]))
        return [w for w, _ in sorted(best.items(), key=lambda w: w[1])]
    raise ValueError(kind)


# A word with its combining marks (Hindi's vowel signs, Thai's tones): Python's \w has letters, not marks.
MARKS = "".join(re.escape(chr(c)) for c in range(0x300, 0x10000) if unicodedata.category(chr(c)).startswith("M"))
WORD_WITH_MARKS = re.compile("[^\\W\\d_](?:[^\\W\\d_]|[" + MARKS + "]|'(?=[^\\W\\d_]))*")
# Sentence starts a corpus has that no one types first: news and Wikipedia boilerplate, and names a script without capitals
# can't tell apart (Korean's 윤, a president's name).
STARTER_SKIP = {
    "sk": {"prečítajte", "foto"},  # "Prečítajte si aj", a news site's links; photo credits
    "bs": {"napomena"},  # Wikipedia's "Napomena:" notes
    "is": {"innlent"},  # a news section's label, "Innlent"
    "eu": {"familiak"},  # Wikipedia's taxonomy lines
    "mk": {"проучување"},  # Wikipedia's "Проучување" headings
    "ga": {"net"},  # web boilerplate
    "ko": {"윤"},
    "sl": {"leta"},  # news datelines, "Leta 2010 ..."
    # Particles from Rime's essays, which never start a sentence: Mandarin's 的 了 和 在, Cantonese's 嘅.
    **{ident: {"的", "了", "和", "在"} for ident in ("zh-pinyin", "zh-zhuyin")},
    **{ident: {"嘅"} for ident in ("yue-yale", "yue-jyutping", "zh-cangjie", "zh-quick")},
}


def lower_word(word, ident):
    """[word] in lower case, Turkish's and Azerbaijani's dotted and dotless i kept apart."""
    if ident in ("tr", "az"):
        word = word.replace("I", "ı").replace("İ", "i")
    return word.lower()


def sentence_starts(path, ident, listed):
    """The words that begin [path]'s sentences ("id<TAB>sentence"), most often first: each sentence's first word in lower case,
    where the input method's list has it ([listed]). A name is left out: a word written with a capital more often than not in
    the middle of a sentence (Suomen, Azərbaycan). So is a prefix before a hyphen or a number (Hebrew's ב-2019). Serbian in
    Latin letters reads Serbian's Cyrillic, spelled in Latin."""
    starts, capital, small = {}, {}, {}
    skip = STARTER_SKIP.get(ident, set())
    for line in open(path, encoding="utf-8"):
        text = line.split("\t", 1)[-1].rstrip("\n").translate(APOSTROPHES)
        words = list(WORD_WITH_MARKS.finditer(text))
        if not words:
            continue
        for match in words[1:]:
            token = match.group(0)
            key = lower_word(token, ident)
            if token[0] != key[0]:
                capital[key] = capital.get(key, 0) + 1
            else:
                small[key] = small.get(key, 0) + 1
        first = words[0]
        # Only a sentence that starts with its word: not one that starts with a number, a dash or a quote's name.
        if text[:first.start()].strip(" \"'“„«»”(–-") or text[first.end():first.end() + 1] in ("-", "–") or \
                text[first.end():first.end() + 1].isdigit():
            continue
        key = lower_word(first.group(0), ident)
        word = (serbian_latin(key) or key) if ident == "sr-latn" else key
        if word in listed and word not in skip:
            starts[(word, key)] = starts.get((word, key), 0) + 1
    ranked = []
    for (word, key), _ in sorted(starts.items(), key=lambda w: -w[1]):
        if capital.get(key, 0) <= small.get(key, 0) and word not in ranked:
            ranked.append(word)
    return ranked


def listed_words(path):
    """Every word of a words.combined, in lower case where the list has it so."""
    words = set()
    for line in open(path, encoding="utf-8"):
        if line.startswith(" word="):
            words.add(line.strip().split(",")[0][5:])
    return words


# Opening marks: a sentence may start after these and still get its capital ("¿Qué", «Привет», „Hallo“, "Hi"). Every quote
# mark is passed over, opening or closing, since a closing one before a sentence's end or after it changes nothing.
QUOTES = "\"“”„«»"


def opening_marks(im):
    rules = im["rules"]
    if not rules.get("capitalise"):
        return rules.get("openingMarks", "")
    own = rules.get("openingMarks", "")
    return own + "".join(c for c in QUOTES if c not in own)


def simplified(data):
    """Traditional to Simplified, character by character, from OpenCC's TSCharacters.txt (its first choice)."""
    table = {}
    for line in open(os.path.join(data, "..", "rime-data", "occtxt", "TSCharacters.txt"), encoding="utf-8"):
        trad, _, simp = line.rstrip("\n").partition("\t")
        if trad and simp:
            table[trad] = simp.split(" ")[0]
    return lambda word: "".join(table.get(c, c) for c in word)


def write_starters(data, only=None):
    """Adds "starters" (and Chinese's "startersSimplified") to every im.json there is, or to those in [only]."""
    to_simplified = None
    for ident in sorted(os.listdir(ASSETS)):
        if only and ident not in only:
            continue
        path = os.path.join(ASSETS, ident, "im.json")
        if not os.path.isfile(path):
            continue
        im = json.load(open(path, encoding="utf-8"))
        im["rules"]["openingMarks"] = opening_marks(im)
        corpus = STARTER_CORPORA.get(ident)
        credit = None
        if ident in FIXED_STARTERS:
            ranked = FIXED_STARTERS[ident]
        elif ident in STARTER_SOURCES:
            file, kind = STARTER_SOURCES[ident]
            source = os.path.normpath(os.path.join(data, file)) if file else ident
            ranked = top_words(source, kind)
        elif corpus and im.get("words"):
            sentences = os.path.normpath(os.path.join(data, "..", "leipzig", corpus + "-sentences.txt"))
            ranked = sentence_starts(sentences, ident, listed_words(os.path.join(ASSETS, ident, im["words"])))
            credit = STARTER_CREDIT.format(corpus=corpus)
        elif im.get("words"):
            ranked = top_words(os.path.join(ASSETS, ident, im["words"]), "list")
        else:
            ranked = []
        notice_path = os.path.join(ASSETS, ident, "NOTICE")
        if os.path.isfile(notice_path):
            parts = [p for p in open(notice_path, encoding="utf-8").read().strip("\n").split("\n\n")
                     if not p.startswith("Starters: ")]
            open(notice_path, "w", encoding="utf-8", newline="\n").write(notice(parts + ([credit] if credit else [])))
        if not ranked and not (ident in STARTER_SOURCES):
            with open(path, "w", encoding="utf-8", newline="\n") as f:
                json.dump(im, f, ensure_ascii=False, indent=2)
                f.write("\n")
            continue
        starters = []
        skip = STARTER_SKIP.get(ident, set())
        for word in ranked:
            if is_word(word) and word not in starters and word not in skip:
                starters.append(word)
            if len(starters) == STARTERS:
                break
        im["starters"] = starters
        if im["script"] == "Hani":
            to_simplified = to_simplified or simplified(data)
            im["startersSimplified"] = [to_simplified(w) for w in starters]
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump(im, f, ensure_ascii=False, indent=2)
            f.write("\n")


# How-tos (owner, 2026-11-17: "we shd have basic instructions in text in each language specifically"): a few lines per input
# method for Settings > Languages, what's special to it. The text is F's (docs/howto-drafts.md), kept in tools/im/howto.json
# (input method id: lines); this writes it into each im.json as "howTo" and the index carries it.
HOWTO = os.path.join(os.path.dirname(os.path.abspath(__file__)), "howto.json")


def write_howto():
    """Adds "howTo" to every im.json that howto.json has lines for, and takes it off the others."""
    lines = json.load(open(HOWTO, encoding="utf-8"))
    for ident in sorted(os.listdir(ASSETS)):
        path = os.path.join(ASSETS, ident, "im.json")
        if not os.path.isfile(path):
            continue
        im = json.load(open(path, encoding="utf-8"))
        if lines.get(ident):
            im["howTo"] = lines[ident]
        elif "howTo" in im:
            del im["howTo"]
        else:
            continue
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump(im, f, ensure_ascii=False, indent=2)
            f.write("\n")


# The language pages' guide (owner-approved flow; E builds the pages, F's text is tools/im/page-text.json): each im.json's
# "guide" and android/assets/im/languages.json, made from what's already there. Ids are page-text.json's keys: a role's
# "role" is roles.<key>.<role>, a guide's "family" is points.<family>, a setting's "reason" is notAvailable.<reason>.
ENGINE_MAPS = {
    # Korean's 2-set (Hangul.kt) and Shift's tense consonants and two vowels.
    "ko": ({"q": "ㅂ", "w": "ㅈ", "e": "ㄷ", "r": "ㄱ", "t": "ㅅ", "y": "ㅛ", "u": "ㅕ", "i": "ㅑ", "o": "ㅐ", "p": "ㅔ", "a": "ㅁ",
            "s": "ㄴ", "d": "ㅇ", "f": "ㄹ", "g": "ㅎ", "h": "ㅗ", "j": "ㅓ", "k": "ㅏ", "l": "ㅣ", "z": "ㅋ", "x": "ㅌ", "c": "ㅊ",
            "v": "ㅍ", "b": "ㅠ", "n": "ㅜ", "m": "ㅡ"},
           {"q": "ㅃ", "w": "ㅉ", "e": "ㄸ", "r": "ㄲ", "t": "ㅆ", "o": "ㅒ", "p": "ㅖ"}),
}
CANGJIE = {"q": "手", "w": "田", "e": "水", "r": "口", "t": "廿", "y": "卜", "u": "山", "i": "戈", "o": "人", "p": "心", "a": "日",
           "s": "尸", "d": "木", "f": "火", "g": "土", "h": "竹", "j": "十", "k": "大", "l": "中", "z": "符", "x": "難", "c": "金",
           "v": "女", "b": "月", "n": "弓", "m": "一"}
ENGINE_MAPS["zh-cangjie"] = (CANGJIE, {})
ENGINE_MAPS["zh-quick"] = (CANGJIE, {})
# Hsu shares keys between initials, finals and tones; the syllable determines their reading.
ENGINE_MAPS["zh-zhuyin"] = ({"q": "", "w": "ㄠ", "e": "ㄧㄝ", "r": "ㄖ", "t": "ㄊ", "y": "ㄚ", "u": "ㄩ", "i": "ㄞ", "o": "ㄡ",
                             "p": "ㄆ", "a": "ㄘㄟ", "s": "ㄙ˙", "d": "ㄉˊ", "f": "ㄈˇ", "g": "ㄍㄜ", "h": "ㄏㄛ", "j": "ㄐㄓˋ",
                             "k": "ㄎㄤ", "l": "ㄌㄥㄦ", "z": "ㄗ", "x": "ㄨ", "c": "ㄒㄕ", "v": "ㄑㄔ", "b": "ㄅ",
                             "n": "ㄋㄣ", "m": "ㄇㄢ"}, {})
LANGUAGE_GROUPS = {
    "zh": [{"title": "Cantonese", "methods": ["yue-yale", "yue-jyutping"]}, {"title": "Mandarin", "methods": ["zh-pinyin", "zh-zhuyin"]},
           {"title": "By shape", "methods": ["zh-cangjie", "zh-quick"]}],
    "sr": [{"title": "Alphabets", "methods": ["sr", "sr-latn"]}],
}
# Each setting a language's page may show, with its level. A row that doesn't apply gives why, as LanguageSettings.rows
# decides (checked against it in tests/languages).
GUIDE_SETTINGS = [("fixes", "language"), ("capitals", "language"), ("doubleSpace", "language"), ("sentenceSpaces", "language"),
                  ("commaSpace", "language"), ("hold", "language"), ("swipe", "language"), ("shorthand", "language"),
                  ("spelling", "language"), ("chineseCharacters", "language"), ("variant", "input-method"),
                  ("lazySounds", "input-method"), ("keyPreview", "input-method")]
GUIDE_OWN_MARKS = {"rime", "mozc"}


def engine_name(im):
    engine = im.get("engine")
    return next(iter(engine)) if isinstance(engine, dict) else engine


def key_letters(value):
    """A key's letters in im.json, as [base, shift, held, shift held]: "a/A", a list, or one letter."""
    if isinstance(value, list):
        return value + [None] * (4 - len(value))
    base, _, shift = value.partition("/")
    return [base, shift or None, None, None]


def held_keys(im):
    keys = im.get("keys")
    return isinstance(keys, dict) and any(isinstance(v, list) and len(v) > 2 and v[2] for v in keys.values())


def setting_reason(row, im):
    """Why [row] doesn't apply to [im], as an id (page-text.json's notAvailable where it has one), or None where it does."""
    engine = engine_name(im)
    rules = im["rules"]
    english = im["id"] == "en"
    full_stop = rules.get("fullStop", ". ")
    chinese = im.get("script") == "Hani"
    key_map = im.get("guide", {}).get("keys", {}).get("map", {})
    mapped = im["script"] != "Latn" and any(output and output != key for key, output in key_map.items())
    return {
        "fixes": None if english or (im.get("words") and engine is None) else ("correctionsChinese" if chinese else "corrections"),
        "capitals": None if rules.get("capitalise") else "noCapitals",
        "doubleSpace": None if full_stop.strip() else "noFullStop",
        "sentenceSpaces": None if full_stop.endswith(" ") else "noSpaceAfterFullStop",
        "commaSpace": None if engine not in GUIDE_OWN_MARKS else "engineMarks",
        # With accents, the keys that hold none hold for their symbol (owner, 2026-11-22, #107): only a third layer takes it.
        "hold": None if not held_keys(im) else "holdLetters",
        "swipe": None if english else "swiping",
        "shorthand": None if english else "englishOnly",
        "spelling": None if english else "englishOnly",
        "chineseCharacters": None if chinese else "chineseOnly",
        "variant": None if im.get("variants") else "noVariants",
        "lazySounds": None if im["id"] in ("yue-yale", "yue-jyutping") else "notThisMethod",
        "keyPreview": None if mapped else "notThisMethod",
    }[row]


def family_of(im):
    """page-text.json's points family for [im]."""
    engine, script, ident = engine_name(im), im.get("script"), im["id"]
    if script == "Hani":
        return "chinese"
    return {"ko": "korean", "ja": "japanese", "vi": "vietnamese", "hi": "hindi", "th": "thai", "he": "hebrew", "ka": "georgian",
            "el": "greek"}.get(ident) or ("cyrillic" if script == "Cyrl" else "latin" if im.get("accents") else "plain")


def guide_of(im, language):
    ident, engine, keys = im["id"], engine_name(im), im.get("keys")
    shown, shifted, held, held_shift = {}, {}, {}, {}
    if isinstance(keys, dict):
        for key, value in keys.items():
            base, shift, hold, hold_shift = key_letters(value)
            if base and base != key:
                shown[key] = base
            if shift and shift != (base or key).upper():
                shifted[key] = shift
            if hold:
                held[key] = hold
            if hold_shift:
                held_shift[key] = hold_shift
    if ident in ENGINE_MAPS:
        shown, shifted = dict(ENGINE_MAPS[ident][0]), dict(ENGINE_MAPS[ident][1])
    chinese = im.get("script") == "Hani"
    if engine in GUIDE_OWN_MARKS:
        roles = {"shift": "choices", "space": "choices", "enter": "code", "backspace": "chinese" if chinese else "code",
                 "sym": "choices"}
    elif engine == "hangul":
        roles = {"shift": "secondLetter", "space": "syllable", "enter": "default", "backspace": "syllable", "sym": "default"}
    elif engine in ("telex", "rules"):
        roles = {"shift": "letters" if im["rules"].get("capitalise") else "secondLetter", "space": "default", "enter": "default",
                 "backspace": "code", "sym": "default"}
    else:
        shift = "sameLetter" if ident == "he" else "secondLetter" if shifted else ("accents" if im.get("accents") else "letters")
        roles = {"shift": shift, "space": "default", "enter": "default", "backspace": "default", "sym": "default"}
    return {"status": stage_of(language), "family": family_of(im), "line": "", "points": [],
            "practice": "",
            "keys": {"legend": "", **({"map": shown} if shown else {}), **({"shift": shifted} if shifted else {}),
                     **({"held": held} if held else {}), **({"heldShift": held_shift} if held_shift else {}),
                     **({"accents": im["accents"]} if im.get("accents") else {}),
                     "capitals": bool(im["rules"].get("capitalise")),
                     "roles": [{"key": key, "role": role} for key, role in roles.items()]}}


def write_guides():
    """Adds "guide" to every im.json (keeping F's line, points and practice), and writes languages.json."""
    languages = {}
    for ident in sorted(os.listdir(ASSETS)):
        path = os.path.join(ASSETS, ident, "im.json")
        if not os.path.isfile(path):
            continue
        im = json.load(open(path, encoding="utf-8"))
        language = language_of(im)
        kept = {k: v for k, v in im.get("guide", {}).items() if k in ("line", "points", "practice") and v}
        im["guide"] = {**guide_of(im, language), **kept}
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump(im, f, ensure_ascii=False, indent=2)
            f.write("\n")
        languages.setdefault(language["id"], []).append(im)
    # English is built in, so it has no im.json. Its page still uses the same setting metadata.
    languages["en"] = [{"id": "en", "script": "Latn", "rules": {"capitalise": True}}]
    out = {"$global": {"settings": [{"id": "learn", "level": "global", "available": True}]}}
    for lang, ims in sorted(languages.items()):
        settings = []
        for row, level in GUIDE_SETTINGS:
            reasons = [setting_reason(row, im) for im in ims]
            # A language's row shows where any of its input methods has it, as its page does.
            available = any(reason is None for reason in reasons)
            # An input method's own switch says which of the language's input methods have it.
            methods = [im["id"] for im, reason in zip(ims, reasons) if reason is None] if level == "input-method" else None
            settings.append({"id": row, "level": level, "available": available, **({"methods": methods} if methods else {}),
                             **({} if available else {"reason": reasons[0]})})
        out[lang] = {"points": [], "roles": [], "groups": LANGUAGE_GROUPS.get(lang, []), "settings": settings}
    with open(os.path.join(ASSETS, "languages.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
        f.write("\n")


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
    im["rules"]["openingMarks"] = opening_marks(im)
    with open(os.path.join(folder, "im.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(im, f, ensure_ascii=False, indent=2)
        f.write("\n")


# Labels (Orchestrator, 2026-11-16, for the owner to confirm): one per input method, at Q and in the switcher, at most two
# characters in the language's own script: Latin's two-letter code in capitals, РУ, ΕΛ, ქა, ไท, עב, हि, and one character
# where the script is dense (한, あ, 耶, 粵, 拼).
# Languages with more than one input method (owner, 2026-11-16: languages, then input methods, the Gboard way). Every other
# input method is a language of its own, under its own name. A language's name is CLDR's for it in that language.
LANGUAGES = {
    "sr": {"name": "Српски", "nameEnglish": "Serbian", "label": "СР", "script": "Cyrl", "methods": ["sr", "sr-latn"]},
    "zh": {"name": "中文", "nameEnglish": "Chinese", "label": "中", "script": "Hani",
           "methods": ["yue-yale", "yue-jyutping", "zh-pinyin", "zh-cangjie", "zh-quick", "zh-zhuyin"]},
}
# Three stages (owner, 2026-11-22; docs/languages.md): experimental, tested (a native speaker checked its typing) and polished
# (as refined as Chinese). Every language is experimental but Chinese (owner, 2026-11-16); English is the keyboard's own.
NOT_EXPERIMENTAL = {"zh"}
# Tested languages stay "experimental" too, so the switcher and every other reader of it are unchanged.
# Italian, Russian and Ukrainian: native speakers confirmed them (owner, 2026-11-24).
TESTED = {"ko", "it", "ru", "uk"}


def stage_of(language):
    """A language's stage, as its guide's "status" gives it to its page."""
    return "polished" if not language["experimental"] else "tested" if language["tested"] else "experimental"


def language_of(im):
    """The language input method [im] belongs to, as the index gives it to Settings and the switcher."""
    for ident, language in LANGUAGES.items():
        if im["id"] in language["methods"]:
            info = {key: language[key] for key in ("name", "nameEnglish", "label", "script")}
            return {"id": ident, **info, "experimental": ident not in NOT_EXPERIMENTAL, "tested": ident in TESTED}
    return {"id": im["id"], "name": im["name"], "nameEnglish": im["nameEnglish"], "label": im["label"], "script": im["script"],
            "experimental": im["id"] not in NOT_EXPERIMENTAL, "tested": im["id"] in TESTED}


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
                        "engine": (next(iter(engine)) if isinstance(engine, dict) else engine), "language": language_of(im),
                        **({"howTo": im["howTo"]} if im.get("howTo") else {}),
                        # The language pages' guide (write_guides).
                        **({"guide": im["guide"]} if im.get("guide") else {}),
                        # Its spelling variants (Portuguese's Brasil and Portugal), for Settings' choice: the first is the default.
                        **({"variants": [{"id": v["id"], "name": v["name"]} for v in im["variants"]]}
                           if im.get("variants") else {})})
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
    # make.py <data dir> --starters: only the starter words, for every input method there is.
    # make.py <data dir> --starters [id ...]: those input methods' only, as reading every corpus takes half an hour.
    if sys.argv[2:3] == ["--starters"]:
        write_starters(data, sys.argv[3:] or None)
        print("starters written")
        return
    # make.py <data dir> --howto: only the how-tos, into every im.json and the index.
    # make.py <data dir> --guide: only the language pages' guide data, into every im.json and languages.json.
    if sys.argv[2:] == ["--guide"]:
        write_guides()
        print("guides written")
        return
    if sys.argv[2:] == ["--howto"]:
        write_howto()
        print("index:", len(write_index_and_list()), "input methods, how-tos written")
        return
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
    write_starters(data)
    write_howto()
    write_guides()
    entries = write_index_and_list()
    print("index:", len(entries), "input methods")


if __name__ == "__main__":
    main()
