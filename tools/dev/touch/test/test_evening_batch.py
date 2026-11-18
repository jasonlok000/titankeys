"""Offline checks for the drawn Yale starter tap. No phone is used."""
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import evening_batch as evening

MARKER = "charter-test"
DRAWN = "choice row: yue-yale predictions 1-2=早晨 | 3-3=你好 | 4-5=唔該 | 6-8=食咗飯未"
MARK = f"I/TitanKeysCodex( 123): {MARKER}"


def log(*lines, marker=MARK):
    return "\n".join([marker, *(f"D/TitanKeysBar(234): {line}" for line in lines)])


class StarterTapTest(unittest.TestCase):
    def test_latest_draw_yields_the_word_at_the_tap(self):
        choices = evening.drawn_starters(log(DRAWN, "bar: yue-yale - | - | -"), MARKER)
        x, word = evening.starter_target(choices)
        self.assertEqual(word, "唔該")
        for centre in (498, 578):
            for pitch in (92, 124):
                self.assertLess(centre - pitch, x)
                self.assertLess(x, centre + pitch)

    def test_split_centre_does_not_tap_the_t_y_boundary(self):
        x, word = evening.starter_target([(4, 4, "你好"), (5, 5, "唔該")])
        self.assertEqual((x, word), (492, "你好"))
        self.assertIsNone(evening.starter_target([(1, 1, "你好")]))

    def test_stale_pending_plain_wrong_route_and_bad_ranges_supply_no_choice(self):
        recent = (
            "choice row: yue-yale pending 1-3=你好",
            "choice row: yue-yale plain - | - | -",
            "choice row: yue-jyutping predictions 1-3=你好",
            "choice row: yue-yale predictions 2-4=你好 | 4-5=唔該",
            "choice row: yue-yale predictions 1-3=你好 | 9-9=唔該",
            "choice row: yue-yale predictions 1-3=你好 | broken",
            "bar: en - | - | -",
            "bar notice: Warming up Yale…",
        )
        for row in recent:
            with self.subTest(row=row):
                self.assertEqual(evening.drawn_starters(log(DRAWN, row), MARKER), [])
        self.assertEqual(evening.drawn_starters(log(DRAWN, marker="") + "\n" + MARK, MARKER), [])
        self.assertEqual(evening.drawn_starters(log(DRAWN, marker=MARK + "-older"), MARKER), [])
        self.assertEqual(evening.drawn_starters(log("bar: yue-yale - | 唔該 | -"), MARKER), [])

    def test_verdict_is_exact_and_looks_when_evidence_is_missing(self):
        cases = [
            (log(DRAWN), "1076x1200", "true", "唔該", True, True),
            (log(DRAWN), "1076x1200", "true", "唔該 ", False, True),
            (log(DRAWN), "1076x1200", "true", "你好", False, True),
            (log(DRAWN), "1076x1200", "true", "", False, True),
            (log(DRAWN), "1080x1200", "true", "唔該", None, False),
            (log(DRAWN), "1076x1200", "false", "唔該", None, False),
            (log(DRAWN, marker=""), "1076x1200", "true", "唔該", None, False),
            (log(DRAWN), "1076x1200", "true", evening.Stop("field disappeared"), None, True),
        ]
        for raw, size, shown, actual, verdict, tapped in cases:
            with (
                self.subTest(size=size, shown=shown, actual=actual, verdict=verdict),
                patch.object(evening, "yale"),
                patch.object(evening, "play"),
                patch.object(evening, "record") as record,
                patch.object(evening.time, "sleep"),
                patch.object(evening.language_checks, "fresh"),
                patch.object(evening.observed, "mark_attempt", return_value=(MARKER, "1234567890.000")),
                patch.object(evening.observed, "own_field", side_effect=["", actual]),
                patch.object(evening.checks, "guarded") as guarded,
                patch.object(evening, "adb", side_effect=[f"Physical size: {size}\n", f"mInputShown={shown}", raw]),
            ):
                evening.check_starter_tap()
                self.assertIs(record.call_args.args[1], verdict)
                if tapped:
                    guarded.assert_called_once_with("tap", "538", str(evening.switcher_checks.BAR_Y))
                else:
                    guarded.assert_not_called()


if __name__ == "__main__":
    unittest.main()
