
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

gpt_path = os.path.join(PRIV, 'gpt-cands.json')
if os.path.exists(gpt_path):
    gpt = json.load(open(gpt_path))
else:
    import torch
    from transformers import AutoModelForCausalLM, AutoTokenizer
    torch.set_num_threads(12)
    tok = AutoTokenizer.from_pretrained('gpt2-medium')
    lm = AutoModelForCausalLM.from_pretrained('gpt2-medium')
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
