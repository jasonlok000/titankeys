#!/usr/bin/env python3
"""Check Settings > Send feedback on the Titan 2's 1076 x 1200 display: Report a bug and Suggest an idea each open the report
form (#97), each with its own first field. Nothing is sent: the check leaves the form with Back.

Email is only the backup, offered when a report can't go (#74). That needs the upload to fail, which can't be done here without
sending or cutting the connection, so this skips it; tools/checks/feedback.test.ts checks the form offers it.

APP=com.titankeys.debug checks the debug build instead. Every press goes through tools/checks/phone.py's guards.
"""

import re
import time
from pathlib import Path

from phone import back, field_with_hint, open_page, tap

# The form's first field's hint for each kind, read from the form so that a new wording can't leave this looking for the old one.
FORM = (Path(__file__).resolve().parents[2] / "app/settings/report.tsx").read_text(encoding="utf-8")
IDEA_HINT, BUG_HINT = re.search(r'placeholder=\{idea \? "([^"]+)" : "([^"]+)"\}', FORM).groups()

# Ink draws Settings without views to find, so the rows go by place: Send feedback's first and second rows.
ROWS = [("Report a bug", 455, BUG_HINT), ("Suggest an idea", 598, IDEA_HINT)]


def main():
    for name, y, hint in ROWS:
        open_page("/settings/feedback")
        tap(300, y)
        for _ in range(10):
            if field_with_hint(hint):
                break
            time.sleep(.5)
        else:
            raise SystemExit(f"FAIL: {name} didn't open the report form (no field showing {hint!r})")
        print(f"PASS: {name} opens the report form", flush=True)
        # Leave it unsent.
        back()
        time.sleep(1)
    print("SKIP: email as the backup needs a failed upload; feedback.test.ts checks the form offers it", flush=True)
    open_page("/settings/feedback", wait=0)


if __name__ == "__main__":
    main()
