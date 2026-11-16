#!/usr/bin/env python3
"""Issue #83, second look: the context pilot (context.py) again, with the phone's own readings instead of the PC's.

phone-replay.sh reads every pair that could join three ways on the phone, with the app's whole recogniser: joined
(J), the first swipe alone (A) and the second alone (B). This takes those readings for the top-row pairs and scores
the two readings as context.py does (the were/we're study's model, BPE and window):

    margin = score(text before + joined word) - score(text before + first word + second word), in nats

Two ways: the log's words where it has them and the phone's for the rest (the pilot's way, with the phone in place of
the PC), and all three from the phone. The replay only holds pairs that could join, so top-row pairs kept apart by a
long gap outside the old window aren't in it; the coverage is printed.

Prints counts and margins only, never text. Writes per-pair numbers (no words) to <private>/context-phone-pairs.jsonl.
Usage: python3 phone_context.py <repo root> <private dir> <baseline tools dir>
Needs <private>/replay-corpus.jsonl and replay-out.jsonl from phone-replay.sh, and the apostrophe study's venv.
"""
import importlib.util
import json
import os
import sys
from collections import Counter

ROOT, PRIV, TOOLS = sys.argv[1:4]
HERE = os.path.dirname(os.path.abspath(__file__))
# context.py reads its paths from sys.argv and loads the were/we're study's model; reuse its window, casing and stats.
spec = importlib.util.spec_from_file_location('context', os.path.join(HERE, 'context.py'))
X = importlib.util.module_from_spec(spec)
spec.loader.exec_module(X)
W = X.W


def main():
    rows = {}
    for line in open(os.path.join(PRIV, 'attempts.jsonl'), encoding='utf-8'):
        a = json.loads(line)
        rows[a['id']] = a
    pairs = [json.loads(l) for l in open(os.path.join(PRIV, 'pairs.jsonl'))]
    new = {l.split()[0]: l.split()[1:3] for l in open(os.path.join(PRIV, 'new-rule.txt'))}
    top_word = {}
    for line in open(os.path.join(PRIV, 'replay-out.jsonl'), encoding='utf-8'):
        e = json.loads(line)
        top_word[e['id']] = X.word(e['top'][0]) if e['top'] else None

    def chain_start(r):
        while r['joined_from'] is not None and r['joined_from'] in rows:
            r = rows[r['joined_from']]
        return r

    top = [p for p in pairs if p['a_end_y'] < X.TOP_ROW and p['b_start_y'] is not None and p['b_start_y'] < X.TOP_ROW
           and 0 <= p['gap'] <= X.MAX_GAP]
    covered = [p for p in top if all(k + '|' + p['pair'] in top_word for k in 'JAB')]
    print(f'top-row pairs within {X.MAX_GAP} ms: {len(top)}; read three ways on the phone: {len(covered)}')
    print('  by label, all:', dict(Counter(p['label'] for p in top)))
    print('  by label, on the phone:', dict(Counter(p['label'] for p in covered)))

    agree, out = Counter(), []
    for p in covered:
        a_id, b_id = map(int, p['pair'].split('-'))
        a, b = rows[a_id], rows[b_id]
        ph_j, ph_a, ph_b = (top_word[k + '|' + p['pair']] for k in 'JAB')
        first = X.word(a['committed'])
        if p['joined']:
            joined, second = X.word(b['committed']), ph_b
            agree['joined pairs'] += 1
            agree['logged joined word = phone joined reading'] += joined == ph_j
        else:
            joined, second = ph_j, X.word(b['committed'])
            agree['pairs kept apart'] += 1
            agree['logged second word = phone reading'] += second == ph_b
        agree['pairs'] += 1
        agree['logged first word = phone reading'] += first == ph_a
        rec = {'pair': p['pair'], 'label': p['label'], 'same_now': new[p['pair']][1] == 'true'}
        before = chain_start(a)['context']
        if None not in (joined, first, second):
            rec['margin'] = X.margin(before, joined, first, second, [])
        if None not in (ph_j, ph_a, ph_b):
            rec['margin_phone'] = X.margin(before, ph_j, ph_a, ph_b, [])
        out.append(rec)

    with open(os.path.join(PRIV, 'context-phone-pairs.jsonl'), 'w') as f:
        for rec in out:
            f.write(json.dumps(rec) + '\n')
    print('\nThe phone against the log:')
    for k, v in agree.items():
        print(f'  {k}: {v}')
    print(f'  a reading missing (log way / all phone): {sum("margin" not in r for r in out)} / {sum("margin_phone" not in r for r in out)}')

    for key, name in (('margin', "the log's words, the phone's for the rest"), ('margin_phone', 'all three from the phone')):
        scored = [r for r in out if key in r]
        print(f'\n== Margin, text before only, {name}: joined word over the two words, nats')
        for g in X.GROUPS:
            print(f'  {g:<15} {X.stats([r[key] for r in scored if r["label"] == g])}')
        v = lambda labs: [r[key] for r in scored if r['label'] in labs]
        print(f'  separation: right join vs right non-join {X.auc(v({"right join"}), v({"right non-join"})):.2f},'
              f' right join vs wrong join {X.auc(v({"right join"}), v({"wrong join"})):.2f},'
              f' one word vs two {X.auc(v(X.ONE), v(X.TWO)):.2f}')
        geo = [r for r in scored if r['same_now']]
        rj = [r[key] for r in geo if r['label'] == 'right join']
        print(f'  geometry (sameStroke now) joins {len(geo)}: {dict(Counter(r["label"] for r in geo))}')
        if rj:
            edge = min(rj)
            for t in sorted({-20, -15, -12, -10, -8, -6, -4} | {round(edge - .01, 2)}):
                cut = Counter(r['label'] for r in geo if r[key] <= t)
                print(f'    veto at margin <= {t:>7.2f}: right joins lost {cut["right join"]:>3} of {len(rj)},'
                      f' wrong joins cut {cut["wrong join"]:>3}, unclear cut {cut["unclear"]:>3}, other cut {cut["other"]:>3},'
                      f' right non-joins cut {cut["right non-join"]}')
            wrong = sorted(r[key] for r in geo if r['label'] == 'wrong join')
            print(f'  lowest right join {edge:.2f}; wrong joins below it: {sum(w < edge for w in wrong)} of {len(wrong)}')


if __name__ == '__main__':
    main()
