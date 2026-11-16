"""Overnight run: trains a swipe encoder from scratch on FUTO's million swipes, then fine-tunes it on Titan strokes the
same way as the CleverKeys test, so the two can be compared. Resumes from its checkpoint if restarted.
The first 40-epoch run overfitted from epoch 14 (validation 0.153 rising to 0.18), so the run is now shorter and the epoch
with the lowest validation loss is the one judged.
Usage: python train_scratch.py futo.npz out-dir [hours] [epochs]"""
import json
import math
import os
import random
import sys
import time

import numpy as np
import torch
import torch.nn.functional as F

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ck_core as C
from swipe_model import BigNet, augment, featurize

DATA, OUT = sys.argv[1:3]
HOURS = float(sys.argv[3]) if len(sys.argv) > 3 else 6
os.makedirs(OUT, exist_ok=True)
DEV = torch.device('cuda')
torch.backends.cuda.matmul.allow_tf32 = True
torch.backends.cudnn.allow_tf32 = True
torch.manual_seed(3)


def log(*parts):
    line = ' '.join(str(p) for p in parts)
    print(line, flush=True)
    with open(os.path.join(OUT, 'train.log'), 'a', encoding='utf-8') as f:
        f.write(time.strftime('%H:%M:%S ') + line + '\n')


npz = np.load(DATA)
features = torch.tensor(npz['features'].astype(np.float32))
words = list(npz['words'])
order = np.random.default_rng(1).permutation(len(words))
val_idx, train_idx = order[:20000], order[20000:]
targets = [torch.tensor([C.INDEX[c] for c in w], dtype=torch.long) for w in words]
features = features.to(DEV)
log('strokes', len(words), 'training', len(train_idx), 'validation', len(val_idx))

model = BigNet().to(DEV)
log('parameters', sum(p.numel() for p in model.parameters()))
BATCH, EPOCHS = 512, int(sys.argv[4]) if len(sys.argv) > 4 else 14
steps_per_epoch = len(train_idx) // BATCH
opt = torch.optim.AdamW(model.parameters(), lr=2e-3, weight_decay=1e-4)
sched = torch.optim.lr_scheduler.OneCycleLR(opt, max_lr=2e-3, total_steps=EPOCHS * steps_per_epoch + 10, pct_start=.05)
start_epoch = 0
ckpt = os.path.join(OUT, 'checkpoint.pt')
if os.path.exists(ckpt):
    state = torch.load(ckpt, map_location=DEV)
    model.load_state_dict(state['model'])
    opt.load_state_dict(state['opt'])
    sched.load_state_dict(state['sched'])
    start_epoch = state['epoch'] + 1
    log('resumed at epoch', start_epoch)
t0 = time.time()


def ctc(lp, idx):
    tg = torch.cat([targets[i] for i in idx]).to(DEV)
    lengths = torch.tensor([len(targets[i]) for i in idx], device=DEV)
    return F.ctc_loss(lp, tg, torch.full((len(idx),), lp.shape[0], dtype=torch.long, device=DEV), lengths, blank=26, zero_infinity=True)


for epoch in range(start_epoch, EPOCHS):
    model.train()
    perm = np.random.default_rng(epoch).permutation(train_idx)
    total = 0.0
    for step in range(steps_per_epoch):
        idx = perm[step * BATCH:(step + 1) * BATCH]
        f = augment(features[idx])
        loss = ctc(model(f), idx)
        opt.zero_grad(set_to_none=True)
        loss.backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), 5)
        opt.step()
        sched.step()
        total += loss.item()
    model.eval()
    with torch.no_grad():
        vl = sum(ctc(model(features[val_idx[i:i + 2048]]), val_idx[i:i + 2048]).item() for i in range(0, len(val_idx), 2048)) / math.ceil(len(val_idx) / 2048)
    best_path = os.path.join(OUT, 'best.pt')
    best_vl = torch.load(best_path, map_location='cpu')['validation'] if os.path.exists(best_path) else float('inf')
    if vl < best_vl:
        torch.save({'model': model.state_dict(), 'validation': vl, 'epoch': epoch}, best_path)
    torch.save({'model': model.state_dict(), 'opt': opt.state_dict(), 'sched': sched.state_dict(), 'epoch': epoch}, ckpt)
    log(f'epoch {epoch + 1}/{EPOCHS} train {total / steps_per_epoch:.4f} validation {vl:.4f} ({(time.time() - t0) / 60:.1f} min)')
    if time.time() - t0 > HOURS * 3600:
        log('stopping at the time limit')
        break
