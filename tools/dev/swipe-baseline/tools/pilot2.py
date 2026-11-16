#!/usr/bin/env python3
"""How much do real Titan strokes help? Fine-tunes the pilot models on growing amounts of your strokes and judges them only
on held-back strokes, separately for words the fine-tuning saw and words it never saw.
Usage: pilot2.py baseline-dir lexicon cleverkeys.onnx"""
import json
import math
import os
import random
import sys

import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ck_core as C

BASE, LEX, CK_MODEL = sys.argv[1:4]
PRIV = (os.environ.get('TK_PRIVATE') or sys.exit('TK_PRIVATE is not set: source tools/dev/env.sh first'))
torch.set_num_threads(12)
root, vocab = C.load_lexicon(LEX)
GRID = {}
for r, row in enumerate(['qwertyuiop', 'asdfghjkl', 'zxcvbnm']):
    for c, ch in enumerate(row):
        GRID[ch] = ((c + (1 if r == 2 else 0) + .5) / 10, (r + .5) / 3)


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


corpus = []
for line in open(os.path.join(PRIV, 'corpus.jsonl'), encoding='utf-8'):
    e = json.loads(line)
    w = C.key(e['expected'])
    if e['first'] and e['set'] in ('prompted', 'hand', 'recorder') and w.isalpha() and w in vocab:
        corpus.append((w, np.array(e['x']), np.array(e['y']), np.array(e['t'], dtype=float), e['set'], int(e['slot'])))
held = [c for c in corpus if c[5] % 3 == 2]
tune_all = [c for c in corpus if c[5] % 3 != 2]
print('tuning strokes', len(tune_all), 'held-back', len(held), flush=True)
feat = {id(c): C.featurize(np.clip(c[1], 0, 1), np.clip(c[2], 0, 1), c[3])[0] for c in corpus}


def rank(net, c):
    net.eval()
    with torch.no_grad():
        lp = net(torch.tensor(feat[id(c)])[None]).squeeze(1).numpy()
    best = C.decode(lp, root)
    order = sorted(best, key=lambda w: -(best[w] / max(len(w), 1) ** C.GAMMA + C.BETA * len(w) + C.LAMBDA * math.log(vocab[w])))[:80]
    return C.position(order, c[0])


def report(name, ranks, seen):
    out = {}
    for s in ('prompted', 'hand', 'recorder'):
        idx = [i for i, c in enumerate(held) if c[4] == s]
        out[s] = C.tally([ranks[i] for i in idx])
    never = [i for i, c in enumerate(held) if c[0] not in seen]
    known = [i for i, c in enumerate(held) if c[0] in seen]
    out['all_held'] = C.tally(ranks)
    if never:
        out['words_never_in_fine_tuning'] = C.tally([ranks[i] for i in never])
    if known:
        out['words_in_fine_tuning'] = C.tally([ranks[i] for i in known])
    print(f'{name:<44}', json.dumps(out), flush=True)
    return out


def finetune(base, items, seed):
    net = Net()
    net.load_state_dict(torch.load(os.path.join(PRIV, 'pilot', base + '.pt')))
    rng = random.Random(seed)
    opt = torch.optim.AdamW(net.parameters(), lr=4e-4, weight_decay=1e-4)
    steps = 150
    sched = torch.optim.lr_scheduler.OneCycleLR(opt, max_lr=4e-4, total_steps=steps + 2, pct_start=.15)
    net.train()
    for step in range(steps):
        batch = [items[rng.randrange(len(items))] for _ in range(64)]
        fs, tg, ln = [], [], []
        for c in batch:
            dx, dy = rng.gauss(0, .012), rng.gauss(0, .02)
            f = feat[id(c)].copy()
            f[0] = np.clip(f[0] + dx, 0, 1)
            f[1] = np.clip(f[1] + dy, 0, 1)
            fs.append(f)
            ids = [C.INDEX[ch] for ch in c[0]]
            tg += ids
            ln.append(len(ids))
        f = torch.tensor(np.stack(fs))
        lp = net(f)
        loss = F.ctc_loss(lp, torch.tensor(tg), torch.full((f.shape[0],), lp.shape[0], dtype=torch.long), torch.tensor(ln), blank=26, zero_infinity=True)
        opt.zero_grad()
        loss.backward()
        torch.nn.utils.clip_grad_norm_(net.parameters(), 5)
        opt.step()
        sched.step()
    return net


out = {}
sess = C.session(CK_MODEL)
keys, mask = C.layout_arrays(GRID)
ck_ranks = [C.position(C.recognise(sess, root, vocab, np.clip(c[1], 0, 1), np.clip(c[2], 0, 1), c[3], keys, mask), c[0]) for c in held]
seen_all = {c[0] for c in tune_all}
out['cleverkeys'] = report('CleverKeys (phone-trained), held-back', ck_ranks, seen_all)
for base in ('clean', 'titan-damage'):
    net = Net()
    net.load_state_dict(torch.load(os.path.join(PRIV, 'pilot', base + '.pt')))
    out[base + ' no fine-tune'] = report(f'{base}, no fine-tuning', [rank(net, c) for c in held], seen_all)
    for n in (100, 200, 400, len(tune_all)):
        pooled = None
        for seed in (1, 2):
            rng = random.Random(seed * 100)
            items = list(tune_all)
            rng.shuffle(items)
            items = items[:n]
            net = finetune(base, items, seed)
            ranks = [rank(net, c) for c in held]
            pooled = ranks if pooled is None else pooled + ranks
        doubled_held = len(pooled) // len(held)
        out[f'{base} ft{n}'] = {}
        for s in ('prompted', 'hand', 'recorder'):
            idx = [i for i in range(len(pooled)) if held[i % len(held)][4] == s]
            out[f'{base} ft{n}'][s] = C.tally([pooled[i] for i in idx])
        seen = seen_all if n == len(tune_all) else None
        if seen is not None:
            never = [i for i in range(len(pooled)) if held[i % len(held)][0] not in seen]
            known = [i for i in range(len(pooled)) if held[i % len(held)][0] in seen]
            out[f'{base} ft{n}']['words_never_in_fine_tuning'] = C.tally([pooled[i] for i in never])
            out[f'{base} ft{n}']['words_in_fine_tuning'] = C.tally([pooled[i] for i in known])
        print(f'{base} fine-tuned on {n} strokes (2 seeds pooled):', json.dumps(out[f'{base} ft{n}']), flush=True)
json.dump(out, open(os.path.join(BASE, 'report', 'pilot-finetune.json'), 'w'), indent=1)
print('done')
