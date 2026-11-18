"""Check the gesture batch against recorded field text without starting adb."""
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tools/dev/touch"))
import gesture_batch as batch


class GestureOracles(unittest.TestCase):
    def test_joined_chinese_removes_one_character(self):
        for after, passed in [("你好我", True), ("你好", False), ("你好我們", False)]:
            with self.subTest(after=after), patch.object(batch.language_checks, "fresh"), \
                    patch.object(batch, "play"), patch.object(batch, "record") as record, \
                    patch.object(batch.checks, "field", side_effect=["你好", "你好我們", after]):
                batch.flick("zh-pinyin")
                self.assertEqual(record.call_args.args[1], passed)

    def test_danish_stop_preserves_dictionary_word(self):
        for after, passed in [("med. ", True), ("med  ", False), ("wwe. ", False)]:
            with self.subTest(after=after), patch.object(batch.language_checks, "fresh"), \
                    patch.object(batch, "play") as play, patch.object(batch, "record") as record, \
                    patch.object(batch.checks, "field", side_effect=["med", after]):
                batch.double_space("da")
                self.assertEqual(play.call_args_list[0].args, (*batch.keys("med"), "+300"))
                self.assertEqual(record.call_args.args[1], passed)


if __name__ == "__main__":
    unittest.main()
