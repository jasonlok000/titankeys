#!/usr/bin/env python3
"""
Makes the next-word tables for librime-predict (#56, docs/studies/56-chinese-prediction.md): for each word, the words that
followed it in Tatoeba's sentences, best first. data.sh compiles them with the plugin's build_predict.

    python3 native/rime/predict.py <yue_sentences.tsv> <cmn_sentences.tsv> <rime sources>

- The sentences: Tatoeba's official per-language exports (CC BY 2.0 FR), https://downloads.tatoeba.org/exports/per_language/
  {yue,cmn}/{yue,cmn}_sentences.tsv.bz2. The exports change every week, so the tables are made once and kept in the repo,
  and SOURCES.md records the files they were made from. Every sentence is first turned into Traditional characters with
  OpenCC's own Simplified-to-Traditional tables (its s2t: the longest phrase, then the character), since Rime's word lists
  are Traditional; the schemas' OpenCC filters turn the suggestions into each variant.
- The words: each sentence cut as Rime commits, the longest word in the schema's word list: rime-cantonese's essay for
  Cantonese (Yale and Jyutping), rime-essay for Mandarin (Pinyin).
- What's kept: every pair seen, at most 8 next words for a word (a page of Rime's choices; the study found no loss), weighted
  by how often each followed. No entry for the start of a text ("$"): with nothing typed yet, the bar stays empty (owner).

Writes native/rime/predict/{yue,cmn}.txt: one "word<TAB>next<TAB>count" a line, words in code point order.
"""
import os
import sys
from collections import Counter, defaultdict

KEEP = 8
LONGEST = 6


def cjk(c):
    return "㐀" <= c <= "鿿" or "\U00020000" <= c <= "\U0003134f"


def opencc_table(path):
    table = {}
    for line in open(path, encoding="utf-8"):
        key, _, values = line.rstrip("\n").partition("\t")
        if key and values:
            table[key] = values.split(" ")[0]
    return table


def traditional(text, phrases, characters, longest):
    """OpenCC's s2t: at each place the longest phrase in STPhrases, else the character's first form in STCharacters."""
    out, at = [], 0
    while at < len(text):
        for n in range(min(longest, len(text) - at), 1, -1):
            if text[at:at + n] in phrases:
                out.append(phrases[text[at:at + n]])
                at += n
                break
        else:
            out.append(characters.get(text[at], text[at]))
            at += 1
    return "".join(out)


def word_list(path):
    words = set()
    for line in open(path, encoding="utf-8"):
        word = line.split("\t", 1)[0]
        if word and not word.startswith("#") and "\t" in line:
            words.add(word)
    return words


def cut(text, words):
    out, at = [], 0
    while at < len(text):
        if not cjk(text[at]):
            out.append(text[at])
            at += 1
            continue
        for n in range(min(LONGEST, len(text) - at), 0, -1):
            if n == 1 or text[at:at + n] in words:
                out.append(text[at:at + n])
                at += n
                break
    return out


def main():
    yue, cmn, rime = sys.argv[1:4]
    dictionary = f"{rime}/librime/deps/opencc/data/dictionary"
    phrases = opencc_table(f"{dictionary}/STPhrases.txt")
    characters = opencc_table(f"{dictionary}/STCharacters.txt")
    longest = max(map(len, phrases))
    here = os.path.dirname(os.path.abspath(__file__))
    os.makedirs(f"{here}/predict", exist_ok=True)
    for name, path, words_path in (("yue", yue, f"{rime}/rime-cantonese/essay-cantonese.txt"),
                                   ("cmn", cmn, f"{rime}/rime-essay/essay.txt")):
        words = word_list(words_path)
        sentences = set()
        for line in open(path, encoding="utf-8"):
            parts = line.rstrip("\n").split("\t")
            if len(parts) >= 3:
                sentences.add(traditional(parts[2], phrases, characters, longest))
        pairs = defaultdict(Counter)
        for text in sorted(sentences):
            pieces = cut(text, words)
            for a, b in zip(pieces, pieces[1:]):
                if cjk(a[0]) and cjk(b[0]):
                    pairs[a][b] += 1
        lines = []
        for word in sorted(pairs):
            best = sorted(pairs[word].items(), key=lambda x: (-x[1], x[0]))[:KEEP]
            lines += [f"{word}\t{nxt}\t{count}\n" for nxt, count in best]
        with open(f"{here}/predict/{name}.txt", "w", encoding="utf-8", newline="\n") as out:
            out.writelines(lines)
        print(f"{name}: {len(sentences)} sentences, {len(pairs)} words, {len(lines)} next words")


if __name__ == "__main__":
    main()
