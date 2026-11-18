"""Exercise the owned-editor restart protocol offline, with no device."""
from pathlib import Path
import sys
import unittest
from unittest.mock import call, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import checks
import own_editor
import switch_capitals


class OwnedEditorRestartTest(unittest.TestCase):
    def setUp(self):
        focus = patch.object(own_editor.languages, "front", return_value=self.window("abcd"))
        adb = patch.object(checks, "adb", side_effect=self.reply)
        app = patch.object(checks, "APP", own_editor.DEBUG)
        self.focus = focus.start()
        self.adb = adb.start()
        app.start()
        self.addCleanup(patch.stopall)
        self.selected = own_editor.IME
        self.answer = own_editor.RESULT
        self.selected_call = call("shell", "settings", "get", "secure", "default_input_method")
        self.restart_call = call("shell", "am", "broadcast", "-n", own_editor.RECEIVER,
                                 "--ez", "read", "true", "--ez", "restart", "true")

    def window(self, token):
        return f"  mCurrentFocus=Window{{{token} u0 {own_editor.ACTIVITY}}}"

    def reply(self, *args):
        if call(*args) == self.selected_call:
            return self.selected + "\n"
        if call(*args) == self.restart_call:
            return "Broadcasting: Intent { ... }\n" + self.answer + "\n"
        self.fail(f"unexpected adb call: {args}")

    def test_restart_checks_the_same_window_and_keyboard_before_and_after(self):
        own_editor.restart()
        self.assertEqual(self.adb.call_args_list, [self.selected_call, self.restart_call, self.selected_call])
        self.assertEqual(self.focus.call_count, 2)

    def test_wrong_window_refuses_the_request(self):
        self.focus.return_value = self.window("abcd").replace("SpaceGestureActivity", "GuideActivity")
        with self.assertRaisesRegex(checks.Stop, "field in front"):
            own_editor.restart()
        self.adb.assert_not_called()

    def test_release_keyboard_refuses_the_request(self):
        self.selected = "com.titankeys/com.titankeys.keyboard.TitanKeysIme"
        with self.assertRaisesRegex(checks.Stop, "selected keyboard"):
            own_editor.restart()
        self.assertEqual(self.adb.call_args_list, [self.selected_call])

    def test_old_apk_and_refused_restart_stop_before_input_with_no_fallback(self):
        for data in ('on en,ru; typing en', 'editor-restart-v1 refused', ''):
            with self.subTest(data=data):
                self.adb.reset_mock()
                self.answer = f'Broadcast completed: result=0, data="{data}"'
                with self.assertRaisesRegex(checks.Stop, "refused or unsupported"):
                    own_editor.restart()
                self.assertEqual(self.adb.call_args_list, [self.selected_call, self.restart_call])

    def test_replaced_window_during_restart_stops_the_trial(self):
        self.focus.side_effect = [self.window("abcd"), self.window("efab")]
        with self.assertRaisesRegex(checks.Stop, "window changed"):
            own_editor.restart()

    def test_capitals_setup_restarts_and_explicit_return_still_switches_android_ime(self):
        with patch.object(checks, "adb", return_value='data="on en,ru; typing en"'), \
             patch.object(checks, "keyboard") as keyboard, patch.object(own_editor, "restart") as restart:
            switch_capitals.select(["en", "ru"], "en")
            restart.assert_called_once()
            keyboard.assert_not_called()
            restart.reset_mock()
            switch_capitals.select(["en", "ru"], "en", return_from_keyboard=True)
            restart.assert_not_called()
            self.assertEqual(keyboard.call_args_list, [call("release"), call("debug")])


if __name__ == "__main__":
    unittest.main()
