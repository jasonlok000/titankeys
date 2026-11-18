"""Check the gesture batch against recorded field text without starting adb."""
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tools/dev/touch"))
import gesture_batch as batch
import batch2_checks as characters


class GestureOracles(unittest.TestCase):
    def test_yale_flicks_follow_the_actual_three_picks(self):
        for final, expected in [("呢好我哋", True), ("呢好我", False)]:
            readings = ["呢好", "呢好我哋", "呢好我哋唔該晒", "呢好我哋唔該", "呢好我哋唔", final]
            with self.subTest(final=final), patch.object(characters.ib, "start"), \
                    patch.object(characters, "play"), patch.object(characters, "record") as record, \
                    patch.object(characters, "read", side_effect=readings):
                characters.check_chinese_characters()
                self.assertEqual(record.call_args_list[0].args[:2], ("zh-chars-flick", expected))

    def test_missing_yale_pick_leaves_the_probe_unresolved(self):
        with patch.object(characters.ib, "start"), patch.object(characters, "play"), \
                patch.object(characters, "record") as record, patch.object(characters, "read", side_effect=["呢好", "呢好"]):
            characters.check_chinese_characters()
            self.assertEqual(record.call_args.args[:2], ("zh-chars-setup", None))

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
