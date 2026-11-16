"""Fine-tuning test on the GPU. Does fine-tuning CleverKeys' pre-trained encoder on Titan strokes beat the pilot model,
on held-back strokes and on words the fine-tuning never saw? Usage: python ck_finetune.py (run in the swipe-training folder)"""
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
from swipe_model import Net, featurize, titan_grid

DEV = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
print('device', DEV, torch.cuda.get_device_name(0) if DEV.type == 'cuda' else '', flush=True)
root, vocab = C.load_lexicon('english.combined')
GRID = titan_grid()
keys_np, mask_np = C.layout_arrays(GRID)
KEYS = torch.tensor(keys_np, device=DEV)
MASK = torch.tensor(mask_np, device=DEV)

strokes = []
for line in open('titan-strokes.jsonl', encoding='utf-8'):
    s = json.loads(line)
    w = C.key(s['expected'])
    if w.isalpha() and w in vocab:
        s['w'] = w
        s['f'] = featurize(s['x'], s['y'], s['t'])
        strokes.append(s)
held = [s for s in strokes if s['first'] and int(s['slot']) % 3 == 2]
tune = [s for s in strokes if int(s['slot']) % 3 != 2]
seen = {s['w'] for s in tune}
print('strokes', len(strokes), 'held-back first tries', len(held), 'tuning strokes', len(tune), flush=True)


def order(lp):
    best = C.decode(lp, root)
    return sorted(best, key=lambda w: -(best[w] / max(len(w), 1) ** C.GAMMA + C.BETA * len(w) + C.LAMBDA * math.log(vocab[w])))[:80]


def report(name, ranks):
    out = {}
    for label, pick in (('prompted', lambda s: s['set'] == 'prompted'), ('hand', lambda s: s['set'] == 'hand'), ('recorder', lambda s: s['set'] == 'recorder'),
                        ('all', lambda s: True), ('never seen in tuning', lambda s: s['w'] not in seen), ('seen in tuning', lambda s: s['w'] in seen)):
        r = [ranks[i] for i, s in enumerate(held) if pick(s)]
        if r:
            out[label] = C.tally(r)
    print(f'{name:<48}', ' | '.join(f"{k} {v['first']}/{v['top3']} (n={v['n']})" for k, v in out.items()), flush=True)
    return out


class CK(torch.nn.Module):
    def __init__(self, path):
        super().__init__()
        self.net = convert(onnx.load(path))

    def forward(self, f):
        outputs = self.net(f, KEYS, MASK)
        le = outputs[0] if isinstance(outputs, (tuple, list)) else outputs
        letters = torch.cat([le[..., :26], le[..., 64:65]], -1)
        return torch.log_softmax(letters, -1).permute(1, 0, 2)


def ck_lp(model, s):
    with torch.no_grad():
        return model(torch.tensor(s['f'], device=DEV)[None])[:, 0].float().cpu().numpy()


def net_lp(model, s):
    with torch.no_grad():
        return model(torch.tensor(s['f'], device=DEV)[None]).squeeze(1).float().cpu().numpy()


def evaluate(model, name, fn):
    model.eval()
    return report(name, [C.position(order(fn(model, s)), s['w']) for s in held])


def finetune(model, items, steps, lr, batch, seed, single):
    rng = random.Random(seed)
    model.train()
    opt = torch.optim.AdamW(model.parameters(), lr=lr, weight_decay=1e-4)
    sched = torch.optim.lr_scheduler.OneCycleLR(opt, max_lr=lr, total_steps=steps + 2, pct_start=.15)
    for step in range(steps):
        group = [items[rng.randrange(len(items))] for _ in range(batch)]
        opt.zero_grad()
        if single:
            # The exported graph takes one stroke at a time, so a batch is built from several passes.
            for s in group:
                f = torch.tensor(s['f'], device=DEV)
                f = (f + torch.tensor([[rng.gauss(0, .012)], [rng.gauss(0, .02)]], device=DEV)).clamp(0, 1)
                lp = model(f[None])
                ids = torch.tensor([C.INDEX[c] for c in s['w']], device=DEV)
                loss = F.ctc_loss(lp, ids[None], torch.tensor([lp.shape[0]]), torch.tensor([len(ids)]), blank=26, zero_infinity=True) / batch
                loss.backward()
        else:
            f = torch.tensor(np.stack([s['f'] for s in group]), device=DEV)
            f = (f + torch.tensor([[[rng.gauss(0, .012)], [rng.gauss(0, .02)]] for _ in group], device=DEV)).clamp(0, 1)
            lp = model(f)
            ids = [C.INDEX[c] for s in group for c in s['w']]
            loss = F.ctc_loss(lp, torch.tensor(ids, device=DEV), torch.full((len(group),), lp.shape[0], dtype=torch.long), torch.tensor([len(s['w']) for s in group]), blank=26, zero_infinity=True)
            loss.backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), 5)
        opt.step()
        sched.step()
    model.eval()
    return model


results = {}
t0 = time.time()
# The converted model must match the original before any result counts.
sess = ort.InferenceSession('ck-model.onnx', providers=['CPUExecutionProvider'])
ck = CK('ck-model-fp32.onnx').to(DEV).eval()
worst = 0.0
for s in held[:20]:
    a = sess.run(['log_emissions'], {'features': s['f'][None], 'layout_keys': keys_np, 'layout_mask': mask_np})[0][0]
    a = np.concatenate([a[:, :26], a[:, [64]]], 1)
    a = a - np.log(np.exp(a).sum(1, keepdims=True))
    worst = max(worst, float(np.abs(a - ck_lp(ck, s)).max()))
print('converted model, largest difference from the original:', round(worst, 5), flush=True)
results['conversion_max_difference'] = worst

results['cleverkeys as shipped'] = evaluate(ck, 'CleverKeys as shipped', ck_lp)
pilot = Net().to(DEV)
pilot.load_state_dict(torch.load('pilot-clean.pt', map_location=DEV))
results['pilot as trained'] = evaluate(pilot, 'pilot model as trained', net_lp)

for lr in (3e-5, 1e-4, 3e-4):
    for seed in (1,):
        model = CK('ck-model-fp32.onnx').to(DEV)
        model = finetune(model, tune, steps=250, lr=lr, batch=32, seed=seed, single=True)
        results[f'cleverkeys fine-tuned lr {lr}'] = evaluate(model, f'CleverKeys fine-tuned, rate {lr}', ck_lp)
        print('  time', round(time.time() - t0), 's', flush=True)
for lr in (4e-4, 1e-3):
    model = Net().to(DEV)
    model.load_state_dict(torch.load('pilot-clean.pt', map_location=DEV))
    model = finetune(model, tune, steps=250, lr=lr, batch=64, seed=1, single=False)
    results[f'pilot fine-tuned lr {lr}'] = evaluate(model, f'pilot fine-tuned, rate {lr}', net_lp)
json.dump(results, open('ck-finetune-results.json', 'w'), indent=1)
print('done in', round(time.time() - t0), 's', flush=True)
