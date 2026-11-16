"""Fine-tuned CleverKeys candidates for every stroke, without a stroke ever being judged by a model that trained on it.
Three models, each fine-tuned without one third of the word positions, read that third.
Usage: python ck_crossfit.py (in the swipe-training folder). Writes ck-crossfit.jsonl."""
import json
import math
import os
import random
import sys

import numpy as np
import onnx
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


class CK(torch.nn.Module):
    def __init__(self):
        super().__init__()
        self.net = convert(onnx.load('ck-model-fp32.onnx'))

    def forward(self, f):
        le = self.net(f, KEYS, MASK)[0]
        return torch.log_softmax(torch.cat([le[..., :26], le[..., 64:65]], -1), -1).permute(1, 0, 2)


def finetune(items, seed=1, steps=250, lr=1e-4, batch=32):
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


out = open('ck-crossfit.jsonl', 'w', encoding='utf-8')
for fold in range(3):
    train = [s for s in strokes if int(s['slot']) % 3 != fold and s['w'].isalpha() and s['w'] in vocab]
    model = finetune(train)
    for s in strokes:
        if int(s['slot']) % 3 != fold:
            continue
        with torch.no_grad():
            lp = model(torch.tensor(s['f'], device=DEV)[None])[:, 0].float().cpu().numpy()
        best = C.decode(lp, root)
        scored = sorted(((best[w] / max(len(w), 1) ** C.GAMMA + C.BETA * len(w) + C.LAMBDA * math.log(vocab[w]), w, best[w]) for w in best), reverse=True)[:80]
        out.write(json.dumps({'id': s['id'], 'c': [[w, round(p, 3), round(f, 3)] for f, w, p in scored]}) + '\n')
    print('fold', fold, 'done', flush=True)
out.close()
print('done', flush=True)
