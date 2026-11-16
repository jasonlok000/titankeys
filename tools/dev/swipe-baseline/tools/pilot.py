#!/usr/bin/env python3
"""Small pilot: does training on Titan-style damaged strokes beat training on clean phone strokes, on real Titan strokes?
Usage: pilot.py baseline-dir dev.jsonl qwerty.json lexicon cleverkeys.onnx"""
import json
import math
import os
import random
import sys
import time

import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ck_core as C

BASE, DATA, LAYOUT, LEX, CK_MODEL = sys.argv[1:6]
PRIV = (os.environ.get('TK_PRIVATE') or sys.exit('TK_PRIVATE is not set: source tools/dev/env.sh first'))
os.makedirs(os.path.join(PRIV, 'pilot'), exist_ok=True)
torch.set_num_threads(10)
torch.manual_seed(5)
random.seed(5)
np.random.seed(5)
root, vocab = C.load_lexicon(LEX)
GRID = {}
for r, row in enumerate(['qwertyuiop', 'asdfghjkl', 'zxcvbnm']):
    for c, ch in enumerate(row):
        GRID[ch] = ((c + (1 if r == 2 else 0) + .5) / 10, (r + .5) / 3)
layout = json.load(open(LAYOUT))
futo = {k['letter']: (k['cx'], k['cy']) for k in layout['keys']}
row_shift = [float(np.mean([GRID[c][0] - futo[c][0] for c in row])) for row in ['qwertyuiop', 'asdfghjkl', 'zxcvbnm']]
print('row shift FUTO to Titan grid', [round(v, 3) for v in row_shift], flush=True)


def load_futo():
    out = []
    for line in open(DATA, encoding='utf-8'):
        try:
            e = json.loads(line)
        except Exception:
            continue
        w = C.key(e['word'])
        if not w.isalpha() or not 2 <= len(w) <= 15 or w not in vocab or e.get('potentially_invalid_sentence') or not isinstance(e['data'], list) or len(e['data']) < 3:
            continue
        x, y, t = np.array([p['x'] for p in e['data']], dtype=float), np.array([p['y'] for p in e['data']], dtype=float), np.array([p['t'] for p in e['data']], dtype=float)
        t -= t[0]
        if t[-1] <= 0 or np.any(np.diff(t) < 0):
            continue
        x = x + np.interp(y * 3, [.5, 1.5, 2.5], row_shift)
        out.append((w, x, y, t))
    return out


futo_strokes = load_futo()
random.shuffle(futo_strokes)
print('FUTO strokes', len(futo_strokes), flush=True)
val = futo_strokes[:1000]
train = futo_strokes[1000:]


def jitter(x, y, t, rng):
    return x + rng.gauss(0, .012), y + rng.gauss(0, .02), t


