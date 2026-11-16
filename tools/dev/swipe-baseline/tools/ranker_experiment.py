#!/usr/bin/env python3
"""How far can retrieval and ranking go on the existing strokes? Combines every FUTO reading, the outline matcher and an
independent whole-dictionary shape matcher, then trains a ranker with words held out of training.
Usage: ranker_experiment.py baseline-dir english.combined"""
import json
import math
import os
import re
import sys
from collections import Counter, defaultdict

import lightgbm as lgb
import numpy as np

BASE, DICT = sys.argv[1:3]
PRIV = (os.environ.get('TK_PRIVATE') or sys.exit('TK_PRIVATE is not set: source tools/dev/env.sh first'))
ROWS = ['qwertyuiop', 'asdfghjkl', 'zxcvbnm']
KEYS = {}
for r, row in enumerate(ROWS):
    for c, ch in enumerate(row):
        KEYS[ch] = (c + (1 if r == 2 else 0) + .5, r + .5)
SP = {'neighbour': 'neighbor', 'tyres': 'tires', 'favourite': 'favorite', 'colour': 'color', 'grey': 'gray'}
POINTS = 40


def key(w):
    w = w.lower().replace('’', "'").strip("'")
    w = SP.get(w, w)
    return w.replace("'", '')


def resample(xy):
    d = np.sqrt(((xy[1:] - xy[:-1]) ** 2).sum(1))
    along = np.concatenate([[0], np.cumsum(d)])
    if along[-1] <= 0:
        return np.repeat(xy[:1], POINTS, axis=0)
    target = np.linspace(0, along[-1], POINTS)
    return np.stack([np.interp(target, along, xy[:, 0]), np.interp(target, along, xy[:, 1])], 1)


def shape_of(p):
    lo, hi = p.min(-2, keepdims=True), p.max(-2, keepdims=True)
    scale = np.maximum((hi - lo).max(-1), 1e-3)[..., None]
    return (p - (lo + hi) / 2) / scale


# ---- lexicon ----------------------------------------------------------------------------------------------------------
words, freqs = [], []
seen = {}
for line in open(DICT, encoding='utf-8'):
    m = re.match(r' word=([^,]+),f=(\d+),flags=([^,]*)', line)
    if not m or 'not_a_word' in m.group(3):
        continue
    w = key(m.group(1))
    if len(w) < 2 or not w.isalpha() or not all(c in KEYS for c in w):
        continue
    f = int(m.group(2))
    if w in seen:
        freqs[seen[w]] = max(freqs[seen[w]], f)
    else:
        seen[w] = len(words)
        words.append(w)
        freqs.append(f)
freqs = np.array(freqs, dtype=np.float32)
templates = np.zeros((len(words), POINTS, 2), dtype=np.float32)
ideal = np.zeros(len(words), dtype=np.float32)
for i, w in enumerate(words):
    pts = []
    for c in w:
        if not pts or pts[-1] != KEYS[c]:
            pts.append(KEYS[c])
    if len(pts) < 2:
        pts = pts * 2
    a = np.array(pts, dtype=np.float32)
    templates[i] = resample(a)
    ideal[i] = np.sqrt(((a[1:] - a[:-1]) ** 2).sum(1)).sum()
shapes = shape_of(templates)
print('lexicon', len(words))

# ---- strokes and dumped candidates ------------------------------------------------------------------------------------
corpus = {}
for line in open(os.path.join(PRIV, 'corpus.jsonl'), encoding='utf-8'):
    e = json.loads(line)
    if e['first'] and e['set'] in ('prompted', 'hand', 'recorder'):
        corpus[e['id']] = e
dump = defaultdict(dict)
for line in open(os.path.join(PRIV, 'dump.jsonl'), encoding='utf-8'):
    e = json.loads(line)
    rid, reading = e['id'].split('#')
    dump[rid][reading] = e
