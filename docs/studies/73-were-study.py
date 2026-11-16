#!/usr/bin/env python3
"""
#73's study: can the next-word model change a typed "were" to "we're" when it's very sure?

It scores each case the way the keyboard's apostrophe check does (ApostropheContext.kt): the window back to the last sentence mark,
at most 10 words, the typed form and the words after it up to 2 or the first mark, and the on-device DistilGPT-2 (int8) reading the
last 32 tokens at each step. The margin is score("we're" ...) - score("were" ...), in nats.

Prints counts and margins only, never the text. Needs numpy and onnxruntime.

Usage: python3 73-were-study.py <repo root> [<labels.tsv> <ctx.pkl> <occ.pkl>]
The last three are the private apostrophe study's working files. They're read, never written, and no text from them is printed.
"""
import pickle
import re
import sys
from collections import Counter

import numpy as np
import onnxruntime as ort

ROOT = sys.argv[1]
PRED = ROOT + "/android/assets/swipe2/predictor/"
CAP = 32
START = 50256
WANTED = 2
MIN_OTHER_WORDS = 2


# GPT-2 byte-level BPE, as ByteBpe.java. Python's re has no \p{L}, so letters are [^\W\d_].
def bytes_to_unicode():
    bs = list(range(ord("!"), ord("~") + 1)) + list(range(ord("¡"), ord("¬") + 1)) + list(range(ord("®"), ord("ÿ") + 1))
    cs = bs[:]
    n = 0
    for b in range(256):
        if b not in bs:
            bs.append(b)
            cs.append(256 + n)
            n += 1
    return dict(zip(bs, map(chr, cs)))


BYTE = bytes_to_unicode()
SPLIT = re.compile(r"'s|'t|'re|'ve|'m|'ll|'d| ?[^\W\d_]+| ?\d+| ?[^\s\w]+|\s+(?!\S)|\s+")
VOCAB = {line.rstrip("\n"): i for i, line in enumerate(open(PRED + "lm-vocab.txt", encoding="utf-8"))}
RANKS = {}
for line in open(PRED + "lm-merges.txt", encoding="utf-8"):
    parts = line.rstrip("\n").split(" ")
    if len(parts) == 2 and not line.startswith("#"):
        RANKS.setdefault(tuple(parts), len(RANKS))


def bpe(piece):
    word = list(piece)
    while len(word) > 1:
        pairs = [(RANKS.get((word[i], word[i + 1]), 1 << 30), i) for i in range(len(word) - 1)]
        rank, i = min(pairs)
        if rank == 1 << 30:
            break
        a, b = word[i], word[i + 1]
        merged, j = [], 0
        while j < len(word):
            if j < len(word) - 1 and word[j] == a and word[j + 1] == b:
                merged.append(a + b)
                j += 2
            else:
                merged.append(word[j])
                j += 1
        word = merged
    return word


def encode(text):
    ids = []
    for piece in SPLIT.findall(text.replace("’", "'")):
        for token in bpe("".join(BYTE[b] for b in piece.encode("utf-8"))):
            ids.append(VOCAB[token])
    return ids


SESSION = ort.InferenceSession(PRED + "model-int8pc.onnx", providers=["CPUExecutionProvider"])
CACHE = {}


def next_logp(ctx):
    key = tuple(ctx[-CAP:])
    if key not in CACHE:
        CACHE[key] = SESSION.run(None, {"input_ids": np.array([list(key)], dtype=np.int64)})[0][0]
    return CACHE[key]


def score(before, text):
    ctx = encode(before) if before else [START]
    total = 0.0
    for t in encode(text):
        total += float(next_logp(ctx)[t])
        ctx = ctx + [t]
    return total


# ContextWindow.SENTENCE.before: back to the last . ! ? or line break, at most 10 words.
def window(text, most=10):
    start = at = len(text)
    words = 0
    while words < most:
        while at > 0 and text[at - 1].isspace():
            if text[at - 1] == "\n":
                return text[start:].rstrip()
            at -= 1
        if at == 0:
            break
        frm = at
        while frm > 0 and not text[frm - 1].isspace():
            frm -= 1
        if text[at - 1] in ".!?":
            break
        start = at = frm
        words += 1
    return text[start:].rstrip()


def word_letter(c):
    return c.isalnum() or c in "'’"


# ApostropheContext.following with #73's rule: 2 words, or sooner at any mark. Returns the words after, or None to drop.
def following(rest, wanted=WANTED):
    if not rest or rest[0].isalnum() or rest[0] in "'’":
        return None
    if rest[0] in "-‐" and (len(rest) == 1 or word_letter(rest[1])):
        return None

    def ready(end):
        stop = end
        while stop < len(rest) and not rest[stop].isspace():
            stop += 1
        return rest[:stop]

    at = words = end = 0
    while True:
        gap = at
        while at < len(rest) and not word_letter(rest[at]):
            at += 1
        if any(not c.isspace() or c == "\n" for c in rest[gap:at]):
            return ready(end)
        if at == len(rest):
            return ready(end)  # the text ends here: as at the end of a message
        while at < len(rest) and (word_letter(rest[at]) or rest[at] in "-‐" and word_letter(rest[at - 1])
                                  and (at + 1 == len(rest) or word_letter(rest[at + 1]))):
            at += 1
        words += 1
        end = at
        if words == wanted or at == len(rest):
            return ready(end)


