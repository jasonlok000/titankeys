import fcntl
import io
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from unittest.mock import call, patch

ROOT = Path(__file__).resolve().parents[2]
sys.path[:0] = [str(ROOT / "tools/dev" / folder) for folder in ("phone", "touch")]
import batch_plan
import checks
import home_queue
import language_checks

HOME = "com.example.launcher/com.example.launcher.Home"
WINDOW = """\
  mCurrentFocus=Window{123 u0 com.example.launcher/.Home}
  mTopFocusedDisplayId=0
  DisplayPolicy
    mAwake=true mScreenOnEarly=true mScreenOnFully=true
    mShowingDream=false mDreamingLockscreen=false
  KeyguardServiceDelegate
    showing=false
    inputRestricted=false
"""


class Clock:
    now = 0

    def monotonic(self):
        return self.now

    def sleep(self, seconds):
        self.now += seconds


class HomeQueueTest(unittest.TestCase):
    def test_window_requires_exact_home_awake_unlocked_proof(self):
        self.assertTrue(home_queue.at_home(WINDOW, HOME))
        for broken in (
            WINDOW.replace("/.Home", "/.Settings"),
            WINDOW.replace("u0", "u10"),
            WINDOW.replace("mAwake=true", "mAwake=false"),
            WINDOW.replace("mScreenOnFully=true", "mScreenOnFully=false"),
            WINDOW.replace("showing=false", "showing=true"),
            WINDOW.replace("inputRestricted=false", "inputRestricted=true"),
            WINDOW.replace("mTopFocusedDisplayId=0", "mTopFocusedDisplayId=2"),
            WINDOW.split("  KeyguardServiceDelegate", 1)[0],
            WINDOW + "mKeyguardShowing=true\n",
        ):
            self.assertFalse(home_queue.at_home(broken, HOME), broken)

    def test_requires_seven_samples_twenty_seconds_apart(self):
        clock, reads = Clock(), []

        def sample():
            reads.append(clock.now)
            return True

        self.assertTrue(home_queue.wait_for_home(sample, monotonic=clock.monotonic, sleep=clock.sleep))
        self.assertEqual(reads, list(range(0, 121, 20)))

    def test_other_app_and_lost_connection_restart_the_whole_period(self):
        clock = Clock()

        def sample():
            if clock.now == 120:
                raise subprocess.TimeoutExpired("adb", 10)
            return clock.now != 60

        self.assertTrue(home_queue.wait_for_home(sample, monotonic=clock.monotonic, sleep=clock.sleep))
        self.assertEqual(clock.now, 260)

    def test_missed_sample_resets_and_only_explicit_cutoff_can_stop_wait(self):
        clock = Clock()

        def suspend(seconds):
            clock.sleep(seconds + (90 if clock.now == 60 else 0))

        self.assertTrue(home_queue.wait_for_home(lambda: True, monotonic=clock.monotonic, sleep=suspend))
        self.assertEqual(clock.now, 290)
        clock = Clock()
        self.assertFalse(home_queue.wait_for_home(lambda: False, monotonic=clock.monotonic, sleep=clock.sleep,
                                                  expired=lambda: clock.now >= 3_600))
        self.assertEqual(clock.now, 3_600)

    def test_busy_lock_and_lost_home_cancel_handoff(self):
        with tempfile.TemporaryDirectory() as tmp, redirect_stdout(io.StringIO()):
            lock_path = Path(tmp, "phone.lock")
            with open(lock_path, "a") as held, patch.dict(os.environ, {"TK_PHONE_LOCK": str(lock_path)}):
                fcntl.flock(held, fcntl.LOCK_EX)
                waits = []
                readings = iter((WINDOW, WINDOW, WINDOW.replace("/.Home", "/.Settings"), WINDOW, WINDOW))

                def wait(is_home, **_):
                    waits.append(is_home())
                    if len(waits) == 2:
                        fcntl.flock(held, fcntl.LOCK_UN)
                    return True

                def run(job):
                    self.assertEqual(job, ["guarded-job"])
                    with open(lock_path, "a") as contender:
                        with self.assertRaises(BlockingIOError):
                            fcntl.flock(contender, fcntl.LOCK_EX | fcntl.LOCK_NB)
                    return 7

                self.assertEqual(home_queue.queued(["guarded-job"], read_window=lambda: next(readings),
                                                   home_resolver=lambda: HOME, wait=wait, run=run), 7)
                self.assertEqual(waits, [True, True, True])

    def test_child_uses_the_parent_lock_and_owner_wait(self):
        with tempfile.TemporaryDirectory() as tmp, redirect_stdout(io.StringIO()):
            lock_path = Path(tmp, "phone.lock")
            command = [sys.executable, "-c", """
import fcntl, os
assert os.environ["TK_PHONE_LOCKED"] == "1"
assert os.environ["TK_WAIT_FOR_OWNER"] == "1"
with open(os.environ["TK_PHONE_LOCK"], "a") as handle:
    try:
        fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError:
        raise SystemExit(23)
raise AssertionError("the job's lock was not held")
"""]
            with patch.dict(os.environ, {"TK_PHONE_LOCK": str(lock_path)}):
                self.assertEqual(home_queue.queued(command, read_window=lambda: WINDOW, home_resolver=lambda: HOME,
                                                   wait=lambda ready, **_: ready()), 23)


