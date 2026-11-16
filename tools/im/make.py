#!/usr/bin/env python3
"""Builds TitanKeys' data-only input methods (docs/languages.md, docs/studies/56-languages.md).

    python3 -I tools/im/make.py <data dir> [id ...]

<data dir> holds the downloaded sources, as the study left them in WSL (~/build/lang/gen):
  aosp-dictionaries/   HeliBoard's word lists (https://codeberg.org/Helium314/aosp-dictionaries)
  cldr/x/package/      CLDR's cldr-misc-full (characters.json per locale)
  kcm/                 Android's keyboard_layout_*.kcm files

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
    "id": ("Bahasa Indonesia", "Indonesian", "ID", "id", "main_id__experimental", LEIPZIG, {"apostrophes": False}),
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
}

# Cyrillic and Greek: Android's national layout on the 26 keys, and the letters that layout puts on punctuation keys, held on the
# nearest key of the same row (docs/studies/56-languages.md, "How the letters sit on the Titan's keys"). Serbian waits: Android's
# layout is shared with Montenegrin (ѕ on Z, QWERTZ's Y and Z), and its mapping needs checking first.
NATIONAL = {
    "ru": ("Русский", "Russian", "RU", "ru", "main_ru", AOSP, "russian", {"p": "хъ", "l": "жэ", "m": "бю", "t": "ё"}),
    "uk": ("Українська", "Ukrainian", "UK", "uk", "main_uk__experimental", LEIPZIG, "ukrainian", {"p": "хї", "l": "жє", "m": "бю", "u": "ґ"}),
    "be": ("Беларуская", "Belarusian", "BE", "be", "main_be", None, "belarusian", {"p": "х", "l": "жэ", "m": "бю", "t": "ё"}),
    "bg": ("Български", "Bulgarian", "BG", "bg", "main_bg__experimental", LEIPZIG, "bulgarian_phonetic", {"p": "ящ", "m": "ью"}),
    "el": ("Ελληνικά", "Greek", "EL", "el", "main_el__legacy", AOSP, "greek", {}),
}
BELARUSIAN = ("Belarusian word forms from GrammarDB (https://github.com/Belarus/GrammarDB), via HeliBoard's aosp-dictionaries "
              "({file}), CC BY-SA 4.0.")

CLDR_NOTICE = "Letters and accents: Unicode CLDR 49, exemplar characters. Unicode License v3. Copyright Unicode, Inc."
KCM_NOTICE = "Key layout: Android's {file} (platform/frameworks/base, InputDevices), Apache License 2.0."


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
            fields = line.strip().split(",")
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
    main, _ = cldr_letters(data, locale)
    head, words = read_words(data, file)
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
        notices.append("Words: " + source.format(file=file + ".combined"))
    rules = {"capitalise": True, "sentenceEnd": ".?!", "openingMarks": extra.get("openingMarks", ""),
             "apostrophes": extra.get("apostrophes", True)}
    write_im(folder, {"id": ident, "name": name, "nameEnglish": english, "label": label, "locale": locale, "script": "Latn",
                      "keys": "qwerty", "accents": accents, "words": words_file, "variants": variants, "rules": rules,
                      "engine": None, "source": {"letters": "CLDR 49 exemplarCharacters", "words": file + ".combined"}})
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

def build_national(data, ident):
    name, english, label, locale, file, source, layout, holds = NATIONAL[ident]
    folder = os.path.join(ASSETS, ident)
    os.makedirs(folder, exist_ok=True)
    main, _ = cldr_letters(data, locale)
    head, words = read_words(data, file)
    kcm = read_kcm(data, layout)
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
    source_line = (source or BELARUSIAN).format(file=file + ".combined")
    rules = {"capitalise": True, "sentenceEnd": ".?!;" if ident == "el" else ".?!", "openingMarks": "", "apostrophes": ident in ("uk", "be")}
    write_im(folder, {"id": ident, "name": name, "nameEnglish": english, "label": label, "locale": locale,
                      "script": "Grek" if ident == "el" else "Cyrl", "keys": keys, "accents": accents, "words": "words.combined",
                      "variants": None, "rules": rules, "engine": None,
                      "source": {"letters": "CLDR 49 exemplarCharacters", "keys": "keyboard_layout_%s.kcm" % layout,
                                 "words": file + ".combined"}})
    open(os.path.join(folder, "NOTICE"), "w", encoding="utf-8", newline="\n").write(
        notice([CLDR_NOTICE, KCM_NOTICE.format(file="keyboard_layout_%s.kcm" % layout), "Words: " + source_line]))


def write_im(folder, im):
    with open(os.path.join(folder, "im.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(im, f, ensure_ascii=False, indent=2)
        f.write("\n")


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
                        "engine": (next(iter(engine)) if isinstance(engine, dict) else engine)})
    order = list(LATIN) + list(NATIONAL) + list(CHINESE)
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
    data = sys.argv[1]
    wanted = sys.argv[2:] or list(LATIN) + list(NATIONAL) + list(CHINESE)
    os.makedirs(ASSETS, exist_ok=True)
    for ident in wanted:
        if ident in LATIN:
            build_latin(data, ident)
        elif ident in NATIONAL:
            build_national(data, ident)
        elif ident in CHINESE:
            build_chinese(data, ident)
        else:
            raise SystemExit("unknown input method " + ident)
        print("built", ident)
    entries = write_index_and_list()
    print("index:", len(entries), "input methods")


if __name__ == "__main__":
    main()
