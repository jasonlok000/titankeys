#!/usr/bin/env python3
"""Scores CompletionPilot output by part: tuning (not the frozen 540), the frozen 540, or everything in a file (the new
log). For each variant: top 1 and top 3 after 1 to 4 letters, and letters typed before the word is in the top 3.
Usage: score_pilot.py pilot.jsonl frozen-540.json part[tuning|held|new|all] [set] [new-ids.json]"""
import json
import sys

sys.path.insert(0, __import__('os').path.dirname(__import__('os').path.abspath(__file__)))
from CompletionVariants import NAMES  # noqa: E402

path, frozen_path, part = sys.argv[1:4]
only = sys.argv[4] if len(sys.argv) > 4 and sys.argv[4] != 'any' else None
frozen = {s['id'] for s in json.load(open(frozen_path))['strokes']}
new = {s['id'] for s in json.load(open(sys.argv[5]))['strokes']} if len(sys.argv) > 5 else set()
rows = [json.loads(l) for l in open(path, encoding='utf-8')]
if part == 'tuning':
    rows = [r for r in rows if r['id'] not in frozen and r['id'] not in new]
elif part == 'new':
    rows = [r for r in rows if r['id'] in new]
elif part == 'held':
    rows = [r for r in rows if r['id'] in frozen]
if only:
    rows = [r for r in rows if r['set'] == only]
print(f'{part} {only or "all sets"}: {len(rows)} words')
print(f"{'variant':<26}" + ''.join(f'{k} letter{"s" if k > 1 else " "} top1/top3  ' for k in (1, 2, 3, 4)) + 'letters typed')
for v, name in enumerate(NAMES):
    cells = []
    for k in (1, 2, 3, 4):
        at = [r for r in rows if r['len'] > k]
        t1 = sum(r['ranks'][k - 1][v] == 0 for r in at)
        t3 = sum(0 <= r['ranks'][k - 1][v] < 3 for r in at)
        cells.append(f'{100 * t1 / max(1, len(at)):5.1f}/{100 * t3 / max(1, len(at)):5.1f} ({len(at):>3})  ')
    typed = sum(r['found'][v] if r['found'][v] > 0 else r['len'] for r in rows) / max(1, len(rows))
    print(f'{name:<26}' + ''.join(cells) + f'{typed:.3f}')
