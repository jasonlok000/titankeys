"""B's per-schema Rime copy, for checks.py --batch rime-files: Rime's shared folder only holds what the Chinese input methods
that are on need. TitanKeys Debug isn't debuggable, so run-as can't list its files: Debug's LeakCheck says what the copy holds,
at the end of its answer: "rime data: 53.8 MB in 41 files; tables and grammars: jyut6ping3.table.bin, tk_yue.gram" (MB of
1,000,000 bytes), or "rime data: none copied" before the first use.

1. Yale only, after the first key: about 54 MB, with jyut6ping3.table.bin and tk_yue.gram, without luna_pinyin.table.bin and
   zh-hant-t-essay-bgw.gram (O).
2. Pinyin on too, typed in: luna_pinyin.table.bin and the grammar appear (O).
3. Yale and Jyutping off (Jyutping shares Yale's two), Pinyin typing: jyut6ping3.table.bin and tk_yue.gram are gone (O).
The finish puts the owner's languages back.
"""
import re

import language_checks
from checks import APP, adb, play, record

LEAK_CHECK = f"{APP}/com.titankeys.keyboard.LeakCheck"
GRAMMAR = "zh-hant-t-essay-bgw.gram"
YALE_SIZE_MB = (49, 59)  # "about 54 MB"
YALE = ["jyut6ping3.table.bin", "tk_yue.gram"]
PINYIN = ["luna_pinyin.table.bin", GRAMMAR]


def rime_data():
    """(MB, files, [tables and grammars]) from LeakCheck's "rime data" part, (0, 0, []) before the first copy, or None when this
    Debug's LeakCheck doesn't say it yet."""
    out = adb("shell", "am", "broadcast", "-n", LEAK_CHECK)
    if "rime data: none copied" in out:
        return 0.0, 0, []
    found = re.search(r'rime data: ([0-9.]+) MB in (\d+) files; tables and grammars: ([^"\n]*)', out)
    if not found:
        return None
    names = [] if found.group(3).strip() == "none" else [n.strip() for n in found.group(3).split(",") if n.strip()]
    return float(found.group(1)), int(found.group(2)), names


def typed_in(on, current):
    language_checks.fresh()
    if not language_checks.turn_on(on, current):
        raise language_checks.Stop(f"the field didn't start with {current}")
    language_checks.fresh()
    play("key:n", "+400", "key:i", "+400", "key:space", "+3000")  # the first keys: Rime copies and opens
    language_checks.fresh()


def check_rime_files():
    """The first keys come before the first read: "none copied" is normal until then."""
    typed_in(["en", "yue-yale"], "yue-yale")
    data = rime_data()
    if data is None:
        return record("rime-files", None, "this Debug's LeakCheck doesn't say what Rime's copy holds: build it from main")
    mb, count, names = data
    missing, extra = [n for n in YALE if n not in names], [n for n in PINYIN if n in names]
    record("rime-yale-only", YALE_SIZE_MB[0] <= mb <= YALE_SIZE_MB[1] and not missing and not extra,
           f"Yale only: {mb} MB in {count} files; tables and grammars {names}; Yale's missing {missing or 'none'}; "
           f"Pinyin's there {extra or 'none'}")
    typed_in(["en", "yue-yale", "zh-pinyin"], "zh-pinyin")
    mb, count, names = rime_data() or (0.0, 0, [])
    missing = [n for n in PINYIN if n not in names]
    record("rime-pinyin-grammar", not missing, f"Pinyin on and typed in: {mb} MB in {count} files; tables and grammars {names}; "
           f"Pinyin's missing {missing or 'none'}")
    typed_in(["en", "zh-pinyin"], "zh-pinyin")  # Jyutping off too: it shares jyut6ping3 and tk_yue.gram
    mb, count, names = rime_data() or (0.0, 0, [])
    left = [n for n in YALE if n in names]
    record("rime-yale-off", not left, f"Yale and Jyutping off, Pinyin typing: {mb} MB in {count} files; Yale's left "
           f"{left or 'none'}")


def finish():
    """The owner's languages as they were before the batches: English typing, Chinese's three, Afrikaans."""
    adb("shell", "am", "broadcast", "-n", f"{APP}/com.titankeys.keyboard.InputMethodsSet", "--es", "on",
        "en,zh-pinyin,yue-jyutping,yue-yale,af", "--es", "current", "en")


BATCH = [check_rime_files]
