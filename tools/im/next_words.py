#!/usr/bin/env python3
"""Builds each input method's next-word table from a text corpus: which words most often follow each word.

    python3 -I tools/im/next_words.py build <id> <text file> [...] [--words <word list>] [--out <file>] [--top 8] [--min 2]
    python3 -I tools/im/next_words.py check <id> <text file> [...] [--words <word list>] [--every 10]
    python3 -I tools/im/next_words.py notice <folder id> <corpus> <table file> <extra note or "">
    python3 -I tools/im/next_words.py convert <pairs.txt> [...]

tools/im/next_words_all.sh builds every table from tools/im/next_words_corpora.json and writes the NOTICE lines.

<text file> is one sentence per line, as in the Leipzig Corpora Collection's <corpus>-sentences.txt ("<number><TAB>
sentence"; the number is dropped) or plain text. "check" holds out every 10th sentence, builds from the rest, and
measures how often the held-out next word is the table's first, or among its first three, for a word in the table.

The table goes to android/assets/im/<id>/pairs.bin by default (Portuguese: pairs-pt-BR.bin and pairs-pt-PT.bin, one per
word list), the compact form NextWords reads (see write_binary): its words in lower case, <s> as the first word at a
sentence's start, each first word's next words commonest first. --text also writes it readable, in English's
android/assets/autocorrect/en_pairs.txt format ("first second<TAB>count" lines); "convert" turns such text into .bin. Only the --top next words for each
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

def _marks():
    """Every combining mark as a character class (Python's \\w leaves out vowel signs such as Devanagari's)."""
    out, start = [], None
    for c in range(sys.maxunicode + 2):
        mark = c <= sys.maxunicode and unicodedata.category(chr(c)).startswith("M")
        if mark and start is None:
            start = c
        elif not mark and start is not None:
            out.append(re.escape(chr(start)) + ("-" + re.escape(chr(c - 1)) if c - 1 > start else ""))
            start = None
    return "".join(out)


MARKS = _marks()
LETTER = rf"(?:[^\W\d_]|[{MARKS}])"
TOKEN = re.compile(rf"{LETTER}(?:(?:[\w'\-]|[{MARKS}])*{LETTER})?|'{LETTER}+")
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


TURKIC = {"tr", "az"}


def lower(word, turkic=False):
    """Lower case as the keyboard looks words up (NextWords): Turkish and Azerbaijani have İ→i and I→ı, where Python's own
    rules would give i with a combining dot, and i."""
    if turkic:
        word = word.replace("İ", "i").replace("I", "ı")
    return word.lower()


class Words:
    def __init__(self, words, turkic=False):
        self.turkic = turkic
        self.exact = words
        self.lower = {}
        for w in sorted(words, key=lambda w: -words[w]):
            self.lower.setdefault(lower(w, turkic), w)
        self.longest = max((len(w) for w in words), default=0)

    def find(self, token):
        if token in self.exact:
            return token
        return self.lower.get(lower(token, self.turkic))


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
    """The keyboard's own split (WordBreak.words in WordList.kt), so the table's words are the ones it looks up: the cheapest
    split into the list's words of two letters or more, a word costing 1 and a stray letter 3; a stray letter is a break.
    A greedy longest match from the left made single consonants (ย, ต, ค) "words" at sentences' starts."""
    n = len(run)
    longest = min(words.longest, 24)
    cost = [0] + [1 << 30] * n
    back = [-1] * (n + 1)
    for i in range(1, n + 1):
        cost[i], back[i] = cost[i - 1] + 3, -1
        for j in range(max(0, i - longest), i):
            if cost[j] + 1 < cost[i] and i - j > 1 and run[j:i] in words.exact:
                cost[i], back[i] = cost[j] + 1, j
    out, i = [], n
    while i > 0:
        if back[i] < 0:
            out.append(None)
            i -= 1
        else:
            out.append(run[back[i]:i])
            i = back[i]
    return out[::-1]


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


def lowered(words, turkic=False):
    """Lower case, as PairCounts looks words up (no change where the script has no case)."""
    return [lower(w, turkic) if w is not None else None for w in words]


def count(lines, words, script):
    pairs = Counter()
    for line in lines:
        prev = START
        for w in lowered(tokens(line, words, script), words.turkic):
            if w is not None and prev is not None:
                pairs[(prev, w)] += 1
            prev = w
    return pairs


