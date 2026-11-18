from pathlib import Path
import sys
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tools/dev/touch"))
import switch_capitals as probe

for text, method, want in (("Й", "ru", True), ("й", "ru", False), ("Й", "en", False), ("ЙЙ", "ru", False)):
    with patch.object(probe, "select"), patch.object(probe.checks, "set_text", return_value=""), \
         patch.object(probe.checks, "play") as play, patch.object(probe.checks, "field", return_value=text), \
         patch.object(probe.checks, "adb", return_value=f'data="on en,ru; typing {method}"'), \
         patch.object(probe.checks, "record") as record:
        probe.trial("fixture", ["en", "ru"], "en", "ru", "", "Й", immediate=True)
        play.assert_called_once_with("key:sym:900:none", "key:q", "+400")
        assert record.call_args.args[1] is want
print("Exact case, destination and single-key controls passed")

import search_row
from search_row import transient_starters, words_rows

for sentence, title, flags in ((True, "SENTENCE", "24001"), (False, "PLAIN", "20001")):
    with patch.object(probe.checks, "keyboard"), patch.object(probe.checks, "adb", side_effect=(
        "Starting", f"mCurrentFocus=Window{{abc u0 {probe.ACTIVITY}}}",
        f"mCurrentEditorInfo:\n  inputType=0x{flags} imeOptions=0x51000006",
    )) as launch:
        probe.editor(sentence)
        assert launch.call_args_list[0].args == (
            "shell", "am", "start", "-W", "-a", f"{probe.checks.APP}.{title}_EDITOR_FIXTURE", "-n",
            probe.ACTIVITY, "--ez", "sentence_capitals", str(sentence).lower(),
        )
with patch.object(search_row, "select"), patch.object(search_row.checks, "adb", return_value="") as launch:
    try:
        search_row.trial(0)
    except search_row.checks.Stop as error:
        assert "Maps search field flags unconfirmed" in str(error)
    else:
        raise AssertionError("The Maps fixture must confirm editor flags before typing")
    assert launch.call_args_list[0].args == (
        "shell", "am", "start", "-W", "-a", f"{probe.checks.APP}.MAPS_EDITOR_FIXTURE", "-n",
        probe.ACTIVITY, "--ez", "maps_search", "true",
    )
print("Distinct task identities for the three editor fixtures passed")

starter = ("the", "I", "you")
next_row = ("a", "new", "one")
assert transient_starters([starter, next_row], starter) is True
assert transient_starters([next_row], starter) is False
assert transient_starters([starter], starter) is None
assert transient_starters([], starter) is None
assert words_rows([("notice", "warm"), ("bar", "ja", list(starter)), ("bar", "en", list(next_row))]) == [next_row]
print("Search row transition controls passed")
