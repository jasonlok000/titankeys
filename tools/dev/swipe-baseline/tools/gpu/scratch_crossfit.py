"""The from-scratch encoder's candidates for every stroke, cross-fitted like ck_crossfit.py: three fine-tunes from
scratch2/scratch-base.pt, each blind to the third of word positions it reads. Then a fine-tune on every stroke, saved and
exported to ONNX (features in, letter log chances out) in case it is chosen.
Usage: python scratch_crossfit.py (in the swipe-training folder). Writes scratch-crossfit.jsonl, scratch-titan.onnx."""
import json
import math
import os
import random
import sys

import numpy as np
import onnxruntime as ort
import torch
import torch.nn.functional as F

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ck_core as C
from swipe_model import BigNet, featurize

DEV = torch.device('cuda')
root, vocab = C.load_lexicon('english.combined')
strokes = []
for line in open('titan-strokes.jsonl', encoding='utf-8'):
    s = json.loads(line)
    s['w'] = C.key(s['expected'])
    s['f'] = featurize(s['x'], s['y'], s['t'])
    strokes.append(s)
usable = [s for s in strokes if s['w'].isalpha() and s['w'] in vocab]


def finetune(items, seed=1, steps=250, lr=1e-4):
    m = BigNet().to(DEV)
    m.load_state_dict(torch.load(os.path.join('scratch2', 'scratch-base.pt'), map_location=DEV))
    rng = random.Random(seed)
    o = torch.optim.AdamW(m.parameters(), lr=lr, weight_decay=1e-4)
    sc = torch.optim.lr_scheduler.OneCycleLR(o, max_lr=lr, total_steps=steps + 2, pct_start=.15)
    m.train()
    for _ in range(steps):
        group = [items[rng.randrange(len(items))] for _ in range(32)]
        f = torch.tensor(np.stack([s['f'] for s in group]), device=DEV)
        f = (f + torch.tensor([[[rng.gauss(0, .012)], [rng.gauss(0, .02)]] for _ in group], device=DEV)).clamp(0, 1)
        lp = m(f)
        tg = torch.tensor([C.INDEX[c] for s in group for c in s['w']], device=DEV)
        loss = F.ctc_loss(lp, tg, torch.full((len(group),), lp.shape[0], dtype=torch.long), torch.tensor([len(s['w']) for s in group]), blank=26, zero_infinity=True)
        o.zero_grad()
        loss.backward()
        torch.nn.utils.clip_grad_norm_(m.parameters(), 5)
        o.step()
        sc.step()
    return m.eval()


def candidates(m, s):
    with torch.no_grad():
        lp = m(torch.tensor(s['f'], device=DEV)[None]).squeeze(1).float().cpu().numpy().astype(np.float64)
    best = C.decode(lp, root)
    return sorted(((best[w] / max(len(w), 1) ** C.GAMMA + C.BETA * len(w) + C.LAMBDA * math.log(vocab[w]), w, best[w]) for w in best), reverse=True)[:80]


with open('scratch-crossfit.jsonl', 'w', encoding='utf-8') as out:
    for fold in range(3):
        m = finetune([s for s in usable if int(s['slot']) % 3 != fold])
        for s in strokes:
            if int(s['slot']) % 3 == fold:
                out.write(json.dumps({'id': s['id'], 'c': [[w, round(p, 3), round(f, 3)] for f, w, p in candidates(m, s)]}) + '\n')
        print('fold', fold, 'done', flush=True)

final = finetune(usable)
torch.save(final.state_dict(), 'scratch-titan.pt')
net = final.cpu().eval()
sample = torch.tensor(usable[0]['f'])[None]
torch.onnx.export(net, (sample,), 'scratch-titan.onnx', input_names=['features'], output_names=['letter_logp'], opset_version=17, dynamo=False)
sess = ort.InferenceSession('scratch-titan.onnx', providers=['CPUExecutionProvider'])
worst = 0.0
with torch.no_grad():
    for s in usable[:40]:
        a = sess.run(None, {'features': s['f'][None]})[0]
        b = net(torch.tensor(s['f'])[None]).numpy()
        worst = max(worst, float(np.abs(a - b).max()))
print('exported scratch-titan.onnx', round(os.path.getsize('scratch-titan.onnx') / 1e6, 1), 'MB, largest difference', round(worst, 6), flush=True)
print('done', flush=True)
