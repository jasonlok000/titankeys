"""Offline lifecycle fixtures; never invokes adb."""
import importlib.util
from pathlib import Path
import sys
import types
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("ime_window", Path(__file__).with_name("run.py"))
check = importlib.util.module_from_spec(spec)
spec.loader.exec_module(check)
APP = "com.titankeys.debug"


class WindowFixtures(unittest.TestCase):
    def run_case(self, returned_type=0x24001, visible=True, setup_focus=True):
        phase = "synthetic"
        calls = []

        def shell(*args):
            nonlocal phase
            calls.append(args)
            if args[:2] == ("settings", "get"):
                return APP + "/com.titankeys.keyboard.TitanKeysIme"
            if args == ("dumpsys", "window"):
                activity = "GuideActivity" if phase != "synthetic" else "SpaceGestureActivity"
                return f"mCurrentFocus=Window{{x u0 {APP}/com.titankeys.keyboard.{activity}}}"
            if args[:3] == ("am", "start", "-W"):
                phase = "setup"
            elif args[:2] == ("am", "start"):
                phase = "returned"
            elif args == ("input", "keyevent", "HOME"):
                phase = "home"
            elif args == ("input", "keyevent", "BACK"):
                phase = "back"
            elif args[:3] == ("cmd", "package", "query-activities"):
                return APP + "/com.vandam.ink.MainActivity"
            return ""

        def state():
            if phase == "synthetic":
                return APP, 0x20001, True
            if phase == "setup":
                return (APP, 0x24001, True) if setup_focus else (None, 0, False)
            if phase == "returned":
                return APP, returned_type, visible
            return APP, 0x24001, False

        with patch.object(check, "shell", side_effect=shell), patch.object(check, "state", side_effect=state), \
             patch.object(check.time, "sleep"), patch.object(sys, "argv", ["run.py", APP, "--cycles", "1"]), \
             patch.dict(sys.modules, {"phone_lock": types.SimpleNamespace(hold=lambda: None)}):
            check.main()
        return calls

    def test_synthetic_to_playground_is_baselined_after_setup(self):
        calls = self.run_case()
        setup = next(i for i, c in enumerate(calls) if c[:3] == ("am", "start", "-W"))
        home = calls.index(("input", "keyevent", "HOME"))
        self.assertLess(setup, home)

    def test_same_package_changed_field_still_fails(self):
        with self.assertRaisesRegex(AssertionError, "expected .*24001, got .*20001"):
            self.run_case(returned_type=0x20001)

    def test_missing_focus_during_setup_fails(self):
        with self.assertRaisesRegex(AssertionError, "did not obtain a focused text editor"):
            self.run_case(setup_focus=False)

    def test_genuinely_hidden_ime_fails(self):
        with self.assertRaisesRegex(AssertionError, "window stayed hidden"):
            self.run_case(visible=False)

    def test_non_debug_setup_is_unchanged(self):
        with patch.object(check, "shell") as shell:
            check.prepare_debug("org.mozilla.firefox")
            check.prepare_debug("com.titankeys")
            shell.assert_not_called()

    def test_setup_never_opens_over_another_app(self):
        with patch.object(check, "shell", return_value="mCurrentFocus=Window{x u0 other/.Main}") as shell:
            with self.assertRaisesRegex(AssertionError, "STOP"):
                check.prepare_debug(APP)
            self.assertEqual(shell.call_count, 1)

    def test_missing_return_editor_fails(self):
        with self.assertRaisesRegex(AssertionError, "got None type=0x0"):
            check.same_editor(None, 0, APP, 0x24001)


if __name__ == "__main__":
    unittest.main()
