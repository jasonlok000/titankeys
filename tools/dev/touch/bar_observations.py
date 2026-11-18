"""Verdicts require the offered text; script or ASCII status is not a verdict."""
from contextlib import contextmanager
from contextvars import ContextVar
import re
import uuid

MARK_TAG = 'TitanKeysCodex'
BAR_TAG = 'TitanKeysBar'
_attempt = ContextVar('bar_attempt', default=None)


class Unobservable(Exception):
    pass


def attempt_lines(raw, marker):
    """Decode candidates only after the exact marker in a bounded brief log dump."""
    lines = raw.splitlines()
    marker_line = re.compile(r'^I/' + MARK_TAG + r'\s*\(\s*\d+\s*\):\s*' + re.escape(marker) + r'\s*$')
    start = next((i for i in range(len(lines) - 1, -1, -1) if marker_line.fullmatch(lines[i])), None)
    if start is None:
        raise Unobservable('attempt marker absent or rotated out of the bounded log window')
    header = re.compile(r'^[VDIWEFAS]/' + BAR_TAG + r'\s*\(\s*\d+\s*\):\s*(.*)$')
    bar = re.compile(r'bar: (\S+) (.*?) \| (.*?) \| (.*)$')
    result = []
    for line in lines[start + 1:]:
        found = header.fullmatch(line)
        if not found:
            continue
        text = found.group(1)
        if match := bar.fullmatch(text):
            result.append(('bar', match.group(1), [word.strip() for word in match.group(2, 3, 4)]))
        elif text.startswith('bar notice: '):
            result.append(('notice', text.removeprefix('bar notice: ')))
        else:
            # An unknown later bar format invalidates older rows rather than
            # making them appear current.
            result.append(('notice', 'unrecognised bar observation'))
    return result


def middle(lines, method):
    """Only the latest observation counts; never reuse another method or a notice."""
    if not lines or lines[-1][0] != 'bar' or lines[-1][1] != method:
        return None
    value = lines[-1][2][1]
    return None if value in ('', '-') else value


def space_verdict(actual, offered, trailing_space):
    return None if offered in (None, '', '-') else actual == offered + trailing_space


def pick_verdict(actual, offered=None):
    # Dates, numbers and Latin candidates are valid Japanese conversion choices.
    # Without a captured selected-cell value, neither ASCII nor non-ASCII proves
    # whether the key picked the right tile.
    return None if offered in (None, '') else actual == offered


def suffix_verdict(before, actual, suffix):
    """Check an action against its observed committed prefix, not a dictionary guess."""
    return None if not before else actual == before + suffix


def own_field():
    import checks
    front = next((s for s in checks.adb('shell', 'dumpsys', 'window').splitlines() if 'mCurrentFocus' in s), '')
    expected = checks.APP + '/com.titankeys.keyboard.SpaceGestureActivity'
    if expected not in front:
        raise checks.Stop('bar observation requires the owned synthetic Space gestures field')
    return checks.field()


@contextmanager
def observed_bar():
    """Enable text-bearing bar logs only around this owned synthetic-field check."""
    import checks
    own_field()
    prop = 'log.tag.' + BAR_TAG
    previous = checks.adb('shell', 'getprop', prop).strip()
    # Same bounded marker shape as the private runner's existing custom-tag gate.
    marker = 'charter-' + uuid.uuid4().hex
    token = _attempt.set(marker)
    try:
        checks.adb('shell', 'setprop', prop, 'D')
        checks.adb('shell', 'log', '-t', MARK_TAG, marker)
        yield
    finally:
        try:
            # adb shell joins arguments: these shell quotes preserve an empty
            # value. The safety proxy normalizes and requotes it as empty too.
            checks.adb('shell', 'setprop', prop, previous or "''")
        finally:
            _attempt.reset(token)


def observed_middle(method):
    import checks
    marker = _attempt.get()
    if marker is None:
        raise checks.Stop('bar observation has no active synthetic attempt')
    own_field()
    raw = checks.adb('logcat', '-d', '-v', 'brief', '-t', '512', '-s', MARK_TAG + ':I', BAR_TAG + ':D', '*:S')
    own_field()
    try:
        lines = attempt_lines(raw, marker)
    except Unobservable as error:
        checks.record('bar-observation', None, str(error))
        return None
    return middle(lines, method)


def observed_candidate_zero(method):
    """Strip.logBar's engine Picker calls BarLog.places: ranked[0] is the middle.
    This is candidate zero specifically, never any matching item from the list.
    """
    return observed_middle(method)
