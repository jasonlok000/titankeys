#!/usr/bin/env python3
"""Turns a raw input log into the labelled attempt table the baseline is computed from.
Usage: build.py raw-log.jsonl Passages.java out-dir"""
import json
import os
import sys
from collections import Counter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import passages as P
import rawlog as R

log_path, passages_path, out_dir = sys.argv[1:4]
os.makedirs(out_dir, exist_ok=True)
events = R.load(log_path)
rep = R.Replayer(events).run()
texts = P.load(passages_path)
style_text = {i: t for i, t in enumerate(texts['styles'])}

report = {'stats': dict(rep.stats), 'attempts': len(rep.attempts), 'with_rec': sum(1 for a in rep.attempts if a.rec),
          'reconstruction': dict(Counter(a.reconstructed for a in rep.attempts)), 'blocks': {}}

# Label prompted blocks by aligning the final text with the passage it best matches.
slot_expected = {}
slot_final = {}
for name, chars in rep.block_snapshots.items():
    tw = R.tokens_with_slots(chars)
    typed = [t[0] for t in tw]
    choice = max(style_text, key=lambda i: sum(w in set(R.tokens_of(style_text[i])) for w in typed))
    words = R.tokens_of(style_text[choice])
    pairs, end = R.align(typed, words)
    matched = sum(1 for i, j in pairs if i is not None and j is not None and typed[i] == words[j])
    sub = sum(1 for i, j in pairs if i is not None and j is not None and typed[i] != words[j])
    ins = sum(1 for i, j in pairs if j is None)
    skip = sum(1 for i, j in pairs if i is None)
    for i, j in pairs:
        if i is None:
            continue
        sid = tw[i][1]
        if sid is None:
            continue
        slot_final.setdefault(sid, typed[i])
        if j is not None:
            slot_expected[sid] = words[j]
    report['blocks'][name] = {'passage': choice + 1, 'typed_words': len(typed), 'passage_words_reached': end,
                              'matched': matched, 'substituted': sub, 'inserted': ins, 'skipped': skip}

final_words = {}
for field_chars in [rep.buf]:
    pass


def letters_only(word):
    return R.norm(word).replace("'", '')


rows = []
for a in rep.attempts:
    if a.rec is None or a.slot is None:
        continue
    slot = rep.slots[a.slot]
    row = {'id': a.id, 'field': a.field, 'block': a.block, 'slot': a.slot, 'wall': rep.clock.wall(a.out['start']), 'start': a.out['start'],
           'end': a.out['end'], 'n': a.out['n'], 'recon': a.reconstructed, 'contacts': a.contacts, 'gaps': a.gaps,
           'words': a.rec['words'], 'scores': a.rec.get('scores'), 'resumed': a.rec['resumed'], 'committed': a.committed,
           'context': a.context[-400:], 'joined_from': a.joined_from, 'superseded_by': a.superseded_by,
           'expected': slot_expected.get(a.slot), 'final': slot_final.get(a.slot), 'slot_attempts': slot.attempts,
           'position': slot.attempts.index(a.id), 'slot_events': [e[0] for e in slot.events],
           'slot_pick': next((e[2] for e in reversed(slot.events) if e[0] == 'choose'), None), 'points': a.points,
           'sizes': a.sizes, 'size_points': a.size_points,
           # Engine 2.0 chose the order (the rec line's "ranked"); absent in logs from before 2.0.0.
           'ranked': bool(a.rec.get('ranked')),
           # The owner corrected the text by hand, so what stayed in the slot is what was meant.
           'hand': R.norm(slot.final) if slot.final else None}
    rows.append(row)

# Chains: a piece that a later swipe rejoined does not count as an attempt of its own.
by_id = {r['id']: r for r in rows}
for r in rows:
    pts = r['points']
    links = [r]
    while links[0]['joined_from'] is not None and links[0]['joined_from'] in by_id:
        links.insert(0, by_id[links[0]['joined_from']])
    r['chain'] = [x['id'] for x in links]
    if len(links) > 1:
        joined, offset = [], 0
        for x in links:
            joined += x['points']
        r['chain_points'] = joined
    else:
        r['chain_points'] = pts

with open(os.path.join(out_dir, 'attempts.jsonl'), 'w', encoding='utf-8') as f:
    for r in rows:
        f.write(json.dumps(r, ensure_ascii=False) + '\n')

deletes = [{**d, 'wall': rep.clock.wall(d['start'])} for d in rep.deletes]
contacts = [{'d': c['d'], 'u': c['u'], 'n': len(c['pts']), 'out': c['out'], 'cancel': c['cancel'],
             'first': c['pts'][0][:2] if c['pts'] else None, 'last': c['pts'][-1][:2] if c['pts'] else None,
             'dur': (c['pts'][-1][2] - c['pts'][0][2]) if c['pts'] else 0,
             'travel': max((R.dist((p[0] * 10, p[1] * 4), (c['pts'][0][0] * 10, c['pts'][0][1] * 4)) for p in c['pts'] if p[0] is not None and c['pts'][0][0] is not None), default=0)}
            for c in rep.contacts]
json.dump({'deletes': deletes, 'contacts': contacts, 'report': report}, open(os.path.join(out_dir, 'extras.json'), 'w'))
print(json.dumps(report, indent=1))
c = Counter()
for r in rows:
    c[('block' if r['block'] else 'free', r['expected'] is not None)] += 1
print(c)
sample = [r for r in rows if r['block'] and r['block'].startswith('style')][:0]
