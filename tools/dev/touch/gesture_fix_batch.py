"""The night of 2026-11-17's two harness fixes, rerun alone (checks.py --batch gestures-fix): in every language, the flick at a
typed line's end (now 800 ms after the last key) and double Space right after a word. See gesture_batch.py."""
import os

import gesture_batch

os.environ.setdefault("TK_GESTURE_ONLY", "flick,stop")
BATCH = [gesture_batch.check_every_language, gesture_batch.english]
