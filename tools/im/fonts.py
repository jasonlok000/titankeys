#!/usr/bin/env python3
"""Builds the app's two text fonts, assets/fonts/Inter-Regular.otf and Inter-Medium.otf (theme.ts), so every input
method's own name shows in Settings > Input methods (docs/languages.md, "Names").

    python3 -I tools/im/fonts.py <Inter dir> <Noto Sans Georgian dir> <Noto Sans CJK dir> [<Noto Sans Devanagari file>]

<Inter dir> holds Inter-Regular.otf and Inter-Medium.otf from the official Inter 4.1 release
(https://github.com/rsms/inter/releases/download/v4.1/Inter-4.1.zip, extras/otf). <Noto Sans Georgian dir> holds
NotoSansGeorgian-Regular.ttf and NotoSansGeorgian-Medium.ttf from Google's official notofonts.github.io repository
(fonts/NotoSansGeorgian/hinted/ttf), and beside them NotoSansThai-Regular.ttf and -Medium.ttf (fonts/NotoSansThai/hinted/ttf),
for the Thai letters in names, and NotoSansHebrew-Regular.ttf and -Medium.ttf for Hebrew's. <Noto Sans CJK dir> holds
NotoSans{KR,HK,JP,SC}-{Regular,Medium}.otf from https://github.com/notofonts/noto-cjk (Sans/SubsetOTF). All are
under the SIL Open Font License 1.1 (licences/Inter-OFL.txt, licences/NotoSansGeorgian-OFL.txt,
licences/NotoSansCJK-OFL.txt, licences/NotoSansThai-OFL.txt, licences/NotoSansHebrew-OFL.txt). Needs fontTools.

Ink draws the screens with these fonts, then Public Sans, then a few of Android's own (symbols, CJK, Arabic), and leaves a
blank where none has the letter. The fonts were Latin-1 only, so Cyrillic and Greek names were blank, and Czech, Romanian,
Vietnamese and others lost letters to Public Sans. Each is now Inter cut down to Latin with its extensions, Greek and
Cyrillic, with Noto Sans Georgian's letters added, drawn at Inter's size. Android's CJK font didn't reach Ink on the Titan
2 Elite (한국어 and the Chinese names were blank), so the Hangul and Chinese characters the app's own text uses (input
method names in android/assets/im/index.json, and the TypeScript in app/ and lib/) are added too, from Noto Sans CJK: Korean
from its Korean cut, Chinese from its Hong Kong cut, and characters missing there from the Japanese and Simplified Chinese
cuts. Run this again when a new name or text brings a new one;
tests/languages fails until then.

Every input method's how-to is the app's own text too (owner, 2026-11-17: Korean's how-to showed blanks where its letters were):
every character of every im.json's howTo, and of tools/im/howto.json, is added, from the first of the sources above that has
it, and Devanagari from Noto Sans Devanagari (Android's own NotoSansDevanagari-VF.ttf, SIL OFL 1.1), given as the fourth
argument; Medium is its weight 500. Ink places no marks, so Devanagari's vowel signs sit beside their letters, not on them.
"""
import glob
import json
import os
import sys
import tempfile

from fontTools import subset
from fontTools.pens.t2CharStringPen import T2CharStringPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))

# What the fonts were (Latin-1, punctuation, arrows), plus the letters every input method's name and keys need.
RANGES = [
    (0x0020, 0x007E), (0x00A0, 0x017F),  # Latin-1 and Latin Extended-A: č ő ł ș
    (0x0218, 0x021B),  # ș ț (Romanian)
    (0x0259, 0x0259),  # ə (Azerbaijani)
    (0x02C6, 0x02DD),  # spacing accents
    (0x0384, 0x03CE),  # Greek
    (0x0400, 0x04FF),  # Cyrillic, Kazakh's included
    (0x1E00, 0x1EFF),  # Latin Extended Additional: Vietnamese
    (0x2000, 0x206F), (0x20AC, 0x20AC), (0x2122, 0x2122), (0x2190, 0x2193), (0x2212, 0x2212),
]
GEORGIAN = range(0x10D0, 0x1100)


def is_hangul(code):
    return 0x1100 <= code <= 0x11FF or 0x3130 <= code <= 0x318F or 0xAC00 <= code <= 0xD7AF