def count_words(text):
    return sum(1 for w in re.split(r"[ \n\t]", text) if any(c.isalnum() for c in w))


def cased(form, typed, opening):
    if len(typed) > 1 and not any(c.islower() for c in typed):
        return form.upper()
    if opening or typed[0].isupper():
        return form[0].upper() + form[1:]
    return form


def margin(before, typed, rest):
    """we're over were, or None when too thin."""
    after = following(rest)
    if after is None:
        return None
    ctx = window(before)
    if count_words(ctx) + count_words(after) < MIN_OTHER_WORDS:
        return None
    opening = ctx == ""
    lead = "" if opening else " "
    base = score(ctx, lead + cased("were", typed, opening) + after)
    alt = score(ctx, lead + cased("we're", typed, opening) + after)
    return alt - base


WORD = re.compile(r"(?<![\w'’])(were|we['’]re)(?![\w'’])", re.IGNORECASE)


def text_cases(texts):
    """Every were and we're in [texts], typed as "were", with the meant form."""
    cases = []
    for text in texts:
        for m in WORD.finditer(text):
            meant = "we're" if m.group(1).lower() != "were" else "were"
            typed = "Were" if m.group(1)[0].isupper() else "were"
            cases.append((meant, margin(text[:m.start()], typed, text[m.end():])))
    return cases


def report(name, cases):
    scored = [(meant, m) for meant, m in cases if m is not None]
    thin = len(cases) - len(scored)
    meant = Counter(x for x, _ in cases)
    were, wer_e = meant["were"], meant["we're"]
    print(f"\n== {name}: {len(cases)} cases (were {were}, we're {wer_e}), too thin {thin}")
    bins = [(-1e9, -4), (-4, -2), (-2, 0), (0, 1), (1, 2), (2, 3), (3, 5), (5, 1e9)]
    print("margin bin        were  we're   (meant)")
    for lo, hi in bins:
        w = sum(1 for x, m in scored if x == "were" and lo <= m < hi)
        r = sum(1 for x, m in scored if x != "were" and lo <= m < hi)
        print(f"  [{lo:>5}, {hi:>5})  {w:5}  {r:5}")
    for t in (0, 0.5, 1, 1.5, 2, 3, 4, 5, 6, 8):
        wrong = sum(1 for x, m in scored if x == "were" and m > t)
        right = sum(1 for x, m in scored if x != "were" and m > t)
        print(f"  threshold {t:>4}: right changes {right:3}, wrong changes {wrong:3}")
    top_were = max((m for x, m in scored if x == "were"), default=None)
    print(f"  highest margin of a meant 'were': {top_were if top_were is None else round(top_were, 2)}")
    return scored


def main():
    # The tokenizer as the app's: " it's" is " it" + "'s", and " its" is one token.
    assert len(encode(" it's")) == 2 and len(encode(" its")) == 1 and len(encode(" we're")) == 2, "tokenizer differs from the app's"
    allscored = []
    tatoeba = [l.rstrip("\n") for l in open(ROOT + "/tests/autocorrect/tatoeba_heldout_en.txt", encoding="utf-8")
               if l.strip() and not l.startswith("#")]
    allscored += report("Tatoeba held-out sentences (tests/autocorrect)", text_cases(tatoeba))

    # en_triples.txt: common three-word runs, each counted once (so the counts weigh nothing): were or we're in the middle.
    triples = []
    for line in open(ROOT + "/android/assets/autocorrect/en_triples.txt", encoding="utf-8"):
        words = line.split("\t")[0].split(" ")
        if len(words) != 3 or "</s>" in words:
            continue
        if words[1].lower().replace("’", "'") in ("were", "we're"):
            before = "" if words[0] == "<s>" else words[0] + " "
            triples.append(before + words[1] + " " + words[2])
    report("en_triples.txt runs with were or we're in the middle", text_cases(triples))

    if len(sys.argv) > 4:
        labels = dict(l.rstrip("\n").split("\t") for l in open(sys.argv[2], encoding="utf-8"))
        ctx = pickle.load(open(sys.argv[3], "rb"))
        judged = []
        for c in ctx:
            meant = labels.get(str(c["id"]), "").lower()
            if meant in ("were", "we're"):
                typed = "Were" if c["committed"][:1].isupper() else "were"
                judged.append((meant, margin(c["before"], typed, c["after"])))
        allscored += report("owner's logs, judged were/we're (apostrophe study labels)", judged)
        finals = pickle.load(open(sys.argv[4], "rb"))["finals"]
        report("owner's logs, every were/we're in the final texts (the written form as the label: weak)", text_cases(finals))
    report("Tatoeba and judged owner cases together", allscored)


if __name__ == "__main__":
    main()
