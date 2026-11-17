# A Cantonese collocation table for librime-octagram's build_grammar: "key count" lines, where a key is the last 1 to 3
# characters before a word, then the word's first 3 characters (what Octagram::Query looks up).
# python3 gram.py essay.txt test.tsv out.txt divisor sources...   (sources: cv hkcancor wiki)
import glob, re, sys
from collections import Counter

import os
H = os.environ.get('CANTO_SRC', os.path.expanduser('~/build/lang/canto-study/src')) + '/'
RUN = re.compile(r'[㐀-鿿\U00020000-\U0003ffff]+')
vocab = set()
for line in open(sys.argv[1], encoding='utf-8'):
    w = line.split('\t', 1)[0]
    if RUN.fullmatch(w): vocab.add(w)
LONGEST = 6
held = [l.split('\t')[1].rstrip('\n') for l in open(sys.argv[2], encoding='utf-8')]
held_set = set(held)

def segment(run):
    out, i = [], 0
    while i < len(run):
        for n in range(min(LONGEST, len(run) - i), 0, -1):
            if n == 1 or run[i:i + n] in vocab:
                out.append(run[i:i + n]); i += n; break
    return out

def overlaps(line):
    # Drop any training line holding a held-out phrase of 4 characters or more.
    return any(p in line for p in held_set if len(p) >= 4)

def sentences(name):
    if name == 'hkcancor':
        for f in glob.glob(H + 'hk/utf8/*'):
            words = []
            for line in open(f, encoding='utf-8'):
                line = line.strip()
                if line.startswith('</sent_tag>'):
                    if words: yield words
                    words = []
                p = line.split('/')
                if len(p) >= 3 and p[0]:
                    if RUN.fullmatch(p[0]): words.append(p[0])
                    elif words: yield words; words = []
    else:
        path = {'cv': 'cv-zh-hk.txt', 'wiki': 'yuewiki.txt'}[name]
        for line in open(H + path, encoding='utf-8'):
            if name == 'cv' and overlaps(line): continue
            for run in RUN.findall(line):
                yield segment(run)

counts = Counter()
for name in sys.argv[5:]:
    n = 0
    for words in sentences(name):
        text = ''
        for w in words:
            for k in range(1, min(3, len(text)) + 1):
                counts[text[-k:] + w[:3]] += 1
            text += w
        n += 1
    print(f'{name}: {n} sentences', file=sys.stderr)
div = float(sys.argv[4])
with open(sys.argv[3], 'w', encoding='utf-8') as f:
    kept = 0
    for key, c in counts.items():
        v = c / div
        if v > 1:
            f.write(f'{key} {v:g}\n'); kept += 1
print(f'{len(counts)} keys, {kept} kept', file=sys.stderr)
