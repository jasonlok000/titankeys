#!/usr/bin/env python3
"""Measures an input method's keys against its standard and types its word list through them (docs/languages.md).

    python3 -I tools/im/measure.py <data dir> th

Thai: every position of Kedmanee (xkeyboard-config's symbols/th "basic", in <data dir>/xkb) must be on the Titan's keys,
where make.py put it (a letter key's own letter or Shift letter, or held, or held with Shift). Then every word in the list
is typed through the keys: how many can be typed, and what share of key presses are holds, weighted by each word's count.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import make  # noqa: E402

ROOT = make.ROOT


def thai(data):
    im = json.load(open(os.path.join(ROOT, "android", "assets", "im", "th", "im.json"), encoding="utf-8"))
    xkb = make.read_xkb(data)
    keys = im["keys"]
    # Where each xkb key should be: its own letter key, or the letter key it's held on.
    on_letter = {v: k for k, v in make.THAI_LETTER_KEYS.items()}
    on_third = {v: k for k, v in make.THAI_THIRD.items()}
    total = matched = 0
    wrong = []
    for name, chars in sorted(xkb.items()):
        for level, char in enumerate(chars):
            total += 1
            if name in on_letter:
                got = keys[on_letter[name]][level]
            elif name in on_third:
                got = keys[on_third[name]][2 + level]
            else:
                got = None
            if got == char:
                matched += 1
            else:
                wrong.append("%s level %d: %r, keys give %r" % (name, level, char, got))
    print("Kedmanee positions on the Titan's keys: %d of %d" % (matched, total))
    for line in wrong:
        print("  " + line)
    # What each character costs: a press, a Shift press, a hold, a held Shift.
    how = {}
    for k, letters in keys.items():
        for level, char in enumerate(letters):
            how.setdefault(char, level)
    words = []
    for line in open(os.path.join(ROOT, "android", "assets", "im", "th", "words.combined"), encoding="utf-8"):
        if line.startswith(" word="):
            word, _, rest = line[6:].partition(",f=")
            words.append((word, int(rest.split(",")[0]) or 1))
    typed = weight = presses = holds = shifts = 0
    missing = {}
    for word, count in words:
        levels = [how.get(c) for c in word]
        if None in levels:
            for c in word:
                if c not in how:
                    missing[c] = missing.get(c, 0) + 1
            continue
        typed += 1
        weight += count
        presses += count * len(levels)
        holds += count * sum(1 for level in levels if level >= 2)
        shifts += count * sum(1 for level in levels if level % 2 == 1)
    print("Words that can be typed: %d of %d (%.2f%%)" % (typed, len(words), 100 * typed / len(words)))
    print("Key presses that are holds, weighted by count: %.1f%%; with Shift: %.1f%%" % (100 * holds / presses, 100 * shifts / presses))
    if missing:
        print("Characters no key types: " + " ".join("%s (U+%04X) in %d" % (c, ord(c), n) for c, n in sorted(missing.items(), key=lambda x: -x[1])))


def words_typed(ident, keys):
    """Types every word of [ident]'s list through [keys]: how many can be, and the share of presses that are holds."""
    how = {}
    for letters in keys.values():
        for level, char in enumerate([letters] if isinstance(letters, str) else letters):
            how.setdefault(char, level)
    total = typed = presses = holds = 0
    missing = {}
    for line in open(os.path.join(ROOT, "android", "assets", "im", ident, "words.combined"), encoding="utf-8"):
        if not line.startswith(" word="):
            continue
        word, _, rest = line[6:].partition(",f=")
        count = int(rest.split(",")[0]) or 1
        total += 1
        levels = [how.get(c) for c in word]
        if None in levels:
            for c in word:
                if c not in how:
                    missing[c] = missing.get(c, 0) + 1
            continue
        typed += 1
        presses += count * len(levels)
        holds += count * sum(1 for level in levels if level >= 2)
    print("Words that can be typed: %d of %d (%.2f%%); key presses that are holds, weighted by count: %.1f%%"
          % (typed, total, 100 * typed / total, 100 * holds / max(presses, 1)))
    if missing:
        print("Characters no key types: " + " ".join("%s (U+%04X) in %d" % (c, ord(c), n) for c, n in sorted(missing.items(), key=lambda x: -x[1])))


def hebrew(data):
    """SI-1452's 27 letters must all be on the keys: where a PC has them, or ת on Q and ף ץ held on P and M."""
    im = json.load(open(os.path.join(ROOT, "android", "assets", "im", "he", "im.json"), encoding="utf-8"))
    keys = im["keys"]
    xkb = make.read_xkb(data, file="symbols-il")
    on_letter = {v: k for k, v in make.THAI_LETTER_KEYS.items()}
    letters = {chars[0]: name for name, chars in xkb.items() if chars and "א" <= chars[0] <= "ת"}
    found = []
    for char, name in sorted(letters.items()):
        key = on_letter.get(name)
        letters_on = lambda k: [keys[k]] if isinstance(keys[k], str) else keys[k]
        if key and letters_on(key)[0] == char:
            found.append(char)
        elif any(char in letters_on(k) for k in keys):
            found.append(char)
        else:
            print("  %s (%s) isn't on the keys" % (char, name))
    print("SI-1452's letters on the Titan's keys: %d of %d (%d where a PC has them)"
          % (len(found), len(letters), sum(1 for c, n in letters.items() if on_letter.get(n) and
                                          ([keys[on_letter[n]]] if isinstance(keys[on_letter[n]], str) else keys[on_letter[n]])[0] == c)))
    words_typed("he", keys)


if __name__ == "__main__":
    {"th": thai, "he": hebrew}[sys.argv[2]](sys.argv[1])
