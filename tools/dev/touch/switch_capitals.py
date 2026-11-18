"""Exact case on the first physical key after a switch, in Debug's disposable sentence editor."""
import re
import time

import checks
import guide_checks

ACTIVITY = checks.APP + "/com.titankeys.keyboard.SpaceGestureActivity"
METHODS = checks.APP + "/com.titankeys.keyboard.InputMethodsSet"


def select(on, current):
    answer = checks.adb("shell", "am", "broadcast", "-n", METHODS, "--es", "on", ",".join(on),
                        "--es", "current", current)
    if not re.search(r"typing " + re.escape(current) + r'"', answer):
        raise checks.Stop("The requested Debug input method was not saved")
    checks.keyboard("release")
    checks.keyboard("debug")


def editor(sentence):
    # A fresh activity holds only its own synthetic sample. Ordinary gesture fixtures keep their original input type.
    checks.adb("shell", "am", "start", "-W", "-n", ACTIVITY, "--ez", "sentence_capitals", str(sentence).lower())
    checks.keyboard("debug")
    if "SpaceGestureActivity" not in next((line for line in checks.adb("shell", "dumpsys", "window").splitlines()
                                           if "mCurrentFocus=" in line), ""):
        raise checks.Stop("The disposable editor is not in front")
    details = checks.adb("shell", "dumpsys", "input_method")
    match = re.search(r"mCurrentEditorInfo:\s*\n\s*inputType=0x([0-9a-fA-F]+)", details)
    if match is None or bool(int(match[1], 16) & 0x4000) != sentence:
        raise checks.Stop("The requested sentence-capitalisation flag could not be verified")


def trial(name, on, source, destination, prefix, expected, *, return_from_keyboard=False, immediate=False):
    select(on, "en")
    before = checks.set_text(prefix)
    if before.casefold() != prefix.casefold():
        raise checks.Stop("The synthetic sentence context could not be established")
    select(on, source)
    if return_from_keyboard:
        # The preceding select changed Android's keyboard back to Debug without sending a key to release.
        gesture = []
    else:
        gesture = ["key:sym:900:none"]
    if immediate:
        checks.play(*gesture, "key:q", "+400")
        shot = None
    else:
        if gesture:
            checks.play(*gesture, "+500")
        time.sleep(.3)
        shot = guide_checks.shot(name + "-before-q")
        checks.play("key:q", "+400")
    after = checks.field()
    saved = checks.adb("shell", "am", "broadcast", "-n", METHODS, "--ez", "read", "true")
    valid = f'typing {destination}"' in saved and after == before + expected
    checks.record(name, valid, f"Expected exactly {expected} on Q in {destination}; "
                  + ("field matched" if valid else "field differed"), shot)


def check_capitals():
    editor(True)
    trial("return-empty", ["en", "ru"], "en", "en", "", "Q", return_from_keyboard=True)
    trial("return-word", ["en", "ru"], "en", "en", "alpha", "q", return_from_keyboard=True)
    trial("switch-from-chinese", ["en", "yue-yale"], "yue-yale", "en", "", "Q")
    trial("switch-from-chinese-fast", ["en", "yue-yale"], "yue-yale", "en", "", "Q", immediate=True)
    trial("switch-to-russian", ["en", "ru"], "en", "ru", "done. ", "Й")
    trial("switch-to-russian-mid-word", ["en", "ru"], "en", "ru", "alpha", "й")


def check_plain_field():
    editor(False)
    trial("return-no-sentence-flag", ["en", "ru"], "en", "en", "", "q", return_from_keyboard=True)


BATCH = [check_capitals, check_plain_field]
