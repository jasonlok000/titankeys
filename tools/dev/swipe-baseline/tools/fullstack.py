#!/usr/bin/env python3
"""The full-stack test: today's engine, fine-tuned CleverKeys and a phone-sized predictor, combined by a ranker that learns
from two thirds of the word positions and is judged only on the held-back third (slot % 3 == 2).
Usage: fullstack.py baseline-dir english.combined"""
import json
import math
import os
import re
import sys
from collections import Counter, defaultdict

import lightgbm as lgb
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rawlog as R

BASE, DICT = sys.argv[1:3]
V = os.path.join((os.environ.get('TK_PRIVATE') or sys.exit('TK_PRIVATE is not set: source tools/dev/env.sh first')), 'v9')


def key(w):
    return R.norm(w).replace("'", '')


freq = {}
for line in open(DICT, encoding='utf-8'):
    m = re.match(r' word=([^,]+),f=(\d+),flags=([^,]*)', line)
    if m and 'not_a_word' not in m.group(3):
        w = key(m.group(1))
        freq[w] = max(freq.get(w, 0), int(m.group(2)))

corpus = {}
for line in open(os.path.join(V, 'corpus.jsonl'), encoding='utf-8'):
    e = json.loads(line)
    if e['first'] and e['set'] in ('prompted', 'hand', 'recorder'):
        corpus[e['id']] = e
dump = defaultdict(dict)
for line in open(os.path.join(V, 'dump.jsonl'), encoding='utf-8'):
    e = json.loads(line)
    rid, reading = e['id'].split('#')
    dump[rid][reading] = e
ck = {json.loads(l)['id']: json.loads(l)['c'] for l in open(os.path.join(V, 'ck-crossfit.jsonl'), encoding='utf-8')}
ids = [i for i in corpus if i in dump and 'plain' in dump[i] and i in ck]
print('first tries with all parts', len(ids), Counter(corpus[i]['set'] for i in ids))

# ---- candidates and their evidence ----------------------------------------------------------------------------------------
examples = []
for rid in ids:
    c = corpus[rid]
    plain, shift = dump[rid]['plain'], dump[rid].get('x+6')
    cand = {}

    def get(w):
        return cand.setdefault(w, {'final': 9, 'p_rank': 99, 'p_ctc': np.nan, 'p_score': np.nan, 'lm': np.nan, 's_rank': 99, 's_score': np.nan, 'ck_rank': 99, 'ck_path': np.nan, 'ck_final': np.nan})

    for rank, t in enumerate(plain['top']):
        get(key(t[0]))['final'] = rank
    for rank, (w, score, ctc, lm) in enumerate(plain['deep']):
        g = get(key(w))
        if rank < g['p_rank']:
            g['p_rank'], g['p_ctc'], g['p_score'], g['lm'] = rank, ctc, score, lm
    if shift:
        for rank, (w, score, ctc, lm) in enumerate(shift['deep']):
            g = get(key(w))
            if rank < g['s_rank']:
                g['s_rank'], g['s_score'] = rank, score
    for rank, (w, path, final) in enumerate(ck[rid]):
        g = get(key(w))
        g['ck_rank'], g['ck_path'], g['ck_final'] = rank, path, final
    x, y = np.array(c['x']) * 10, np.array(c['y']) * 3
    length = float(np.sqrt(np.diff(x) ** 2 + np.diff(y) ** 2).sum())
    examples.append({'id': rid, 'set': c['set'], 'slot': int(c['slot']), 'expected': key(c['expected']), 'cand': cand, 'length': length,
                     'time': c['t'][-1] - c['t'][0], 'context': c['context'], 'words_before': len(re.findall(r"[A-Za-z']+", c['context'][-300:]))})

# ---- phone-sized predictor ----------------------------------------------------------------------------------------------------
LM_NAME = os.environ.get('LM_NAME', 'distilgpt2')
LM_TAG = LM_NAME.split('/')[-1]
# LM_ONNX runs an exported copy (as the phone would) instead of the PyTorch model; LM_CAP keeps only the last N tokens.
LM_ONNX, LM_CAP = os.environ.get('LM_ONNX'), int(os.environ.get('LM_CAP', '0'))
if LM_ONNX:
    LM_TAG += '-' + os.path.splitext(os.path.basename(LM_ONNX))[0]
if LM_CAP:
    LM_TAG += f'-cap{LM_CAP}'
lm_path = os.path.join(V, f'{LM_TAG}-cands.json')
print('predictor:', LM_TAG, flush=True)
if os.path.exists(lm_path):
    lmscores = json.load(open(lm_path))
elif LM_ONNX:
    import onnxruntime as ort
    from transformers import AutoTokenizer
    tok = AutoTokenizer.from_pretrained(os.path.dirname(LM_ONNX))
    sess = ort.InferenceSession(LM_ONNX, providers=['CPUExecutionProvider'])
    start = tok.bos_token_id if tok.bos_token_id is not None else tok.eos_token_id
    lmscores = {}
    for n, e in enumerate(examples):
        context = e['context'][-500:].rstrip(' ')
        ids = tok(context, add_special_tokens=False)['input_ids'] if context.strip() else []
        ids = (ids[-LM_CAP:] if LM_CAP else ids[:200]) or [start]
        logp = sess.run(None, {'input_ids': np.array([ids], dtype=np.int64)})[0][0]
        scores = {}
        for w in e['cand']:
            best = -99.0
            for form in (w, w.capitalize()):
                t = tok(' ' + form, add_special_tokens=False)['input_ids']
                if t:
                    best = max(best, float(logp[t[0]]))
            scores[w] = best
        lmscores[e['id']] = scores
    json.dump(lmscores, open(lm_path, 'w'))
