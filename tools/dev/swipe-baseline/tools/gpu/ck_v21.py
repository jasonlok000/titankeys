"""Engine 2.1 (#31) encoder twin on the 3090. Uses titan-strokes-v21.jsonl (strokes and words only), whose 'part' is
train, held (engine 2.0's frozen 540) or test (the later half of the new log). The held and test strokes are never
trained on here.
1. Picks the fine-tune length (250 or 400 steps) with two folds on the train part; both folds must agree, else 250.
2. Cross-fits the train part (3 folds) so the ranker trains on honest candidates.
3. Fine-tunes on the whole train part and reads held and test with it; exports it as ck-titan-v21-twin.onnx.
Usage: python ck_v21.py [with-held]. Writes ck-v21.log, ck-v21-candidates.jsonl, ck-titan-v21-twin.onnx.
with-held trains on the held part too (as a release would) and writes the same files with -wh, for the clean test only."""
import json
import math
import os
import random
import sys
import time

import numpy as np
import onnx
import onnxruntime as ort
import torch
import torch.nn.functional as F
from onnx2torch import convert

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ck_core as C
from swipe_model import featurize, titan_grid

DEV = torch.device('cuda')
root, vocab = C.load_lexicon('english.combined')
keys_np, mask_np = C.layout_arrays(titan_grid())
KEYS, MASK = torch.tensor(keys_np, device=DEV), torch.tensor(mask_np, device=DEV)
strokes = []
for line in open('titan-strokes-v21.jsonl', encoding='utf-8'):
    s = json.loads(line)
    s['w'] = C.key(s['expected'])
    s['f'] = featurize(s['x'], s['y'], s['t'])
    strokes.append(s)
usable = [s for s in strokes if s['w'].isalpha() and s['w'] in vocab]
WITH_HELD = len(sys.argv) > 1 and sys.argv[1] == 'with-held'
TAG = '-wh' if WITH_HELD else ''
TRAIN_PARTS = ('train', 'held') if WITH_HELD else ('train',)
train = [s for s in usable if s['part'] in TRAIN_PARTS]
t0 = time.time()


def log(*parts):
    line = time.strftime('%H:%M:%S ') + ' '.join(str(p) for p in parts)
    print(line, flush=True)
    with open(f'ck-v21{TAG}.log', 'a', encoding='utf-8') as f:
        f.write(line + '\n')


class Net(torch.nn.Module):
    def __init__(self):
        super().__init__()
        self.net = convert(onnx.load('ck-model-fp32.onnx'))

    def forward(self, features, layout_keys, layout_mask):
        return self.net(features, layout_keys, layout_mask)[0]


class CK(torch.nn.Module):
    def __init__(self):
        super().__init__()
        self.net = Net()

    def forward(self, f):
        le = self.net(f, KEYS, MASK)
        return torch.log_softmax(torch.cat([le[..., :26], le[..., 64:65]], -1), -1).permute(1, 0, 2)


def finetune(items, seed, steps, lr=1e-4, batch=32):
    model = CK().to(DEV)
    rng = random.Random(seed)
    opt = torch.optim.AdamW(model.parameters(), lr=lr, weight_decay=1e-4)
    sched = torch.optim.lr_scheduler.OneCycleLR(opt, max_lr=lr, total_steps=steps + 2, pct_start=.15)
    model.train()
    for _ in range(steps):
        opt.zero_grad()
        for s in [items[rng.randrange(len(items))] for _ in range(batch)]:
            f = torch.tensor(s['f'], device=DEV)
            f = (f + torch.tensor([[rng.gauss(0, .012)], [rng.gauss(0, .02)]], device=DEV)).clamp(0, 1)
            lp = model(f[None])
            ids = torch.tensor([C.INDEX[c] for c in s['w']], device=DEV)
            (F.ctc_loss(lp, ids[None], torch.tensor([lp.shape[0]]), torch.tensor([len(ids)]), blank=26, zero_infinity=True) / batch).backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), 5)
        opt.step()
        sched.step()
    return model.eval()


def candidates(model, s):
    with torch.no_grad():
        lp = model(torch.tensor(s['f'], device=DEV)[None])[:, 0].float().cpu().numpy()
    best = C.decode(lp, root)
    return sorted(((best[w] / max(len(w), 1) ** C.GAMMA + C.BETA * len(w) + C.LAMBDA * math.log(vocab[w]), w, best[w]) for w in best), reverse=True)[:80]


def first_rate(model, items):
    hits = [C.position([w for _, w, _ in candidates(model, s)], s['w']) for s in items if s['first']]
    return 100 * sum(h == 1 for h in hits) / len(hits), 100 * sum(1 <= h <= 3 for h in hits) / len(hits)


log('strokes', len(strokes), 'usable', len(usable), 'train', len(train))
# ---- 1. length, by two folds on the train part -----------------------------------------------------------------------
halves = [[s for s in train if int(s['slot']) % 2 == k] for k in (0, 1)]
votes = [250, 250] if WITH_HELD else []  # picked on the train part alone in the first run
for k in (() if WITH_HELD else (0, 1)):
    fit, judge = halves[1 - k], halves[k]
    scores = {steps: first_rate(finetune(fit, 1, steps), judge) for steps in (250, 400)}
    log('fold', k, {s: tuple(round(v, 1) for v in r) for s, r in scores.items()})
    votes.append(max(scores, key=lambda s: scores[s]))
STEPS = votes[0] if votes[0] == votes[1] else 250
log('chosen steps', STEPS, 'votes', votes)
# ---- 2. cross-fit the train part, 3. the twin for held and test -------------------------------------------------------
out = open(f'ck-v21{TAG}-candidates.jsonl', 'w', encoding='utf-8')
for fold in range(3):
    model = finetune([s for s in train if int(s['slot']) % 3 != fold], 1, STEPS)
    for s in strokes:
        if s['part'] in TRAIN_PARTS and int(s['slot']) % 3 == fold:
            out.write(json.dumps({'id': s['id'], 'c': [[w, round(p, 3), round(f, 3)] for f, w, p in candidates(model, s)]}) + '\n')
    log('cross-fit fold', fold, 'done')
twin = finetune(train, 1, STEPS)
for s in strokes:
    if s['part'] not in TRAIN_PARTS:
        out.write(json.dumps({'id': s['id'], 'c': [[w, round(p, 3), round(f, 3)] for f, w, p in candidates(twin, s)]}) + '\n')
out.close()
for part in ('test',) if WITH_HELD else ('held', 'test'):
    log('twin alone on', part, tuple(round(v, 1) for v in first_rate(twin, [s for s in usable if s['part'] == part])))
torch.save(twin.state_dict(), f'ck-titan-v21{TAG}-twin.pt')
net = twin.net.cpu().eval()
f0 = torch.tensor(train[0]['f'])[None]
torch.onnx.export(net, (f0, torch.tensor(keys_np), torch.tensor(mask_np)), f'ck-titan-v21{TAG}-twin.onnx',
                  input_names=['features', 'layout_keys', 'layout_mask'], output_names=['log_emissions'], opset_version=17, dynamo=False)
log(f'exported ck-titan-v21{TAG}-twin.onnx; all done in', round(time.time() - t0), 's')
