"""Restart input in the owned synthetic editor while keeping TitanKeys Debug selected."""
import re

import checks
import languages

DEBUG = "com.titankeys.debug"
ACTIVITY = f"{DEBUG}/{languages.FIELD_ACTIVITY}"
IME = f"{DEBUG}/com.titankeys.keyboard.TitanKeysIme"
RECEIVER = f"{DEBUG}/com.titankeys.keyboard.InputMethodsSet"
RESULT = 'Broadcast completed: result=0, data="editor-restart-v1 restarted"'


def owned_window():
    if checks.APP != DEBUG:
        raise checks.Stop("an editor restart needs TitanKeys Debug")
    focus = languages.front().strip()
    if not re.fullmatch(r"mCurrentFocus=Window\{\S+ u0 " + re.escape(ACTIVITY) + r"\}", focus):
        raise checks.Stop("an editor restart needs Debug's Space gestures field in front")
    if checks.adb("shell", "settings", "get", "secure", "default_input_method").strip() != IME:
        raise checks.Stop("an editor restart needs TitanKeys Debug as the selected keyboard")
    return focus


def restart():
    window = owned_window()
    # The read flag stops an older receiver before it reaches its settings write.
    reply = checks.adb("shell", "am", "broadcast", "-n", RECEIVER, "--ez", "read", "true", "--ez", "restart", "true")
    if reply.strip().splitlines()[-1:] != [RESULT]:
        raise checks.Stop("Debug could not confirm the editor restart (refused or unsupported by this build)")
    if owned_window() != window:
        raise checks.Stop("Debug's Space gestures window changed during the editor restart")