def titan_damage(x, y, t, rng):
    kx, ky, tt = x * 10, y * 3, t.copy()
    kx, ky = np.clip(kx, .56, 9.49), np.clip(ky, .46, 3)
    if len(kx) > 6 and rng.random() < .85:
        cut = rng.uniform(0, .3)
        d = np.sqrt(np.diff(kx) ** 2 + np.diff(ky) ** 2)
        along = np.concatenate([[0], np.cumsum(d)])
        lo, hi = along[-1] * cut * .33, along[-1] * (1 - cut * .67)
        keep = (along >= lo) & (along <= hi)
        if keep.sum() >= 3:
            kx, ky, tt = kx[keep], ky[keep], tt[keep] - tt[keep][0]
    if rng.random() < .8:
        kx = kx - rng.uniform(.15, .45) * np.maximum(0, kx - 6.5)
    kx = kx + rng.gauss(0, .4)
    ky = ky + rng.gauss(.1, .18)
    if rng.random() < .33:
        where = np.where(ky < .85)[0]
        if len(where) > 4:
            s = where[rng.randrange(len(where) // 2 + 1)]
            cut = tt[s] + rng.uniform(40, 120)
            keep = (tt < tt[s]) | (tt > cut)
            if keep.sum() >= 3:
                kx, ky, tt = kx[keep], ky[keep], tt[keep]
    return kx / 10, ky / 3, tt


class Block(nn.Module):
    def __init__(self, ch, dilation):
        super().__init__()
        self.conv = nn.Conv1d(ch, ch, 5, padding=2 * dilation, dilation=dilation)
        self.norm = nn.GroupNorm(8, ch)
        self.conv2 = nn.Conv1d(ch, ch, 1)

    def forward(self, x):
        return x + self.conv2(F.gelu(self.norm(self.conv(x))))


class Net(nn.Module):
    def __init__(self, ch=96):
        super().__init__()
        self.head = nn.Conv1d(4, ch, 5, padding=2)
        self.a = nn.Sequential(Block(ch, 1), Block(ch, 2), Block(ch, 3))
        self.down = nn.Conv1d(ch, ch, 4, stride=2, padding=1)
        self.b = nn.Sequential(Block(ch, 1), Block(ch, 2), Block(ch, 4), Block(ch, 8), Block(ch, 1))
        self.out = nn.Conv1d(ch, 27, 1)

    def forward(self, f):
        delta = torch.zeros_like(f)
        delta[:, :, 1:] = f[:, :, 1:] - f[:, :, :-1]
        h = F.gelu(self.head(torch.cat([f, delta * 10], 1)))
        return F.log_softmax(self.out(self.b(self.down(self.a(h)))), 1).permute(2, 0, 1)


def features(w, x, y, t):
    return C.featurize(np.clip(x, 0, 1), np.clip(y, 0, 1), t)[0]


def batches(items, aug, rng, size=384):
    order = list(range(len(items)))
    rng.shuffle(order)
    for i in range(0, len(order), size):
        fs, targets, lengths = [], [], []
        for j in order[i:i + size]:
            w, x, y, t = items[j]
            x2, y2, t2 = aug(x, y, t, rng)
            fs.append(features(w, x2, y2, t2))
            ids = [C.INDEX[c] for c in w]
            targets += ids
            lengths.append(len(ids))
        yield torch.tensor(np.stack(fs)), torch.tensor(targets), torch.tensor(lengths)


def train_model(items, aug, epochs, lr, name, start=None):
    net = Net() if start is None else start
    opt = torch.optim.AdamW(net.parameters(), lr=lr, weight_decay=1e-4)
    rng = random.Random(11)
    steps = max(1, epochs * math.ceil(len(items) / 384))
    sched = torch.optim.lr_scheduler.OneCycleLR(opt, max_lr=lr, total_steps=steps + 5, pct_start=.15)
    t0 = time.time()
    for ep in range(epochs):
        net.train()
        total, n = 0, 0
        for f, tg, ln in batches(items, aug, rng):
            lp = net(f)
            loss = F.ctc_loss(lp, tg, torch.full((f.shape[0],), lp.shape[0], dtype=torch.long), ln, blank=26, zero_infinity=True)
            opt.zero_grad()
            loss.backward()
            torch.nn.utils.clip_grad_norm_(net.parameters(), 5)
            opt.step()
            sched.step()
            total += float(loss)
            n += 1
        print(f'  {name} epoch {ep + 1}/{epochs} loss {total / max(n, 1):.3f} ({time.time() - t0:.0f}s)', flush=True)
    torch.save(net.state_dict(), os.path.join(PRIV, 'pilot', name + '.pt'))
    return net


def rank_net(net, x, y, t, word):
    net.eval()
    with torch.no_grad():
        lp = net(torch.tensor(features(word, x, y, t))[None]).squeeze(1).numpy()
    best = C.decode(lp, root)
    order = sorted(best, key=lambda w: -(best[w] / max(len(w), 1) ** C.GAMMA + C.BETA * len(w) + C.LAMBDA * math.log(vocab[w])))[:80]
    return C.position(order, word)


corpus = []
for line in open(os.path.join(PRIV, 'corpus.jsonl'), encoding='utf-8'):
    e = json.loads(line)
    if e['first'] and e['set'] in ('prompted', 'hand', 'recorder') and C.key(e['expected']).isalpha() and C.key(e['expected']) in vocab:
        corpus.append((C.key(e['expected']), np.array(e['x']), np.array(e['y']), np.array(e['t'], dtype=float), e['set'], int(e['slot'])))
held = [c for c in corpus if c[5] % 3 == 2]
tune = [c for c in corpus if c[5] % 3 != 2]
print('Titan strokes', len(corpus), 'held back', len(held), 'tuning', len(tune), flush=True)

sess = C.session(CK_MODEL)
keys, mask = C.layout_arrays(GRID)


def score_set(name, fn):
    out = {}
    for s in ('prompted', 'hand', 'recorder'):
        for label, group in (('all', corpus), ('held', held)):
            items = [c for c in group if c[4] == s]
            if not items:
                continue
            out[f'{s}/{label}'] = C.tally([fn(w, x, y, t) for w, x, y, t, _, _ in items])
    print(name, json.dumps(out), flush=True)
    return out


results = {}
results['cleverkeys phone-trained, same decoder'] = score_set('CK', lambda w, x, y, t: C.position(C.recognise(sess, root, vocab, x, y, t, keys, mask), w))


def eval_futo(net, name):
    rng = random.Random(3)
    clean = C.tally([rank_net(net, x, y, t, w) for w, x, y, t in val[:600]])
    damaged = []
    for w, x, y, t in val[:600]:
        x2, y2, t2 = titan_damage(x, y, t, rng)
        damaged.append(rank_net(net, np.clip(x2, 0, 1), np.clip(y2, 0, 1), t2, w))
    r = {'futo_clean': clean, 'futo_damaged': C.tally(damaged)}
    print(name, json.dumps(r), flush=True)
    return r


EPOCHS = 9
print('\n=== A: trained on phone strokes as recorded ===', flush=True)
net_a = train_model(train, jitter, EPOCHS, 2e-3, 'clean')
results['A futo'] = eval_futo(net_a, 'A on FUTO')
results['A titan'] = score_set('A', lambda w, x, y, t: rank_net(net_a, x, y, t, w))

print('\n=== B: trained with Titan-style damage ===', flush=True)
net_b = train_model(train, titan_damage, EPOCHS, 2e-3, 'titan-damage')
results['B futo'] = eval_futo(net_b, 'B on FUTO')
results['B titan'] = score_set('B', lambda w, x, y, t: rank_net(net_b, x, y, t, w))

print('\n=== C: B, then fine-tuned on your real strokes (tuning part only) ===', flush=True)
net_c = Net()
net_c.load_state_dict(net_b.state_dict())
items = [(w, x, y, t) for w, x, y, t, _, _ in tune]
net_c = train_model(items, jitter, 25, 4e-4, 'fine-tuned', start=net_c)
results['C titan'] = score_set('C', lambda w, x, y, t: rank_net(net_c, x, y, t, w))
json.dump(results, open(os.path.join(BASE, 'report', 'pilot.json'), 'w'), indent=1)
print('done', flush=True)
