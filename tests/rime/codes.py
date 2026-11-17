"""Cangjie, Quick and Zhuyin on a public text (tests/rime/codes.sh): each character of Tatoeba's Cantonese sentences
(CC BY 2.0 FR, Traditional characters), typed by its Cangjie code, its Quick code (the Cangjie code's first and last
letters), or its commonest Mandarin reading on the Hsu Zhuyin layout with its tone, through rime_tool, the app's own Rime,
with the shipped data. Counts how often the character is the first choice, and on the first page of 8.

    python3 -I codes.py <rime_tool> <shipped rime folder> <rime sources folder> <yue_sentences.tsv> [characters]

<rime sources folder> holds rime-cangjie and rime-terra-pinyin, as native/rime/data.sh fetches them.
"""
import re
import collections
import os
import random
import subprocess
import sys
import tempfile

tool, shipped, sources, sentences = sys.argv[1:5]
dicts = os.path.join(sources, "rime-cangjie")
wanted = int(sys.argv[5]) if len(sys.argv) > 5 else 600

# Each character's Cangjie codes, from rime-cangjie's dictionary (the one cangjie5.table.bin is built from).
codes = collections.defaultdict(list)
for name in ("cangjie5.base.dict.yaml", "cangjie5.extended.dict.yaml"):
    body = False
    for line in open(os.path.join(dicts, name), encoding="utf-8"):
        if line.startswith("..."):
            body = True
            continue
        if not body or line.startswith("#") or "\t" not in line:
            continue
        text, code = line.rstrip("\n").split("\t")[:2]
        if len(text) == 1 and code.isalpha():
            codes[text].append(code)

# Each character's commonest Mandarin reading, from rime-terra-pinyin's dictionary (the one Zhuyin's table is built from).
readings = {}
body = False
for line in open(os.path.join(sources, "rime-terra-pinyin", "terra_pinyin.dict.yaml"), encoding="utf-8"):
    if line.startswith("..."):
        body = True
        continue
    parts = line.rstrip("\n").split("\t")
    if not body or len(parts) < 2 or len(parts[0]) != 1:
        continue
    share = float(parts[2].rstrip("%")) if len(parts) > 2 and parts[2].endswith("%") else 100.0
    if parts[0] not in readings or share > readings[parts[0]][1]:
        readings[parts[0]] = (parts[1], share)

# tk_zhuyin.schema.yaml's own spelling rules: the reading as Zhuyin, then Hsu's keys.
ZHUYIN = [(r"^m(\d)$", r"mu\1"), (r"^r5$", "er5"), ("iu", "iou"), ("ui", "uei"), ("ong", "ung"), (r"^yi?", "i"), (r"^wu?", "u"),
          ("iu", "v"), (r"^([jqx])u", r"\1v"), (r"([iuv])n", r"\1en"), (r"^zhi?", "Z"), (r"^chi?", "C"), (r"^shi?", "S"),
          (r"^([zcsr])i", r"\1"), ("ai", "A"), ("ei", "I"), ("ao", "O"), ("ou", "U"), ("ang", "K"), ("eng", "G"), ("an", "M"),
          ("en", "N"), ("er", "R"), ("eh", "E"), (r"([iv])e", r"\1E"), (r"1$", "")]
HSU = str.maketrans("bpmfdtnlgkhjqxZCSrzcsiuvaoeEAIOUMNKGR2345", "bpmfdtnlgkhjvcjvcrzasexuyhgeiawomnklldfjs")


def hsu(reading):
    for pattern, to in ZHUYIN:
        reading = re.sub(pattern, to, reading)
    return reading.translate(HSU)


# Characters as they come in the text, so common ones count as often as they're used: a sample, the same each run.
text = []
for line in open(sentences, encoding="utf-8"):
    parts = line.rstrip("\n").split("\t")
    if len(parts) >= 3:
        text.extend(c for c in parts[2] if "一" <= c <= "鿿" and c in codes and c in readings)
random.seed(56)
sample = random.sample(text, min(wanted, len(text)))


def first_page(schema, code):
    with tempfile.TemporaryDirectory() as user:
        out = subprocess.run([tool, "try", shipped, user, schema, "zh_hk", code, "8"], capture_output=True, text=True).stdout
    return out.split(": ", 1)[1].split() if ": " in out else []


cache = {}
for schema, code_of in (("tk_cangjie", lambda c: min(codes[c], key=len)),
                        ("tk_quick", lambda c: (lambda k: k if len(k) <= 2 else k[0] + k[-1])(min(codes[c], key=len))),
                        ("tk_zhuyin", lambda c: hsu(readings[c][0]))):
    first = page = 0
    for c in sample:
        code = code_of(c)
        key = (schema, code)
        if key not in cache:
            cache[key] = first_page(schema, code)
        got = cache[key]
        first += bool(got) and got[0] == c
        page += c in got
    n = len(sample)
    print("%s: %d characters, %.1f%% the first choice, %.1f%% on the first page of 8" % (schema, n, 100 * first / n, 100 * page / n))
