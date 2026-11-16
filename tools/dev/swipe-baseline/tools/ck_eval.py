#!/usr/bin/env python3
"""Runs CleverKeys' open CTC encoder on the same strokes and dictionary as the current engine: a real head-to-head.
Usage: ck_eval.py baseline-dir model.onnx english.combined"""
import json
import math
import os
import re
import sys
from collections import defaultdict

import numpy as np
import onnxruntime as ort

BASE, MODEL, DICT = sys.argv[1:4]
PRIV = (os.environ.get('TK_PRIVATE') or sys.exit('TK_PRIVATE is not set: source tools/dev/env.sh first'))
ROWS = ['qwertyuiop', 'asdfghjkl', 'zxcvbnm']
KEYS = {}
for r, row in enumerate(ROWS):
    for c, ch in enumerate(row):
        KEYS[ch] = ((c + (1 if r == 2 else 0) + .5) / 10, (r + .5) / 3)
ALPHA = 'abcdefghijklmnopqrstuvwxyz'
INDEX = {c: i for i, c in enumerate(ALPHA)}
SP = {'neighbour': 'neighbor', 'tyres': 'tires', 'favourite': 'favorite', 'colour': 'color', 'grey': 'gray'}


def key(w):
    w = w.lower().replace('’', "'").strip("'")
    w = SP.get(w, w)
    return w.replace("'", '')


class Node:
    __slots__ = ('kids', 'freq', 'char', 'depth', 'word', 'id')

    def __init__(self, char, depth, nid):
        self.kids, self.freq, self.char, self.depth, self.word, self.id = {}, None, char, depth, None, nid


root = Node(-1, 0, 0)
count = 1
vocab = {}
for line in open(DICT, encoding='utf-8'):
    m = re.match(r' word=([^,]+),f=(\d+),flags=([^,]*)', line)
    if not m or 'not_a_word' in m.group(3):
        continue
    w = key(m.group(1))
    if len(w) < 1 or not all(c in INDEX for c in w):
        continue
    f = max(1, int(m.group(2)))
    vocab[w] = max(vocab.get(w, 0), f)
for w, f in vocab.items():
    n = root
    for ch in w:
        i = INDEX[ch]
        if i not in n.kids:
            n.kids[i] = Node(i, n.depth + 1, count)
            count += 1
        n = n.kids[i]
    n.freq = f
    n.word = w
print('lexicon', len(vocab), 'trie nodes', count)

session = ort.InferenceSession(MODEL, providers=['CPUExecutionProvider'])
print([(i.name, i.shape) for i in session.get_inputs()], [(o.name, o.shape) for o in session.get_outputs()][:3])
layout_keys = np.zeros((1, 64, 2), dtype=np.float32)
layout_mask = np.zeros((1, 64), dtype=bool)
for ch, i in INDEX.items():
    layout_keys[0, i] = KEYS[ch]
    layout_mask[0, i] = True


def featurize(x, y, t):
    x, y, t = np.array(x, dtype=float), np.array(y, dtype=float), np.array(t, dtype=float)
    duration = t[-1] - t[0]
    if duration <= 1e-12:
        hx, hy = np.array([x[0], x[-1]]), np.array([y[0], y[-1]])
    else:
        n = max(2, int(np.rint(duration / (1000 / 60))) + 1)
        grid = np.minimum(t[0] + np.arange(n) * (duration / (n - 1)), t[-1])
        idx1 = np.searchsorted(t, grid, 'left')
        hx, hy = np.zeros(n), np.zeros(n)
        for i, (g, j) in enumerate(zip(grid, idx1)):
            if j == 0:
                hx[i], hy[i] = x[0], y[0]
            elif j >= len(t):
                hx[i], hy[i] = x[-1], y[-1]
            else:
                seg = t[j] - t[j - 1]
                f = (g - t[j - 1]) / seg if seg > 1e-12 else 0
                hx[i], hy[i] = x[j - 1] + f * (x[j] - x[j - 1]), y[j - 1] + f * (y[j] - y[j - 1])
    pos = np.linspace(0, 1, 64) * (len(hx) - 1)
    i0 = pos.astype(int)
    i1 = np.minimum(i0 + 1, len(hx) - 1)
    frac = pos - i0
    rx = np.clip(hx[i0] + frac * (hx[i1] - hx[i0]), 0, 1)
    ry = np.clip(hy[i0] + frac * (hy[i1] - hy[i0]), 0, 1)
    return np.stack([rx, ry], 0)[None].astype(np.float32)


