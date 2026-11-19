"""The KeyFeed probe must start from a verified empty editor before attributing text to one chord."""
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

DEV = Path(__file__).resolve().parents[2]
sys.path[:0] = [str(DEV / "touch"), str(DEV / "phone")]
import keyfeed_batch as batch


class KeyFeedBatchTest(unittest.TestCase):
    def test_thai_chord_requires_an_empty_field_and_an_exact_result(self):
        for before, after, expected in [("ๅๅ", "๐", None), ("", "ๅ๐ๅ", False), ("", "๐", True)]:
            with self.subTest(before=before, after=after), \
                 patch.object(batch.language_checks.guide_checks, "leave_guide"), \
                 patch.object(batch.language_checks.languages, "in_field", return_value=True), \
                 patch.object(batch.language_checks, "play"), \
                 patch.object(batch.checks, "set_text", side_effect=["", before, "", ""]), \
                 patch.object(batch.language_checks, "turn_on", return_value=True), \
                 patch.object(batch, "adb"), patch.object(batch, "debug_lines", return_value=[]), \
                 patch.object(batch, "feed", return_value="queued") as send, \
                 patch.object(batch.time, "sleep"), patch.object(batch, "record") as record, \
                 patch.object(batch, "focused_text", side_effect=["ჭ"] if before else [after, "ჭ"]):
                batch.check_key_feed()
                results = {name: verdict for (name, verdict, _), _kwargs in record.call_args_list}
                self.assertIs(results["key-feed-th"], expected)
                self.assertIs(results["key-feed-ka"], True)
                sent = [args[0] for args, _kwargs in send.call_args_list]
                self.assertEqual(any("D 60 Q;" in keys for keys in sent), before == "")


if __name__ == "__main__":
    unittest.main()
