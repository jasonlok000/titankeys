"""Public sample codes and simulated UI dump failures. No connected device needed."""
from pathlib import Path
import sys
import unittest
from unittest.mock import Mock, call, patch

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tools/dev/touch"))
import checks
import language_checks
import chinese_checks
import review_batch
import switcher_checks


class LanguageProbes(unittest.TestCase):
    def test_same_method_restart_verifies_saved_set_and_android_subtype(self):
        on = ["en", "ru"]
        debug = f"{checks.APP}/com.titankeys.keyboard.TitanKeysIme"
        subtype = {method: ident for ident, method in language_checks.languages.subtype_ids().items()}
        for saved, active, logs, wanted in [
            ("en,ru", "en", [], True),
            ("en,ru", "ru", ["TitanKeysIM: Input method at field start: en (saved)"], False),
            ("en,fr", "en", ["TitanKeysIM: Input method at field start: en (saved)"], False),
        ]:
            with self.subTest(saved=saved, active=active, logs=logs):
                methods = ["en"]

                def adb(*args):
                    if args[:4] == ("shell", "settings", "get", "secure"):
                        return debug
                    if args[-3:] == ("--ez", "read", "true"):
                        return f'Broadcast completed: result=0, data="on {methods[0]}; typing en"'
                    self.assertEqual(args[-6:], ("--es", "on", "en,ru", "--es", "current", "en"))
                    methods[0] = saved
                    return 'Broadcast completed: result=0, data="on en,ru; typing en"'

                with patch.dict(language_checks.os.environ, {"TK_DEBUG_IME": debug}), \
                        patch.object(language_checks, "field_in_front", return_value=True), \
                        patch.object(language_checks, "adb", side_effect=adb), \
                        patch.object(language_checks.languages, "current_subtype", return_value=subtype[active]), \
                        patch.object(language_checks.languages, "log_lines", return_value=logs), \
                        patch.object(language_checks.languages, "logs_on"), \
                        patch.object(language_checks.own_editor, "restart") as restart, \
                        patch.object(language_checks.time, "sleep"):
                    self.assertIs(language_checks.turn_on(on, "en"), wanted)
                restart.assert_called_once_with()

    def test_review_restart_reorders_both_owned_activities_before_focusing(self):
        app = checks.APP
        field = review_batch.languages.FIELD_ACTIVITY
        main = "com.vandam.ink.MainActivity"
        windows = [f"mCurrentFocus=Window{{123 u0 {app}/{activity}}}" for activity in [field, main, field, field]]
        with patch.object(review_batch.languages, "front", side_effect=windows) as front, \
                patch.object(review_batch.languages, "focus_field", return_value=True) as focus, \
                patch.object(review_batch, "adb") as adb, patch.object(review_batch.time, "sleep"):
            events = Mock()
            events.attach_mock(front, "front")
            events.attach_mock(focus, "focus")
            events.attach_mock(adb, "adb")
            review_batch.field_start_again()
        self.assertEqual(events.mock_calls, [
            call.front(), call.adb("shell", "am", "start", "--activity-reorder-to-front", "-n", f"{app}/{main}"),
            call.front(), call.adb("shell", "am", "start", "--activity-reorder-to-front", "-n", f"{app}/{field}"),
            call.front(), call.focus(), call.front(),
        ])

    def test_review_restart_stops_without_focusing_if_own_activity_is_unverified(self):
        app = checks.APP
        field = review_batch.languages.FIELD_ACTIVITY
        in_field = f"mCurrentFocus=Window{{123 u0 {app}/{field}}}"
        in_main = f"mCurrentFocus=Window{{123 u0 {app}/com.vandam.ink.MainActivity}}"
        in_release = f"mCurrentFocus=Window{{123 u0 com.titankeys/{field}}}"
        in_owner = "mCurrentFocus=Window{123 u0 other.app/.Main}"
        for windows, starts in [
            ([in_release], 0),
            ([in_field, in_owner], 1),
            ([in_field, in_main, in_owner], 2),
            ([in_field, in_main, in_main], 2),
        ]:
            with self.subTest(starts=starts), patch.object(review_batch.languages, "front", side_effect=windows), \
                    patch.object(review_batch.languages, "focus_field") as focus, \
                    patch.object(review_batch, "adb") as adb, patch.object(review_batch.time, "sleep"):
                with self.assertRaises(checks.Stop):
                    review_batch.field_start_again()
                self.assertEqual(adb.call_count, starts)
                focus.assert_not_called()

    def test_shape_and_phonetic_methods_use_composition(self):
        for method, actions, expected in [
            ("zh-cangjie", ["h", "q", "i", "space"], "我"),
            ("zh-quick", ["h", "q", "i", "space"], "我"),
            ("zh-zhuyin", ["n", "e", "enter"], "ne"),
        ]:
            with self.subTest(method=method), patch.object(language_checks, "fresh"), \
                    patch.object(language_checks.guide_checks, "leave_guide"), \
                    patch.object(language_checks.languages, "in_field", return_value=True), \
                    patch.object(language_checks, "turn_on", return_value=True), \
                    patch.object(language_checks.languages, "log_lines", return_value=[]), \
                    patch.object(language_checks.checks, "field", return_value=expected), \
                    patch.object(language_checks, "record") as record, patch.object(language_checks, "play") as play:
                language_checks.one(method, ["en", method])
                self.assertEqual(list(play.call_args.args[:-1]), language_checks.gestures(actions))
                self.assertIs(record.call_args.args[1], True)

    def test_switcher_tap_has_its_own_single_haptic(self):
        lines = ["tap at 538,1170 on y6", "the tap played its own haptic"]
        press = ("menu_press", "Played")
        for played, text, passed in [
            ([press], "й", True), ([press], "Й", True), ([press], "q", False),
            ([], "й", False), ([("menu_press", "Skipped")], "й", False),
            ([press, ("screen_tap", "Played")], "й", False), ([press, press], "й", False),
        ]:
            self.assertIs(switcher_checks.switcher_tap_ok(lines, played, text), passed)

    def test_space_with_only_predictions(self):
        for first, second, expected in [("我", "我 ", True), ("我", "我為", False), ("", " ", False)]:
            with self.subTest(second=second), patch.object(chinese_checks, "fresh"), \
                    patch.object(language_checks, "turn_on", return_value=True), \
                    patch.object(chinese_checks, "shot_then", side_effect=[first, second]), \
                    patch.object(chinese_checks, "record") as record:
                chinese_checks.suggestions("zh-next-pinyin", "zh-pinyin", "wo")
                self.assertIs(record.call_args.args[1], expected)

    def read(self, dumps, windows):
        def adb(*args):
            if args[1] == "dumpsys":
                return f"mCurrentFocus=Window{{123 u0 {next(windows)}/Main}}"
            if args[1] == "cat":
                return next(dumps)
            return ""
        return patch.object(checks, "adb", side_effect=adb)

    def test_retry_empty_dump(self):
        app = checks.APP
        xml = f'<hierarchy><node package="{app}" focused="true" class="android.widget.EditText" text="ok"/></hierarchy>'
        with self.read(iter(["", xml]), iter([app] * 4)), patch.object(checks.time, "sleep"):
            self.assertEqual(checks.field(), "ok")

    def test_stop_after_empty_dumps(self):
        with self.read(iter([""] * 3), iter([checks.APP] * 6)), patch.object(checks.time, "sleep"):
            with self.assertRaisesRegex(checks.Stop, "three attempts"):
                checks.field()

    def test_lost_focus_deletes_dump_unread(self):
        with self.read(iter([]), iter([checks.APP, "another.app"])) as adb:
            with self.assertRaisesRegex(checks.Stop, "lost focus"):
                checks.field()
            self.assertEqual(adb.call_args.args[1], "rm")


if __name__ == "__main__":
    unittest.main()
