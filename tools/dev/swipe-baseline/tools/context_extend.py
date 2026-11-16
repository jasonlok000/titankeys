#!/usr/bin/env python3
"""Does the predictor improve on everyday text when it can also see what you wrote just before, in earlier fields?
Counts only. Usage: context_extend.py baseline-dir"""
import json
import os
import sys
from collections import defaultdict

import torch
from transformers import AutoModelForCausalLM, AutoTokenizer

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rawlog as R

P = (os.environ.get('TK_PRIVATE') or sys.exit('TK_PRIVATE is not set: source tools/dev/env.sh first'))
gpt = json.load(open(os.path.join(P, 'gpt-cands.json')))
rows = {json.loads(l)['id']: json.loads(l) for l in open(os.path.join(P, 'attempts.jsonl'), encoding='utf-8')}
corpus = {json.loads(l)['id']: json.loads(l) for l in open(os.path.join(P, 'corpus.jsonl'), encoding='utf-8')}
replay = {json.loads(l)['id']: json.loads(l) for l in open(os.path.join(P, 'replay-4f75dcb-full.jsonl'), encoding='utf-8')}


def key(w):
    return R.norm(w).replace("'", '')


# What each field held when you last typed in it, and when that was.
fields = defaultdict(lambda: {'text': '', 'wall': 0})
for r in sorted(rows.values(), key=lambda r: r['wall']):
    f = fields[r['field']]
    text = r['context'] + (r['hand'] or '')
    if len(text) >= len(f['text']):
        f['text'] = text
    f['wall'] = r['wall']
order = sorted(fields, key=lambda k: fields[k]['wall'])

torch.set_num_threads(12)
tok = AutoTokenizer.from_pretrained('gpt2-medium')
lm = AutoModelForCausalLM.from_pretrained('gpt2-medium')
lm.eval()


def scores(context, cands):
    encoded = tok(context.rstrip(' ') if context.strip() else tok.bos_token, return_tensors='pt', truncation=True, max_length=200)
    with torch.no_grad():
        logp = torch.log_softmax(lm(**encoded).logits[0, -1], -1)
    out = {}
    for w in cands:
        best = -99.0
        for form in (w, w.capitalize()):
            ids = tok(' ' + form)['input_ids']
            if ids:
                best = max(best, float(logp[ids[0]]))
        out[w] = best
    return out


counts = defaultdict(lambda: defaultdict(int))
for cid, cands in gpt.items():
    c = corpus[cid]
    if c['set'] != 'hand':
        continue
    r = rows[int(cid.split('-')[1])]
    exp = key(c['expected'])
    if exp not in cands:
        continue
    words_before = len(c['context'].split())
    band = 'no words before it' if words_before == 0 else '1-3 words before it' if words_before <= 3 else '4+ words before it'
    earlier = [fields[k]['text'] for k in order if k != r['field'] and fields[k]['wall'] < r['wall'] and r['wall'] - fields[k]['wall'] < 15 * 60 * 1000][-3:]
    extended = ('\n'.join(t.strip() for t in earlier if t.strip()) + '\n' + c['context'])[-500:]
    plain_first = max(cands, key=lambda w: cands[w]) == exp
    wider = scores(extended, cands)
    wider_first = max(wider, key=lambda w: wider[w]) == exp
    engine_wrong = [key(w) for w in replay[cid]['top']][:1] != [exp]
    for b in (band, 'all'):
        st = counts[b]
        st['n'] += 1
        st['had earlier text'] += bool(earlier)
        st['predictor first, this field only'] += plain_first
        st['predictor first, with earlier text'] += wider_first
        if engine_wrong:
            st['engine wrong'] += 1
            st['  of those, first with this field only'] += plain_first
            st['  of those, first with earlier text'] += wider_first
for b in ('no words before it', '1-3 words before it', '4+ words before it', 'all'):
    st = counts[b]
    n = st['n']
    print(f"== everyday words, right word among the candidates, {b}: n={n}")
    for k, v in st.items():
        if k == 'n':
            continue
        base = st['engine wrong'] if k.startswith('  of') else n
        print(f'   {k}: {v} ({100 * v / max(base, 1):.1f}%)')