READINGS = ['plain', 'x+3', 'x-3', 'x+6', 'x-6', 'y-5', 'y+5', 'sx12', 'sy125', 'endx', 'startx', 'bothx']
ck = {}
ck_path = os.path.join(PRIV, 'ck.jsonl')
if os.path.exists(ck_path):
    for line in open(ck_path, encoding='utf-8'):
        e = json.loads(line)
        ck[e['id']] = e['c']
print('cleverkeys strokes', len(ck))


def norm_len(ctc, w):
    return ctc / max(1, len(w)) ** .1126


examples = []
for rid, c in corpus.items():
    if rid not in dump or 'plain' not in dump[rid]:
        continue
    expected = key(c['expected'])
    stroke = np.array([c['x'], c['y']], dtype=np.float32).T * np.array([10, 3], dtype=np.float32)
    time_ms = c['t'][-1] - c['t'][0] if len(c['t']) > 1 else 0
    length = float(np.sqrt(((stroke[1:] - stroke[:-1]) ** 2).sum(1)).sum())
    pts = resample(stroke)
    sh = shape_of(pts[None])[0]
    loc = np.sqrt(((templates - pts) ** 2).sum(2)).mean(1)
    shp = np.sqrt(((shapes - sh) ** 2).sum(2)).mean(1)
    start = np.sqrt(((templates[:, 0] - pts[0]) ** 2).sum(1))
    end = np.sqrt(((templates[:, -1] - pts[-1]) ** 2).sum(1))
    alt = -(shp + .35 * loc) + (freqs / 255) * .35
    alt_top = np.argsort(-alt)[:60]
    info = dump[rid]
    plain = info['plain']
    cand = {}

    def get(w):
        return cand.setdefault(w, {'reads': 0, 'best_ctc': None, 'best_score': None, 'best_rank': 99, 'plain_rank': 99, 'plain_ctc': None, 'plain_score': None, 'lm': None, 'final_rank': 9,
                                   'outline_close': None, 'outline_score': None, 'alt': False, 'ck_rank': 99, 'ck_ctc': None, 'ck_final': None})

    for reading in READINGS:
        e = info.get(reading)
        if not e:
            continue
        for rank, (w, score, ctc, lm) in enumerate(e['deep']):
            w = key(w)
            g = get(w)
            g['reads'] += 1
            g['best_ctc'] = max(g['best_ctc'], ctc) if g['best_ctc'] is not None else ctc
            g['best_score'] = max(g['best_score'], score) if g['best_score'] is not None else score
            g['best_rank'] = min(g['best_rank'], rank)
            g['lm'] = lm
            if reading == 'plain':
                g['plain_rank'], g['plain_ctc'], g['plain_score'] = rank, ctc, score
    for rank, t in enumerate(plain['top']):
        get(key(t[0]))['final_rank'] = rank
    for w, score, close in plain.get('outlines', []):
        g = get(key(w))
        g['outline_close'], g['outline_score'] = close, score
    for i in alt_top:
        get(words[int(i)])['alt'] = True
    scored_ck = []
    for w, s, f in ck.get(rid, []):
        w = key(w)
        scored_ck.append((s / max(len(w), 1) ** .9 + .25 * len(w) + 2 * math.log(max(f, 1)), w, s))
    scored_ck.sort(reverse=True)
    for rank, (final, w, s) in enumerate(scored_ck):
        g = get(w)
        g['ck_rank'], g['ck_ctc'], g['ck_final'] = rank, s, final
    examples.append({'id': rid, 'set': c['set'], 'expected': expected, 'cand': cand, 'length': length, 'time': time_ms, 'context': plain['n'], 'style': c['style'], 'slot': c['slot'],
                     'alt': (alt, shp, loc, start, end), 'pts0': pts[0], 'ptsN': pts[-1]})
print('strokes', len(examples))

