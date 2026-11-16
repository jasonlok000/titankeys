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


if __name__ == "__main__":
    {"th": thai}[sys.argv[2]](sys.argv[1])
