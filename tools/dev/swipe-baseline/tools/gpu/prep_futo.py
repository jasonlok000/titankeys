"""Turns FUTO's public swipe file into fixed-size training arrays on the Titan key grid.
Usage: python prep_futo.py qwerty.json out.npz file.jsonl [more.jsonl]"""
import json
import os
import sys
from multiprocessing import Pool

import numpy as np

from swipe_model import INDEX, ROWS, featurize, titan_grid

SHIFT = None


def row_shift(layout_path):
    futo = {k['letter']: (k['cx'], k['cy']) for k in json.load(open(layout_path))['keys']}
    grid = titan_grid()
    return [float(np.mean([grid[c][0] - futo[c][0] for c in row])) for row in ROWS]


def init(shift):
    global SHIFT
    SHIFT = shift


def convert(line):
    try:
        e = json.loads(line)
    except Exception:
        return None
    w = str(e.get('word', '')).lower().replace("'", '')
    data = e.get('data')
    if not w.isalpha() or not all(c in INDEX for c in w) or not 2 <= len(w) <= 20 or e.get('potentially_invalid_sentence') or not isinstance(data, list) or len(data) < 3:
        return None
    x = np.array([p['x'] for p in data], dtype=float)
    y = np.array([p['y'] for p in data], dtype=float)
    t = np.array([p['t'] for p in data], dtype=float)
    t -= t[0]
    if t[-1] <= 0 or np.any(np.diff(t) < 0):
        return None
    x = x + np.interp(y * 3, [.5, 1.5, 2.5], SHIFT)
    return featurize(x, y, t).astype(np.float16), w


def lines(paths):
    for path in paths:
        with open(path, encoding='utf-8') as f:
            for line in f:
                yield line


if __name__ == '__main__':
    layout, out, *paths = sys.argv[1:]
    feats, words = [], []
    with Pool(max(1, (os.cpu_count() or 4) - 2), initializer=init, initargs=(row_shift(layout),)) as pool:
        for n, result in enumerate(pool.imap(convert, lines(paths), chunksize=1024)):
            if result is not None:
                feats.append(result[0])
                words.append(result[1])
            if n % 100000 == 0:
                print('read', n, 'kept', len(words), flush=True)
    np.savez(out, features=np.stack(feats), words=np.array(words))
    print('kept', len(words), flush=True)