# ---- retrieval ceiling ------------------------------------------------------------------------------------------------
print('\n=== Is the right word found at all? (first tries) ===')
out = {}
for s in ('prompted', 'hand', 'recorder'):
    ex = [e for e in examples if e['set'] == s]
    n = len(ex)
    plain_found = sum(e['cand'].get(e['expected'], {}).get('plain_rank', 99) < 99 for e in ex)
    any_found = sum(e['cand'].get(e['expected'], {}).get('reads', 0) > 0 for e in ex)
    outline = sum(e['cand'].get(e['expected'], {}).get('outline_close') is not None for e in ex)
    alt_rank = []
    for e in ex:
        if e['expected'] in seen:
            order = e['alt'][0]
            alt_rank.append(int((order > order[seen[e['expected']]]).sum()) + 1)
        else:
            alt_rank.append(10 ** 9)
    union = sum(e['expected'] in e['cand'] for e in ex)
    ck_found = sum(e['cand'].get(e['expected'], {}).get('ck_rank', 99) < 99 for e in ex)
    both_models = sum(e['cand'].get(e['expected'], {}).get('ck_rank', 99) < 99 or e['cand'].get(e['expected'], {}).get('reads', 0) > 0 for e in ex)
    out[s] = {'n': n, 'futo_plain_top48': plain_found, 'futo_12_readings_top48': any_found, 'cleverkeys_top80': ck_found, 'futo_readings_or_cleverkeys': both_models, 'outline_top30': outline, 'shape_matcher_top1': sum(r <= 1 for r in alt_rank), 'shape_matcher_top3': sum(r <= 3 for r in alt_rank), 'shape_matcher_top60': sum(r <= 60 for r in alt_rank), 'all_combined': union}
    print(s, {k: (v, round(100 * v / n, 1)) if k != 'n' else v for k, v in out[s].items()})


# ---- learned ranking --------------------------------------------------------------------------------------------------
def features(e, w, g):
    alt, shp, loc, start, end = e['alt']
    i = seen.get(w)
    in_lex = i is not None
    length = e['length']
    wl = len(w)
    return [
        g['plain_rank'], g['best_rank'], g['reads'], g['final_rank'],
        np.nan if g['plain_ctc'] is None else g['plain_ctc'], np.nan if g['plain_score'] is None else g['plain_score'],
        np.nan if g['best_ctc'] is None else g['best_ctc'], np.nan if g['best_score'] is None else g['best_score'],
        np.nan if g['lm'] is None else g['lm'], np.nan if g['outline_close'] is None else g['outline_close'], np.nan if g['outline_score'] is None else g['outline_score'],
        float(shp[i]) if in_lex else np.nan, float(loc[i]) if in_lex else np.nan, float(start[i]) if in_lex else np.nan, float(end[i]) if in_lex else np.nan, float(alt[i]) if in_lex else np.nan,
        float(freqs[i]) if in_lex else -1, wl, float(ideal[i]) if in_lex else np.nan, length, length / max(float(ideal[i]), .1) if in_lex else np.nan,
        e['context'], min(e['context'], 1), e['time'], length * 1000 / max(e['time'], 1), float(g['alt']),
        g['ck_rank'], np.nan if g['ck_ctc'] is None else g['ck_ctc'], np.nan if g['ck_final'] is None else g['ck_final'],
    ]


X, y, qid, sets, cands = [], [], [], [], []
for q, e in enumerate(examples):
    for w, g in e['cand'].items():
        X.append(features(e, w, g))
        y.append(int(w == e['expected']))
        qid.append(q)
        cands.append(w)
X = np.array(X, dtype=np.float32)
y = np.array(y)
qid = np.array(qid)
print('\ncandidate rows', X.shape)
FOLDS = 5
fold_of_word = {}
for e in examples:
    fold_of_word.setdefault(e['expected'], (hash(e['expected']) & 0x7fffffff) % FOLDS)
