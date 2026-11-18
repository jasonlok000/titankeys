import importlib.util
from pathlib import Path
import sys
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

HELPER = Path(__file__).resolve().parents[2] / "tools/dev/touch/retest_batch.py"
APP = "com.titankeys.debug"
ACTIVITY = f"{APP}/com.vandam.ink.MainActivity"
PLAYGROUND = f"{APP}/com.titankeys.keyboard.GuideActivity"
OTHER = "com.example.device/.Activity"
ROUTE = ("shell", "am", "start", "-W", "-n", ACTIVITY,
         "--es", "com.vandam.ink.notification.HREF", "/settings")


def window(component):
    return f"mCurrentFocus=Window{{117bb u0 {component}}}"


class Stop(Exception):
    pass


class SettingsNavigationTest(unittest.TestCase):
    def setUp(self):
        self.adb = Mock(return_value="Starting: Intent\nStatus: ok\nComplete\n")
        self.front = Mock(side_effect=[window(PLAYGROUND), window(ACTIVITY)])
        checks = SimpleNamespace(APP=APP, Stop=Stop, adb=self.adb, record=Mock())
        languages = SimpleNamespace(REPO="", front=self.front)
        spec = importlib.util.spec_from_file_location("settings_navigation_test_target", HELPER)
        self.helper = importlib.util.module_from_spec(spec)
        with patch.dict(sys.modules, checks=checks, languages=languages, guide_checks=SimpleNamespace()):
            spec.loader.exec_module(self.helper)
        self.helper.time = SimpleNamespace(sleep=Mock())
        self.step = Mock()

    def test_open_from_playground_or_ink_front_without_a_label_or_tap(self):
        for start in (PLAYGROUND, ACTIVITY):
            with self.subTest(start=start):
                self.adb.reset_mock()
                self.front.side_effect = [window(start), window(ACTIVITY)]
                self.helper.open_settings(self.step)
                self.adb.assert_called_once_with(*ROUTE)
                self.step.assert_not_called()

    def test_refuses_non_debug_target_before_a_window_query(self):
        self.helper.APP = "com.titankeys"
        with self.assertRaisesRegex(Stop, "need TitanKeys Debug"):
            self.helper.open_settings(self.step)
        self.front.assert_not_called()
        self.adb.assert_not_called()
        self.step.assert_not_called()

    def test_refuses_foreign_foreground_without_sending_anything(self):
        self.front.side_effect = [window(OTHER)]
        with self.assertRaisesRegex(Stop, "not in front"):
            self.helper.open_settings(self.step)
        self.adb.assert_not_called()
        self.step.assert_not_called()

    def test_refuses_unconfirmed_launch(self):
        for bad_result in ("", "Status: timeout", "Error type 3"):
            with self.subTest(result=bad_result):
                self.adb.reset_mock()
                self.front.side_effect = [window(PLAYGROUND)]
                self.adb.return_value = bad_result
                with self.assertRaisesRegex(Stop, "did not confirm"):
                    self.helper.open_settings(self.step)
                self.adb.assert_called_once_with(*ROUTE)
                self.step.assert_not_called()

    def test_stops_if_the_expected_window_does_not_come_to_front(self):
        for result in (OTHER, PLAYGROUND, ACTIVITY + "Other"):
            with self.subTest(result=result):
                self.adb.reset_mock()
                self.front.side_effect = [window(PLAYGROUND), window(result)]
                with self.assertRaisesRegex(Stop, "did not stay in front"):
                    self.helper.open_settings(self.step)
                self.adb.assert_called_once_with(*ROUTE)
                self.step.assert_not_called()


if __name__ == "__main__":
    unittest.main()
