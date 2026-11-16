#!/usr/bin/env python3
"""Next-word suggestions before any letter: how often the right word is in the top 1, 3 and 8, with the phone's current
model (nextrank-small.jsonl) and with the phone-sized predictor as the app runs it (8-bit ONNX, last 32 tokens, whole-word
tokens that are in the dictionary). Also a mix: the predictor's list with the current model's words filling gaps.
Usage: next_phone.py baseline-dir english.combined lm-dir [model-file]"""
import json
import os
import re
import sys

import numpy as np
import onnxruntime as ort
from transformers import AutoTokenizer

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ck_core as C

BASE, LEX, LM_DIR = sys.argv[1:4]
MODEL = sys.argv[4] if len(sys.argv) > 4 else 'model-int8pc.onnx'
CAP = int(os.environ.get('LM_CAP', '32'))
P = (os.environ.get('TK_PRIVATE') or sys.exit('TK_PRIVATE is not set: source tools/dev/env.sh first'))
_, vocab = C.load_lexicon(LEX)
corpus, small = {}, {}
for line in open(os.path.join(P, 'corpus.jsonl'), encoding='utf-8'):
    e = json.loads(line)
    if e['first'] and e['set'] in ('prompted', 'hand'):
        corpus[e['id']] = e
for line in open(os.path.join(P, 'nextrank-small.jsonl'), encoding='utf-8'):
    e = json.loads(line)
    small[e['id']] = e
tok = AutoTokenizer.from_pretrained(LM_DIR)
sess = ort.InferenceSession(os.path.join(LM_DIR, MODEL), providers=['CPUExecutionProvider'])
start = int(re.search(r'start_token=(\d+)', open(os.path.join(LM_DIR, 'lm-info.txt')).read()).group(1))
vocab_tokens = tok.convert_ids_to_tokens(list(range(len(tok))))
word_ids, word_of = [], []
for i, t in enumerate(vocab_tokens):
    if t and t[0] == 'Ġ' and re.fullmatch(r"[A-Za-z]+('[a-z]+)?", t[1:]) and C.key(t[1:]) in vocab:
        word_ids.append(i)
        word_of.append(C.key(t[1:]))
word_ids = np.array(word_ids)
rows = {}
for n, (cid, e) in enumerate(corpus.items()):
    if cid not in small:
        continue
    context = e['context'][-500:].rstrip(' ')
    ids = tok(context, add_special_tokens=False)['input_ids'] if context.strip() else []
    ids = ids[-CAP:] or [start]
    logp = sess.run(None, {'input_ids': np.array([ids], dtype=np.int64)})[0][0][word_ids]
    order = []
    for j in np.argsort(-logp, kind='stable'):
        if word_of[j] not in order:
            order.append(word_of[j])
        if len(order) >= 50:
            break
    rows[cid] = order
expected = {cid: C.key(corpus[cid]['expected']) for cid in rows}


def rate(rank_of, ids, k):
    return round(100 * sum(1 <= rank_of(i) <= k for i in ids) / len(ids), 1)


out = {}
for s in ('prompted', 'hand'):
    ids = [i for i in rows if corpus[i]['set'] == s]
    lm_rank = lambda i: rows[i].index(expected[i]) + 1 if expected[i] in rows[i] else -1
    out[s] = {'n': len(ids)}
    for k in (1, 3, 8):
        out[s][f'phone model top {k}'] = rate(lambda i: small[i]['rank'], ids, k)
        out[s][f'predictor top {k}'] = rate(lm_rank, ids, k)
    print(s, out[s], flush=True)
json.dump(out, open(os.path.join(BASE, 'report', 'next-word-phone.json'), 'w'), indent=1)