# A stable hash, so that reruns match.
import zlib
fold_of_word = {w: zlib.crc32(w.encode()) % FOLDS for w in fold_of_word}
q_fold = np.array([fold_of_word[e['expected']] for e in examples])
preds = np.zeros(len(y))
for fold in range(FOLDS):
    test_q = np.where(q_fold == fold)[0]
    mask = np.isin(qid, test_q)
    train = ~mask
    order = np.argsort(qid[train], kind='stable')
    model = lgb.LGBMRanker(objective='lambdarank', n_estimators=300, learning_rate=.05, num_leaves=31, min_child_samples=8, subsample=.85, subsample_freq=1, colsample_bytree=.85, verbose=-1, random_state=7)
    model.fit(X[train][order], y[train][order], group=np.bincount(qid[train][order], minlength=len(examples))[np.bincount(qid[train][order], minlength=len(examples)) > 0], eval_at=[3])
    preds[mask] = model.predict(X[mask])


def evaluate(selector, scores, name):
    ok1 = ok3 = n = 0
    for q, e in enumerate(examples):
        if not selector(e):
            continue
        idx = np.where(qid == q)[0]
        order = idx[np.argsort(-scores[idx])]
        ranked = [cands[i] for i in order[:3]]
        ok1 += ranked[:1] == [e['expected']]
        ok3 += e['expected'] in ranked
        n += 1
    print(f'  {name:<46} n={n:<4} first {100 * ok1 / n:.1f}%  top3 {100 * ok3 / n:.1f}%')
    return {'n': n, 'first': round(100 * ok1 / n, 1), 'top3': round(100 * ok3 / n, 1)}


print('\n=== Ranker trained without each word it is tested on ===')
out['ranker'] = {}
out['current'] = {}
for s in ('prompted', 'hand', 'recorder'):
    out['current'][s] = evaluate(lambda e: e['set'] == s, -X[:, 3], f'current engine order on {s}')
    out['ranker'][s] = evaluate(lambda e: e['set'] == s, preds, f'learned ranker on {s}')
out['ranker']['prompted_held_back'] = evaluate(lambda e: e['set'] == 'prompted' and int(e['slot']) % 3 == 2, preds, 'learned ranker, prompted held-back third')


def fit_predict(train_q, test_q):
    mask_tr, mask_te = np.isin(qid, train_q), np.isin(qid, test_q)
    order = np.argsort(qid[mask_tr], kind='stable')
    counts = np.bincount(qid[mask_tr][order], minlength=len(examples))
    m = lgb.LGBMRanker(objective='lambdarank', n_estimators=300, learning_rate=.05, num_leaves=31, min_child_samples=8, subsample=.85, subsample_freq=1, colsample_bytree=.85, verbose=-1, random_state=7)
    m.fit(X[mask_tr][order], y[mask_tr][order], group=counts[counts > 0], eval_at=[3])
    scores = np.zeros(len(y))
    scores[mask_te] = m.predict(X[mask_te])
    return scores


print('\n=== Trained on the other two sets, tested on the third ===')
out['leave_one_set_out'] = {}
q_sets = np.array([e['set'] for e in examples])
for s in ('prompted', 'hand', 'recorder'):
    scores = fit_predict(np.where(q_sets != s)[0], np.where(q_sets == s)[0])
    out['leave_one_set_out'][s] = evaluate(lambda e: e['set'] == s, scores, f'trained elsewhere, tested on {s}')
json.dump(out, open(os.path.join(BASE, 'report', 'ranker-experiment.json'), 'w'), indent=1)

importance = Counter()
model = lgb.LGBMRanker(objective='lambdarank', n_estimators=300, learning_rate=.05, num_leaves=31, min_child_samples=8, subsample=.85, subsample_freq=1, colsample_bytree=.85, verbose=-1, random_state=7, importance_type='gain')
order = np.argsort(qid, kind='stable')
model.fit(X[order], y[order], group=np.bincount(qid[order], minlength=len(examples)))
names = ['plain_rank', 'best_rank', 'reads', 'final_rank', 'plain_ctc', 'plain_score', 'best_ctc', 'best_score', 'lm', 'outline_close', 'outline_score', 'shape', 'loc', 'start', 'end', 'altscore', 'freq', 'len', 'ideal', 'stroke_len', 'ratio', 'context', 'hascontext', 'time', 'speed', 'alt_candidate', 'ck_rank', 'ck_ctc', 'ck_final']
for n, v in sorted(zip(names, model.feature_importances_), key=lambda x: -x[1])[:12]:
    print(f'   {n:<16} {v:.0f}')

