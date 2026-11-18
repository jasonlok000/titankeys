"""Choose reruns from completed prior reports and batches whose inputs were reviewed as unchanged."""
import argparse
from collections import Counter
from pathlib import Path
import re

REUSABLE = "Completed all checks with no unresolved results."


def passed_report(folder, batch):
    """Only the newest report counts. An old, incomplete, failed or unreviewed result must run again."""
    name = re.compile(r"report-" + re.escape(batch) + r"-(\d{8}-\d{6})\.md")
    reports = sorted(path for path in folder.glob(f"report-{batch}-*.md") if name.fullmatch(path.name))
    if not reports:
        return False
    try:
        lines = reports[-1].read_text(encoding="utf-8").splitlines()
    except (OSError, UnicodeError):
        return False
    stamp = name.fullmatch(reports[-1].name)[1]
    if len(lines) < 5 or lines[0] != f"# {batch}, {stamp}" or lines[4] != REUSABLE:
        return False
    count = re.fullmatch(r"([1-9]\d*) YES, 0 NO, 0 to look at", lines[2])
    if not count:
        return False
    verdicts = re.findall(r"(?m)^- \*\*(YES|NO|LOOK)\*\* ", "\n".join(lines))
    return len(verdicts) == int(count[1]) and set(verdicts) == {"YES"} and "For the owner's hands:" not in lines


def batches_to_run(requested, *, phase, previous=None, unchanged=()):
    """The initial T7 and final T8 runs always keep the whole list, including repeated slots."""
    if phase != "rerun" or previous is None:
        return list(requested)
    counts = Counter(requested)
    reviewed = set(unchanged)
    return [batch for batch in requested
            if counts[batch] > 1 or batch not in reviewed or not passed_report(Path(previous), batch)]


def main(args, known):
    parser = argparse.ArgumentParser(prog="checks.py --plan", description=__doc__)
    parser.add_argument("phase", choices=("t7", "rerun", "t8"))
    parser.add_argument("--previous", type=Path, help="one prior run's directory containing report-*.md")
    parser.add_argument("--unchanged", action="append", choices=known, default=[],
                        help="a batch whose build inputs, harness, and prerequisites you verified unchanged (repeatable)")
    parser.add_argument("batches", nargs="+", choices=known)
    options = parser.parse_intermixed_args(args)
    for batch in batches_to_run(options.batches, phase=options.phase, previous=options.previous, unchanged=options.unchanged):
        print(batch)
