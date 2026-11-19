"""Exercise the leak and feedback harness without calling adb."""
import importlib.util
import os
from pathlib import Path
import sys
from types import SimpleNamespace
from unittest.mock import Mock, patch

ROOT = Path(__file__).resolve().parents[2]
APP = 'com.titankeys.debug'


def load(path, name):
    spec = importlib.util.spec_from_file_location(name, ROOT / path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


checks = SimpleNamespace(APP=APP, Stop=RuntimeError, adb=Mock(), record=Mock())
guide = SimpleNamespace(guide=Mock(), leave_guide=Mock())
languages = SimpleNamespace(in_field=Mock(return_value=True))
with patch.dict(sys.modules, checks=checks, guide_checks=guide, languages=languages, retest_batch=Mock()):
    leak = load('tools/dev/touch/leak_batch.py', 'leak_batch')

open_playground = "playground 1ead6cc: open, held by the open playgrounds' list"
destroyed = "guide d1e: destroyed, still held, held by the playground's asker"
ending = '; input methods: 1 keyboard(s) (typing en); rime data: none copied'
assert leak.verdict(open_playground + ending, True) is True
assert leak.verdict(open_playground + ending) is None
assert leak.verdict(open_playground + '; ' + destroyed + ending, True) is False
assert leak.verdict('guide a17: finishing' + ending, True) is None
assert leak.verdict('guide a17: open' + ending, True) is None
assert leak.verdict(leak.CLEAN + ending) is True
assert leak.verdict('Broadcast completed: result=0') is None
assert leak.verdict('unexpected; ' + open_playground + ending, True) is None

with patch.object(leak.time, 'sleep'):
    checks.adb.side_effect = [f'Broadcast completed: result=0, data="{destroyed + ending}"',
                              f'Broadcast completed: result=0, data="{leak.CLEAN + ending}"']
    leak.leakcheck('transient', 'a retry')
    assert checks.record.call_args.args[1] is True and not leak.held_seen
    checks.adb.side_effect = [f'Broadcast completed: result=0, data="{destroyed + ending}"'] * 2
    leak.leakcheck('retained', 'a retry')
    assert checks.record.call_args.args[1] is False and leak.held_seen
    with patch.dict(os.environ, TK_SAFE_RUNNER='test'):
        leak.guide_runs(2)
    assert languages.in_field.call_count == 3
    assert guide.guide.call_count == guide.leave_guide.call_count == 2

phone = SimpleNamespace(accent_count=Mock(return_value=120), back=Mock(), open_page=Mock(),
                        screen=Mock(), tap=Mock(), title_right_edge=Mock(side_effect=[600, 730]))
with patch.dict(sys.modules, phone=phone):
    feedback = load('tools/checks/feedback.py', 'feedback')
with patch.object(feedback.time, 'sleep'), patch.dict(os.environ, TK_SAFE_RUNNER='test'):
    with patch.object(feedback, 'guarded_pixels', return_value=object()) as owned:
        feedback.main()
    assert owned.call_count == 2 and phone.back.call_count == 2
    phone.screen.assert_not_called()
    with patch.object(feedback, 'guarded_pixels', side_effect=feedback.Unobservable('refused')):
        try:
            feedback.main()
        except feedback.Unobservable:
            pass
        else:
            raise AssertionError('missing owned capture was treated as a verdict')
    phone.screen.assert_not_called()
print('PASS leak lifecycle classification, guarded fixture return, and feedback surface selection')
