"""Does more of your data, or a longer fine-tune, still help CleverKeys? Then trains the chosen setting on every stroke and
exports it for the phone.
1. Learning curve: fine-tune on 25, 50, 75 and 100% of the tuning strokes (two seeds each), and 100% with longer runs.
   Judged on the held-back first tries (slot % 3 == 2), split by set and by words never seen in any tuning stroke.
2. Picks the setting with the best held-back first choice (top 3 breaks ties).
3. Cross-fits that setting (three models, each blind to the third it reads) so the ranker trains on honest candidates.
4. Fine-tunes that setting on every stroke and exports it to ck-titan.onnx, checked against the PyTorch model.
Usage: python ck_curve.py (in the swipe-training folder). Writes ck-curve.json, ck-crossfit2.jsonl, ck-titan.onnx."""
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
for line in open('titan-strokes.jsonl', encoding='utf-8'):
    s = json.loads(line)
    s['w'] = C.key(s['expected'])
    s['f'] = featurize(s['x'], s['y'], s['t'])
    strokes.append(s)
usable = [s for s in strokes if s['w'].isalpha() and s['w'] in vocab]
held = [s for s in usable if s['first'] and int(s['slot']) % 3 == 2]
tune = [s for s in usable if int(s['slot']) % 3 != 2]
seen = {s['w'] for s in tune}
t0 = time.time()


def log(*parts):
    line = time.strftime('%H:%M:%S ') + ' '.join(str(p) for p in parts)
    print(line, flush=True)
    with open('ck-curve.log', 'a', encoding='utf-8') as f:
        f.write(line + '\n')


log('strokes', len(strokes), 'usable', len(usable), 'held-back first tries', len(held), 'tuning', len(tune))


class Net(torch.nn.Module):
    """The converted CleverKeys graph with only the letter emissions, as the phone will use it."""

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


def judge(model, name):
    ranks = [C.position([w for _, w, _ in candidates(model, s)], s['w']) for s in held]
    out = {}
    for label, pick in (('prompted', lambda s: s['set'] == 'prompted'), ('hand', lambda s: s['set'] == 'hand'), ('recorder', lambda s: s['set'] == 'recorder'),
                        ('all', lambda s: True), ('never seen in tuning', lambda s: s['w'] not in seen), ('seen in tuning', lambda s: s['w'] in seen)):
        r = [ranks[i] for i, s in enumerate(held) if pick(s)]
        if r:
            out[label] = C.tally(r)
    log(f'{name:<34}', ' | '.join(f"{k} {v['first']}/{v['top3']} (n={v['n']})" for k, v in out.items()), f'({round(time.time() - t0)} s)')
    return out


results = {}
# ---- 1. learning curve ------------------------------------------------------------------------------------------------------
for fraction in (.25, .5, .75, 1.0):
    for seed in (1, 2):
        part = random.Random(100 + seed).sample(tune, int(len(tune) * fraction))
        results[f'{int(fraction * 100)}% of strokes, 250 steps, seed {seed}'] = judge(finetune(part, seed, 250), f'{int(fraction * 100)}% x250 seed {seed}')
for steps in (500, 1000):
    for seed in (1, 2):
        results[f'100% of strokes, {steps} steps, seed {seed}'] = judge(finetune(tune, seed, steps), f'100% x{steps} seed {seed}')
json.dump(results, open('ck-curve.json', 'w'), indent=1)

# ---- 2. choose ------------------------------------------------------------------------------------------------------------
options = {}
for steps in (250, 500, 1000):
    runs = [v['all'] for k, v in results.items() if k.startswith(f'100% of strokes, {steps} steps')]
    options[steps] = (np.mean([r['first'] for r in runs]), np.mean([r['top3'] for r in runs]))
STEPS = max(options, key=lambda k: options[k])
log('chosen steps', STEPS, 'held-back first/top 3 by steps', {k: tuple(round(x, 1) for x in v) for k, v in options.items()})
results['chosen steps'] = STEPS
json.dump(results, open('ck-curve.json', 'w'), indent=1)

# ---- 3. cross-fit with the chosen setting ----------------------------------------------------------------------------------
with open('ck-crossfit2.jsonl', 'w', encoding='utf-8') as out:
    for fold in range(3):
        model = finetune([s for s in usable if int(s['slot']) % 3 != fold], 1, STEPS)
        for s in strokes:
            if int(s['slot']) % 3 == fold:
                out.write(json.dumps({'id': s['id'], 'c': [[w, round(p, 3), round(f, 3)] for f, w, p in candidates(model, s)]}) + '\n')
        log('cross-fit fold', fold, 'done')

# ---- 4. final model on every stroke, exported ---------------------------------------------------------------------------
final = finetune(usable, 1, STEPS)
torch.save(final.state_dict(), 'ck-titan.pt')
net = final.net.cpu().eval()
f0 = torch.tensor(held[0]['f'])[None]
k0, m0 = torch.tensor(keys_np), torch.tensor(mask_np)
torch.onnx.export(net, (f0, k0, m0), 'ck-titan.onnx', input_names=['features', 'layout_keys', 'layout_mask'], output_names=['log_emissions'],
                  opset_version=17, dynamo=False)
sess = ort.InferenceSession('ck-titan.onnx', providers=['CPUExecutionProvider'])
worst = 0.0
with torch.no_grad():
    for s in held[:40]:
        a = sess.run(['log_emissions'], {'features': s['f'][None], 'layout_keys': keys_np, 'layout_mask': mask_np})[0]
        b = net(torch.tensor(s['f'])[None], k0, m0).numpy()
        worst = max(worst, float(np.abs(a - b).max()))
log('exported ck-titan.onnx', round(os.path.getsize('ck-titan.onnx') / 1e6, 1), 'MB, largest difference from PyTorch', round(worst, 6))
results['export difference'] = worst
json.dump(results, open('ck-curve.json', 'w'), indent=1)
log('all done')
