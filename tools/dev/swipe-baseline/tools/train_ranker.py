#!/usr/bin/env python3
"""Trains the swipe ranker that ships in the app, from inputs computed exactly as the app computes them:
- engine evidence recorded on the phone by EvidenceRunner (shown words, plain and shifted readings),
- fine-tuned CleverKeys candidates, cross-fitted so no stroke is read by a model that trained on it,
- the phone's predictor (8-bit ONNX, last LM_CAP tokens), with the phone's word scoring.
First judges it on the held-back word positions (slot % 3 == 2) after training on the rest, then trains on everything and
writes the model (LightGBM text, trees of all seeds in one file) and parity cases for the Java version.
Usage: train_ranker.py baseline-dir english.combined lm-dir ck-crossfit.jsonl out-dir"""
import json
import math
import os
import re
import sys
from collections import Counter

import lightgbm as lgb
import numpy as np
import onnxruntime as ort
from transformers import AutoTokenizer

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ck_core as C

BASE, DICT, LM_DIR, CK_PATH, OUT = sys.argv[1:6]
LM_CAP = int(os.environ.get('LM_CAP', '32'))
LM_FILE = os.environ.get('LM_FILE', 'model-int8pc.onnx')
V = os.path.join((os.environ.get('TK_PRIVATE') or sys.exit('TK_PRIVATE is not set: source tools/dev/env.sh first')), os.environ.get('V_DIR', 'v9'))
# SPLITS: engine 2.1's fixed parts (split_v21.py). Then the ranker trains on 'train' and is judged on 'held' and 'test'.
SPLITS = json.load(open(os.path.join(V, 'splits.json'))) if os.environ.get('SPLITS') else None
os.makedirs(OUT, exist_ok=True)
FEATURES = ['final', 'p_rank', 'p_ctc', 'p_score', 'lm', 's_rank', 's_score', 'ck_rank', 'ck_path', 'ck_final', 'lm_logp', 'lm_gap',
            'freq', 'len', 'stroke_len', 'time', 'words_before', 'has_context']
key = C.key
_, vocab = C.load_lexicon(DICT)

corpus = {}
for line in open(os.path.join(V, 'corpus.jsonl'), encoding='utf-8'):
    e = json.loads(line)
    if e['first'] and e['set'] in ('prompted', 'hand', 'recorder'):
        corpus[e['id']] = e
evidence = {}
for line in open(os.environ.get('EVIDENCE_FILE') or os.path.join(V, 'evidence.jsonl'), encoding='utf-8'):
    e = json.loads(line)
    evidence[e['id'].split('#')[0]] = e
ck = {}
for line in open(CK_PATH, encoding='utf-8'):
    e = json.loads(line)
    ck[e['id']] = e['c']
# CK2: a second encoder's cross-fitted candidates, to test both together. Its features are added at the end.
CK2_PATH = os.environ.get('CK2')
ck2 = {}
if CK2_PATH:
    for line in open(CK2_PATH, encoding='utf-8'):
        e = json.loads(line)
        ck2[e['id']] = e['c']
    FEATURES = FEATURES + ['ck2_rank', 'ck2_path', 'ck2_final']
ids = [i for i in corpus if i in evidence and i in ck]
print('first tries with every input', len(ids), Counter(corpus[i]['set'] for i in ids), flush=True)

tok = AutoTokenizer.from_pretrained(LM_DIR)
sess = ort.InferenceSession(os.path.join(LM_DIR, LM_FILE), providers=['CPUExecutionProvider'])
START = int(re.search(r'start_token=(\d+)', open(os.path.join(LM_DIR, 'lm-info.txt')).read()).group(1))
first_token = {}


def next_logp(context):
    """As NextWordModel.java: the last 500 characters without trailing spaces, then the last LM_CAP tokens."""
    context = context[-500:].rstrip(' ')
    ids_ = tok(context, add_special_tokens=False)['input_ids'] if context.strip() else []
    ids_ = ids_[-LM_CAP:] or [START]
    return sess.run(None, {'input_ids': np.array([ids_], dtype=np.int64)})[0][0]


def word_logp(logp, k):
    """As NextWordModel.java: the better of the word and its capitalised form, by the chance of its first token."""
    best = -99.0
    for form in (k, k[:1].upper() + k[1:]):
        if form not in first_token:
            t = tok(' ' + form, add_special_tokens=False)['input_ids']
            first_token[form] = t[0] if t else -1
        if first_token[form] >= 0:
            best = max(best, float(logp[first_token[form]]))
    return best


