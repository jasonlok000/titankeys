# python3 mk_cmn.py essay.txt out.txt source=alpha ...   (sources: jieba, cv)
# Adds word counts to rime-essay's weights (Traditional), scaled so a source's total is alpha times the essay's.
# jieba: its dict.txt (MIT), Simplified, turned Traditional by OpenCC's STPhrases then STCharacters (first choice).
# cv: Common Voice's zh-TW sentences (CC0), cut by the essay's own words, longest first.
import os, re, sys
from collections import Counter

OCC = os.path.expanduser('~/build/tk-rime/src/librime/deps/opencc/data/dictionary/')
SRC = os.path.expanduser('~/build/lang/canto-study/src/')
RUN = re.compile(r'[㐀-鿿]+')


def table(name):
    m = {}
    for line in open(OCC + name, encoding='utf-8'):
        a, _, b = line.rstrip('\n').partition('\t')
        if b:
            m[a] = b.split(' ')[0]
    return m


def traditional():
    phrases, chars = table('STPhrases.txt'), table('STCharacters.txt')
    return lambda w: phrases.get(w) or ''.join(chars.get(c, c) for c in w)


def jieba():
    t = traditional()
    c = Counter()
    for line in open(SRC + 'jieba-dict.txt', encoding='utf-8'):
        p = line.split()
        if len(p) >= 2 and RUN.fullmatch(p[0]) and p[1].isdigit():
            c[t(p[0])] += int(p[1])
    return c


def cv(vocab):
    longest = 6
    c = Counter()
    for line in open(SRC + 'cv-zh-tw.txt', encoding='utf-8'):
        for run in RUN.findall(line):
            i = 0
            while i < len(run):
                for n in range(min(longest, len(run) - i), 0, -1):
                    if n == 1 or run[i:i + n] in vocab:
                        c[run[i:i + n]] += 1
                        i += n
                        break
    return c


base, order = {}, []
for line in open(sys.argv[1], encoding='utf-8'):
    w, _, n = line.rstrip('\n').partition('\t')
    if w not in base:
        order.append(w)
    base[w] = int(n or 0)
total = sum(base.values())
out = dict(base)
for arg in sys.argv[3:]:
    name, alpha = arg.split('=')
    counts = jieba() if name == 'jieba' else cv(base)
    scale = float(alpha) * total / sum(counts.values())
    added = 0
    for w, n in counts.items():
        if w not in out:
            order.append(w); out[w] = 0; added += 1
        out[w] += int(round(n * scale))
    print(f'{name}: {len(counts)} words, {added} new, scale {scale:.2f}', file=sys.stderr)
with open(sys.argv[2], 'w', encoding='utf-8') as f:
    for w in order:
        f.write(f'{w}\t{max(out[w], 1)}\n')
