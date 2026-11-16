#!/usr/bin/env python3
"""How often next-word prediction alone could give the word, with the phone's small model and a much stronger one.
Runs on this computer only. Usage: next_strong.py baseline-dir english.combined"""
import json
import os
import re
import sys

import torch
from transformers import AutoModelForCausalLM, AutoTokenizer

BASE, LEX = sys.argv[1:3]
P = (os.environ.get('TK_PRIVATE') or sys.exit('TK_PRIVATE is not set: source tools/dev/env.sh first'))
SP = {'neighbour': 'neighbor', 'tyres': 'tires', 'favourite': 'favorite', 'colour': 'color', 'grey': 'gray'}
ROWS = ['qwertyuiop', 'asdfghjkl', 'zxcvbnm']
KEYS = {}
for r, row in enumerate(ROWS):
    for c, ch in enumerate(row):
        KEYS[ch] = ((c + (1 if r == 2 else 0) + .5) / 10, (r + .5) / 3)


def key(w):
    w = w.lower().replace('’', "'").strip("'")
    w = SP.get(w, w)
    return w.replace("'", '')


def near(x, y, ch):
    if ch not in KEYS:
        return False
    return ((x - KEYS[ch][0]) * 10) ** 2 + ((y - KEYS[ch][1]) * 3) ** 2 <= 1.6 ** 2


corpus, replay, small = {}, {}, {}
for line in open(os.path.join(P, 'corpus.jsonl'), encoding='utf-8'):
    e = json.loads(line)
    if e['first'] and e['set'] in ('prompted', 'hand'):
        corpus[e['id']] = e
for line in open(os.path.join(P, 'replay-4f75dcb-full.jsonl'), encoding='utf-8'):
    e = json.loads(line)
    replay[e['id']] = e
for line in open(os.path.join(P, 'nextrank-small.jsonl'), encoding='utf-8'):
    e = json.loads(line)
    small[e['id']] = e

torch.set_num_threads(12)
name = 'gpt2-medium'
tok = AutoTokenizer.from_pretrained(name)
model = AutoModelForCausalLM.from_pretrained(name)
model.eval()
print('model loaded', sum(p.numel() for p in model.parameters()) if False else '', flush=True)
token_word = {}
for i in range(len(tok)):
    text = tok.decode([i])
    if text.startswith(' ') and text[1:].isalpha():
        token_word[i] = text[1:].lower()
ids = torch.tensor(sorted(token_word))
words_for = [token_word[int(i)] for i in ids]
print('word tokens', len(words_for), flush=True)

results = {}
for n, (cid, e) in enumerate(corpus.items()):
    context = e['context'][-500:]
    if not context.strip():
        context = tok.bos_token
    encoded = tok(context.rstrip(' '), return_tensors='pt', truncation=True, max_length=200)
    with torch.no_grad():
        logits = model(**encoded).logits[0, -1]
    scores = torch.log_softmax(logits, -1)[ids].numpy()
    best = {}
    for w, s in zip(words_for, scores):
        if w not in best or best[w] < s:
            best[w] = float(s)
    expected = key(e['expected'])
    if expected not in best:
        pieces = tok(' ' + expected)['input_ids']
        if pieces:
            piece = pieces[0]
            best[expected] = float(torch.log_softmax(logits, -1)[piece])
    order = sorted(best, key=lambda w: -best[w])
    rank = order.index(expected) + 1 if expected in best else -1
    x0, y0, xn, yn = e['x'][0], e['y'][0], e['x'][-1], e['y'][-1]
    filtered = [w for w in order[:6000] if w and w[0] in KEYS and near(x0, y0, w[0]) and near(xn, yn, w[-1])]
    frank = filtered.index(expected) + 1 if expected in filtered else -1
    results[cid] = {'rank': rank, 'filtered': frank}
    if (n + 1) % 150 == 0:
        print(n + 1, flush=True)


def summarise(getter, ids_):
    n = len(ids_)
    d = {}
    for k in (1, 3, 5, 10):
        d[f'in_top_{k}'] = round(100 * sum(1 <= getter(i) <= k for i in ids_) / n, 1)
    return d


out = {}
for s in ('prompted', 'hand'):
    ids_ = [i for i in corpus if corpus[i]['set'] == s and i in small]
    first_ok = lambda i: [key(w) for w in replay[i]['top']][:1] == [key(corpus[i]['expected'])]
    top3_ok = lambda i: key(corpus[i]['expected']) in [key(w) for w in replay[i]['top']]
    missed_first = [i for i in ids_ if not first_ok(i)]
    not_shown = [i for i in ids_ if not top3_ok(i)]
    out[s] = {
        'n': len(ids_),
        'before_swiping_no_stroke_info': {'phone_model': summarise(lambda i: small[i]['rank'], ids_), 'strong_model': summarise(lambda i: results[i]['rank'], ids_)},
        'with_stroke_start_and_end': {'phone_model': summarise(lambda i: small[i]['filtered'], ids_), 'strong_model': summarise(lambda i: results[i]['filtered'], ids_)},
        'words_swipe_got_wrong_first_n': len(missed_first),
        'wrong_first_rescued_no_stroke': {'phone_model': summarise(lambda i: small[i]['rank'], missed_first), 'strong_model': summarise(lambda i: results[i]['rank'], missed_first)},
        'wrong_first_rescued_with_stroke': {'phone_model': summarise(lambda i: small[i]['filtered'], missed_first), 'strong_model': summarise(lambda i: results[i]['filtered'], missed_first)},
        'not_in_strip_n': len(not_shown),
        'not_in_strip_rescued_with_stroke': {'phone_model': summarise(lambda i: small[i]['filtered'], not_shown), 'strong_model': summarise(lambda i: results[i]['filtered'], not_shown)},
    }
    print(s, json.dumps(out[s], indent=None))
json.dump(out, open(os.path.join(BASE, 'report', 'next-word-shortcut.json'), 'w'), indent=1)