def table(pairs, top, least, max_mb=0.0):
    """Each first word's top next words. With max_mb, the rarest of those pairs go first until the file fits."""
    after = defaultdict(list)
    for (a, b), n in pairs.items():
        if n >= least:
            after[a].append((n, b))
    rows = [(n, a, b) for a, nexts in after.items() for n, b in sorted(nexts, key=lambda x: (-x[0], x[1]))[:top]]
    if max_mb:
        budget, kept = max_mb * 1e6, []
        for n, a, b in sorted(rows, key=lambda r: -r[0]):
            budget -= len(f"{a} {b}\t{n}\n".encode("utf-8"))
            if budget < 0:
                break
            kept.append((n, a, b))
        rows = kept
    nexts = defaultdict(list)
    for n, a, b in sorted(rows, key=lambda r: (r[1], -r[0], r[2])):
        nexts[a].append(b)
    return dict(nexts), {(a, b): n for n, a, b in rows}


def korean_words(lines, vocabulary):
    c = Counter(m.group(0) for line in lines for m in HANGUL.finditer(line))
    return Words({w: n for w, n in c.most_common(vocabulary)})


def word_lists(im, folder, args, lines):
    """(variant, words) for each table to build: Portuguese has one per variant; Korean without a list uses the corpus."""
    turkic = im.get("id") in TURKIC
    if args.words:
        return [("", Words(read_words(args.words), turkic))]
    if im.get("variants") and all(v.get("words") for v in im["variants"]):
        return [(v["id"], Words(read_words(os.path.join(folder, v["words"])), turkic)) for v in im["variants"]]
    if im.get("words"):
        return [("", Words(read_words(os.path.join(folder, im["words"])), turkic))]
    if im.get("script") == "Kore":
        return [("", korean_words(lines, args.vocabulary))]
    raise SystemExit(f"{im['id']} has no word list")


def load(args):
    import json
    folder = os.path.join(IM, args.id)
    im = json.load(open(os.path.join(folder, "im.json"), encoding="utf-8"))
    lines = list(sentences(args.text))
    if args.id == "sr-latn":
        # Its word list is the Cyrillic one written in Latin letters by make.py, so the text is too.
        import importlib.util
        spec = importlib.util.spec_from_file_location("make", os.path.join(ROOT, "tools", "im", "make.py"))
        make = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(make)
        cyrillic = re.compile("[" + "".join(make.SERBIAN_LATIN_LETTERS) + "".join(make.SERBIAN_LATIN_LETTERS).upper() + "]+")
        lines = [cyrillic.sub(lambda m: make.serbian_latin(m.group(0)) or m.group(0), line) for line in lines]
    return im, im.get("script") or "", lines, folder


def build(args):
    im, script, lines, folder = load(args)
    for variant, words in word_lists(im, folder, args, lines):
        nexts, kept = table(count(lines, words, script), args.top, args.min, args.max_mb)
        name = f"pairs-{variant}.bin" if variant else "pairs.bin"
        out = args.out or os.path.join(folder, name)
        rows = sorted(((a, b, kept[(a, b)]) for a, bs in nexts.items() for b in bs), key=lambda r: (r[0], -r[2], r[1]))
        if out.endswith(".bin"):
            write_binary(out, nexts)
        if args.text_out or not out.endswith(".bin"):
            text = args.text_out or out
            with open(text, "w", encoding="utf-8", newline="\n") as f:
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
        nexts, _ = table(count(train, words, script), args.top, args.min, args.max_mb)
        res = Counter()
        for line in held:
            prev = START
            for w in lowered(tokens(line, words, script), words.turkic):
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


MAGIC = b"TKNW"
VERSION = 1