# ---- personal vocabulary, in the order the words were typed ------------------------------------------------------------
print('\n=== Personal vocabulary, trained only on earlier text (everyday set) ===')
attempts = {}
for line in open(os.path.join(PRIV, 'attempts.jsonl'), encoding='utf-8'):
    r = json.loads(line)
    attempts[r['id']] = r['wall']
hand_q = sorted([q for q, e in enumerate(examples) if e['set'] == 'hand'], key=lambda q: attempts[int(examples[q]['id'].split('-')[1])])
cut = int(len(hand_q) * .6)
early, late = hand_q[:cut], hand_q[cut:]
others = [q for q, e in enumerate(examples) if e['set'] != 'hand']

used = Counter()
missed = Counter()
X_extra, y_extra, q_extra, cand_extra = [], [], [], []
base_rows = {q: np.where(qid == q)[0] for q in range(len(examples))}
user_feats = np.zeros((len(y), 2), dtype=np.float32)
injected = 0
for q in hand_q:
    e = examples[q]
    alt, shp, loc, start, end = e['alt']
    for i in base_rows[q]:
        user_feats[i] = (used[cands[i]], missed[cands[i]])
    # Words the owner has used before can be offered even when no engine lists them.
    for w in used:
        if w in e['cand'] or w not in seen:
            continue
        i = seen[w]
        if start[i] <= 2 and end[i] <= 2:
            g = {'reads': 0, 'best_ctc': None, 'best_score': None, 'best_rank': 99, 'plain_rank': 99, 'plain_ctc': None, 'plain_score': None, 'lm': None, 'final_rank': 9, 'outline_close': None, 'outline_score': None, 'alt': False, 'ck_rank': 99, 'ck_ctc': None, 'ck_final': None}
            X_extra.append(features(e, w, g) + [used[w], missed[w]])
            y_extra.append(int(w == e['expected']))
            q_extra.append(q)
            cand_extra.append(w)
            injected += int(w == e['expected'])
    top = e['cand']
    rows_q = [(X[i, 3], cands[i]) for i in base_rows[q]]
    first = min(rows_q)[1]
    if first != e['expected']:
        missed[e['expected']] += 1
    used[e['expected']] += 1
print(' right word added only by the personal list:', injected)
X2 = np.vstack([np.hstack([X, user_feats]), np.array(X_extra, dtype=np.float32)]) if X_extra else np.hstack([X, user_feats])
y2 = np.concatenate([y, np.array(y_extra, dtype=int)]) if y_extra else y
qid2 = np.concatenate([qid, np.array(q_extra, dtype=int)]) if q_extra else qid
cands2 = cands + cand_extra


def fit_score(data, labels, groups, train_q, test_q):
    tr, te = np.isin(groups, train_q), np.isin(groups, test_q)
    order = np.argsort(groups[tr], kind='stable')
    counts = np.bincount(groups[tr][order], minlength=len(examples))
    m = lgb.LGBMRanker(objective='lambdarank', n_estimators=300, learning_rate=.05, num_leaves=31, min_child_samples=8, subsample=.85, subsample_freq=1, colsample_bytree=.85, verbose=-1, random_state=7)
    m.fit(data[tr][order], labels[tr][order], group=counts[counts > 0], eval_at=[3])
    s = np.zeros(len(labels))
    s[te] = m.predict(data[te])
    return s


