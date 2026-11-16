#!/usr/bin/env python3
"""#30 mistaps: turns a raw log into the plain input that HapticReplay.kt feeds to the keyboard's own Contact class.
Lines: 'M action time n (x y time step)*n' for touchpad motion and 'K code down time' for physical keys (firmware gesture
keys left out, as the keyboard leaves them out). Only touches and keys, no text. Usage: haptic_events.py raw-log.jsonl out.txt, or haptic_events.py --rest recording.jsonl out.txt for the resting-touch
recording, whose touches (phases 1 to 7, before 400 s) replay one point per motion and have no key presses."""
import json
import sys

if sys.argv[1] == '--rest':
    rows = [json.loads(l) for l in open(sys.argv[2], encoding='utf-8')][1:]
    first = rows[0]['start']
    with open(sys.argv[3], 'w') as out:
        for r in rows:
            if (r['start'] - first) / 1000 >= 400:
                continue
            pts = [p for p in r['points'] if p[0] is not None]
            for i, p in enumerate(pts):
                action = 0 if i == 0 else 1 if i == len(pts) - 1 else 2
                out.write(f"M {action} {p[4]} 1 {p[0]!r} {p[1] * .75!r} {p[4]} {p[2]}\n")
            if len(pts) == 1:
                p = pts[0]
                out.write(f"M 1 {p[4]} 1 {p[0]!r} {p[1] * .75!r} {p[4]} {p[2]}\n")
    sys.exit()

log_path, out_path = sys.argv[1:3]
with open(out_path, 'w') as out:
    for line in open(log_path, encoding='utf-8'):
        e = json.loads(line)
        k = e.get('k')
        if k == 'm':
            samples = e.get('s') or []
            sizes = e.get('sz') or []
            raw = e.get('p') or []
            # Sizes belong to the raw points. The samples are the same points, aligned, when the counts agree.
            steps = [round(s / 1.125) if s is not None else 0 for s in sizes] if len(sizes) == len(samples) == len(raw) else [0] * len(samples)
            parts = ['M', str(e['a']), str(e['t']), str(len(samples))]
            for (x, y, t), step in zip(samples, steps):
                parts += ['nan' if x is None else repr(x), 'nan' if y is None else repr(y), str(t), str(step)]
            out.write(' '.join(parts) + '\n')
        elif k == 'key' and not e.get('g'):
            out.write(f"K {e['c']} {1 if e['d'] else 0} {e['t']}\n")
        elif k == 'start':
            out.write(f"S {e['t']}\n")
