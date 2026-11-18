import importlib.util
import os
from pathlib import Path
import sys
import types
import unittest
from unittest.mock import patch
ROOT = Path(__file__).resolve().parents[2]
checks = types.ModuleType("checks")
checks.APP = "com.titankeys.debug"
checks.Stop = type("Stop", (Exception,), {})
for name in ("checks", "language_checks", "languages", "own_editor"):
    sys.modules[name] = checks if name == "checks" else types.ModuleType(name)
spec = importlib.util.spec_from_file_location("menu_controls", ROOT / "tools/dev/touch/menu_controls_checks.py")
m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m)
ROW = "I/TitanKeysMenu( 123): choices page=1 pages=3 previous=0 next=1 visible=1 bounds=0,800,1076,400 row=100 controls=1000,950,1000,1050,880,1050"
class Checks(unittest.TestCase):
    def test_fresh_owned_state(self):
        state = m.parse_menu("marker\n" + ROW, "marker", "123")
        self.assertEqual(state["page"], 1)
        self.assertEqual(m.point(state,9,2), (1000,1050))
    def test_control_must_be_inside_visible_menu(self):
        state=m.parse_menu("marker\n"+ROW,"marker","123")
        state["next_y"]=5000
        with self.assertRaises(checks.Stop): m.point(state,9,2)
        state["next_y"]=1050; state["visible"]=0
        with self.assertRaises(checks.Stop): m.point(state,9,2)
    def test_no_stale_or_foreign_state(self):
        for text in (ROW, ROW + "\nmarker", "marker\n" + ROW.replace("123", "456")):
            self.assertIsNone(m.parse_menu(text,"marker","123"))
    def test_last_marker_wins(self):
        self.assertIsNone(m.parse_menu("marker\n"+ROW+"\nmarker", "marker", "123"))
    def test_bad_geometry_or_page_is_unobservable(self):
        for row in (ROW.replace("page=1", "page=4"), ROW.replace("1076,400", "0,400")):
            self.assertIsNone(m.parse_menu("marker\n"+row,"marker","123"))
    def test_cursor_is_metadata_only(self):
        self.assertEqual(m.cursor_from_dump("mCursorSelStart=4 mCursorSelEnd=9"),(4,9))
        self.assertIsNone(m.cursor_from_dump("mCursorSelStart=-1 mCursorSelEnd=-1"))
        self.assertIsNone(m.cursor_from_dump("some unrelated state"))
    def test_runner_required(self):
        with patch.dict(os.environ, {}, clear=True):
            with self.assertRaises(checks.Stop): m.guard()
    def test_missing_cursor_never_passes(self):
        rows=[]
        with patch.object(m,"snapshot",return_value=("seed",None)), patch.object(checks,"record",side_effect=lambda *a: rows.append(a),create=True):
            m.unchanged("x",("seed",None))
        self.assertIsNone(rows[-1][1])
    def test_actual_movement_fails(self):
        rows=[]
        with patch.object(m,"snapshot",return_value=("seed",(2,2))), patch.object(checks,"record",side_effect=lambda *a: rows.append(a),create=True):
            m.unchanged("x",("seed",(1,1)))
        self.assertIs(rows[-1][1],False)
    def test_restores_methods_after_check_failure(self):
        calls=[]
        def adb(*args):
            calls.append(args)
            return 'data="on en,af; typing af"'
        with patch.object(m,"guard"), patch.object(checks,"adb",side_effect=adb,create=True):
            with self.assertRaises(ValueError):
                with m.methods(): raise ValueError("synthetic test failure")
        self.assertEqual(len(calls),3)
        self.assertIn("en,af",calls[1])
    def test_restoration_requires_matching_readback(self):
        with patch.object(m,"guard"), patch.object(checks,"adb",side_effect=['on en,af; typing af','on en,af; typing af','on en; typing en'],create=True):
            with self.assertRaises(checks.Stop):
                with m.methods(): pass
    def test_method_selection_restarts_the_owned_editor_before_readback(self):
        reply = "on en,zh-pinyin,yue-jyutping; typing zh-pinyin"
        with patch.object(m, "guard"), \
             patch.object(checks, "adb", side_effect=[reply, reply], create=True) as adb, \
             patch.object(m.own_editor, "restart", create=True) as restart:
            restart.side_effect = lambda: self.assertEqual(adb.call_count, 1)
            self.assertTrue(m.select_method("zh-pinyin"))
        restart.assert_called_once_with()
        self.assertEqual(adb.call_count, 2)
    def test_methods_readback(self):
        self.assertEqual(m.saved_methods('data="on en,zh-pinyin; typing en"'),(["en","zh-pinyin"],"en"))
        with self.assertRaises(checks.Stop): m.saved_methods("missing")
if __name__ == "__main__": unittest.main()
