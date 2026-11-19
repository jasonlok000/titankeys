"""Offline checks for selecting gesture cases and reading the double Space result."""
import os
from pathlib import Path
import sys
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import Mock, call, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import gesture_batch as gestures


class GestureBatchTest(unittest.TestCase):
    def setUp(self):
        self.environment = self.enterContext(patch.dict(os.environ))
        self.environment.pop("TK_GESTURE_ONLY", None)
        self.environment.pop("TK_LANG_FROM", None)
        self.enterContext(patch.object(gestures.language_checks, "sets", return_value=[["da", "et"], ["ja"]]))
        self.turn_on = self.enterContext(patch.object(gestures.language_checks, "turn_on", return_value=True))
        self.enterContext(patch.object(gestures.checks, "past_cutoff", return_value=False))

    def run_selected(self):
        events = Mock()
        with (
            patch.object(gestures, "flick", events.flick),
            patch.object(gestures, "double_space", events.stop),
            patch.object(gestures, "three_taps", events.taps),
            patch.object(gestures, "code_flick", events.code_flick),
        ):
            gestures.check_every_language()
        return events.mock_calls

    def test_default_checks_each_method_in_batch_order(self):
        self.assertEqual(self.run_selected(), [
            call.flick("da"), call.stop("da"), call.taps("da"),
            call.flick("et"), call.stop("et"), call.taps("et"),
            call.flick("ja"), call.stop("ja"), call.taps("ja"), call.code_flick("ja"),
        ])

    def test_selects_only_the_requested_case(self):
        self.environment["TK_GESTURE_ONLY"] = "stop:et"
        self.assertEqual(self.run_selected(), [call.stop("et")])
        self.turn_on.assert_called_once_with(["en", "da", "et"], "et")

    def test_combines_bare_and_exact_selectors_without_repeating_checks(self):
        self.environment["TK_GESTURE_ONLY"] = "flick, flick:ja, code-flick:ja"
        self.assertEqual(self.run_selected(), [call.flick("da"), call.flick("et"), call.flick("ja"), call.code_flick("ja")])

    def test_unmatched_selectors_stop_before_switching_input_methods(self):
        for selector in ("stop:unknown", "code-flick:et", "stpo:et"):
            with self.subTest(selector=selector):
                self.environment["TK_GESTURE_ONLY"] = f"flick,{selector}"
                with self.assertRaisesRegex(gestures.Stop, selector):
                    self.run_selected()
        self.turn_on.assert_not_called()

    def test_estonian_double_space_requires_the_dictionary_word_and_stop(self):
        for before, after, expected in (("See", "See. ", True), ("See", "Seed. ", False),
                                        ("See", "See . ", False), ("", ". ", False), ("qwe", "qwe. ", False)):
            with (
                self.subTest(before=before, after=after),
                patch.object(gestures.language_checks, "fresh"),
                patch.object(gestures, "play") as play,
                patch.object(gestures, "record") as record,
                patch.object(gestures.checks, "field", side_effect=[before, after]),
            ):
                gestures.double_space("et")
                self.assertEqual(play.call_args_list[0], call(*gestures.keys("see"), "+300"))
                self.assertEqual(record.call_args.args[:2], ("stop-et", expected))

    def test_focused_reports_do_not_count_as_a_full_batch_pass(self):
        with TemporaryDirectory() as folder, patch.object(gestures.checks, "details", [("stop-et", "YES", "", None)]):
            self.environment["TK_OUT"] = folder
            for batch, selectors, start, reusable in (
                ("gestures", "", "", True),
                ("gestures", "stop:et", "", False),
                ("gestures-fix", "flick,stop", "", True),
                ("gestures-fix", "stop:et", "", False),
                ("gestures-fix", "flick,stop", "et", False),
            ):
                with self.subTest(batch=batch, selectors=selectors, start=start):
                    self.environment.update(TK_GESTURE_ONLY=selectors, TK_LANG_FROM=start)
                    path = gestures.checks.report(batch, "20261119-000000", [], finished=True)
                    self.assertEqual(gestures.checks.batch_plan.REUSABLE in Path(path).read_text(encoding="utf-8"), reusable)


if __name__ == "__main__":
    unittest.main()
