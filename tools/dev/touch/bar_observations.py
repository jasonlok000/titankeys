"""Verdicts require the offered text; script or ASCII status is not a verdict."""
from contextlib import contextmanager


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
    import interactions_batch as ib
    own_field()
    prop = 'log.tag.' + ib.TAG
    previous = checks.adb('shell', 'getprop', prop).strip()
    checks.adb('shell', 'setprop', prop, 'D')
    try:
        checks.adb('logcat', '-c')
        yield
    finally:
        checks.adb('shell', 'setprop', prop, previous or "''")


def observed_middle(method):
    import interactions_batch as ib
    own_field()
    lines = ib.bar_lines()
    own_field()
    return middle(lines, method)


def observed_candidate_zero(method):
    """Strip.logBar's engine Picker calls BarLog.places: ranked[0] is the middle.
    This is candidate zero specifically, never any matching item from the list.
    """
    return observed_middle(method)
