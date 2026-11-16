"""Shared pieces for running the open CTC encoder over strokes with any key layout."""
import math
import re

import numpy as np
import onnxruntime as ort

ALPHA = 'abcdefghijklmnopqrstuvwxyz'
INDEX = {c: i for i, c in enumerate(ALPHA)}
SP = {'neighbour': 'neighbor', 'tyres': 'tires', 'favourite': 'favorite', 'colour': 'color', 'grey': 'gray'}
GAMMA, BETA, GP, BP, BEAM, LAMBDA = .9, .25, .25, .9882, 100, 2.0


def key(w):
    w = w.lower().replace('’', "'").strip("'")
    w = SP.get(w, w)
    return w.replace("'", '')


class Node:
    __slots__ = ('kids', 'freq', 'char', 'depth', 'word', 'id')

    def __init__(self, char, depth, nid):
        self.kids, self.freq, self.char, self.depth, self.word, self.id = {}, None, char, depth, None, nid


def load_lexicon(path):
    root = Node(-1, 0, 0)
    count = 1
    vocab = {}
    for line in open(path, encoding='utf-8'):
        m = re.match(r' word=([^,]+),f=(\d+),flags=([^,]*)', line)
        if not m or 'not_a_word' in m.group(3):
            continue
        w = key(m.group(1))
        if len(w) < 1 or not all(c in INDEX for c in w):
            continue
        vocab[w] = max(vocab.get(w, 0), max(1, int(m.group(2))))
    for w, f in vocab.items():
        n = root
        for ch in w:
            i = INDEX[ch]
            if i not in n.kids:
                n.kids[i] = Node(i, n.depth + 1, count)
                count += 1
            n = n.kids[i]
        n.freq, n.word = f, w
    return root, vocab


def session(model):
    options = ort.SessionOptions()
    options.intra_op_num_threads = 1
    return ort.InferenceSession(model, options, providers=['CPUExecutionProvider'])


def layout_arrays(centres):
    keys = np.zeros((1, 64, 2), dtype=np.float32)
    mask = np.zeros((1, 64), dtype=bool)
    for ch, i in INDEX.items():
        keys[0, i] = centres[ch]
        mask[0, i] = True
    return keys, mask


def featurize(x, y, t):
    x, y, t = np.asarray(x, dtype=float), np.asarray(y, dtype=float), np.asarray(t, dtype=float)
    duration = t[-1] - t[0]
    if len(x) < 2 or duration <= 1e-12:
        hx, hy = np.array([x[0], x[-1]]), np.array([y[0], y[-1]])
    else:
        n = max(2, int(np.rint(duration / (1000 / 60))) + 1)
        grid = np.minimum(t[0] + np.arange(n) * (duration / (n - 1)), t[-1])
        idx = np.searchsorted(t, grid, 'left')
        hx, hy = np.zeros(n), np.zeros(n)
        for i, (g, j) in enumerate(zip(grid, idx)):
            if j == 0:
                hx[i], hy[i] = x[0], y[0]
            elif j >= len(t):
                hx[i], hy[i] = x[-1], y[-1]
            else:
                seg = t[j] - t[j - 1]
                f = (g - t[j - 1]) / seg if seg > 1e-12 else 0
                hx[i], hy[i] = x[j - 1] + f * (x[j] - x[j - 1]), y[j - 1] + f * (y[j] - y[j - 1])
    pos = np.linspace(0, 1, 64) * (len(hx) - 1)
    i0 = pos.astype(int)
    i1 = np.minimum(i0 + 1, len(hx) - 1)
    f = pos - i0
    rx = np.clip(hx[i0] + f * (hx[i1] - hx[i0]), 0, 1)
    ry = np.clip(hy[i0] + f * (hy[i1] - hy[i0]), 0, 1)
    return np.stack([rx, ry], 0)[None].astype(np.float32)


def decode(lp, root):
    """FUTO-style trie Viterbi beam. lp is frames x 27, with blank last. Returns word -> path score."""
    beam = [(0.0, root, False)]
    blank = 26
    for t in range(lp.shape[0]):
        frame = lp[t].tolist()
        nxt = {}
        for score, node, ended in beam:
            s = score + frame[blank]
            k = (node.id << 1) | 1
            cur = nxt.get(k)
            if cur is None or cur[0] < s:
                nxt[k] = (s, node, True)
            for ci, child in node.kids.items():
                s = score + frame[ci]
                k = child.id << 1
                cur = nxt.get(k)
                if cur is None or cur[0] < s:
                    nxt[k] = (s, child, False)
            if not ended and node.depth > 0:
                s = score + frame[node.char]
                k = node.id << 1
                cur = nxt.get(k)
                if cur is None or cur[0] < s:
                    nxt[k] = (s, node, False)
        cand = list(nxt.values())
        if len(cand) > BEAM:
            cand.sort(key=lambda h: -(h[0] / max(h[1].depth, 1) ** GP + BP * h[1].depth))
            cand = cand[:BEAM]
        beam = cand
    best = {}
    for score, node, _ in beam:
        if node.word is not None and (node.word not in best or best[node.word] < score):
            best[node.word] = score
    return best


def recognise(sess, root, vocab, x, y, t, keys, mask):
    emissions = sess.run(['log_emissions'], {'features': featurize(x, y, t), 'layout_keys': keys, 'layout_mask': mask})[0][0]
    lp = np.concatenate([emissions[:, :26], emissions[:, [64]]], 1)
    best = decode(lp, root)
    return sorted(best, key=lambda w: -(best[w] / max(len(w), 1) ** GAMMA + BETA * len(w) + LAMBDA * math.log(vocab[w])))[:80]


def tally(rows):
    n = len(rows)
    return {'n': n, 'first': round(100 * sum(r == 1 for r in rows) / n, 1), 'top3': round(100 * sum(1 <= r <= 3 for r in rows) / n, 1), 'found': round(100 * sum(1 <= r for r in rows) / n, 1)}


def position(ranked, word):
    return ranked.index(word) + 1 if word in ranked else -1