class RunPolicyTest(unittest.TestCase):
    def test_only_complete_passed_unchanged_batches_can_be_reused(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)

            def report(name, verdict="YES", reusable=True, day=17):
                stamp = f"202611{day:02d}-220000"
                marker = batch_plan.REUSABLE if reusable else "Old or partial run."
                summary = "1 YES, 0 NO, 0 to look at" if verdict == "YES" else "0 YES, 1 NO, 0 to look at"
                (folder / f"report-{name}-{stamp}.md").write_text(
                    f"# {name}, {stamp}\n\n{summary}\n\n{marker}\n\n- **{verdict}** a: detail\n", encoding="utf-8")

            requested = ["guide", "heap", "heap", "shift", "chinese-ui", "zh-back", "gestures"]
            for name in ("guide", "heap", "shift", "gestures"):
                report(name)
            report("chinese-ui", "LOOK")
            report("zh-back", reusable=False)
            report("gestures", "NO", day=18)
            unchanged = ["guide", "heap", "chinese-ui", "zh-back", "gestures"]
            for phase in ("t7", "t8"):
                self.assertEqual(batch_plan.batches_to_run(requested, phase=phase, previous=folder, unchanged=unchanged), requested)
            self.assertEqual(batch_plan.batches_to_run(requested, phase="rerun", previous=folder, unchanged=unchanged),
                             requested[1:])

    def test_only_no_gets_automatic_capture_and_visual_evidence_survives(self):
        with patch.object(checks, "capture", side_effect=lambda name: name) as capture, \
             patch.object(checks, "results", []), patch.object(checks, "details", []), redirect_stdout(io.StringIO()):
            checks.record("ok", True, "passed")
            checks.record("needs-evidence", None, "not observed")
            checks.record("first-choice", None, "compare against the row", "timed-shot")
            checks.record("bad", False, "failed")
            self.assertEqual(capture.call_args_list, [call("bad-no")])
            self.assertEqual([line[3] for line in checks.details], [None, None, "timed-shot", "bad-no"])

    def test_capture_focus_requires_visible_debug_only(self):
        title = f"Window{{123 u0 {checks.APP}/.Space}}"
        window = (f"  mCurrentFocus={title}\n"
                  f"  Window #1 {title}:\n"
                  f"    mOwnerUid=10000 package={checks.APP}\n"
                  "    isOnScreen=true\n"
                  "  Window #2 Window{789 u0 StatusBar}:\n"
                  "    mOwnerUid=10001 package=com.android.systemui\n"
                  "    isVisible=true\n")
        self.assertEqual(checks.capture_focus(window), title)
        self.assertIsNone(checks.capture_focus(window.replace("StatusBar", "NotificationShade")))
        self.assertIsNone(checks.capture_focus(window.replace("isOnScreen=true", "isOnScreen=false")))

    def test_capture_visibility_belongs_to_one_window(self):
        title = f"Window{{123 u0 {checks.APP}/.Space}}"
        own = (f"  mCurrentFocus={title}\n  Window #0 {title}:\n"
               f"    mOwnerUid=10000 package={checks.APP}\n    isOnScreen=true\n")
        other = "  Window #1 Window{abc u0 Popup}:\n    mOwnerUid=10001 package=other.app\n"
        unrelated = "    Animation:\n      isVisible=true\n  Display state:\n    isOnScreen=true\n"
        self.assertEqual(checks.capture_focus(own + other + "    isOnScreen=false\n" + unrelated), title)
        self.assertIsNone(checks.capture_focus(own + other + unrelated))
        self.assertIsNone(checks.capture_focus(own + other + "    isOnScreen=true\n"))
        self.assertIsNone(checks.capture_focus(own + other.replace("package=other.app", "") + "    isOnScreen=true\n"))


