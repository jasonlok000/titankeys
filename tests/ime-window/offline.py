import unittest
from unittest.mock import patch

import run


class SameFieldBack(unittest.TestCase):
    def test_uses_exact_debug_window_and_accepts_hidden_bar(self):
        front = f"mCurrentFocus=Window{{id u0 {run.DEBUG}/{run.SPACE}}}"
        with patch("run.shell", return_value=front) as adb, patch("run.state", side_effect=[
            (run.DEBUG, 131073, True), (run.DEBUG, 131073, False),
        ]), patch("run.time.sleep"):
            run.debug_back_stays_in_editor()
        commands = [call.args for call in adb.call_args_list if call.args[0] != "dumpsys"]
        self.assertEqual(commands, [
            ("am", "start", "-W", "-n", f"{run.DEBUG}/{run.SPACE}", "--ez", run.KEEP_EDITOR, "true"),
            ("input", "keyevent", "BACK"),
        ])

    def test_keeping_bar_visible_is_a_failure(self):
        front = f"mCurrentFocus=Window{{id u0 {run.DEBUG}/{run.SPACE}}}"
        with patch("run.shell", return_value=front), patch("run.state", side_effect=[
            (run.DEBUG, 131073, True), (run.DEBUG, 131073, True),
        ]), patch("run.time.sleep"):
            with self.assertRaisesRegex(AssertionError, "remained or came back"):
                run.debug_back_stays_in_editor()

    def test_leaving_fixture_is_not_a_pass(self):
        front = f"mCurrentFocus=Window{{id u0 {run.DEBUG}/{run.SPACE}}}"
        home = "mCurrentFocus=Window{id u0 home.app/.Main}"
        with patch("run.shell", side_effect=[front] * 6 + [home]), patch("run.state", return_value=(
            run.DEBUG, 131073, True,
        )), patch("run.time.sleep"):
            with self.assertRaisesRegex(AssertionError, "Back closed"):
                run.debug_back_stays_in_editor()

    def test_refuses_to_start_from_another_app(self):
        with patch("run.shell", return_value="mCurrentFocus=Window{id u0 other.app/.Main}") as adb:
            with self.assertRaisesRegex(AssertionError, "Debug must be in front"):
                run.debug_back_stays_in_editor()
        adb.assert_called_once_with("dumpsys", "window")


if __name__ == "__main__":
    unittest.main()
