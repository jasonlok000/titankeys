#!/usr/bin/env python3
"""Builds each input method's next-word table from a text corpus: which words most often follow each word.

    python3 -I tools/im/next_words.py build <id> <text file> [...] [--words <word list>] [--out <file>] [--top 8] [--min 2]
    python3 -I tools/im/next_words.py check <id> <text file> [...] [--words <word list>] [--every 10]

<text file> is one sentence per line, as in the Leipzig Corpora Collection's <corpus>-sentences.txt ("<number><TAB>
sentence"; the number is dropped) or plain text. "check" holds out every 10th sentence, builds from the rest, and
measures how often the held-out next word is the table's first, or among its first three, for a word in the table.

The table goes to android/assets/im/<id>/pairs.txt by default (Portuguese: pairs-pt-BR.txt and pairs-pt-PT.txt, one per
word list), in the same format as English's android/assets/autocorrect/en_pairs.txt (PairCounts reads it): "first
second<TAB>count" lines in lower case, <s> as the first word at a sentence's start. Only the --top next words for each
first word are kept, each seen at least --min times. The format is agreed with the suggestion bar's reader (C).

Tokens follow the input method's word list (words.combined): Unicode letters and marks, with apostrophes and hyphens
inside a word; a token takes the word list's own spelling (German nouns keep their capital, a capitalised first word
finds its lower-case entry), then lower case for the table; a hyphenated token not in the list splits at its hyphens. A word not in the list breaks the
chain: no pair crosses it. Thai, written without spaces, is split by the longest word-list match. Korean uses its word list
when it has one, else the corpus's own most common (--vocabulary, default 50,000) space-separated words.
"""
import argparse
import os
import re
import sys
import unicodedata
from collections import Counter, defaultdict

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
IM = os.path.join(ROOT, "android", "assets", "im")
START = "<s>"
TOKEN = re.compile(r"[^\W\d_](?:[\w'\-]*[^\W\d_])?|'[^\W\d_]+")
THAI = re.compile(r"[฀-๿]+")
HANGUL = re.compile(r"[가-힣]+")


def read_words(path):
    """The word list's words with their frequency class, most frequent first."""
    words = {}
    for line in open(path, encoding="utf-8"):
        if not line.startswith(" word="):
            continue
        fields = dict(f.split("=", 1) for f in line.strip().split(",") if "=" in f)
        w = fields.get("word")
        if w and w not in words:
            words[w] = int(fields.get("f", 0))
    return words


class Words:
    def __init__(self, words):
        self.exact = words
        self.lower = {}
        for w in sorted(words, key=lambda w: -words[w]):
            self.lower.setdefault(w.lower(), w)
        self.longest = max((len(w) for w in words), default=0)

    def find(self, token):
        if token in self.exact:
            return token
        return self.lower.get(token.lower())


def sentences(paths):
    for path in paths:
        for line in open(path, encoding="utf-8", errors="replace"):
            line = line.rstrip("\n")
            if "\t" in line and line.split("\t", 1)[0].isdigit():
                line = line.split("\t", 1)[1]
            line = unicodedata.normalize("NFC", line).replace("’", "'").replace("ʼ", "'")
            if line.strip():
                yield line


def thai_split(run, words):
    """Longest word-list match from the left; an unknown letter becomes a break."""
    out, i = [], 0
    while i < len(run):
        for j in range(min(len(run), i + words.longest), i, -1):
            if run[i:j] in words.exact:
                out.append(run[i:j])
                i = j
                break
        else:
            out.append(None)
            i += 1
    return out


def tokens(line, words, script):
    """The sentence's words in the word list's spelling, None where a word isn't in the list."""
    if script == "Kore":
        return [t if t in words.exact else None for t in (m.group(0) for m in HANGUL.finditer(line))]
    if script == "Thai":
        out = []
        for m in re.finditer(r"[฀-๿]+|\S+", line):
            out.extend(thai_split(m.group(0), words) if THAI.fullmatch(m.group(0)) else [None])
        return out
    out = []
    for m in TOKEN.finditer(line):
        t = m.group(0)
        w = words.find(t)
        if w is None and "-" in t:
            out.extend(words.find(p) if p else None for p in t.split("-"))
            continue
        out.append(w)
    return out


def lowered(words):
    """Lower case, as PairCounts looks words up (no change where the script has no case)."""
    return [w.lower() if w is not None else None for w in words]


