"""Offline fixtures for phone verdicts: no adb, touch feed, build or phone required."""
import os
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tools/dev/touch"))
import interactions_batch as ib
import worker_lines_batch as wb


class OracleChecks(unittest.TestCase):
    def setUp(self):
        # A missed mock must fail locally, never start a device command.
        self.hardware = patch("subprocess.run", side_effect=AssertionError("device command in offline test"))
        self.hardware.start()
        self.addCleanup(self.hardware.stop)

    def test_switch_ignores_source_until_destination(self):
        old = ("bar", "ja", ["old", "old", "old"])
        new = ("bar", "ko", ["-", "가", "-"])
        self.assertIs(ib.switch_verdict([old, ("notice", "rk"), new], "ko"), True)
        self.assertIs(ib.switch_verdict([old, new, old], "ko"), False)
        self.assertIsNone(ib.switch_verdict([old, ("notice", "rk")], "ko"))
        self.assertIsNone(ib.switch_verdict([], "ko"))

    def korean(self, fields):
        with patch.object(ib, "start"), patch.object(ib, "mark"), \
                patch.object(ib, "play") as play, patch.object(ib, "record") as record, \
                patch.object(ib, "bar_lines", return_value=[("bar", "ko", ["-", "가", "-"])]), \
                patch.object(ib.checks, "field", side_effect=fields), patch.object(ib.time, "sleep"):
            ib.check_korean_startup()
        return play, record

    def test_korean_delayed_commit_and_immediate_keys(self):
        play, record = self.korean(["", "", "가", "가 "])
        self.assertIs(record.call_args.args[1], True)
        self.assertEqual(play.call_args_list[0].args,
                         ("key:sym:900:none", "key:r:60:none", "+90", "key:k:60:none", "+90"))
        self.assertEqual(play.call_args_list[1].args, ("key:space",))

    def test_korean_exact_text_and_space(self):
        for bad in ("rk ", "가", "가  ", "notice rk", "old 가 "):
            with self.subTest(bad=bad):
                _, record = self.korean([""] + [bad] * 8)
                self.assertIs(record.call_args.args[1], False)

    def test_missing_bar_instrumentation_still_checks_field(self):
        with patch.object(ib, "start"), patch.object(ib, "mark"), patch.object(ib, "play"), \
                patch.object(ib, "record") as record, patch.object(ib, "bar_lines", return_value=[]) as bars, \
                patch.object(ib.checks, "field", side_effect=["", "가 "]), patch.object(ib.time, "sleep"):
            ib.check_korean_startup()
            self.assertEqual(bars.call_count, 16)
            self.assertIs(record.call_args.args[1], True)

    def test_korean_refuses_dirty_setup(self):
        with patch.object(ib, "start"), patch.object(ib.checks, "field", return_value="old"), \
                patch.object(ib, "play") as play:
            with self.assertRaises(ib.Stop):
                ib.check_korean_startup()
            play.assert_not_called()

    def test_focus_loss_stops_instead_of_failing_app(self):
        with patch.object(ib.checks, "field", side_effect=ib.Stop("lost focus")):
            with self.assertRaises(ib.Stop):
                ib.wait_field("가 ")

    def test_unknown_overflow_never_fails(self):
        opened = ["Shift (59): the bigger menu opens"]
        closed = ["Shift (59): the bigger menu closes"]
        for lines in (opened, closed, [], ["unrelated opens"]):
            self.assertIsNone(wb.shift_verdict(lines))
        self.assertIs(wb.shift_verdict(["Shift row overflow=false"]), True)
        self.assertIs(wb.shift_verdict(["Shift row overflow=false", *opened]), False)
        self.assertIs(wb.shift_verdict(["Shift row overflow=true"]), False)
        self.assertIs(wb.shift_verdict(["Shift row overflow=true", *opened]), True)
        self.assertIs(wb.shift_verdict(opened, True), True)
        self.assertIs(wb.shift_verdict(closed, False), True)
        self.assertIs(wb.shift_verdict(opened, False), False)
        self.assertIs(wb.shift_verdict(closed, True), False)
        self.assertIsNone(wb.shift_verdict([], False))

    def test_shift_after_pick_can_legitimately_open_and_restores_feed(self):
        with patch.dict(os.environ, {"TK_KEYFEED": "previous"}), patch.object(wb, "yale") as setup, \
                patch.object(wb, "adb"), patch.object(wb, "play"), patch.object(wb, "record") as record, \
                patch.object(wb, "choices", return_value=["Shift (59): the bigger menu opens"]):
            wb.check_shift_nothing_more()
            self.assertEqual(os.environ["TK_KEYFEED"], "previous")
            self.assertEqual(setup.call_count, 2)
            self.assertEqual([call.args[1] for call in record.call_args_list], [None, None])


if __name__ == "__main__":
    unittest.main()
