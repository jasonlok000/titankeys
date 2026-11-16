
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
