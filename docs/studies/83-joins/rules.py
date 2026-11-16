#!/usr/bin/env python3
"""Issue #83: replays candidate joining rules on every labelled pair from pairs.py and counts what each would change.

Today's decision is what the log recorded (the app's own Swipe.sameStroke reproduces it; see live-rule.txt). A rule
here either cuts some of today's joins or adds joins today's code did not make. For each it reports, against today:
  right joins kept, wrong joins cut (pairs and episodes: a run of joins counts once), unclear joins cut,
  missed joins found, right non-joins wrongly joined, and pairs with another outcome that it would join,
plus a rough change in the headline (prompted first-choice): each fixed pair counts +1, each broken pair -1.
A missed join found counts as fixed only if the engine reads the joined swipe right, which only a phone replay shows,
so the headline column is a ceiling for the adding rules. Numbers only.
Usage: rules.py pairs.jsonl live-rule.txt out.json
"""
import json
import sys
from collections import Counter

pairs_path, live_path, out_path = sys.argv[1:4]
rows = [json.loads(l) for l in open(pairs_path)]
live = {}
for line in open(live_path):
    pair, could, same = line.split()
    live[pair] = (could == 'true', same == 'true')
for r in rows:
    r['could'], r['same'] = live[r['pair']]


def g(r, f, missing=0.0):
    v = r.get(f)
    return missing if v is None else v


TOP_ROW, TOP_EDGE = 1 / 3, .267


def top_gate(r):
    """Today's window: the first ended on the top row, the second starts at the top edge, close by, soon."""
    return r['gap'] <= 400 and r['a_end_y'] < TOP_ROW and r['b_start_y'] < TOP_EDGE and r['jump_keys'] <= 3.5


def tiny(r):
    return r['b_alone_tiny']


def clean_first(r, margin=10, per_letter=-.3):
    """The first swipe already read as one clear word: a wide lead over the next word and a close path fit."""
    return g(r, 'a_margin') >= margin and g(r, 'a_path_per_letter', -9) >= per_letter


def moving_landing(r, speed=.004):
    return r['b_start_speed'] >= speed


# Each rule maps a pair to True (join) or False (keep apart), starting from what the keyboard did.
CUT = {
    'C1 a second touch over 250 ms later': lambda r: r['gap'] > 250,
    'C2 over 150 ms later and the first word was clear': lambda r: r['gap'] > 150 and clean_first(r),
    'C3 first word clear and second swipe long (5+ keys)': lambda r: r['b_len'] >= 5 and clean_first(r),
    'C4 the second touch lands still (new words land still)': lambda r: not moving_landing(r, .002) and not tiny(r),
    'C5 a still flicker off the top row (resting finger)': lambda r: not top_gate(r) and tiny(r) and r['b_start_speed'] == 0,
    'C2+C5': lambda r: (r['gap'] > 150 and clean_first(r)) or (not top_gate(r) and tiny(r) and r['b_start_speed'] == 0),
    # Contact size is logged from 2.0 on; without it these keep today's join.
    'C6 over 150 ms later and the first ended with a full-size touch (2+ steps)':
        lambda r: r['gap'] > 150 and g(r, 'a_end_size') >= 2,
    'C7 C2 or C6': lambda r: r['gap'] > 150 and (clean_first(r) or g(r, 'a_end_size') >= 2),
}
ADD = {
    'A1 lands moving within 150 ms, 2 keys or less, anywhere on the pad':
        lambda r: r['gap'] < 150 and moving_landing(r) and r['jump_keys'] <= 2,
    'A2 lands moving within 150 ms, anywhere (no distance limit)': lambda r: r['gap'] < 150 and moving_landing(r),
    'A3 today\'s window widened to the whole top row when it lands moving':
        lambda r: r['gap'] <= 150 and r['a_end_y'] < TOP_ROW and r['b_start_y'] < TOP_ROW and r['jump_keys'] <= 3.5 and moving_landing(r),
    'A4 within 100 ms, 2 keys or less, anywhere': lambda r: r['gap'] < 100 and r['jump_keys'] <= 2,
}