def is_han(code):
    return 0x3400 <= code <= 0x4DBF or 0x4E00 <= code <= 0x9FFF or 0xF900 <= code <= 0xFAFF


def is_thai(code):
    return 0x0E01 <= code <= 0x0E7F


def is_hebrew(code):
    return 0x0590 <= code <= 0x05FF


def names_in(script):
    """Every letter of [script] (a test) in the input methods' and languages' names: ไทย and עברית in Settings."""
    index = json.load(open(os.path.join(ROOT, "android", "assets", "im", "index.json"), encoding="utf-8"))
    return sorted({ord(c) for m in index for c in m["name"] + m["language"]["name"] if script(ord(c))})


def howto_text():
    """Every how-to's text: each input method's howTo in android/assets/im, and tools/im/howto.json."""
    out = []

    def walk(value):
        if isinstance(value, str):
            out.append(value)
        elif isinstance(value, list):
            for item in value:
                walk(item)
        elif isinstance(value, dict):
            for item in value.values():
                walk(item)

    for path in glob.glob(os.path.join(ROOT, "android", "assets", "im", "*", "im.json")):
        walk(json.load(open(path, encoding="utf-8")).get("howTo", []))
    howto = os.path.join(ROOT, "tools", "im", "howto.json")
    if os.path.exists(howto):
        walk(json.load(open(howto, encoding="utf-8")))
    native = os.path.join(ROOT, "tools", "im", "native-pages.json")
    if os.path.exists(native):
        walk(json.load(open(native, encoding="utf-8")))
    return out


def keymap_text():
    """Legends drawn from the generated guide, including the explicit Shift and hold layers."""
    for path in glob.glob(os.path.join(ROOT, "android", "assets", "im", "*", "im.json")):
        with open(path, encoding="utf-8") as f:
            keys = json.load(f).get("guide", {}).get("keys", {})
        for layer in ("map", "shift", "held", "heldShift", "accents"):
            for legend in keys.get(layer, {}).values():
                yield legend
                if keys.get("capitals"):
                    yield legend.upper()


def cjk_in_text():
    """Every Hangul and Chinese character in the input methods' names and in the app's own text."""
    index = json.load(open(os.path.join(ROOT, "android", "assets", "im", "index.json"), encoding="utf-8"))
    text = [m["name"] for m in index] + [m["language"]["name"] for m in index]
    for pattern in ("app/**/*.ts", "app/**/*.tsx", "lib/**/*.ts", "lib/**/*.tsx"):
        for path in glob.glob(os.path.join(ROOT, pattern), recursive=True):
            text.append(open(path, encoding="utf-8").read())
    text += howto_text()
    codes = {ord(c) for chunk in text for c in chunk}
    return sorted(c for c in codes if is_hangul(c)), sorted(c for c in codes if is_han(c))
# As the Latin-1 fonts had: names and settings are whole letters (NFC), so no accent placement.
FEATURES = ["calt", "tnum", "kern"]


def unicodes():
    out = set()
    for start, end in RANGES:
        out.update(range(start, end + 1))
    return sorted(out)


def cut(path):
    options = subset.Options()
    options.layout_features = FEATURES
    options.name_IDs = ["*"]
    options.notdef_outline = True
    # Ink rasterises with ab_glyph, which ignores hints.
    options.hinting = False
    options.recalc_bounds = True
    font = TTFont(path)
    subsetter = subset.Subsetter(options)
    subsetter.populate(unicodes=unicodes())
    subsetter.subset(font)
    return font


def add_glyphs(font, source_path, codes):
    """Draws [codes]' letters from the font at [source_path] into [font]'s CFF, scaled to its units per em."""
    source = TTFont(source_path)
    scale = font["head"].unitsPerEm / source["head"].unitsPerEm
    glyphs = source.getGlyphSet()
    cmap = source.getBestCmap()
    cff = font["CFF "].cff
    top = cff.topDictIndex[0]
    strings = top.CharStrings
    order = font.getGlyphOrder()
    added = {}
    for code in codes:
        name = cmap.get(code)
        if name is None:
            sys.exit("U+%04X isn't in %s" % (code, source_path))
        width = round(glyphs[name].width * scale)
        pen = T2CharStringPen(width, glyphs)
        glyphs[name].draw(TransformPen(pen, (scale, 0, 0, scale, 0, 0)))
        charstring = pen.getCharString(private=top.Private, globalSubrs=cff.GlobalSubrs)
        new = "uni%04X" % code
        strings.charStringsIndex.append(charstring)
        strings.charStrings[new] = len(strings.charStringsIndex) - 1
        top.charset.append(new)
        order.append(new)
        font["hmtx"].metrics[new] = (width, 0)
        added[code] = new
    font.setGlyphOrder(order)
    for table in font["cmap"].tables:
        if table.isUnicode():
            table.cmap.update(added)
    # Left side bearings and bounds come from the outlines as the font is saved.
    for code, name in added.items():
        charstring = strings[name]
        bounds = charstring.calcBounds(strings)
        font["hmtx"].metrics[name] = (font["hmtx"].metrics[name][0], round(bounds[0]) if bounds else 0)
    return len(added)


