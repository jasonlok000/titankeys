# python3 mkessay.py base-essay.txt out.txt source=alpha ...
# Adds each source's word counts to the essay's weights, scaled so the source's total is alpha times the essay's total.
# Sources: wordshk (words.hk existingwordcount.csv), hkcancor (HKCanCor's segmented words), cv (Common Voice zh-HK,
# segmented by longest match on the essay's words).
import glob, re, sys
from collections import Counter

import os
H = os.environ.get('CANTO_SRC', os.path.expanduser('~/build/lang/canto-study/src')) + '/'
CJK = re.compile(r'^[㐀-鿿\U00020000-\U0003ffff]+$')

def wordshk():
    c = Counter()
    for line in open(H + 'existingwordcount.csv', encoding='utf-8'):
        w, _, n = line.rstrip('\n').rpartition(',')
        if CJK.match(w) and n.isdigit():
            c[w] += int(n)
    return c

def hkcancor():
    c = Counter()
    for f in glob.glob(H + 'hk/utf8/*'):
        for line in open(f, encoding='utf-8'):
            p = line.strip().split('/')
            if len(p) >= 3 and CJK.match(p[0]):
                c[p[0]] += 1
    return c

def cv(vocab):
    c = Counter()
    longest = max(len(w) for w in vocab)
    for line in open(H + 'cv-zh-hk.txt', encoding='utf-8'):
        for run in re.findall(r'[㐀-鿿]+', line):
            i = 0
            while i < len(run):
                for n in range(min(longest, len(run) - i), 0, -1):
                    if n == 1 or run[i:i + n] in vocab:
                        c[run[i:i + n]] += 1
                        i += n
                        break
    return c

base = {}
order = []
for line in open(sys.argv[1], encoding='utf-8'):
    w, _, n = line.rstrip('\n').partition('\t')
    if w not in base:
        order.append(w)
    base[w] = int(n or 0)
total = sum(base.values())
out = dict(base)
for arg in sys.argv[3:]:
    name, alpha = arg.split('=')
    counts = {'wordshk': wordshk, 'hkcancor': hkcancor}.get(name, lambda: cv(base))()
    scale = float(alpha) * total / sum(counts.values())
    added = 0
    for w, n in counts.items():
        if w not in out:
            order.append(w); out[w] = 0; added += 1
        out[w] += int(round(n * scale))
    print(f'{name}: {len(counts)} words, {sum(counts.values())} counted, {added} new, scale {scale:.1f}', file=sys.stderr)
with open(sys.argv[2], 'w', encoding='utf-8') as f:
    for w in order:
        f.write(f'{w}\t{max(out[w], 1)}\n')
