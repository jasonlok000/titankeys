"""The next-word fallback (tk_predict.h), measured on the shipped tables (#56; docs/languages.md, "Next-word suggestions").

    python3 native/rime/predict_fallback.py native/rime/predict/yue.txt held-out.txt [seed]

Held-out text, one sentence a line (not Tatoeba, which the tables are made from: Common Voice's zh-HK sentences for
Cantonese, its zh-TW ones for Mandarin), is cut into words by the table's own words (longest first), and the words into
commits, as someone types them: one word at a time, or phrases of 2 to 4 words at once. After each commit that's followed by
more text in its run, it asks what librime-predict suggests (the table's next words for the whole commit, best first) and
whether the next character is the first character of one of the first 3, as the Chinese study measured
(docs/studies/56-chinese-prediction.md). "Before" looks up the whole commit only, as librime-predict does; "after" then tries
what the commit ends with, longest first, as tk_predict.h does.
"""
import random
import re
import sys
from collections import defaultdict

RUN = re.compile(r'[㐀-鿿\U00020000-\U0003ffff]+')


def table(path):
    nexts = defaultdict(list)
    for line in open(path, encoding='utf-8'):
        parts = line.rstrip('\n').split('\t')
        if len(parts) == 3:
            nexts[parts[0]].append(parts[1])
    return nexts


def words(run, vocab, longest):
    out, i = [], 0
    while i < len(run):
        for n in range(min(longest, len(run) - i), 0, -1):
            if n == 1 or run[i:i + n] in vocab:
                out.append(run[i:i + n])
                i += n
                break
    return out


def suggest(nexts, commit, fallback):
    if commit in nexts:
        return nexts[commit]
    if fallback:
        for k in range(1, len(commit)):
            if commit[k:] in nexts:
                return nexts[commit[k:]]
    return []


def main():
    nexts = table(sys.argv[1])
    longest = max(len(w) for w in nexts)
    rng = random.Random(int(sys.argv[3]) if len(sys.argv) > 3 else 56)
    runs = [r for line in open(sys.argv[2], encoding='utf-8') for r in RUN.findall(line) if len(r) >= 2]
    for mode in ('words', 'phrases'):
        counts = {False: [0, 0], True: [0, 0]}  # fallback: [any suggestion, next character in the first 3]
        boundaries = 0
        for run in runs:
            ws = words(run, nexts, longest)
            commits = []
            i = 0
            while i < len(ws):
                n = 1 if mode == 'words' else rng.randint(2, 4)
                commits.append(''.join(ws[i:i + n]))
                i += n
            for at in range(len(commits) - 1):
                boundaries += 1
                following = commits[at + 1][0]
                for fallback in (False, True):
                    got = suggest(nexts, commits[at], fallback)
                    counts[fallback][0] += bool(got)
                    counts[fallback][1] += any(s[0] == following for s in got[:3])
        b, a = counts[False], counts[True]
        print(f'{mode}: {boundaries} commits followed by more text; suggestions shown {100*b[0]/boundaries:.1f}% -> '
              f'{100*a[0]/boundaries:.1f}%; next character in the first 3 {100*b[1]/boundaries:.1f}% -> {100*a[1]/boundaries:.1f}%')


if __name__ == '__main__':
    main()