def devanagari_font(path, weight, folder):
    """Noto Sans Devanagari at [weight]: the variable font's default (400) for Regular, its weight 500 for Medium."""
    font = TTFont(path)
    if weight == "Medium" and "fvar" in font:
        from fontTools.varLib import instancer
        font = instancer.instantiateVariableFont(font, {"wght": 500})
    out = os.path.join(folder, "devanagari-%s.ttf" % weight)
    font.save(out)
    return out


def add_missing(font, codes, sources):
    """Use the first source with an outline for each missing character."""
    wanted = set(codes).difference(font.getBestCmap())
    count = 0
    for path in sources:
        if not wanted:
            break
        if not path:
            continue
        with TTFont(path, lazy=True) as source:
            available = wanted.intersection(source.getBestCmap())
        if available:
            count += add_glyphs(font, path, sorted(available))
            wanted.difference_update(available)
    if wanted:
        sys.exit("U+%04X used by Settings is in none of the sources: give Noto Sans Devanagari, or add a source" % min(wanted))
    return count


def main():
    inter, georgian, cjk = sys.argv[1], sys.argv[2], sys.argv[3]
    devanagari = sys.argv[4] if len(sys.argv) > 4 else None
    hangul, han = cjk_in_text()
    labels = [*howto_text(), *keymap_text()]
    label_codes = {ord(c) for text in labels for c in text if not c.isspace() and ord(c) > 0x7F}
    for weight in ("Regular", "Medium"):
        font = cut(os.path.join(inter, "Inter-%s.otf" % weight))
        georgian_path = os.path.join(georgian, "NotoSansGeorgian-%s.ttf" % weight)
        cjk_paths = [os.path.join(cjk, "NotoSans%s-%s.otf" % (region, weight)) for region in ("HK", "JP", "SC", "KR")]
        count = add_glyphs(font, georgian_path, [c for c in GEORGIAN if c in TTFont(georgian_path).getBestCmap()])
        # Thai's letters in names, from Noto Sans Thai beside Noto Sans Georgian (names only: Ink doesn't place Thai's marks).
        add_glyphs(font, os.path.join(georgian, "NotoSansThai-%s.ttf" % weight), names_in(is_thai))
        # Hebrew's, from Noto Sans Hebrew beside them; Ink orders right-to-left text itself.
        add_glyphs(font, os.path.join(georgian, "NotoSansHebrew-%s.ttf" % weight), names_in(is_hebrew))
        count_hangul = add_glyphs(font, os.path.join(cjk, "NotoSansKR-%s.otf" % weight), hangul)
        count_han = add_missing(font, han, cjk_paths)
        # The remaining language copy and keymaps need kana, Hebrew, Devanagari, Bopomofo and other outlines.
        with tempfile.TemporaryDirectory() as temp:
            count_labels = add_missing(font, label_codes, [
                os.path.join(inter, "Inter-%s.otf" % weight), georgian_path,
                os.path.join(georgian, "NotoSansThai-%s.ttf" % weight),
                os.path.join(georgian, "NotoSansHebrew-%s.ttf" % weight), *cjk_paths,
                devanagari_font(devanagari, weight, temp) if devanagari else None])
        out = os.path.join(ROOT, "assets", "fonts", "Inter-%s.otf" % weight)
        font.save(out)
        print("%s: %d glyphs, %d Georgian, %d Hangul, %d Chinese, %d more for how-tos and keymaps, %d bytes"
              % (out, len(font.getGlyphOrder()), count, count_hangul, count_han, count_labels, os.path.getsize(out)))


if __name__ == "__main__":
    main()
