#!/usr/bin/env python3
"""Issue #83: the phone replay of every pair that could join, read joined, first alone and second alone, with the
app's recogniser (the baseline's replay.sh and BaselineRunner). It shows whether the joins the new rule adds or cuts
really give the word the owner kept, which the logs alone can't.

  replay_pairs.py export attempts.jsonl pairs.jsonl old-rule.txt new-rule.txt corpus.jsonl
  replay_pairs.py score corpus.jsonl replay-out.jsonl pairs.jsonl old-rule.txt new-rule.txt report.json

Within 150 ms the keyboard reads a pair both ways and joins it when the joined reading leads by more than the first
word's own lead (Composer's check). The score runs that check on the replayed readings, so it also shows whether the
check glues quick separate words, like "in" then "to" (the #83 review), with a stricter lead alongside for comparison.

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
        pair, could, same = line.split()[:3]
        out[pair] = (could == 'true', same == 'true')
    return out


CHECK_MILLIS = 150


def candidates(pairs, old, new):
    return [r for r in pairs if r['joined'] or r['label'] == 'missed join' or old[r['pair']][0] or new[r['pair']][0]
            or 0 <= r['gap'] <= CHECK_MILLIS]


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
    top, margin = {}, {}
    for line in open(replay_path, encoding='utf-8'):
        e = json.loads(line)
        top[e['id']] = key(e['top'][0]) if e['top'] else None
        s = e.get('scores') or []
        margin[e['id']] = s[0][1] - s[1][1] if len(s) > 1 else (99 if s else None)
    pairs = [json.loads(l) for l in open(pairs_path)]
    old, new = load_rule(old_path), load_rule(new_path)

    def right(kind, pair):
        i = kind + '|' + pair
        return bool(i in top and expected.get(i) and top[i] == expected[i])

    def check(pair, gap, lead):
        """Composer's 150 ms check: the joined reading must lead by more than the first word's own lead (plus lead)."""
        j, a = margin.get('J|' + pair), margin.get('A|' + pair)
        return bool(0 <= gap <= CHECK_MILLIS and j is not None and a is not None and top.get('J|' + pair) and j > a + lead)

    res = Counter()
    for p in candidates(pairs, old, new):
        pair, lab, gap = p['pair'], p['label'], p['gap']
        j, a = right('J', pair), right('A', pair)
        res[f'{lab}: joined reading right'] += j
        res[f'{lab}: first alone right'] += a
        res[f'{lab}: pairs'] += 1
        for name, same, lead in (('before #83', old[pair][1], 0), ('now', new[pair][1], 0), ('now, lead 2', new[pair][1], 2)):
            joins = same or check(pair, gap, lead)
            res[f'{name}: joined'] += joins
            res[f'{name}: right'] += j if joins else a
            if 0 <= gap <= CHECK_MILLIS and not same:
                # Pairs only the check decides: does it glue words the owner kept apart, or find the ones it should join?
                res[f'{name}: check joins {lab}'] += joins
        if old[pair][1] != new[pair][1] or check(pair, gap, 0) != check(pair, gap, 2):
            res['pairs whose decision changes'] += 1
    summary = dict(sorted(res.items()))
    json.dump(summary, open(out_path, 'w'), indent=1)
    print(json.dumps(summary, indent=1))


if __name__ == '__main__':
    {'export': export, 'score': score}[sys.argv[1]](*sys.argv[2:])