def judge(scores, groups, names, chosen, label):
    ok1 = ok3 = 0
    for q in chosen:
        idx = np.where(groups == q)[0]
        order = idx[np.argsort(-scores[idx])]
        ranked = [names[i] for i in order[:3]]
        ok1 += ranked[:1] == [examples[q]['expected']]
        ok3 += examples[q]['expected'] in ranked
    r = {'n': len(chosen), 'first': round(100 * ok1 / len(chosen), 1), 'top3': round(100 * ok3 / len(chosen), 1)}
    print(f"  {label:<56} {r}")
    return r


out['personal'] = {
    'current engine, later 40%': judge(-X[:, 3], qid, cands, late, 'current engine on the later 40%'),
    'ranker no personal': judge(fit_score(X, y, qid, others + early, late), qid, cands, late, 'ranker, earlier text only, no personal features'),
    'ranker personal': judge(fit_score(X2, y2, qid2, others + early, late), qid2, cands2, late, 'ranker plus personal words and past corrections'),
}
json.dump(out, open(os.path.join(BASE, 'report', 'ranker-experiment.json'), 'w'), indent=1)

# ---- ideas: a strong language model, a memory of past swipe shapes, and personal use counts ----------------------------
print('\n=== Ideas, judged on the latest 40% of strokes and learned only from earlier ones ===', flush=True)


def when(e):
    if e['set'] == 'recorder':
        return (0, int(e['id'].split('-')[1]))
    return (1, attempts[int(e['id'].split('-')[1])])


order_q = sorted(range(len(examples)), key=lambda q: when(examples[q]))
rows_of = {q: np.where(qid == q)[0] for q in range(len(examples))}


def path40(e):
    c = corpus[e['id']]
    pts = np.array([c['x'], c['y']], dtype=np.float32).T * np.array([10, 3], dtype=np.float32)
    return resample(pts)


memory = defaultdict(list)
uses_before = Counter()
mem = np.zeros((len(y), 3), dtype=np.float32)
mem[:, 0] = np.nan
for q in order_q:
    e = examples[q]
    p = path40(e)
    for i in rows_of[q]:
        prior = memory.get(cands[i])
        if prior:
            mem[i] = (min(float(np.sqrt(((p - m) ** 2).sum(1)).mean()) for m in prior), len(prior), uses_before[cands[i]])
        else:
            mem[i] = (np.nan, 0, uses_before[cands[i]])
    memory[e['expected']].append(p)
    uses_before[e['expected']] += 1

LM_NAME = os.environ.get('LM_NAME', 'gpt2-medium')
gpt_path = os.path.join(PRIV, 'gpt-cands.json' if LM_NAME == 'gpt2-medium' else f'lm-cands-{LM_NAME.replace("/", "_")}.json')
print(' language model:', LM_NAME, flush=True)
if os.path.exists(gpt_path):
    gpt = json.load(open(gpt_path))
else:
    import torch
    from transformers import AutoModelForCausalLM, AutoTokenizer
    torch.set_num_threads(12)
    tok = AutoTokenizer.from_pretrained(LM_NAME)
    lm = AutoModelForCausalLM.from_pretrained(LM_NAME)
    lm.eval()
    gpt = {}
    for n, e in enumerate(examples):
        context = corpus[e['id']]['context'][-500:].rstrip(' ')
        encoded = tok(context if context.strip() else tok.bos_token, return_tensors='pt', truncation=True, max_length=200)
        with torch.no_grad():
            logp = torch.log_softmax(lm(**encoded).logits[0, -1], -1)
        scores = {}
        for w in e['cand']:
            best = -99.0
            for form in (w, w.capitalize()):
                ids = tok(' ' + form)['input_ids']
                if ids:
                    best = max(best, float(logp[ids[0]]))
            scores[w] = best
        gpt[e['id']] = scores
        if (n + 1) % 200 == 0:
            print('  language model', n + 1, flush=True)
    json.dump(gpt, open(gpt_path, 'w'))
g = np.zeros((len(y), 2), dtype=np.float32)
for q, e in enumerate(examples):
    idx = rows_of[q]
    vals = np.array([gpt[e['id']].get(cands[i], -99.0) for i in idx], dtype=np.float32)
    g[idx, 0] = vals
    g[idx, 1] = vals - vals.max()