def count(lines, words, script):
    pairs = Counter()
    for line in lines:
        prev = START
        for w in lowered(tokens(line, words, script)):
            if w is not None and prev is not None:
                pairs[(prev, w)] += 1
            prev = w
    return pairs


def table(pairs, top, least):
    after = defaultdict(list)
    for (a, b), n in pairs.items():
        if n >= least:
            after[a].append((n, b))
    return {a: [b for _, b in sorted(nexts, key=lambda x: (-x[0], x[1]))[:top]] for a, nexts in after.items()}, \
        {(a, b): n for (a, b), n in pairs.items() if n >= least}


def korean_words(lines, vocabulary):
    c = Counter(m.group(0) for line in lines for m in HANGUL.finditer(line))
    return Words({w: n for w, n in c.most_common(vocabulary)})


def word_lists(im, folder, args, lines):
    """(variant, words) for each table to build: Portuguese has one per variant; Korean without a list uses the corpus."""
    if args.words:
        return [("", Words(read_words(args.words)))]
    if im.get("variants") and all(v.get("words") for v in im["variants"]):
        return [(v["id"], Words(read_words(os.path.join(folder, v["words"])))) for v in im["variants"]]
    if im.get("words"):
        return [("", Words(read_words(os.path.join(folder, im["words"]))))]
    if im.get("script") == "Kore":
        return [("", korean_words(lines, args.vocabulary))]
    raise SystemExit(f"{im['id']} has no word list")


def load(args):
    import json
    folder = os.path.join(IM, args.id)
    im = json.load(open(os.path.join(folder, "im.json"), encoding="utf-8"))
    lines = list(sentences(args.text))
    return im, im.get("script") or "", lines, folder


def build(args):
    im, script, lines, folder = load(args)
    for variant, words in word_lists(im, folder, args, lines):
        nexts, kept = table(count(lines, words, script), args.top, args.min)
        name = f"pairs-{variant}.txt" if variant else "pairs.txt"
        out = args.out or os.path.join(folder, name)
        rows = sorted(((a, b, kept[(a, b)]) for a, bs in nexts.items() for b in bs), key=lambda r: (r[0], -r[2], r[1]))
        with open(out, "w", encoding="utf-8", newline="\n") as f:
            f.write(f"# {im['nameEnglish']}{' (' + variant + ')' if variant else ''} next words: the {args.top} most common words"
                    f" after each word. See NOTICE.\n")
            f.write("# Format: first<space>second<tab>count. <s> marks the start of a sentence.\n")
            for a, b, n in rows:
                f.write(f"{a} {b}\t{n}\n")
        size = os.path.getsize(out)
        print(f"{args.id}{' ' + variant if variant else ''}: {len(lines)} sentences, {len(nexts)} first words, {len(rows)} pairs,"
              f" {size / 1e6:.2f} MB -> {out}")


def check(args):
    im, script, lines, folder = load(args)
    train = [l for i, l in enumerate(lines) if i % args.every]
    held = [l for i, l in enumerate(lines) if not i % args.every]
    for variant, words in word_lists(im, folder, args, train):
        nexts, _ = table(count(train, words, script), args.top, args.min)
        res = Counter()
        for line in held:
            prev = START
            for w in lowered(tokens(line, words, script)):
                if w is not None and prev is not None:
                    kind = "start" if prev == START else "inside"
                    res[kind + " pairs"] += 1
                    guesses = nexts.get(prev, [])
                    res[kind + " with a table"] += bool(guesses)
                    res[kind + " top 1"] += guesses[:1] == [w]
                    res[kind + " top 3"] += w in guesses[:3]
                prev = w
        for kind in ("inside", "start"):
            n = res[kind + " pairs"] or 1
            print(f"{args.id}{' ' + variant if variant else ''} {kind}: {res[kind + ' pairs']} held-out pairs; first word in the table"
                  f" {res[kind + ' with a table'] / n:.1%}; next word first {res[kind + ' top 1'] / n:.1%}, in the top 3"
                  f" {res[kind + ' top 3'] / n:.1%}")


def main():
    p = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    p.add_argument("mode", choices=["build", "check"])
    p.add_argument("id")
    p.add_argument("text", nargs="+")
    p.add_argument("--words")
    p.add_argument("--out")
    p.add_argument("--top", type=int, default=8)
    p.add_argument("--min", type=int, default=2)
    p.add_argument("--every", type=int, default=10)
    p.add_argument("--vocabulary", type=int, default=50_000)
    args = p.parse_args()
    (build if args.mode == "build" else check)(args)


if __name__ == "__main__":
    main()
