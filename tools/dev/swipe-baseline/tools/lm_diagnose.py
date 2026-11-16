#!/usr/bin/env python3
"""Why does the strong language model help prose and not everyday text? Counts only, no text.
Usage: lm_diagnose.py baseline-dir"""
import json
import os
import re
import sys
from collections import Counter, defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rawlog as R

P = (os.environ.get('TK_PRIVATE') or sys.exit('TK_PRIVATE is not set: source tools/dev/env.sh first'))
gpt = json.load(open(os.path.join(P, 'gpt-cands.json')))
corpus = {json.loads(l)['id']: json.loads(l) for l in open(os.path.join(P, 'corpus.jsonl'), encoding='utf-8')}
replay = {json.loads(l)['id']: json.loads(l) for l in open(os.path.join(P, 'replay-4f75dcb-full.jsonl'), encoding='utf-8')}


def key(w):
    return R.norm(w).replace("'", '')


def sentence_words(text):
    tail = re.split(r'[.!?\n]', text)[-1]
    return len(re.findall(r"[A-Za-z']+", tail))


stats = defaultdict(Counter)
for rid, cands in gpt.items():
    c = corpus[rid]
    s = c['set']
    if s not in ('prompted', 'hand'):
        continue
    exp = key(c['expected'])
    ctx = c['context']
    words = len(re.findall(r"[A-Za-z']+", ctx[-500:]))
    sw = sentence_words(ctx)
    st = stats[s]
    st['n'] += 1
    st['context empty'] += words == 0
    st['context 1-3 words'] += 1 <= words <= 3
    st['context 4-10 words'] += 4 <= words <= 10
    st['context 11+ words'] += words > 10
    st['sentence start (no words since . ! ? or new line)'] += sw == 0
    st['context chars, total'] += len(ctx[-500:])
    in_c = exp in cands
    st['right word among candidates'] += in_c
    engine_right = [key(w) for w in replay[rid]['top']][:1] == [exp]
    if in_c:
        ranked = sorted(cands, key=lambda w: -cands[w])
        r = ranked.index(exp) + 1
        st['language model ranks it 1st among candidates'] += r == 1
        st['language model ranks it in top 3 among candidates'] += r <= 3
        if not engine_right:
            st['engine wrong, word among candidates'] += 1
            st['  of those, language model ranks it 1st'] += r == 1
    st['candidates per stroke, total'] += len(cands)
for s, st in stats.items():
    n = st['n']
    print(f'== {s}: {n} strokes')
    for k, v in st.items():
        if k == 'n':
            continue
        if 'total' in k:
            print(f'   {k.replace(", total", "")}: average {v / n:.1f}')
        elif k.startswith('  of those'):
            print(f"   {k}: {v} of {st['engine wrong, word among candidates']}")
        else:
            print(f'   {k}: {v} ({100 * v / n:.1f}%)')
