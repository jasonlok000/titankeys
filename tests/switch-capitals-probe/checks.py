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