def features(c, ev, ckc, logp):
    """The candidates and their features, exactly as SwipeRanker.java builds them."""
    cand = {}

    def get(k):
        return cand.setdefault(k, [9, 99, math.nan, math.nan, math.nan, 99, math.nan, 99, math.nan, math.nan])

    for rank, t in enumerate(ev['top']):
        g = get(key(t[0]))
        if g[0] == 9:
            g[0] = rank
    for rank, (w, score, ctc, lm) in enumerate(ev['plain']):
        g = get(key(w))
        if rank < g[1]:
            g[1], g[2], g[3], g[4] = rank, ctc, score, lm
    for rank, (w, score, ctc, lm) in enumerate(ev['shifted']):
        g = get(key(w))
        if rank < g[5]:
            g[5], g[6] = rank, score
    for rank, (w, path, final) in enumerate(ckc):
        g = get(w)
        if rank < g[7]:
            g[7], g[8], g[9] = rank, path, final
    second = {}
    for rank, (w, path, final) in enumerate(ck2.get(c['id'], []) if CK2_PATH else []):
        get(w)
        if w not in second:
            second[w] = [rank, path, final]
    cand.pop('', None)
    x, y = np.array(c['x'], dtype=np.float32).astype(np.float64) * 10, np.array(c['y'], dtype=np.float32).astype(np.float64) * 3
    length = float(np.sqrt(np.diff(x) ** 2 + np.diff(y) ** 2).sum())
    t = np.array(c['t'], dtype=np.float32).astype(np.float64)
    before = len(re.findall(r"[A-Za-z']+", c['context'][-300:]))
    lms = {k: word_logp(logp, k) for k in cand}
    top_lm = max(lms.values()) if lms else 0.0
    rows = []
    for k, g in cand.items():
        extra = (second.get(k, [99, math.nan, math.nan]) if CK2_PATH else [])
        rows.append((k, g + [lms[k], lms[k] - top_lm, vocab.get(k, -1), len(k), length, float(t[-1] - t[0]), before, min(before, 1)] + extra))
    return rows


examples = []
for n, rid in enumerate(ids):
    c = corpus[rid]
    rows = features(c, evidence[rid], ck[rid], next_logp(c['context']))
    examples.append({'id': rid, 'set': c['set'], 'slot': int(c['slot']), 'expected': key(c['expected']), 'rows': rows})
    if (n + 1) % 400 == 0:
        print('  features', n + 1, flush=True)

X = np.array([r for e in examples for _, r in e['rows']], dtype=np.float32)
y = np.array([int(k == e['expected']) for e in examples for k, _ in e['rows']])
qid = np.array([q for q, e in enumerate(examples) for _ in e['rows']])
names = [k for e in examples for k, _ in e['rows']]
rows_of = {q: np.where(qid == q)[0] for q in range(len(examples))}
PARAMS = dict(objective='lambdarank', n_estimators=300, learning_rate=.05, num_leaves=31, min_child_samples=8, subsample=.85, subsample_freq=1,
              colsample_bytree=.85, verbose=-1)


def fit(train_q, seeds=(1, 2, 3)):
    tr = np.isin(qid, train_q)
    order = np.argsort(qid[tr], kind='stable')
    counts = np.bincount(qid[tr][order], minlength=len(examples))
    models = []
    for seed in seeds:
        m = lgb.LGBMRanker(random_state=seed, **PARAMS)
        m.fit(X[tr][order], y[tr][order], group=counts[counts > 0])
        models.append(m)
    return models


def judge(scores, held, label):
    out = {}
    for s in ('all', 'prompted', 'hand', 'recorder'):
        qs = [q for q in held if s == 'all' or examples[q]['set'] == s]
        if not qs:
            continue
        ok1 = ok3 = found = 0
        for q in qs:
            idx = rows_of[q]
            top = [names[i] for i in idx[np.argsort(-scores[idx], kind='stable')][:3]]
            ok1 += top[:1] == [examples[q]['expected']]
            ok3 += examples[q]['expected'] in top
            found += examples[q]['expected'] in {names[i] for i in idx}
        out[s] = {'n': len(qs), 'first': round(100 * ok1 / len(qs), 1), 'top3': round(100 * ok3 / len(qs), 1), 'found': round(100 * found / len(qs), 1)}
    print(f'  {label:<34}', ' | '.join(f"{k} {v['first']}/{v['top3']} found {v['found']} (n={v['n']})" for k, v in out.items()), flush=True)
    return out


if os.environ.get('PERSONAL_TEST'):
    # Do your own counts help? Learn from the earlier 60% of strokes in time, judge the later 40%. For each candidate:
    # how often you kept that word after a swipe before, and how often the engine had it wrong first before.
    wall = {}
    for line in open(os.path.join(V, 'attempts.jsonl'), encoding='utf-8'):
        r = json.loads(line)
        wall[r['id']] = r['wall']
    # Only strokes from the raw log have a time; the separate recorder session is left out of this test.
    order_t = sorted((q for q in range(len(examples)) if examples[q]['id'].split('-')[0] in ('log', 'hand')),
                     key=lambda q: wall[int(examples[q]['id'].split('-')[1])])
    used, missed = Counter(), Counter()
    extra = np.zeros((len(y), 2), dtype=np.float32)
    for q in order_t:
        idx = rows_of[q]
        for i in idx:
            extra[i] = (used[names[i]], missed[names[i]])
        first = [names[i] for i in idx if X[i, 0] == 0]
        if first != [examples[q]['expected']]:
            missed[examples[q]['expected']] += 1
        used[examples[q]['expected']] += 1
    cut = int(len(order_t) * .6)
    early, late = order_t[:cut], order_t[cut:]
    X_plain = X
    for label, data in (('ranker, no personal counts', X_plain), ('ranker + your use and miss counts', np.hstack([X_plain, extra]))):
        X = data
        judge(np.mean([m.predict(X) for m in fit(early)], 0), late, label)
        for s in ('hand', 'prompted'):
            judge(np.mean([m.predict(X) for m in fit(early)], 0), [q for q in late if examples[q]['set'] == s], f'  {s} only')
    sys.exit(0)

