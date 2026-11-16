#!/usr/bin/env python3
"""Builds the app's two text fonts, assets/fonts/Inter-Regular.otf and Inter-Medium.otf (theme.ts), so every input
method's own name shows in Settings > Input methods (docs/languages.md, "Names").

    python3 -I tools/im/fonts.py <Inter dir> <Noto Sans Georgian dir>

<Inter dir> holds Inter-Regular.otf and Inter-Medium.otf from the official Inter 4.1 release
(https://github.com/rsms/inter/releases/download/v4.1/Inter-4.1.zip, extras/otf). <Noto Sans Georgian dir> holds
NotoSansGeorgian-Regular.ttf and NotoSansGeorgian-Medium.ttf from Google's official notofonts.github.io repository
(fonts/NotoSansGeorgian/hinted/ttf). Both are under the SIL Open Font License 1.1 (licences/Inter-OFL.txt,
licences/NotoSansGeorgian-OFL.txt). Needs fontTools.

Ink draws the screens with these fonts, then Public Sans, then a few of Android's own (symbols, CJK, Arabic), and leaves a
blank where none has the letter. The fonts were Latin-1 only, so Cyrillic and Greek names were blank, and Czech, Romanian,
Vietnamese and others lost letters to Public Sans. Each is now Inter cut down to Latin with its extensions, Greek and
Cyrillic, with Noto Sans Georgian's letters added, drawn at Inter's size. Hangul and Chinese come from Android's CJK font.
"""
import os
import sys

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


def add_georgian(font, georgian_path):
    """Draws Noto Sans Georgian's letters into [font]'s CFF, scaled to its units per em."""
    source = TTFont(georgian_path)
    scale = font["head"].unitsPerEm / source["head"].unitsPerEm
    glyphs = source.getGlyphSet()
    cmap = source.getBestCmap()
    cff = font["CFF "].cff
    top = cff.topDictIndex[0]
    strings = top.CharStrings
    order = font.getGlyphOrder()
    added = {}
    for code in GEORGIAN:
        name = cmap.get(code)
        if name is None:
            continue
        width = round(glyphs[name].width * scale)
        pen = T2CharStringPen(width, None)
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


def main():
    inter, georgian = sys.argv[1], sys.argv[2]
    for weight in ("Regular", "Medium"):
        font = cut(os.path.join(inter, "Inter-%s.otf" % weight))
        count = add_georgian(font, os.path.join(georgian, "NotoSansGeorgian-%s.ttf" % weight))
        out = os.path.join(ROOT, "assets", "fonts", "Inter-%s.otf" % weight)
        font.save(out)
        print("%s: %d glyphs, %d Georgian, %d bytes" % (out, len(font.getGlyphOrder()), count, os.path.getsize(out)))


if __name__ == "__main__":
    main()