class LiveSwitchTest(unittest.TestCase):
    def test_selects_language_then_method_without_an_ime_restart(self):
        methods = ["en", "ru", "sr", "sr-latn"]
        with patch.object(language_checks, "active_method", side_effect=["en", "sr-latn"]), \
             patch.object(language_checks, "play") as play:
            self.assertTrue(language_checks.switch_live(methods, "sr-latn"))
        # Both rows sit over Q to O since 2026-11-20 (65de9f35), so a second tile starts further left.
        play.assert_called_once_with("key:sym:900:none", "+500", "key:u", "+500", "key:t", "+500")

    def test_two_languages_open_the_destination_method_row_directly(self):
        with patch.object(language_checks, "active_method", side_effect=["en", "yue-jyutping"]), \
             patch.object(language_checks, "play") as play, patch.object(checks, "guarded") as guarded:
            self.assertTrue(language_checks.switch_live(["en", "yue-yale", "yue-jyutping"], "yue-jyutping"))
        play.assert_called_once_with("key:sym:900:none", "+500", "key:t", "+500")
        guarded.assert_not_called()

    def test_q_changes_methods_within_the_current_language(self):
        with patch.object(language_checks, "active_method", side_effect=["yue-yale", "yue-jyutping"]), \
             patch.object(language_checks, "play") as play, patch.object(checks, "guarded") as guarded:
            self.assertTrue(language_checks.switch_live(["en", "yue-yale", "yue-jyutping"], "yue-jyutping"))
        play.assert_not_called()
        guarded.assert_called_once_with("tap", "54", "1170")

    def test_fallback_only_when_live_readback_fails(self):
        ids = {7: "ru"}
        with patch.object(language_checks, "field_in_front", return_value=True), \
             patch.object(language_checks.languages, "logs_on"), patch.object(checks, "keyboard") as keyboard, \
             patch.object(language_checks, "switch_live", side_effect=[True, False]), \
             patch.object(language_checks, "adb", return_value='data="on en,ru; typing ru"') as adb, \
             patch.object(language_checks.own_editor, "restart") as restart, \
             patch.object(language_checks.languages, "subtype_ids", return_value=ids), \
             patch.object(language_checks, "active_method", return_value="ru") as active_method:
            self.assertTrue(language_checks.turn_on(["en", "ru"], "ru"))
            adb.assert_not_called()
            restart.assert_not_called()
            active_method.assert_not_called()
            self.assertTrue(language_checks.turn_on(["en", "ru"], "ru"))
            adb.assert_called_once_with("shell", "am", "broadcast", "-n", language_checks.RECEIVER,
                                        "--es", "on", "en,ru", "--es", "current", "ru")
            restart.assert_called_once_with()
            active_method.assert_called_once_with(["en", "ru"], ids)
            keyboard.assert_not_called()


if __name__ == "__main__":
    unittest.main()
