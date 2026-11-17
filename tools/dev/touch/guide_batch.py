"""The guide's checks for checks.py --batch guide: #66 steer, #67 triple Space, #68's hunts, #69's pastes, #84's pill (in
guide_checks.py), and #90: every slide as a screenshot, for the refinements the owner reads through (opened one by one through
GuideStart, only adb, only Debug)."""
import re

import checks
import guide_checks
import languages
from checks import APP, Stop, adb, record


def slide_ids():
    """The guide's slides, as GuideStart names them when asked for one it hasn't got."""
    answer = adb("shell", "am", "broadcast", "-n", f"{APP}/com.titankeys.keyboard.GuideStart", "--es", "step", "-")
    found = re.search(r"no slide called -: ([^\"]+)", answer)
    if not found:
        raise Stop(f"GuideStart didn't list its slides: {answer.strip()[-200:]}")
    return found.group(1).split()


def own_screen():
    """TitanKeys Debug's own screen in front, where the guide opens from."""
    guide_checks.leave_guide()
    if not languages.in_field():
        raise Stop(f"not back on TitanKeys Debug's own screen: {languages.front().strip()}")


def wrap(check):
    def run():
        own_screen()
        try:
            check()
        finally:
            guide_checks.leave_guide()
    run.__name__ = check.__name__
    run.__doc__ = check.__doc__
    return run


def check_90_slides():
    """#90: each slide as it opens, a screenshot each, for the owner's read-through."""
    for step in slide_ids():
        own_screen()
        lines = guide_checks.guide(step)
        name = guide_checks.shot(f"90-{step}")
        record(f"90-{step}", None, f"the slide's first lines: {' / '.join(lines[:2])[:120]}", name)
        guide_checks.leave_guide()


def check_swipe_slide():
    """E's guide (main c8de2255): on the swipe slide, keys and Backspace type nothing; only a swipe does."""
    own_screen()
    guide_checks.guide("swipe")
    try:
        before = checks.field()
    except Stop:
        before = None
    checks.play("key:a", "+120", "key:s", "+120", "key:del", "+500")
    try:
        after = checks.field()
    except Stop:
        after = None
    record("guide-swipe-keys", before is not None and after == before,
           f"the slide's field before {before!r}, after A, S and Backspace {after!r}", guide_checks.shot("guide-swipe-keys"))
    guide_checks.leave_guide()


def check_first_delete_slide():
    """E's guide: the first delete slide keeps its "e" (the extra e that Backspace takes) in place as it opens."""
    own_screen()
    guide_checks.guide("backspace")
    record("guide-delete-e", None, "the first delete slide shows its extra \"e\"", guide_checks.shot("guide-delete-e"))
    guide_checks.leave_guide()


BATCH = [wrap(check) for check in (guide_checks.check_66_steer, guide_checks.check_67_selectmore, guide_checks.check_68_hunts,
                                   guide_checks.check_69_paste, guide_checks.check_69_clip, guide_checks.check_84_menu)]
BATCH += [check_swipe_slide, check_first_delete_slide, check_90_slides]


def finish():
    guide_checks.leave_guide()
