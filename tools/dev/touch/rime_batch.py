"""B's per-schema Rime copy (main 0e5ea510), for checks.py --batch rime-files: Rime's shared folder only holds what the Chinese
input methods that are on need. TitanKeys Debug isn't debuggable, so run-as can't list its files: B's Debug-only receiver does.
Its name and answer are settings until B's receiver is on main: RIME_RECEIVER (a component) answering one file a line, "BYTES
NAME", in the broadcast's data, lines split by ";" or newlines. Without it, each check says so instead of failing.

1. Yale only, after the first key: the shared folder is about 54 MB and has no zh-hant-t-essay-bgw.gram (O).
2. Pinyin on too, typed in: the grammar file appears (O).
3. Yale off, Pinyin typing: jyut6ping3's table and tk_yue.gram are gone (O).
The finish puts the owner's languages back.
"""
import os
import re
import time

import language_checks
from checks import APP, adb, play, record

RIME_RECEIVER = os.environ.get("TK_RIME_RECEIVER", f"{APP}/com.titankeys.keyboard.RimeFiles")
GRAMMAR = "zh-hant-t-essay-bgw.gram"
YALE_SIZE_MB = (49, 59)  # "about 54 MB"


def files():
    """{name: bytes} from B's receiver, or None when it isn't there."""
    out = adb("shell", "am", "broadcast", "-n", RIME_RECEIVER)
    if "data=" not in out:
        return None
    data = out.split("data=", 1)[1].strip().strip('"')
    found = {}
    for line in re.split(r"[;\n]", data):
        parts = line.strip().split(None, 1)
        if len(parts) == 2 and parts[0].isdigit():
            found[os.path.basename(parts[1])] = int(parts[0])
    return found


def typed_in(on, current):
    language_checks.fresh()
    if not language_checks.turn_on(on, current):
        raise language_checks.Stop(f"the field didn't start with {current}")
    language_checks.fresh()
    play("key:n", "+400", "key:i", "+400", "key:space", "+3000")  # the first keys: Rime copies and opens
    language_checks.fresh()


def check_rime_files():
    if files() is None:
        return record("rime-files", None, f"B's receiver ({RIME_RECEIVER}) isn't in this build: these checks wait for it")
    typed_in(["en", "yue-yale"], "yue-yale")
    listed = files() or {}
    mb = sum(listed.values()) / 1048576
    record("rime-yale-only", YALE_SIZE_MB[0] <= mb <= YALE_SIZE_MB[1] and GRAMMAR not in listed,
           f"Yale only: {mb:.1f} MB in {len(listed)} files; {GRAMMAR} {'there' if GRAMMAR in listed else 'absent'}")
    typed_in(["en", "yue-yale", "zh-pinyin"], "zh-pinyin")
    listed = files() or {}
    record("rime-pinyin-grammar", GRAMMAR in listed, f"Pinyin on and typed in: {GRAMMAR} "
           f"{'there, ' + str(listed.get(GRAMMAR)) + ' bytes' if GRAMMAR in listed else 'absent'}")
    typed_in(["en", "zh-pinyin"], "zh-pinyin")
    listed = files() or {}
    left = sorted(name for name in listed if "jyut6ping3" in name or name == "tk_yue.gram")
    record("rime-yale-off", not left, f"Yale off, Pinyin typing: jyut6ping3's and tk_yue.gram's files left: {left or 'none'}")


def finish():
    """The owner's languages as they were before the batches: English typing, Chinese's three, Afrikaans."""
    adb("shell", "am", "broadcast", "-n", f"{APP}/com.titankeys.keyboard.InputMethodsSet", "--es", "on",
        "en,zh-pinyin,yue-jyutping,yue-yale,af", "--es", "current", "en")


BATCH = [check_rime_files]
