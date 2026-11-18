#!/usr/bin/env python3
"""Offline fixtures for accessible page oracles; never imports a live adb client."""
from contextlib import contextmanager
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import Mock, patch
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'tools/dev/touch'))
from page_probe import APP, Missing, PageUI, Unobservable, matches, method_state, parse_nodes, practice, shown_name


def node(text='', **extra):
    return dict({'package': APP, 'text': text, 'bounds': '[10,20][210,100]', 'enabled': 'true', 'clickable': 'true'}, **extra)


class ProbeTests(unittest.TestCase):
    def test_exact_accented_accessible_label(self):
        rows = parse_nodes(f'<hierarchy><node package="{APP}" text="Português" clickable="true" bounds="[1,2][3,4]"/></hierarchy>')
        self.assertTrue(matches(rows[0], 'Português', 'action'))
        self.assertFalse(matches(rows[0], 'Portugu'))
        self.assertTrue(matches(node('', **{'content-desc': 'Português, Portuguese'}), 'Português'))

    def test_empty_dump_is_unobservable_not_missing(self):
        for xml in ('', '<bad', '<hierarchy><node package="other"/></hierarchy>'):
            with self.assertRaises(Unobservable):
                parse_nodes(xml)

    def test_valid_owned_dump_missing_label_is_failure(self):
        ui = PageUI(Mock(), sleep=lambda _: None)
        ui.snapshot = Mock(return_value=[node('English')])
        with self.assertRaises(Missing):
            ui.find('Português')

    def test_lost_front_never_taps(self):
        c = Mock()
        c.adb.return_value = 'mCurrentFocus=Window{a u0 com.owner.bank/Main}'
        with self.assertRaises(Unobservable):
            PageUI(c, sleep=lambda _: None).tap(node('Português'))
        self.assertFalse(any(call.args[:2] == ('shell', 'input') for call in c.adb.call_args_list))

    def test_changed_front_during_dump_is_not_missing_and_deletes_dump(self):
        c = Mock()
        fronts = iter([f'mCurrentFocus=Window{{a u0 {APP}/Main}}', 'mCurrentFocus=Window{a u0 other/Main}'])
        c.adb.side_effect = lambda *a: next(fronts) if a[:3] == ('shell', 'dumpsys', 'window') else ''
        with self.assertRaises(Unobservable):
            PageUI(c).snapshot()
        self.assertTrue(any(call.args[:3] == ('shell', 'rm', '-f') for call in c.adb.call_args_list))

    def test_readback_and_finally_restore(self):
        state = (['en', 'af'], 'af')
        writes = []
        def adb(*args):
            nonlocal state
            if '--es' in args:
                state = (args[args.index('on') + 1].split(','), args[args.index('current') + 1])
                writes.append(state)
            return 'Broadcast completed: data="on ' + ','.join(state[0]) + '; typing ' + state[1] + '"'
        ui = PageUI(SimpleNamespace(adb=adb))
        with self.assertRaises(Missing):
            with ui.methods(['en', 'pt'], 'en'):
                raise Missing('fixture failure')
        self.assertEqual(state, (['en', 'af'], 'af'))
        self.assertEqual(len(writes), 2)
        with self.assertRaises(Unobservable):
            method_state('not set')

    def test_toggle_and_practice_button_are_not_confused(self):
        toggle = node('倉頡', checkable='true', checked='true', **{'class': 'android.widget.Switch'})
        button = node('倉頡', checkable='false', **{'class': 'android.widget.Button'})
        self.assertTrue(matches(toggle, '倉頡', 'toggle'))
        self.assertFalse(matches(toggle, '倉頡', 'button'))
        self.assertTrue(matches(button, '倉頡', 'button'))
        # Actual T5 Control.className and NodeKind::Button(selectable=true).
        for checked in ('true', 'false'):
            radio = node('倉頡', checkable='true', checked=checked, **{'class': 'android.widget.RadioButton'})
            self.assertTrue(matches(radio, '倉頡', 'button'))
            self.assertTrue(matches(radio, '倉頡', 'choice'))
            self.assertFalse(matches(radio, '倉頡', 'toggle'))
        self.assertFalse(matches(toggle, '倉頡', 'choice'))
        self.assertFalse(matches(button, '倉頡', 'choice'))

    def test_shown_name_matches_app_script_fallback(self):
        self.assertEqual(shown_name({'name': 'Português', 'nameEnglish': 'Portuguese', 'script': 'Latn'}), 'Português')
        self.assertEqual(shown_name({'name': '倉頡', 'nameEnglish': 'Cangjie', 'script': 'Hani'}), '倉頡')
        self.assertEqual(shown_name({'name': 'हिन्दी', 'nameEnglish': 'Hindi', 'script': 'Deva'}), 'Hindi')

    def test_chinese_practice_uses_actual_radio_selector(self):
        ui = Mock()
        ui.find.side_effect = [node('倉頡', checkable='true', checked='false', **{'class': 'android.widget.RadioButton'}),
                               node('', **{'class': 'android.widget.EditText'})]
        ui.binding.return_value = True
        ui.c.field.return_value = '手'
        with patch('page_probe.assets', return_value=({'page': {'typeHere': 'Type here'}}, {}, lambda _: {'name': '倉頡', 'script': 'Hani'})):
            self.assertEqual(practice(ui, 'zh-cangjie', 'q', choose=True), 1)
        self.assertEqual(ui.find.call_args_list[0].args, ('倉頡', 'choice'))

    def test_practice_refuses_preexisting_text(self):
        ui = Mock()
        ui.find.return_value = node('existing owner text', **{'class': 'android.widget.EditText'})
        with patch('page_probe.assets', return_value=({'page': {'typeHere': 'Type here'}}, {}, lambda _: {})):
            with self.assertRaises(Unobservable):
                practice(ui, 'pt', 'o')
        ui.c.play.assert_not_called()

    def test_practice_method_binding_and_result_cleanup(self):
        ui = Mock()
        ui.find.return_value = node('', **{'class': 'android.widget.EditText'})
        ui.binding.return_value = True
        ui.c.field.return_value = 'O'
        with patch('page_probe.assets', return_value=({'page': {'typeHere': 'Type here'}}, {}, lambda _: {})):
            self.assertEqual(practice(ui, 'pt', 'o'), 1)
        self.assertEqual([call.args for call in ui.c.play.call_args_list], [('key:o',), ('key:del',)])

    def test_practice_binding_change_does_not_type(self):
        ui = Mock()
        ui.find.return_value = node('', **{'class': 'android.widget.EditText'})
        ui.binding.side_effect = [True, False]
        with patch('page_probe.assets', return_value=({'page': {'typeHere': 'Type here'}}, {}, lambda _: {})):
            with self.assertRaises(Unobservable):
                practice(ui, 'pt', 'o')
        ui.c.play.assert_not_called()

    def test_fifth_toggle_oracle_catches_saved_state_change(self):
        fake_checks = SimpleNamespace(record=Mock())
        fake_guide = SimpleNamespace(shot=lambda _: 'fixture')
        with patch.dict(sys.modules, {'checks': fake_checks, 'guide_checks': fake_guide}):
            import importlib
            sys.modules.pop('page_batch', None)
            batch = importlib.import_module('page_batch')
        ids = ['a', 'b', 'c', 'd', 'e', 'f']
        wording = {'page': {'methodsFull': 'Four at a time'}}
        pages = {'zh': {'groups': [{'title': 'Cantonese', 'methods': ids[:2]}, {'title': 'Mandarin', 'methods': ids[2:4]}, {'title': 'By shape', 'methods': ids[4:]}]}}
        for broken in (False, True):
            class UI:
                tapped = False
                restored = False
                @contextmanager
                def methods(self, on, current):
                    try:
                        yield
                    finally:
                        self.restored = True
                def open_language(self, label):
                    pass
                def find(self, label, kind=None):
                    return node(label, checkable='true', checked='true' if label in ids[:4] else 'false')
                def tap(self, n):
                    self.tapped = True
                def state(self):
                    return (['en', *ids[:4], *(['e'] if broken and self.tapped else [])], 'en')
            ui = UI()
            with patch.object(batch, 'PageUI', return_value=ui), patch.object(batch, 'assets', return_value=(wording, pages, lambda identifier: {'name': identifier})):
                batch.check_chinese_groups_and_limit()
            self.assertIs(fake_checks.record.call_args.args[1], not broken)
            self.assertTrue(ui.restored)


if __name__ == '__main__':
    unittest.main()
