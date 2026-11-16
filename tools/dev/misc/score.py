"""Scores a recorder session (the debug build's swipe recorder). Usage: python3 -I score.py session.jsonl [more.jsonl]
Try it on android/debug/recorder/samples/session-20261108-114501.jsonl; your own sessions come from pull-recordings.sh."""
import json
import sys
from collections import Counter

ENTER = 66  # Android's key code; Enter moves the recorder on, so it isn't typing
GAP_BUCKETS = [(50, "up to 50 ms"), (90, "51-90 ms"), (200, "91-200 ms"), (500, "201-500 ms"), (float("inf"), "over 500 ms")]


def load(path):
    """Returns the final attempt for each prompt, honouring Redo and Skip."""
    attempts, header = {}, None
    for line in open(path, encoding="utf-8"):
        entry = json.loads(line)
        if "format" in entry:
            header = entry
        elif "redo" in entry or "skip" in entry:
            attempts.pop(entry.get("redo", entry.get("skip")), None)
        else:
            attempts[entry["index"]] = entry
    return header, [attempts[i] for i in sorted(attempts)]


def percent(part, whole):
    return f"{100 * part / whole:.1f}%" if whole else "n/a"


def key_presses_during(attempt):
    return sum(1 for e in attempt["events"] if e["k"] == "key" and e["d"] and not e["g"] and e["c"] != ENTER)


def report(name, attempts):
    total = len(attempts)
    first = sum(1 for a in attempts if a["top"][:1] and a["top"][0].lower() == a["prompt"])
    top3 = sum(1 for a in attempts if a["prompt"] in [w.lower() for w in a["top"][:3]])
    none = sum(1 for a in attempts if not a["top"])
    split = [a for a in attempts if a["touches"] > 1]
    joined = sum(1 for a in split if a["joined"])
    print(f"\n{name}: {total} words")
    print(f"  right word first : {percent(first, total)}")
    print(f"  right word top 3 : {percent(top3, total)}")
    print(f"  no word found    : {percent(none, total)}")
    print(f"  2 or more touches: {len(split)} ({percent(len(split), total)}), rejoined {joined}")
    print(f"  with a key press : {sum(1 for a in attempts if key_presses_during(a))}")
    gaps = [g for a in split for g in a["gaps"]]
    if gaps:
        counts = Counter(next(label for limit, label in GAP_BUCKETS if g <= limit) for g in gaps)
        print("  gap lengths      : " + ", ".join(f"{label}: {counts[label]}" for _, label in GAP_BUCKETS if counts[label]))


def outcomes(attempt, kind):
    return sum(1 for e in attempt["events"] if e["k"] == "out" and e["type"] == kind)


def report_typed(attempts):
    print(f"\nTyping: {len(attempts)} sentences, {sum(key_presses_during(a) for a in attempts)} key presses")
    print(f"  touches seen     : {sum(a['touches'] for a in attempts)}")
    print(f"  read as swipes   : {sum(outcomes(a, 'swipe') for a in attempts)}")
    print(f"  read as taps     : {sum(outcomes(a, 'tap') for a in attempts)}")


def main():
    everything = []
    for path in sys.argv[1:]:
        header, loaded = load(path)
        print(f"{path}: {header}")
        everything += loaded
    attempts = [a for a in everything if a["pace"] != "typed"]
    report("All swipes", attempts)
    for pace in ("normal", "rushed"):
        report(pace.capitalize(), [a for a in attempts if a["pace"] == pace])
    report_typed([a for a in everything if a["pace"] == "typed"])
    misses = [a for a in attempts if a["top"][:1] and a["top"][0].lower() != a["prompt"]]
    print("\nExamples of misses:")
    for a in misses[:25]:
        print(f"  {a['prompt']:<16} -> {', '.join(a['top'][:3])}  ({a['touches']} touch)")


if __name__ == "__main__":
    main()