GAMMA, BETA, GP, BP, BEAM = .9, .25, .25, .9882, 100


def decode(lp):
    """FUTO-style trie Viterbi beam, as in CleverKeys' CtcBeamDecoder. lp is frames x 27, blank last."""
    beam = [(0.0, root, False)]
    blank = 26
    for t in range(lp.shape[0]):
        frame = lp[t].tolist()
        nxt = {}
        for score, node, ended in beam:
            s = score + frame[blank]
            k = (node.id << 1) | 1
            cur = nxt.get(k)
            if cur is None or cur[0] < s:
                nxt[k] = (s, node, True)
            for ci, child in node.kids.items():
                s = score + frame[ci]
                k = child.id << 1
                cur = nxt.get(k)
                if cur is None or cur[0] < s:
                    nxt[k] = (s, child, False)
            if not ended and node.depth > 0:
                s = score + frame[node.char]
                k = node.id << 1
                cur = nxt.get(k)
                if cur is None or cur[0] < s:
                    nxt[k] = (s, node, False)
        cand = list(nxt.values())
        if len(cand) > BEAM:
            cand.sort(key=lambda h: -(h[0] / max(h[1].depth, 1) ** GP + BP * h[1].depth))
            cand = cand[:BEAM]
        beam = cand
    best = {}
    for score, node, _ in beam:
        if node.word is None:
            continue
        if node.word not in best or best[node.word] < score:
            best[node.word] = score
    return best


out = open(os.path.join(PRIV, 'ck.jsonl'), 'w', encoding='utf-8')
done = 0
for line in open(os.path.join(PRIV, 'corpus.jsonl'), encoding='utf-8'):
    e = json.loads(line)
    if not e['first'] or e['set'] not in ('prompted', 'hand', 'recorder'):
        continue
    features = featurize(e['x'], e['y'], e['t'])
    emissions = session.run(['log_emissions'], {'features': features, 'layout_keys': layout_keys, 'layout_mask': layout_mask})[0][0]
    lp = np.concatenate([emissions[:, :26], emissions[:, [64]]], 1)
    best = decode(lp)
    rows = sorted(((round(s, 3), w) for w, s in best.items()), reverse=True)[:80]
    out.write(json.dumps({'id': e['id'], 'c': [[w, s, vocab[w]] for s, w in rows]}) + '\n')
    done += 1
    if done % 200 == 0:
        print(done, flush=True)
out.close()

# ---- compare ------------------------------------------------------------------------------------------------------
corpus = {json.loads(l)['id']: json.loads(l) for l in open(os.path.join(PRIV, 'corpus.jsonl'), encoding='utf-8')}
replay = {json.loads(l)['id']: json.loads(l) for l in open(os.path.join(PRIV, 'replay-4f75dcb-full.jsonl'), encoding='utf-8')}
ck = {json.loads(l)['id']: json.loads(l)['c'] for l in open(os.path.join(PRIV, 'ck.jsonl'), encoding='utf-8')}


def rank(cands, expected, lam):
    scored = sorted(((s / max(len(w), 1) ** GAMMA + BETA * len(w) + lam * math.log(f), w) for w, s, f in cands), reverse=True)
    for i, (_, w) in enumerate(scored):
        if w == expected:
            return i + 1
    return 10 ** 6


res = {}
for s in ('prompted', 'hand', 'recorder'):
    ids = [i for i in ck if corpus[i]['set'] == s]
    futo = [replay[i]['rank'] for i in ids if i in replay]
    line = {'n': len(ids), 'futo_decoder_with_context': {k: round(100 * sum(1 <= r <= k for r in futo) / len(futo), 1) for k in (1, 3, 16, 48)}}
    for lam in (.5, 1, 2, 4):
        r = [rank(ck[i], key(corpus[i]['expected']), lam) for i in ids]
        line[f'cleverkeys_lambda_{lam}'] = {k: round(100 * sum(x <= k for x in r) / len(r), 1) for k in (1, 3, 16, 48)}
    res[s] = line
    print(s, json.dumps(line))
json.dump(res, open(os.path.join(BASE, 'report', 'cleverkeys-head-to-head.json'), 'w'), indent=1)