best = torch.load(os.path.join(OUT, 'best.pt'), map_location=DEV)
model.load_state_dict(best['model'])
log('judging epoch', best['epoch'] + 1, 'validation', round(best['validation'], 4))
torch.save(model.state_dict(), os.path.join(OUT, 'scratch-base.pt'))

# ---- judge it the same way as the CleverKeys test ----------------------------------------------------------------------
root, vocab = C.load_lexicon('english.combined')


def order_words(lp):
    best = C.decode(lp, root)
    return sorted(best, key=lambda w: -(best[w] / max(len(w), 1) ** C.GAMMA + C.BETA * len(w) + C.LAMBDA * math.log(vocab[w])))[:80]


def lp_of(m, f):
    with torch.no_grad():
        return m(torch.tensor(f, device=DEV)[None]).squeeze(1).float().cpu().numpy()


results = {}
sample = [i for i in val_idx[:1500] if words[i] in vocab]
ranks = [C.position(order_words(lp_of(model, npz['features'][i].astype(np.float32))), words[i]) for i in sample]
results['futo validation'] = C.tally(ranks)
log('FUTO phone strokes, held out:', results['futo validation'])

strokes = []
for line in open('titan-strokes.jsonl', encoding='utf-8'):
    s = json.loads(line)
    w = C.key(s['expected'])
    if w.isalpha() and w in vocab:
        s['w'], s['f'] = w, featurize(s['x'], s['y'], s['t'])
        strokes.append(s)
held = [s for s in strokes if s['first'] and int(s['slot']) % 3 == 2]
tune = [s for s in strokes if int(s['slot']) % 3 != 2]
seen = {s['w'] for s in tune}


def judge(m, name):
    m.eval()
    ranks = [C.position(order_words(lp_of(m, s['f'])), s['w']) for s in held]
    out = {}
    for label, pick in (('prompted', lambda s: s['set'] == 'prompted'), ('hand', lambda s: s['set'] == 'hand'), ('recorder', lambda s: s['set'] == 'recorder'),
                        ('all', lambda s: True), ('never seen in tuning', lambda s: s['w'] not in seen), ('seen in tuning', lambda s: s['w'] in seen)):
        r = [ranks[i] for i, s in enumerate(held) if pick(s)]
        if r:
            out[label] = C.tally(r)
    log(name, ' | '.join(f"{k} {v['first']}/{v['top3']} (n={v['n']})" for k, v in out.items()))
    return out


results['scratch as trained'] = judge(model, 'from scratch, as trained:')
for lr in (3e-5, 1e-4, 3e-4):
    m = BigNet().to(DEV)
    m.load_state_dict(torch.load(os.path.join(OUT, 'scratch-base.pt'), map_location=DEV))
    rng = random.Random(1)
    o = torch.optim.AdamW(m.parameters(), lr=lr, weight_decay=1e-4)
    sc = torch.optim.lr_scheduler.OneCycleLR(o, max_lr=lr, total_steps=252, pct_start=.15)
    m.train()
    for step in range(250):
        group = [tune[rng.randrange(len(tune))] for _ in range(32)]
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
    results[f'scratch fine-tuned lr {lr}'] = judge(m, f'from scratch, fine-tuned at rate {lr}:')
json.dump(results, open(os.path.join(OUT, 'scratch-results.json'), 'w'), indent=1)
log('all done')
