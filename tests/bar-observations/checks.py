#!/usr/bin/env python3
"""Synthetic observations only; no phone, native engine or user log access."""
from pathlib import Path
import sys
import unittest
from types import SimpleNamespace
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'tools/dev/touch'))
from bar_observations import middle, observed_bar, own_field, pick_verdict, space_verdict


class Observations(unittest.TestCase):
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


if __name__ == '__main__':
    unittest.main()
