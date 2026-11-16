#!/usr/bin/env python3
"""Cases for tuning how much the next-word model counts in typed autocorrect. Each word the owner really wrote (everyday
and prompted text, with its real text before it) is given once as typed correctly and once with a slip that is common on
the Titan 2 Elite's keys: a neighbouring key, a missed letter, an extra neighbouring key or two swapped letters. Each case
carries the phone predictor's log chance for every word the corrector could pick.
Usage: autocorrect_cases.py baseline-dir english.combined lm-dir out.tsv"""
import base64
import json
import os
import random
import re
import sys
from collections import defaultdict

import numpy as np
import onnxruntime as ort
from transformers import AutoTokenizer

BASE, LEX, LM_DIR, OUT = sys.argv[1:5]
CAP = int(os.environ.get('LM_CAP', '32'))
rng = random.Random(11)
ROWS = ['qwertyuiop', 'asdfghjkl', 'zxcvbnm']
POS = {ch: (r, c) for r, row in enumerate(ROWS) for c, ch in enumerate(row)}
NEAR = {a: [b for b in POS if b != a and ((POS[a][0] - POS[b][0]) ** 2 + (POS[a][1] - POS[b][1]) ** 2) ** .5 <= 1.5] for a in POS}

words = {}
for line in open(LEX, encoding='utf-8'):
    m = re.match(r' word=([^,]+),f=(\d+),flags=([^,]*)', line)
    if m and 'not_a_word' not in m.group(3):
        w = m.group(1).lower()
        if re.fullmatch(r"[a-z]+('[a-z]+)?", w):
            words[w] = max(words.get(w, 0), int(m.group(2)))
lexicon = [w for w, f in words.items() if f >= 60]
deletes = defaultdict(set)


def variants(w, depth=2):
    out, frontier = {w}, {w}
    for _ in range(depth):
        frontier = {f[:i] + f[i + 1:] for f in frontier for i in range(len(f))}
        out |= frontier
    return out


for w in lexicon:
    plain = w.replace("'", '')
    for v in variants(plain):
        deletes[v].add(w)


def nearby(typed):
    found = set()
    for v in variants(typed):
        found |= deletes.get(v, set())
    return found


def slip(w):
    i = rng.randrange(len(w))
    kind = rng.choices(['near', 'missed', 'extra', 'swap'], [.5, .2, .15, .15])[0]
    if kind == 'near' and w[i] in NEAR:
        return w[:i] + rng.choice(NEAR[w[i]]) + w[i + 1:]
    if kind == 'missed' and len(w) > 3:
        return w[:i] + w[i + 1:]
    if kind == 'extra' and w[i] in NEAR:
        return w[:i + 1] + rng.choice(NEAR[w[i]]) + w[i + 1:]
    if kind == 'swap' and i + 1 < len(w) and w[i] != w[i + 1]:
        return w[:i] + w[i + 1] + w[i] + w[i + 2:]
    return None


tok = AutoTokenizer.from_pretrained(LM_DIR)
sess = ort.InferenceSession(os.path.join(LM_DIR, 'model-int8pc.onnx'), providers=['CPUExecutionProvider'])
start = int(re.search(r'start_token=(\d+)', open(os.path.join(LM_DIR, 'lm-info.txt')).read()).group(1))
first = {}


def chance(logp, w):
    best = -99.0
    for form in (w, w[:1].upper() + w[1:]):
        if form not in first:
            t = tok(' ' + form, add_special_tokens=False)['input_ids']
            first[form] = t[0] if t else -1
        if first[form] >= 0:
            best = max(best, float(logp[first[form]]))
    return best


n = 0
with open(OUT, 'w', encoding='utf-8', newline='\n') as out:
    for line in open(os.path.join((os.environ.get('TK_PRIVATE') or sys.exit('TK_PRIVATE is not set: source tools/dev/env.sh first')), 'v9', 'corpus.jsonl'), encoding='utf-8'):
        e = json.loads(line)
        w = e['expected']
        if not e['first'] or e['set'] not in ('hand', 'prompted') or not re.fullmatch(r'[a-z]{3,}', w) or w not in words:
            continue
        context = e['context']
        if context and not context.endswith(' '):
            continue
        typo = slip(w)
        if not typo or typo in words:
            continue
        ctx = context[-500:].rstrip(' ')
        ids = tok(ctx, add_special_tokens=False)['input_ids'] if ctx.strip() else []
        ids = ids[-CAP:] or [start]
        logp = sess.run(None, {'input_ids': np.array([ids], dtype=np.int64)})[0][0]
        for typed in (typo, w):
            pool = nearby(typed) | {typed, w}
            scores = ','.join(f'{c}:{chance(logp, c):.4f}' for c in sorted(pool))
            out.write('\t'.join([e['set'], base64.b64encode(context.encode()).decode(), typed, w, scores]) + '\n')
            n += 1
print('cases', n)
