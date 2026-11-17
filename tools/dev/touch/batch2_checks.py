"""Main 50d59aa2's changes, for checks.py --batch batch2 (the Master, 2026-11-17).

1. Chinese gestures by one character (D, 1550f034), on 你好我哋唔該晒 typed in Yale: a flick deletes 晒, then 該, then 唔 (O);
   three Space taps take the last character (J, a shot); a slide steps one character (J, a shot).
2. E's dominoes past 100 words (2b68a18f): one held delete through a 250-word text. The hold never stops for them: the field
   ends empty (O); "dominoes run" is logged once (O); the lift's line says "as dominoes" (O); a shot mid-hold (J).
"""
import os
import time

import checks
import delete_batch
import interactions_batch as ib
from checks import APP, Stop, adb, play, record

TEXT = " ".join(["the quick brown fox jumps over the lazy dog"] * 28)  # 252 words
HOLD_MS = 85000  # about 35 words in 10 s: 252 take some 72 s


def read():
    try:
        return checks.field()
    except Stop:
        return None


def check_chinese_characters():
    ib.start(["en", "yue-yale"], "yue-yale")
    for code in ("neihou", "ngodei", "mgoisaai"):
        play(*ib.keys(code), "+500", "key:space", "+700")
    text = read()
    if text != "你好我哋唔該晒":
        return record("zh-chars-setup", None, f"neihou, ngodei and mgoisaai typed {text!r}, not 你好我哋唔該晒")
    left = []
    for _ in range(3):
        play("+600", "flick", "+700")
        left.append(read())
    record("zh-chars-flick", left == ["你好我哋唔該", "你好我哋唔", "你好我哋"], f"three flicks left {left}")
    play("+600", "space:3", "+700", shot="zh-chars-triple", at=1000)
    record("zh-chars-triple", None, "three Space taps: the last character, 哋, selected", "zh-chars-triple")
    play("key:space", "+300", "spacehold:2:-1", "+600", shot="zh-chars-slide", at=1200)
    record("zh-chars-slide", None, "a slide left: one character at a time", "zh-chars-slide")


def check_hold_250():
    ib.start(["en"], "en")
    before = checks.set_text(TEXT)
    time.sleep(2.5)
    adb("logcat", "-c")
    os.environ["TK_TOUCH_MAX_MS"] = str(HOLD_MS + 5000)
    try:
        play(f"flick:3.2:90:{HOLD_MS}", "+1500", shot="hold-250", at=40000)
    finally:
        os.environ.pop("TK_TOUCH_MAX_MS", None)
    after = read() or ""
    lines = delete_batch.delete_lines()
    runs = [line for line in lines if line.startswith("dominoes run")]
    lifts = [line for line in lines if line.startswith("delete animation:")]
    record("hold-250-empty", after.strip() == "", f"{len(before.split())} words, one {HOLD_MS // 1000} s hold: "
           f"{len(after.split())} left")
    record("hold-250-run-once", len(runs) == 1, f"dominoes lines: {runs or 'none'}")
    record("hold-250-lift", bool(lifts) and "as dominoes" in lifts[-1], f"the lift: {lifts[-1] if lifts else 'none logged'}")
    record("hold-250-look", None, "40 s into the hold: the dominoes live, the count counting on", "hold-250")


# check_hold_250 is out: the dominoes are dropped (owner, 2026-11-17 evening).
BATCH = [check_chinese_characters]


def finish():
    adb("shell", "setprop", f"log.tag.{ib.TAG}", "''")
    checks.set_text("")
    adb("shell", "am", "broadcast", "-n", f"{APP}/com.titankeys.keyboard.InputMethodsSet", "--es", "on",
        "en,zh-pinyin,yue-jyutping,yue-yale,af", "--es", "current", "en")