def write_binary(path, nexts):
    """The table as NextWords reads it in the app: compact, so loading is one read and a lookup a binary search.

    Big-endian: "TKNW", version, then the counts V (words), F (first words), B (bytes of words), N (next words); the V+1
    starts of each word in the bytes; the B bytes of every word in UTF-8, the F first words first, sorted by their bytes; the
    F+1 starts of each first word's next words; and the N next words as word numbers, commonest first. No counts: the app
    only needs the order.
    """
    import struct
    firsts = sorted(nexts, key=lambda w: w.encode("utf-8"))
    rest = sorted({b for bs in nexts.values() for b in bs} - set(firsts), key=lambda w: w.encode("utf-8"))
    vocab = firsts + rest
    number = {w: i for i, w in enumerate(vocab)}
    blob, starts = bytearray(), [0]
    for w in vocab:
        blob += w.encode("utf-8")
        starts.append(len(blob))
    next_starts, ids = [0], []
    for w in firsts:
        ids += [number[b] for b in nexts[w]]
        next_starts.append(len(ids))
    with open(path, "wb") as f:
        f.write(MAGIC + struct.pack(">5i", VERSION, len(vocab), len(firsts), len(blob), len(ids)))
        f.write(struct.pack(f">{len(starts)}i", *starts))
        f.write(bytes(blob))
        f.write(struct.pack(f">{len(next_starts)}i", *next_starts))
        f.write(struct.pack(f">{len(ids)}i", *ids))
    return len(vocab), len(firsts), len(ids)


def read_text_table(path):
    """A pairs.txt table back as each first word's next words, in the file's order (commonest first)."""
    nexts = {}
    for line in open(path, encoding="utf-8"):
        if not line.strip() or line.startswith("#") or "\t" not in line:
            continue
        pair = line.split("\t", 1)[0]
        a, b = pair.split(" ", 1)
        nexts.setdefault(a, []).append(b)
    return nexts


def convert(paths):
    for path in paths:
        out = path[:-4] + ".bin"
        v, f, n = write_binary(out, read_text_table(path))
        print(f"{path}: {v} words, {f} first words, {n} pairs -> {out} ({os.path.getsize(out) / 1e6:.2f} MB)")


LEIPZIG_NOTICE = ("Next words ({file}): counts of word pairs from the Leipzig Corpora Collection's {corpus} "
                  "(https://wortschatz.uni-leipzig.de/en/download), CC BY 4.0{extra}. D. Goldhahn, T. Eckart and U. Quasthoff: "
                  "Building Large Monolingual Dictionaries at the Leipzig Corpora Collection: From 100 to 200 Languages. LREC 2012. "
                  "Built by tools/im/next_words.py; only the counts of the most common word pairs are included.")


def notice(folder_id, corpus, file, extra):
    """Writes the folder's NOTICE line for its table: in place of an older one for the same file, else at the end. Other
    paragraphs (the word list's, its ranking, its starters) stay as they are, where they are."""
    path = os.path.join(IM, folder_id, "NOTICE")
    lines = open(path, encoding="utf-8").read().rstrip("\n").split("\n")
    line = LEIPZIG_NOTICE.format(file=file, corpus=corpus, extra=f", {extra}" if extra else "")
    at = next((i for i, l in enumerate(lines) if l.startswith(f"Next words ({file}):")), None)
    if at is not None:
        lines[at] = line
    else:
        while lines and not lines[-1]:
            lines.pop()
        lines += ["", line]
    open(path, "w", encoding="utf-8", newline="\n").write("\n".join(lines) + "\n")


def main():
    if len(sys.argv) > 1 and sys.argv[1] == "notice":
        notice(*sys.argv[2:6])
        return
    if len(sys.argv) > 1 and sys.argv[1] == "convert":
        convert(sys.argv[2:])
        return
    p = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    p.add_argument("mode", choices=["build", "check"])
    p.add_argument("id")
    p.add_argument("text", nargs="+")
    p.add_argument("--words")
    p.add_argument("--out", help="the table, pairs.bin by default; a .txt name writes the readable text instead")
    # Its own name: "text" is the corpus files.
    p.add_argument("--text", dest="text_out", help="also write the readable text table here")
    p.add_argument("--top", type=int, default=8)
    p.add_argument("--min", type=int, default=2)
    p.add_argument("--every", type=int, default=10)
    p.add_argument("--vocabulary", type=int, default=50_000)
    p.add_argument("--max-mb", dest="max_mb", type=float, default=0.0,
                   help="drop the rarest pairs until the table fits in this many MB (0: no limit)")
    args = p.parse_args()
    (build if args.mode == "build" else check)(args)


if __name__ == "__main__":
    main()
