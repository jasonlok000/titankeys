"""Read every suggestion row around a tap in Debug's Maps-shaped search field."""
import re
import time

import checks
from bar_observations import Unobservable, mark_attempt, observed_bar, own_field, read_attempt
from switch_capitals import launch_editor, select


def words_rows(events):
    return [tuple(event[2]) for event in events if event[:2] == ("bar", "en")]


def transient_starters(rows, starters):
    """A return to the empty-field row before a different final prediction is the reported flash."""
    if not rows or not starters or rows[-1] == starters:
        return None
    return starters in rows[:-1]


def rows_since(attempt):
    return words_rows(read_attempt(attempt))


def row_name(slot):
    return "search-row-" + ("left" if slot == 0 else "center")


def trial(slot):
    launch_editor("MAPS_EDITOR_FIXTURE", "maps_search", True)
    select(["en"], "en")
    details = checks.adb("shell", "dumpsys", "input_method")
    if not re.search(r"mCurrentEditorInfo:\s*\n\s*inputType=0x80001 imeOptions=0x12000003\b", details):
        raise checks.Stop("Maps search field flags unconfirmed")
    label = row_name(slot)
    with observed_bar():
        checks.set_text("a")
        empty = mark_attempt()
        checks.set_text("")
        time.sleep(.6)
        empty_rows = rows_since(empty)
        typed = mark_attempt()
        checks.play(*[v for letter in "testing" for v in ("key:" + letter, "+90")], "+500")
        offers = rows_since(typed)
        if own_field() != "testing" or not empty_rows or not offers or offers[-1][slot] in ("", "-"):
            return checks.record(label, None, "Synthetic word, empty-field row or offered choice unavailable")
        picked = offers[-1][slot]
        chosen = mark_attempt()
        own_field()
        checks.adb("shell", "input", "tap", "185" if slot == 0 else "538", "1170")
        time.sleep(.8)
        value = own_field()
        rows = rows_since(chosen)
        flash = transient_starters(rows, empty_rows[-1])
        accepted = value == picked + " "
        checks.record(label, False if not accepted or flash is True else True if flash is False else None,
                      f"Picked {picked!r}, field {value!r}, starter row {empty_rows[-1]!r}, all post-tap rows {rows!r}")


def check_search():
    for slot in (0, 1):
        try:
            trial(slot)
        except Unobservable as error:
            checks.record(row_name(slot), None, str(error))


BATCH = [check_search]
