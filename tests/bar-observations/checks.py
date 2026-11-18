#!/usr/bin/env python3
"""Synthetic observations only; no phone, native engine or user log access."""
from pathlib import Path
import sys
import unittest
from types import SimpleNamespace
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'tools/dev/touch'))
from bar_observations import Unobservable, attempt_lines, middle, observed_bar, own_field, pick_verdict, space_verdict, suffix_verdict


class Observations(unittest.TestCase):
    def test_marker_excludes_earlier_attempt_and_unrelated_tags(self):
        raw = '\n'.join(['D/TitanKeysBar( 10): bar: el - | stale | -',
                         'I/TitanKeysCodex( 20): charter-old',
                         'D/TitanKeysBar( 10): bar: el - | also stale | -',
                         'I/TitanKeysCodex( 20): charter-current',
                         'I/Other( 10): bar: el - | unrelated | -',
                         'D/TitanKeysBar( 10): bar: el - | καλημέρα | -'])
        self.assertEqual(middle(attempt_lines(raw, 'charter-current'), 'el'), 'καλημέρα')

    def test_rotated_marker_is_unobservable_not_stale_success(self):
        with self.assertRaises(Unobservable):
            attempt_lines('D/TitanKeysBar(10): bar: el - | καλημέρα | -', 'missing')
        with self.assertRaises(Unobservable):
            attempt_lines('I/Other(10): charter-current', 'charter-current')

    def test_marker_without_new_bar_or_with_new_notice_never_reuses_old_row(self):
        head = 'D/TitanKeysBar(10): bar: ja - | old | -\nI/TitanKeysCodex(20): charter-current\n'
        self.assertIsNone(middle(attempt_lines(head, 'charter-current'), 'ja'))
        for tail in ('D/TitanKeysBar(10): bar notice: loading', 'D/TitanKeysBar(10): unknown new format'):
            self.assertIsNone(middle(attempt_lines(head + tail, 'charter-current'), 'ja'))
    def test_greek_correction_matches_observed_middle(self):
        offered = middle([('bar', 'el', ['κσλημερα', 'καλημέρα', '-'])], 'el')
        self.assertTrue(space_verdict('καλημέρα ', offered, ' '))
        self.assertFalse(space_verdict('κσλημερα ', offered, ' '))

    def test_bar_only_or_disabled_correction_keeps_observed_typed_word(self):
        offered = middle([('bar', 'el', ['καλημέρα', 'κσλημερα', '-'])], 'el')
        self.assertTrue(space_verdict('κσλημερα ', offered, ' '))
        self.assertFalse(space_verdict('καλημέρα ', offered, ' '))

    def test_no_stale_bar_reuse(self):
        for lines in ([], [('notice', 'loading')], [('bar', 'es', ['-', 'café', '-'])],
                      [('bar', 'el', ['-', 'καλημέρα', '-']), ('notice', 'loading')],
                      [('bar', 'el', ['-', '-', '-'])]):
            self.assertIsNone(middle(lines, 'el'))
            self.assertIsNone(space_verdict('anything', middle(lines, 'el'), ' '))

    def test_japanese_date_candidate_is_exact_text_not_script(self):
        for value in ('2026/11/18', '2026-11-18', '今日', 'きょう'):
            self.assertTrue(pick_verdict(value, value))
            self.assertTrue(space_verdict(value, value, ''))
            self.assertFalse(pick_verdict('', value))
            self.assertFalse(pick_verdict('wrong page', value))
        self.assertFalse(pick_verdict('2026/11/19', '2026/11/18'))

    def test_unobserved_menu_tile_remains_look(self):
        for value in ('', '今日', '2026/11/18', 'unrelated'):
            self.assertIsNone(pick_verdict(value))

    def test_nihon_must_commit_zero_not_any_offered_choice(self):
        # Engine Picker uses BarLog.places: second at left, candidate zero at middle.
        zero = middle([('bar', 'ja', ['日本', 'にほん', 'ニホン'])], 'ja')
        self.assertTrue(space_verdict('にほん', zero, ''))
        self.assertFalse(space_verdict('日本', zero, ''))
        self.assertFalse(space_verdict('ニホン', zero, ''))

    def test_prediction_space_does_not_pick_following_word(self):
        self.assertTrue(suffix_verdict('おはよう', 'おはよう ', ' '))
        self.assertFalse(suffix_verdict('おはよう', 'おはようございます', ' '))
        self.assertFalse(suffix_verdict('おはよう', 'おはよう  ', ' '))
        self.assertIsNone(suffix_verdict('', ' ', ' '))

    def test_japanese_marks_preserve_the_observed_prefix(self):
        for prefix in ('日本', 'にほん', 'ニホン', '123'):
            self.assertTrue(suffix_verdict(prefix, prefix + '。、', '。、'))
            self.assertFalse(suffix_verdict(prefix, prefix + '.,', '。、'))
            self.assertFalse(suffix_verdict(prefix, 'changed。、', '。、'))
        self.assertIsNone(suffix_verdict('', '。、', '。、'))

    def test_owned_field_required_before_reading_text(self):
        checks = SimpleNamespace(APP='com.titankeys.debug', Stop=RuntimeError, adb=Mock(return_value='mCurrentFocus=Window{u0 other/App}'), field=Mock())
        with patch.dict(sys.modules, {'checks': checks}):
            with self.assertRaises(RuntimeError):
                own_field()
        checks.field.assert_not_called()

    def test_log_property_restored_on_failure(self):
        calls = []
        def adb(*args):
            calls.append(args)
            if 'window' in args:
                return 'mCurrentFocus=Window{u0 com.titankeys.debug/com.titankeys.keyboard.SpaceGestureActivity}'
            if 'getprop' in args:
                return 'I'
            return ''
        checks = SimpleNamespace(APP='com.titankeys.debug', Stop=RuntimeError, adb=adb, field=lambda: '')
        with patch.dict(sys.modules, {'checks': checks, 'interactions_batch': SimpleNamespace(TAG='TitanKeysBar')}):
            with self.assertRaises(ValueError):
                with observed_bar():
                    raise ValueError('fixture')
        self.assertEqual(calls[-1], ('shell', 'setprop', 'log.tag.TitanKeysBar', 'I'))
        self.assertFalse(any(call[:2] == ('logcat', '-c') for call in calls))
        self.assertTrue(any(call[:4] == ('shell', 'log', '-t', 'TitanKeysCodex') for call in calls))

    def test_empty_property_restores_empty_shell_argument_and_markers_are_unique(self):
        import shlex
        calls = []
        def adb(*args):
            calls.append(args)
            return 'mCurrentFocus=Window{u0 com.titankeys.debug/com.titankeys.keyboard.SpaceGestureActivity}' if 'window' in args else ''
        checks = SimpleNamespace(APP='com.titankeys.debug', Stop=RuntimeError, adb=adb, field=lambda: '')
        with patch.dict(sys.modules, {'checks': checks}):
            with observed_bar():
                pass
            with observed_bar():
                pass
        restores = [c for c in calls if c[:3] == ('shell', 'setprop', 'log.tag.TitanKeysBar') and c[-1] != 'D']
        self.assertEqual(len(restores), 2)
        self.assertEqual(shlex.split(' '.join(restores[-1][1:]))[-1], '')
        markers = [c[-1] for c in calls if c[:4] == ('shell', 'log', '-t', 'TitanKeysCodex')]
        self.assertEqual(len(set(markers)), 2)


if __name__ == '__main__':
    unittest.main()
