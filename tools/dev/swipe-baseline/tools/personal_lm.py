#!/usr/bin/env python3
"""The personal predictor test, on this PC only. Fine-tunes the predictor lightly on the owner's earliest 60% of messages
(by time), then judges next-word suggestions on everyday text written after them. Variants: the pre-trained model, the
SODA conversation model, and each with the light personal fine-tune. Personal models must never go into a public build.
Usage: personal_lm.py baseline-dir english.combined soda-dir out-dir"""
import json
import os
import random
import re
import subprocess
import sys

import numpy as np
import onnxruntime as ort
import torch
from transformers import AutoModelForCausalLM, AutoTokenizer

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ck_core as C

BASE, LEX, SODA, OUT = sys.argv[1:5]
V = os.path.join((os.environ.get('TK_PRIVATE') or sys.exit('TK_PRIVATE is not set: source tools/dev/env.sh first')), 'v9')
os.makedirs(OUT, exist_ok=True)
torch.set_num_threads(12)
messages = [json.loads(l) for l in open(os.path.join(V, 'messages.jsonl'), encoding='utf-8')]
cut = messages[int(len(messages) * .6)]['wall']
early = [m['text'] for m in messages if m['wall'] < cut]
print('messages for fine-tuning', len(early), 'words', sum(len(t.split()) for t in early), flush=True)
json.dump({'cut': cut}, open(os.path.join(OUT, 'cut.json'), 'w'))


def finetune(start, name, epochs=3, lr=3e-5):
    path = os.path.join(OUT, name)
    if os.path.exists(os.path.join(path, 'model.safetensors')):
        return path
    tok = AutoTokenizer.from_pretrained(start)
    model = AutoModelForCausalLM.from_pretrained(start)
    model.train()
    opt = torch.optim.AdamW(model.parameters(), lr=lr, weight_decay=.01)
    rng = random.Random(3)
    for epoch in range(epochs):
        order = early[:]
        rng.shuffle(order)
        for i in range(0, len(order), 8):
            rows = [[tok.eos_token_id] + tok(t, add_special_tokens=False)['input_ids'][:127] for t in order[i:i + 8]]
            width = max(len(r) for r in rows)
            ids = torch.full((len(rows), width), tok.eos_token_id)
            labels = torch.full((len(rows), width), -100)
            for j, r in enumerate(rows):
                ids[j, :len(r)] = torch.tensor(r)
                labels[j, :len(r)] = torch.tensor(r)
            loss = model(input_ids=ids, labels=labels).loss
            opt.zero_grad()
            loss.backward()
            opt.step()
        print(f'  {name} epoch {epoch + 1} loss {loss.item():.3f}', flush=True)
    model.save_pretrained(path)
    tok.save_pretrained(path)
    return path


variants = {'pre-trained': 'distilgpt2', 'soda-8000': SODA}
variants['personal'] = finetune('distilgpt2', 'personal')
variants['soda-8000 + personal'] = finetune(SODA, 'soda-personal')
exported = {}
for name, src in variants.items():
    dest = os.path.join(OUT, 'onnx-' + re.sub(r'[^a-z0-9]+', '-', name))
    if not os.path.exists(os.path.join(dest, 'model-int8pc.onnx')):
        subprocess.run([sys.executable, '-I', '-W', 'ignore', os.path.join(os.path.dirname(os.path.abspath(__file__)), 'export_lm.py'), src, dest],
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    exported[name] = dest
print(json.dumps(exported, indent=1), flush=True)

# ---- next-word suggestions on everyday text written after the fine-tuning messages -----------------------------------
_, vocab = C.load_lexicon(LEX)
wall = {json.loads(l)['id']: json.loads(l)['wall'] for l in open(os.path.join(V, 'attempts.jsonl'), encoding='utf-8')}
late = []
for line in open(os.path.join(V, 'corpus.jsonl'), encoding='utf-8'):
    e = json.loads(line)
    if e['first'] and e['set'] == 'hand' and wall.get(int(e['id'].split('-')[1]), 0) >= cut:
        late.append(e)
print('later everyday words', len(late), flush=True)
report = {}
for name, dest in exported.items():
    tok = AutoTokenizer.from_pretrained(dest)
    sess = ort.InferenceSession(os.path.join(dest, 'model-int8pc.onnx'), providers=['CPUExecutionProvider'])
    start = tok.eos_token_id
    toks = tok.convert_ids_to_tokens(list(range(len(tok))))
    ids_w = [(i, C.key(t[1:])) for i, t in enumerate(toks) if t and t[0] == 'Ġ' and re.fullmatch(r"[A-Za-z]+('[a-z]+)?", t[1:]) and C.key(t[1:]) in vocab]
    word_ids = np.array([i for i, _ in ids_w])
    word_of = [w for _, w in ids_w]
    ranks = []
    for e in late:
        context = e['context'][-500:].rstrip(' ')
        ids = tok(context, add_special_tokens=False)['input_ids'] if context.strip() else []
        ids = ids[-32:] or [start]
        logp = sess.run(None, {'input_ids': np.array([ids], dtype=np.int64)})[0][0][word_ids]
        order = []
        for j in np.argsort(-logp, kind='stable'):
            if word_of[j] not in order:
                order.append(word_of[j])
            if len(order) >= 20:
                break
        want = C.key(e['expected'])
        ranks.append(order.index(want) + 1 if want in order else 99)
    ranks = np.array(ranks)
    report[name] = {k: round(100 * float((ranks <= k).mean()), 1) for k in (1, 3, 8)}
    print(f'{name:<24} next word in top 1 / 3 / 8: {report[name][1]} / {report[name][3]} / {report[name][8]}  (n={len(late)})', flush=True)
json.dump(report, open(os.path.join(BASE, 'report', 'personal-lm.json'), 'w'), indent=1)
