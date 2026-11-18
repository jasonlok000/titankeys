#!/usr/bin/env python3
"""Offline fixtures for accessible page oracles; never imports a live adb client."""
from contextlib import contextmanager
import os
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
    def test_open_language_requires_destination_state(self):
        ui = PageUI(Mock(), sleep=lambda _: None)
        ui.open_list = Mock()
        ui.tap = Mock()
        ui.snapshot = Mock(return_value=[])
        def find(label, kind=None):
            if label == 'Try it':
                raise Missing('Still on list')
            return node(label)
        ui.find = Mock(side_effect=find)
        with patch('page_probe.assets', return_value=({'page': {'tryIt': 'Try it', 'moved': 'Moved'}}, {}, None)):
            with self.assertRaises(Missing):
                ui.open_language('Português')
        ui.tap.assert_called_once()

    def test_open_language_uses_the_localized_practice_heading(self):
        ui = PageUI(Mock(), sleep=lambda _: None)
        ui.open_list, ui.tap, ui.find = Mock(), Mock(), Mock()
        ui.snapshot = Mock(return_value=[])
        ui.open_language('Português', 'pt')
        self.assertEqual([call.args[0] for call in ui.find.call_args_list], ['Português', 'Português', 'Experimente'])

    def test_variant_requires_persisted_selection(self):
        ui = PageUI(Mock())
        ui.variant_options = Mock(return_value=({'pt-PT': node('Portugal')}, 'pt-BR'))
        ui.tap = Mock()
        with self.assertRaisesRegex(Missing, 'did not persist'):
            ui.set_variant('pt', 'pt-PT')
        self.assertEqual(ui.variant_options.call_count, 2)

    def test_selected_variant_read_back_without_mutation(self):
        ui = PageUI(Mock())
        ui.variant_options = Mock(return_value=({'pt-PT': node('Portugal')}, 'pt-PT'))
        ui.tap = Mock()
        self.assertEqual(ui.set_variant('pt', 'pt-PT'), 'pt-PT')
        ui.tap.assert_not_called()
        self.assertEqual(ui.variant_options.call_count, 2)

    def test_variant_options_need_one_selected_radio(self):
        ui = PageUI(Mock())
        ui.open_language = Mock()
        ui.tap = Mock()
        im = {'name': 'Português', 'script': 'Latn', 'variants': [{'id': 'pt-BR', 'name': 'Brasil'}, {'id': 'pt-PT', 'name': 'Portugal'}]}
        ui.find = Mock(side_effect=lambda label, kind: node(label, checked='false'))
        with patch('page_probe.assets', return_value=({'settings': {'variant': 'Spelling'}}, {}, lambda _: im)):
            with self.assertRaisesRegex(Missing, 'exactly one'):
                ui.variant_options('pt')
        self.assertEqual(ui.find.call_args_list[0].args, ('Ortografia', 'action'))
        self.assertEqual(ui.find.call_args_list[-1].args, ('Portugal', 'choice'))

    def test_howto_uses_current_generated_points(self):
        ui = PageUI(Mock())
        ui.open_language = Mock()
        ui.find = Mock()
        im = {'name': 'Español', 'script': 'Latn', 'guide': {'family': 'latin', 'points': ['Current one', 'Current two']}}
        with patch('page_probe.assets', return_value=({'page': {'how': 'How it works'}, 'points': {}}, {}, lambda _: im)):
            self.assertEqual(ui.howto('es'), im['guide']['points'])
        self.assertEqual([c.args[0] for c in ui.find.call_args_list], ['Cómo funciona', *im['guide']['points']])

    def test_scroll_uses_owned_container_bounds(self):
        ui = PageUI(Mock(), sleep=lambda _: None)
        ui.own = Mock()
        ui.scroll([node('body', scrollable='true', bounds='[100,200][500,800]')], True)
        ui.c.adb.assert_called_once_with('shell', 'input', 'swipe', '300', '650', '300', '350', '350')


    def portuguese_batch(self):
        import importlib.util
        fake_checks = SimpleNamespace(APP=APP, Stop=RuntimeError, adb=Mock(), play=Mock(), record=Mock())
        imports = {'checks': fake_checks, 'guide_checks': Mock(), 'interactions_batch': Mock(),
                   'language_checks': Mock(), 'languages': Mock(), 'retest_batch': Mock()}
        path = Path(__file__).resolve().parents[2] / 'tools/dev/touch/pt_spelling_batch.py'
        spec = importlib.util.spec_from_file_location('pt_fixture', path)
        module = importlib.util.module_from_spec(spec)
        with patch.dict(sys.modules, imports):
            spec.loader.exec_module(module)
        return module

    def test_portuguese_failed_selection_never_types_default(self):
        batch = self.portuguese_batch()
        with patch.object(batch, 'pick', return_value=False), patch.object(batch, 'offers') as offers:
            batch.check_brasil()
            batch.check_portugal()
            offers.assert_not_called()

    def test_portuguese_restores_original_even_after_failed_selection(self):
        batch = self.portuguese_batch()
        ui = Mock()
        ui.variant_options.return_value = ({}, 'pt-PT')
        ui.set_variant.side_effect = [Missing('not persisted'), None]
        with patch('page_probe.PageUI', return_value=ui):
            self.assertFalse(batch.pick('pt-BR'))
            self.assertEqual(batch._original_variant, 'pt-PT')
            batch.finish()
        self.assertEqual(ui.set_variant.call_args.args, ('pt', 'pt-PT'))
        self.assertIsNone(batch._original_variant)


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
        ui.find.return_value = node('Digite aqui', **{'class': 'android.widget.EditText'})
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

    def test_ordinary_field_checks_the_typed_letter_without_selecting_a_method(self):
        field_activity = 'com.titankeys.keyboard.SpaceGestureActivity'
        debug_ime = APP + '/com.titankeys.keyboard.TitanKeysIme'
        languages = SimpleNamespace(FIELD_ACTIVITY=field_activity, in_field=Mock(return_value=True))
        for new_text, succeeds in (('Sampleй', True), ('Sample', False), ('Sampleq', False)):
            with self.subTest(new_text=new_text):
                c = Mock()
                c.adb.side_effect = lambda *args: (
                    f'mCurrentFocus=Window{{a u0 {APP}/{field_activity}}}' if args[1] == 'dumpsys' else debug_ime
                )
                c.field.side_effect = ['Sample', 'Sample', new_text, 'Sample']
                ui = PageUI(c)
                with patch.dict(os.environ, TK_DEBUG_IME=debug_ime, TK_IME_HELD='1'), patch.dict(sys.modules, {'languages': languages}):
                    if succeeds:
                        ui.verify_ordinary_key('q', 'й')
                    else:
                        with self.assertRaisesRegex(Missing, 'restored method'):
                            ui.verify_ordinary_key('q', 'й')
                self.assertEqual([call.args[0] for call in c.play.call_args_list],
                                 ['key:q', 'key:del'] if succeeds else ['key:q'])
                c.keyboard.assert_not_called()
                self.assertFalse(any('broadcast' in call.args for call in c.adb.call_args_list))

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
                def open_language(self, label, language):
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
