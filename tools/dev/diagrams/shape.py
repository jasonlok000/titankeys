"""Offline test of a second recogniser: matches each path against the shape of dictionary words.
Tuned on index % 3 != 2 only; the held-back third is read once settings are chosen.
Usage: python shape.py run-name [--hold]"""
import json
import os
import pickle
import sys

import numpy as np

from swipes import CENTRES, REPO, SESSION, contacts, data_path, load, rejoined

DICTIONARY = REPO / "android/assets/swipe/english.combined"
# Replay results ($TK_DATA/replay/<run>.jsonl) and the cache are only needed when this runs as a tool, not by matcher_gifs.py.
N = int(os.environ.get("POINTS", "32"))
MIN_FREQ = int(os.environ.get("MIN_FREQ", "40"))
REACH = 3.0  # keys: how far a path's ends may be from a word's first and last letters
GROUP_GAP = 300


def lexicon():
    words = {}
    for line in open(DICTIONARY, encoding="utf-8"):
        if not line.startswith(" word="):
            continue
        fields = dict(part.split("=", 1) for part in line.strip().split(",") if "=" in part)
        word = fields["word"].lower().replace("'", "")
        if len(word) < 2 or not (word.isascii() and word.isalpha()):
            continue
        if "offensive" in fields.get("flags", "") or int(fields["f"]) < MIN_FREQ:
            continue
        words[word] = max(words.get(word, 0), int(fields["f"]))
    return words


def resample(points):
    points = np.asarray(points, dtype=float)
    steps = np.hypot(*np.diff(points, axis=0).T)
    along = np.concatenate([[0], np.cumsum(steps)])
    if along[-1] == 0:
        return np.repeat(points[:1], N, axis=0)
    wanted = np.linspace(0, along[-1], N)
    return np.column_stack([np.interp(wanted, along, points[:, 0]), np.interp(wanted, along, points[:, 1])])


def normalised(path):
    low, high = path.min(axis=0), path.max(axis=0)
    centred = path - (low + high) / 2
    return centred / max((high - low).max(), 1e-6)


def key_space(points):
    return [(x * 10, y * 4) for x, y in points]


def templates(words):
    names, frequencies, absolute = [], [], []
    for word, frequency in words.items():
        letters = rejoined(word)
        if len(letters) < 2 or any(c not in CENTRES for c in letters):
            continue
        names.append(word)
        frequencies.append(frequency)
        absolute.append(resample(key_space([CENTRES[c] for c in letters])))
    absolute = np.stack(absolute)
    shapes = np.stack([normalised(a) for a in absolute])
    return names, np.array(frequencies, dtype=float), absolute, shapes


def attempt_path(attempt):
    """The last group of touches, joined as the keyboard would join pieces of one swipe."""
    found = [c for c in contacts(attempt) if len(c.points) >= 2]
    if not found:
        return None
    group = [found[-1]]
    for contact in reversed(found[:-1]):
        # The same windows the keyboard uses to join a swipe that lost tracking.
        if group[0].start - contact.end > (200 if contact.points[-1][1] < 0.25 else 90):
            break
        group.insert(0, contact)
    points = [(p[0], p[1]) for contact in group for p in contact.points]
    return points if len(points) >= 2 else None


def build():
    words = lexicon()
    names, frequencies, absolute, shapes = templates(words)
    _, attempts, _, _ = load(SESSION)
    out = {}
    for attempt in attempts:
        if attempt["pace"] == "typed":
            continue
        points = attempt_path(attempt)
        if points is None:
            continue
        path = resample(key_space(points))
        shape = normalised(path)
        near = (np.hypot(*(absolute[:, 0] - path[0]).T) <= REACH) & (np.hypot(*(absolute[:, -1] - path[-1]).T) <= REACH)
        index = np.nonzero(near)[0]
        shape_d = np.hypot(*(shapes[index] - shape).transpose(2, 0, 1)).mean(axis=1)
        location_d = np.hypot(*(absolute[index] - path).transpose(2, 0, 1)).mean(axis=1)
        ends_d = np.hypot(*(absolute[index, 0] - path[0]).T) + np.hypot(*(absolute[index, -1] - path[-1]).T)
        out[attempt["index"]] = dict(prompt=attempt["prompt"], names=[names[i] for i in index], freq=frequencies[index],
                                     shape=shape_d, location=location_d, ends=ends_d)
    pickle.dump(out, open(data_path("shape-cache.pkl"), "wb"))
    print("candidates per swipe:", int(np.median([len(v["names"]) for v in out.values()])), "lexicon:", len(names))
    return out


def ranked(entry, ws, wl, we, wf):
    score = ws * entry["shape"] + wl * entry["location"] + we * entry["ends"] - wf * entry["freq"] / 255
    order = np.argsort(score)
    return [entry["names"][i] for i in order[:8]]


def neural(run):
    out = {}
    for line in open(data_path("replay", f"{run}.jsonl"), encoding="utf-8"):
        entry = json.loads(line)
        if "index" in entry and entry["pace"] != "typed":
            out[entry["index"]] = [w.lower() for w in entry["top"][:3]]
    return out


def score(cache, net, keep, settings):
    alone, mixed, first = 0, 0, 0
    items = [i for i in cache if keep(i) and i in net]
    for i in items:
        entry = cache[i]
        prompt = entry["prompt"]
        order = ranked(entry, *settings)
        alone += prompt in order[:3]
        first += order[:1] == [prompt]
        shown = net[i][:2]
        for word in order:
            if len(shown) >= 3:
                break
            if word not in shown:
                shown.append(word)
        mixed += prompt in shown
    return alone, first, mixed, len(items)


if __name__ == "__main__":
    run = sys.argv[1]
    cache_file = data_path("shape-cache.pkl")
    cache = pickle.load(open(cache_file, "rb")) if cache_file.exists() else build()
    net = neural(run)
    tune = lambda i: i % 3 != 2
    hold = lambda i: i % 3 == 2
    base = lambda keep: sum(cache[i]["prompt"] in net[i] for i in cache if keep(i) and i in net)
    print(f"neural alone: tune top3 {base(tune)}  hold top3 {base(hold)}  (of {sum(1 for i in cache if tune(i))} / {sum(1 for i in cache if hold(i))})")
    best = None
    for ws in (1, 2, 4, 8):
        for wl in (0, 0.1, 0.25, 0.5):
            for we in (0, 0.1, 0.25):
                for wf in (0, 0.25, 0.5, 1, 2):
                    settings = (ws, wl, we, wf)
                    result = score(cache, net, tune, settings)
                    if best is None or result[2] > best[0][2] or (result[2] == best[0][2] and result[0] > best[0][0]):
                        best = (result, settings)
    print("best on the tune set (shape, location, ends, frequency):", best[1])
    print("  tune: shape alone top3 %d, first %d; neural 2 + shape 1, top3 %d of %d" % best[0])
    if "--hold" in sys.argv:
        print("  HOLD: shape alone top3 %d, first %d; neural 2 + shape 1, top3 %d of %d" % score(cache, net, hold, best[1]))