def evaluate(name, decide, kind):
    res = Counter()
    eps = set()
    for r in rows:
        lab, joined = r['label'], r['joined']
        if kind == 'cut' and joined:
            keep = not decide(r)
            if lab == 'right join':
                res['right joins'] += 1
                res['right joins kept'] += keep
                res['right lost ' + ('1.2' if r['version'] == '1.2.0' else '2.x')] += not keep
                res['headline'] -= (not keep) and r['set'] == 'prompted'
            elif lab == 'wrong join':
                res['wrong joins'] += 1
                if not keep:
                    res['wrong joins cut'] += 1
                    res['wrong cut ' + ('1.2' if r['version'] == '1.2.0' else '2.x')] += 1
                    eps.add(r['episode'])
                    res['headline'] += r['set'] == 'prompted'
            elif lab == 'unclear':
                res['unclear joins'] += 1
                res['unclear cut'] += not keep
        elif kind == 'add' and not joined:
            j = decide(r)
            if lab == 'missed join':
                res['missed joins'] += 1
                res['missed found'] += j
                res['headline'] += j and r['set'] == 'prompted'
            elif lab == 'right non-join':
                res['right non-joins joined'] += j
                res['headline'] -= j and r['set'] == 'prompted'
            else:
                res['other pairs joined'] += j
    res['wrong episodes cut'] = len(eps)
    return dict(res)


wrong_eps = {r['episode'] for r in rows if r['label'] == 'wrong join'}
summary = {
    'pairs': len(rows),
    'labels': dict(Counter(('joined: ' if r['joined'] else 'apart: ') + r['label'] for r in rows)),
    'wrong join episodes': len(wrong_eps),
    'joins with contact size logged': dict(Counter(r['label'] for r in rows if r['joined'] and r['a_end_size'] is not None)),
    'joins by version': dict(Counter(r['version'] for r in rows if r['joined'])),
    'logged joins the app\'s sameStroke reproduces': sum(r['same'] for r in rows if r['joined']),
    'apart pairs inside today\'s window (couldContinue)': dict(Counter(r['label'] for r in rows if not r['joined'] and r['could'])),
    'rules': {},
}


def group(r):
    if r['joined']:
        return r['label']
    if r['label'] == 'missed join':
        return 'missed join'
    if r['label'] == 'right non-join' and r['could']:
        return 'right non-join in window'
    return None


FEATURES = ['gap', 'a_end_y', 'b_start_y', 'jump_keys', 'a_end_speed', 'b_start_speed', 'carries_on', 'reverses', 'a_len',
            'b_len', 'b_dur', 'a_margin', 'a_path_per_letter', 'b_margin', 'a_end_size', 'b_start_size']


def q(v, p):
    v = sorted(v)
    return round(v[int(p * (len(v) - 1))], 4) if v else None


groups = {}
for r in rows:
    k = group(r)
    if k:
        groups.setdefault(k, []).append(r)
summary['features (p10, median, p90)'] = {
    f: {k: [q([r[f] for r in v if r[f] is not None], p) for p in (.1, .5, .9)] for k, v in groups.items()} for f in FEATURES}
summary['group sizes'] = {k: len(v) for k, v in groups.items()}
summary['share landing moving (0.004 keys/ms or more)'] = {k: round(sum(r['b_start_speed'] >= .004 for r in v) / len(v), 2) for k, v in groups.items()}
summary['share with a tiny second touch'] = {k: round(sum(r['b_alone_tiny'] for r in v) / len(v), 2) for k, v in groups.items()}
summary['right joins that changed the word'] = sum(bool(r['join_changed_word']) for r in rows if r['label'] == 'right join')
summary["kept pairs ending and starting on the top row, inside today's window"] = sum(
    1 for r in rows if r['label'] == 'right non-join' and r['could'] and r['tops'] == [True, True])
summary['kept pairs ending and starting on the top row, gap 400 ms or less'] = sum(
    1 for r in rows if r['label'] == 'right non-join' and r['tops'] == [True, True] and r['gap'] <= 400)
for name, rule in CUT.items():
    summary['rules'][name] = evaluate(name, rule, 'cut')
for name, rule in ADD.items():
    summary['rules'][name] = evaluate(name, rule, 'add')
json.dump(summary, open(out_path, 'w'), indent=1)
for k, v in summary.items():
    if k not in ('rules', 'features (p10, median, p90)'):
        print(k, v)
for f, by in summary['features (p10, median, p90)'].items():
    print(f, by)
for name, res in summary['rules'].items():
    print(name)
    print('   ', ', '.join(f'{k} {v}' for k, v in res.items()))
