"""Public sample codes and simulated UI dump failures. No connected device needed."""
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tools/dev/touch"))
import checks
import language_checks
import chinese_checks


class LanguageProbes(unittest.TestCase):
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