cut = int(len(order_q) * .6)
train_q, test_q = order_q[:cut], order_q[cut:]
print(' train strokes', len(train_q), 'test strokes', len(test_q), 'test by set', dict(Counter(examples[q]['set'] for q in test_q)), flush=True)


def judge_on(scores, chosen, label):
    out = {}
    for s in ('all', 'prompted', 'hand', 'recorder'):
        qs = [q for q in chosen if s == 'all' or examples[q]['set'] == s]
        if not qs:
            continue
        ok1 = ok3 = 0
        for q in qs:
            idx = rows_of[q]
            order = idx[np.argsort(-scores[idx], kind='stable')]
            top = [cands[i] for i in order[:3]]
            ok1 += top[:1] == [examples[q]['expected']]
            ok3 += examples[q]['expected'] in top
        out[s] = {'n': len(qs), 'first': round(100 * ok1 / len(qs), 1), 'top3': round(100 * ok3 / len(qs), 1)}
    print(f'  {label:<52}', ' | '.join(f"{k} {v['first']}/{v['top3']} (n={v['n']})" for k, v in out.items()), flush=True)
    return out


def fit(data):
    tr = np.isin(qid, train_q)
    order = np.argsort(qid[tr], kind='stable')
    counts = np.bincount(qid[tr][order], minlength=len(examples))
    m = lgb.LGBMRanker(objective='lambdarank', n_estimators=300, learning_rate=.05, num_leaves=31, min_child_samples=8, subsample=.85, subsample_freq=1, colsample_bytree=.85, verbose=-1, random_state=7)
    m.fit(data[tr][order], y[tr][order], group=counts[counts > 0], eval_at=[3])
    return m.predict(data)


out['ideas'] = {}
out['ideas']['current engine'] = judge_on(-X[:, 3], test_q, 'current engine')
out['ideas']['ranker'] = judge_on(fit(X), test_q, 'ranker, no new signals')
out['ideas']['ranker + strong language model'] = judge_on(fit(np.hstack([X, g])), test_q, 'ranker + strong language model')
out['ideas']['ranker + shape memory and use counts'] = judge_on(fit(np.hstack([X, mem])), test_q, 'ranker + your past swipe shapes and use counts')
out['ideas']['ranker + all'] = judge_on(fit(np.hstack([X, g, mem])), test_q, 'ranker + all three')

# The idea as a plain rule: the engine's own order, with a boost for likely words and for words close to a past swipe of them.
best_score = np.nan_to_num(X[:, 7], nan=-60.0)
engine_rank = X[:, 3]
fitted = None
for lm_w in (0, .25, .5, 1, 2, 3):
    for mem_w in (0, 1, 2, 4, 8):
        for thr in (.4, .6, .8):
            near = np.nan_to_num(mem[:, 0], nan=99.0) < thr
            s = -engine_rank * 10 + best_score * .2 + lm_w * g[:, 1] + mem_w * near
            r = judge_on.__wrapped__(s, train_q) if False else None
            ok = 0
            for q in train_q:
                idx = rows_of[q]
                ok += cands[idx[np.argmax(s[idx])]] == examples[q]['expected']
            if fitted is None or ok > fitted[0]:
                fitted = (ok, lm_w, mem_w, thr)
print(' rule chosen on earlier strokes: language model weight', fitted[1], 'shape memory boost', fitted[2], 'closeness', fitted[3], flush=True)
near = np.nan_to_num(mem[:, 0], nan=99.0) < fitted[3]
rule = -engine_rank * 10 + best_score * .2 + fitted[1] * g[:, 1] + fitted[2] * near
out['ideas']['plain rule'] = judge_on(rule, test_q, 'plain rule: engine order + boosts')
json.dump(out, open(os.path.join(BASE, 'report', 'ranker-experiment.json'), 'w'), indent=1)
print('ideas done', flush=True)
