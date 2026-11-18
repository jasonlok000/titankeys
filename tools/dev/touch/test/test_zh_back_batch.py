"""Offline tests for the zh-back harness. No ADB or device is used."""
import importlib.util
from pathlib import Path
import sys
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, call, patch

PROBE = Path(__file__).resolve().parents[1] / "zh_back_batch.py"
APP = "com.titankeys.debug"
ACTIVITY = "com.titankeys.keyboard.SpaceGestureActivity"
WINDOW = f"mCurrentFocus=Window{{abcd u0 {APP}/{ACTIVITY}}}"
IDS = {1: "en", 2: "yue-yale"}
YALE = "Input method at field start: yue-yale (saved)"
OPEN = "TitanKeysSym: Sym handled: panel now Symbols, shown true"
CODE = [*(f"key:{letter}" for letter in "neihou"), "+600"]


class Stop(Exception):
    pass


class ZhBackProbeTest(unittest.TestCase):
    def setUp(self):
        self.checks = SimpleNamespace(
            APP=APP, Stop=Stop, adb=Mock(side_effect=self.adb), record=Mock(), field=Mock(return_value=""),
            guarded=Mock(), set_text=Mock(),
        )
        self.chinese = SimpleNamespace(YALE="yue-yale", CODE="neihou", code=Mock(return_value=CODE), fresh=Mock(), play=Mock())
        self.language_checks = SimpleNamespace(turn_on=Mock(return_value=True), active_method=Mock(return_value="yue-yale"))
        self.languages = SimpleNamespace(FIELD_ACTIVITY=ACTIVITY, front=Mock(return_value=WINDOW), subtype_ids=Mock(return_value=IDS))
        spec = importlib.util.spec_from_file_location("zh_back_batch_test_target", PROBE)
        self.probe = importlib.util.module_from_spec(spec)
        with patch.dict(sys.modules, checks=self.checks, chinese_checks=self.chinese,
                        language_checks=self.language_checks, languages=self.languages):
            spec.loader.exec_module(self.probe)
        self.probe.time = SimpleNamespace(sleep=Mock())
        self.probe.lines = Mock(side_effect=[[], [OPEN], [OPEN], [OPEN]])
        self.operations = Mock()
        self.operations.attach_mock(self.chinese.fresh, "fresh")
        self.operations.attach_mock(self.chinese.play, "play")
        self.operations.attach_mock(self.checks.guarded, "guarded")

    def adb(self, *args):
        if args == ("shell", "dumpsys", "input_method"):
            return "mShowRequested=true mInputShown=true"
        if args[:2] == ("shell", "getprop"):
            return "I\n"
        if args[:2] == ("shell", "setprop"):
            return ""
        self.fail(f"unexpected ADB operation in mock: {args}")

    def verdict(self, name):
        return next(result.args[1] for result in self.checks.record.call_args_list if result.args[0] == name)

    def test_live_yale_and_real_back_precede_exact_enter_without_field_start_log(self):
        # turn_on can return False after a same-method restart without a field-start log.
        self.language_checks.turn_on.return_value = False
        self.checks.field.side_effect = ["", "", "", "", "", "", "neihou"]
        self.probe.check_back_then_enter()
        self.language_checks.turn_on.assert_called_once_with(["en", "yue-yale"], "yue-yale")
        self.assertEqual(self.language_checks.active_method.call_args_list, [call(["en", "yue-yale"], IDS)] * 2)
        self.assertEqual(self.operations.mock_calls, [
            call.fresh(), call.play("key:sym", "+700"), call.guarded("keyevent", "4"),
            call.play(*CODE), call.play("key:enter", "+400"),
        ])
        for name in ("zh-back-setup", "zh-back-window", "zh-back-field", "zh-back-code", "zh-enter"):
            self.assertIs(self.verdict(name), True, name)
        self.checks.set_text.assert_called_once_with("")

    def test_panel_leak_fails_before_enter_and_verdict_keeps_exact_text(self):
        self.checks.field.side_effect = ["", "", "", "", "", "~", "~eihou"]
        self.probe.check_back_then_enter()
        self.assertIs(self.verdict("zh-back-window"), True)
        self.assertIs(self.verdict("zh-back-code"), False)
        self.assertIs(self.verdict("zh-enter"), False)
        self.checks.set_text.assert_not_called()

    def test_a_hidden_keyboard_fails_even_if_enter_is_correct(self):
        self.probe.input_shown = Mock(side_effect=[True, False])
        self.checks.field.side_effect = ["", "", "", "", "", "", "neihou"]
        self.probe.check_back_then_enter()
        self.assertIs(self.verdict("zh-back-window"), False)
        self.assertIs(self.verdict("zh-enter"), True)

    def test_wrong_or_unverified_live_method_after_back_refuses_yale_input(self):
        for method in ("en", None):
            with self.subTest(method=method):
                self.checks.record.reset_mock()
                self.chinese.play.reset_mock()
                self.language_checks.active_method.side_effect = ["yue-yale", method]
                # The old field-start message still says Yale.
                self.probe.lines.side_effect = [[YALE], [YALE, OPEN], [YALE, OPEN]]
                self.probe.check_back_then_enter()
                self.assertIsNone(self.verdict("zh-back-method"))
                self.chinese.play.assert_called_once_with("key:sym", "+700")
                self.checks.set_text.assert_not_called()

    def test_saved_yale_without_live_readback_refuses_input_despite_old_log(self):
        self.language_checks.active_method.return_value = None
        self.probe.lines.side_effect = [[YALE]]
        self.probe.check_back_then_enter()
        self.assertIsNone(self.verdict("zh-back-setup"))
        self.assertEqual(self.operations.mock_calls, [])

    def test_no_android_back_if_symbols_is_unverified(self):
        self.probe.lines.side_effect = [[], []]
        self.probe.check_back_then_enter()
        self.assertIsNone(self.verdict("zh-back-setup"))
        self.checks.guarded.assert_not_called()

    def test_replaced_window_after_back_stops_without_further_input(self):
        self.checks.guarded.side_effect = lambda *_: setattr(
            self.languages.front, "return_value", WINDOW.replace("abcd", "bbbb")
        )
        with self.assertRaisesRegex(Stop, "after Android Back: the Debug window changed"):
            self.probe.check_back_then_enter()
        self.assertIsNone(self.verdict("zh-back-window"))
        self.chinese.play.assert_called_once_with("key:sym", "+700")
        self.checks.adb.assert_any_call("shell", "setprop", "log.tag.TitanKeysIM", "I")

    def test_unowned_window_refuses_setup_and_input(self):
        self.languages.front.return_value = "mCurrentFocus=Window{zzzz u0 some.other.app/.Activity}"
        with self.assertRaisesRegex(Stop, "isn't in front"):
            self.probe.check_back_then_enter()
        self.language_checks.turn_on.assert_not_called()
        self.checks.adb.assert_not_called()
        self.assertEqual(self.operations.mock_calls, [])

    def test_ime_visibility_requires_a_consistent_value(self):
        for response, expected in (
            ("unavailable", None),
            ("mInputShown=false mInputShown=true", None),
            ("mShowRequested=true mInputShown=false", False),
            ("mShowRequested=true mInputShown=true", True),
        ):
            with self.subTest(response=response):
                self.checks.adb.side_effect = None
                self.checks.adb.return_value = response
                self.assertIs(self.probe.input_shown(), expected)


if __name__ == "__main__":
    unittest.main()