if SPLITS:
    held = [q for q, e in enumerate(examples) if SPLITS[e['id']] == 'held']
    test = [q for q, e in enumerate(examples) if SPLITS[e['id']] == 'test']
    train = [q for q, e in enumerate(examples) if SPLITS[e['id']] == 'train']
    if os.environ.get('HELD_TRAIN'):
        # As a release would: the frozen 540 join the training, and only the clean test is judged.
        train, held = train + held, []
else:
    held = [q for q, e in enumerate(examples) if e['slot'] % 3 == 2]
    train = [q for q, e in enumerate(examples) if e['slot'] % 3 != 2]
    test = []
print('training word positions', len(train), 'held-back', len(held), flush=True)
report = {'today': judge(-X[:, 0], held, "today's engine (this branch)")}
twin_scores = np.mean([m.predict(X) for m in fit(train)], 0)
if held:
    report['ranker'] = judge(twin_scores, held, 'ranker, held back')
if not SPLITS and os.environ.get('TWIN_ONLY'):
    # Engine 2.0's own twin: its per-word picks on the held-back third, for a paired comparison.
    with open(os.path.join(V, 'twin-ranks.jsonl'), 'w', encoding='utf-8') as f:
        for q in held:
            idx = rows_of[q]
            top = [names[i] for i in idx[np.argsort(-twin_scores[idx], kind='stable')][:3]]
            f.write(json.dumps({'id': examples[q]['id'], 'part': 'held', 'top': top, 'expected': examples[q]['expected']}) + chr(10))
if test:
    report['today, test'] = judge(-X[:, 0], test, "today's engine order, test")
    report['ranker, test'] = judge(twin_scores, test, 'ranker twin, clean test')
    # Per-word ranks of the twin, for paired comparison with engine 2.0 on the phone.
    with open(os.path.join(V, 'twin-ranks.jsonl'), 'w', encoding='utf-8') as f:
        for q in held + test:
            idx = rows_of[q]
            top = [names[i] for i in idx[np.argsort(-twin_scores[idx], kind='stable')][:3]]
            f.write(json.dumps({'id': examples[q]['id'], 'part': SPLITS[examples[q]['id']], 'top': top, 'expected': examples[q]['expected']}) + chr(10))
if os.environ.get('TWIN_ONLY'):
    json.dump(report, open(os.path.join(BASE, 'report', 'ranker-train-v21.json'), 'w'), indent=1)
    sys.exit(0)

# ---- the shipped model: every word position, three seeds in one file ---------------------------------------------------
final = fit(list(range(len(examples))))
texts = [m.booster_.model_to_string() for m in final]
trees = []
for text in texts:
    section = text.split('\nend of trees', 1)[0]
    trees += ['Tree=' + t for t in section.split('\nTree=')[1:]]
with open(os.path.join(OUT, 'swipe-ranker.txt'), 'w', encoding='utf-8', newline='\n') as f:
    f.write('# Swipe ranker: LightGBM trees of %d seeds, summed. Features in this order:\n' % len(final))
    f.write('feature_names=' + ' '.join(FEATURES) + '\n')
    f.write(f'lm_cap={LM_CAP}\n')
    for i, t in enumerate(trees):
        f.write(re.sub(r'^Tree=\d+', f'Tree={i}', t.strip()) + '\n\n')
print('wrote', len(trees), 'trees', flush=True)
raw = np.sum([m.booster_.predict(X, raw_score=True) for m in final], 0)
# Parity cases for SwipeRanker.java: every input it reads, and the features and scores it must reproduce.
with open(os.path.join(V, 'ranker-parity.jsonl'), 'w', encoding='utf-8') as f:
    for q, e in enumerate(examples[:300]):
        c = corpus[e['id']]
        idx = rows_of[q]
        f.write(json.dumps({'x': c['x'], 'y': c['y'], 't': c['t'], 'context': c['context'], 'top': evidence[e['id']]['top'],
                            'plain': evidence[e['id']]['plain'], 'shifted': evidence[e['id']]['shifted'], 'ck': ck[e['id']],
                            'lm': {names[i]: float(X[i, FEATURES.index('lm_logp')]) for i in idx},
                            'rows': {names[i]: [None if math.isnan(v) else float(v) for v in X[i]] for i in idx},
                            'scores': {names[i]: float(raw[i]) for i in idx}}) + '\n')
report['features'] = FEATURES
report['lm'] = {'file': LM_FILE, 'cap': LM_CAP, 'ck': os.path.basename(CK_PATH)}
json.dump(report, open(os.path.join(BASE, 'report', 'ranker-train.json'), 'w'), indent=1)
print('done', flush=True)
