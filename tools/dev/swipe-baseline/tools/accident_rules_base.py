"""The accident labels of accident_rules.py, for other tools: strokes deleted with nothing kept in their place, against
kept or hand-corrected words, outside the known test windows of the 2026-11-08/09 logs."""
import datetime
import json
import os

import rawlog as R

TESTS = [('10:12:00', '11:05:00'), ('11:07:16', '11:07:18'), ('11:12:09', '11:13:04'), ('11:16:20', '11:16:48'), ('11:24:12', '11:24:20'), ('14:31:16', '14:40:59')]
TEST_DAYS = {'2026-11-08', '2026-11-09'}


def is_test(r):
    when = datetime.datetime.fromtimestamp(r['wall'] / 1000)
    t = when.strftime('%H:%M:%S')
    return when.strftime('%Y-%m-%d') in TEST_DAYS and any(a <= t <= b for a, b in TESTS)


def info(r):
    pts = R.key_units(r['chain_points'])
    length = R.path_length(pts) if len(pts) > 1 else 0
    dur = pts[-1][2] - pts[0][2] if len(pts) > 1 else 0
    s = r['scores'] or []
    margin = s[0][1] - s[1][1] if len(s) > 1 else 99
    return length, dur, margin


def labelled(private_dir):
    rows = [json.loads(l) for l in open(os.path.join(private_dir, 'attempts.jsonl'), encoding='utf-8')]
    stroke = [r for r in rows if r['superseded_by'] is None and not is_test(r)]
    gone = [r for r in stroke if r['hand'] is None and any(e.startswith('backspace') for e in r['slot_events'])]
    kept = [r for r in stroke if r['hand'] is not None]
    return gone, kept