else:
    import torch
    from transformers import AutoModelForCausalLM, AutoTokenizer
    torch.set_num_threads(12)
    tok = AutoTokenizer.from_pretrained(LM_NAME)
    model = AutoModelForCausalLM.from_pretrained(LM_NAME).eval()
    if tok.bos_token is None:
        tok.bos_token = tok.eos_token
    lmscores = {}
    for n, e in enumerate(examples):
        context = e['context'][-500:].rstrip(' ')
        enc = tok(context if context.strip() else tok.bos_token, return_tensors='pt', truncation=True, max_length=200)
        with torch.no_grad():
            logp = torch.log_softmax(model(**enc).logits[0, -1], -1)
        scores = {}
        for w in e['cand']:
            best = -99.0
            for form in (w, w.capitalize()):
                t = tok(' ' + form)['input_ids']
                if t:
                    best = max(best, float(logp[t[0]]))
            scores[w] = best
        lmscores[e['id']] = scores
        if (n + 1) % 300 == 0:
            print('  predictor', n + 1, flush=True)
    json.dump(lmscores, open(lm_path, 'w'))

# ---- features ---------------------------------------------------------------------------------------------------------------
ENGINE = ['final', 'p_rank', 'p_ctc', 'p_score', 'lm', 's_rank', 's_score']
CK = ['ck_rank', 'ck_path', 'ck_final']
rows, labels, qid, names = [], [], [], []
for q, e in enumerate(examples):
    scores = lmscores[e['id']]
    top_lm = max(scores.values()) if scores else 0
    for w, g in e['cand'].items():
        ideal = 0.0
        base = [g[k] for k in ENGINE] + [g[k] for k in CK]
        lm = scores.get(w, -99.0)
        common = [freq.get(w, -1), len(w), e['length'], e['time'], e['words_before'], min(e['words_before'], 1)]
        rows.append(base + [lm, lm - top_lm] + common)
        labels.append(int(w == e['expected']))
        qid.append(q)
        names.append(w)
X = np.array(rows, dtype=np.float32)
y = np.array(labels)
qid = np.array(qid)
cols = {name: i for i, name in enumerate(ENGINE + CK + ['lm_logp', 'lm_gap', 'freq', 'len', 'stroke_len', 'time', 'words_before', 'has_context'])}
SETS = {
    'engine only': ENGINE + ['freq', 'len', 'stroke_len', 'time', 'words_before', 'has_context'],
    'engine + fine-tuned CleverKeys': ENGINE + CK + ['freq', 'len', 'stroke_len', 'time', 'words_before', 'has_context'],
    'engine + predictor': ENGINE + ['lm_logp', 'lm_gap', 'freq', 'len', 'stroke_len', 'time', 'words_before', 'has_context'],
    'engine + fine-tuned CleverKeys + predictor': list(cols),
}
held = [q for q, e in enumerate(examples) if e['slot'] % 3 == 2]
train = [q for q, e in enumerate(examples) if e['slot'] % 3 != 2]
print('training word positions', len(train), 'held-back', len(held), Counter(examples[q]['set'] for q in held))
rows_of = {q: np.where(qid == q)[0] for q in range(len(examples))}


def judge(scores, label):
    out = {}
    for s in ('all', 'prompted', 'hand', 'recorder'):
        qs = [q for q in held if s == 'all' or examples[q]['set'] == s]
        ok1 = ok3 = found = 0
        for q in qs:
            idx = rows_of[q]
            top = [names[i] for i in idx[np.argsort(-scores[idx], kind='stable')][:3]]
            ok1 += top[:1] == [examples[q]['expected']]
            ok3 += examples[q]['expected'] in top
            found += examples[q]['expected'] in examples[q]['cand']
        out[s] = {'n': len(qs), 'first': round(100 * ok1 / len(qs), 1), 'top3': round(100 * ok3 / len(qs), 1), 'found': round(100 * found / len(qs), 1)}
    print(f'  {label:<46}', ' | '.join(f"{k} {v['first']}/{v['top3']} (n={v['n']})" for k, v in out.items()), flush=True)
    return out


results = {'today': judge(-X[:, cols['final']], "today's engine")}
ck_only = np.where(X[:, cols['ck_rank']] < 99, -X[:, cols['ck_rank']], -999)
results['fine-tuned CleverKeys alone'] = judge(ck_only, 'fine-tuned CleverKeys alone')
for label, features in SETS.items():
    cols_idx = [cols[f] for f in features]
    tr = np.isin(qid, train)
    order = np.argsort(qid[tr], kind='stable')
    counts = np.bincount(qid[tr][order], minlength=len(examples))
    preds = []
    for seed in (1, 2, 3):
        m = lgb.LGBMRanker(objective='lambdarank', n_estimators=300, learning_rate=.05, num_leaves=31, min_child_samples=8, subsample=.85, subsample_freq=1,
                           colsample_bytree=.85, verbose=-1, random_state=seed)
        m.fit(X[tr][order][:, cols_idx], y[tr][order], group=counts[counts > 0])
        preds.append(m.predict(X[:, cols_idx]))
    results[label] = judge(np.mean(preds, 0), 'ranker: ' + label)
json.dump(results, open(os.path.join(BASE, 'report', f'fullstack-{LM_TAG}.json'), 'w'), indent=1)
print('found = the right word is among the candidates at all')
