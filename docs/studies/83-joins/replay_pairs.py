#!/usr/bin/env python3
"""Issue #83: the phone replay of every pair that could join, read joined, first alone and second alone, with the
app's recogniser (the baseline's replay.sh and BaselineRunner). It shows whether the joins the new rule adds or cuts
really give the word the owner kept, which the logs alone can't.

  replay_pairs.py export attempts.jsonl pairs.jsonl old-rule.txt new-rule.txt corpus.jsonl
  replay_pairs.py score corpus.jsonl replay-out.jsonl pairs.jsonl old-rule.txt new-rule.txt report.json

The corpus holds typed text, so it stays in the private folder. The report is numbers only.
"""
import json
import sys
from collections import Counter


def key(w):
    return (w or '').lower().replace("'", '').replace('’', '')


def load_rule(path):
    out = {}
    for line in open(path):
        pair, could, same = line.split()
        out[pair] = (could == 'true', same == 'true')
    return out


def candidates(pairs, old, new):
    return [r for r in pairs if r['joined'] or r['label'] == 'missed join' or old[r['pair']][0] or new[r['pair']][0]]


def export(attempts_path, pairs_path, old_path, new_path, out_path):
    rows = {r['id']: r for r in (json.loads(l) for l in open(attempts_path, encoding='utf-8'))}
    pairs = [json.loads(l) for l in open(pairs_path)]
    old, new = load_rule(old_path), load_rule(new_path)

    def points(ps):
        ps = [p for p in ps if p[0] is not None and p[1] is not None]
        t0 = ps[0][2]
        return [round(p[0], 5) for p in ps], [round(p[1] * 4 / 3, 5) for p in ps], [p[2] - t0 for p in ps]

    def first_of_chain(r):
        while r['joined_from'] is not None and r['joined_from'] in rows:
            r = rows[r['joined_from']]
        return r

    n = 0
    with open(out_path, 'w', encoding='utf-8') as out:
        for p in candidates(pairs, old, new):
            a_id, b_id = map(int, p['pair'].split('-'))
            a, b = rows[a_id], rows[b_id]
            context = first_of_chain(a)['context']
            # What the owner kept: one word for a join, else the first word and the second.
            joined_word = b['hand'] if p['joined'] else a['hand']
            readings = {
                'J': (a['chain_points'] + b['points'], context, joined_word or ''),
                'A': (a['chain_points'], context, a['hand'] or ''),
                'B': (b['points'], context + (a['committed'] or '') + ' ', b['hand'] or ''),
            }
            for kind, (ps, ctx, expected) in readings.items():
                x, y, t = points(ps)
                if t[-1] > 9000:
                    continue
                out.write(json.dumps({'id': kind + '|' + p['pair'], 'set': 'join83', 'style': p['label'], 'slot': 0, 'first': True,
                                      'expected': expected, 'context': ctx, 'live': [], 'x': x, 'y': y, 't': t}, ensure_ascii=False) + '\n')
                n += 1
    print('replay lines', n)


def score(corpus_path, replay_path, pairs_path, old_path, new_path, out_path):
    expected = {}
    for line in open(corpus_path, encoding='utf-8'):
        e = json.loads(line)
        expected[e['id']] = key(e['expected'])
    top = {}
    for line in open(replay_path, encoding='utf-8'):
        e = json.loads(line)
        top[e['id']] = key(e['top'][0]) if e['top'] else None
    pairs = [json.loads(l) for l in open(pairs_path)]
    old, new = load_rule(old_path), load_rule(new_path)

    def right(kind, pair):
        i = kind + '|' + pair
        return i in top and expected.get(i) and top[i] == expected[i]

    res = Counter()
    for p in candidates(pairs, old, new):
        pair, lab = p['pair'], p['label']
        j, a = right('J', pair), right('A', pair)
        res[f'{lab}: joined reading right'] += bool(j)
        res[f'{lab}: first alone right'] += bool(a)
        res[f'{lab}: pairs'] += 1
        o, n = old[pair][1], new[pair][1]
        logged = p['joined']
        joins = (False if logged and o and not n else True if not logged and n and not o else logged)
        if joins == logged:
            continue
        # A changed decision: does the word the keyboard now types match what the owner kept?
        before, after = (j, a) if logged else (a, j)
        res['changed pairs'] += 1
        res['changed: fixed'] += bool(after and not before)
        res['changed: broken'] += bool(before and not after)
        res['changed: right both ways'] += bool(before and after)
        res['changed: wrong both ways'] += bool(not before and not after)
    summary = dict(sorted(res.items()))
    json.dump(summary, open(out_path, 'w'), indent=1)
    print(json.dumps(summary, indent=1))


if __name__ == '__main__':
    {'export': export, 'score': score}[sys.argv[1]](*sys.argv[2:])
