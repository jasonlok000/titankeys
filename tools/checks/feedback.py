#!/usr/bin/env python3
"""Check Settings > Send feedback on the Titan 2's 1076 x 1200 display: Report a bug and Suggest an idea each open the report
form (#97), each under its own title. Nothing is sent: the check leaves the form with Back.

Ink draws its pages without views that uiautomator can see, so the form goes by its look: its orange "What's it about?" label,
where Send feedback has none, and its title's length ("Report a bug" ends near x 600, "Suggest an idea" near x 730, and
"Send feedback" near x 692). The pixels are read in memory, only while the app is in front, and never saved.

Email is only the backup, offered when a report can't go (#74). That needs the upload to fail, which can't be done here without
sending or cutting the connection, so this skips it; tools/checks/feedback.test.ts checks the form offers it.

APP=com.titankeys.debug checks the debug build instead. Every press goes through tools/checks/phone.py's guards.
"""

import time

from phone import accent_count, back, open_page, screen, tap, title_right_edge

# The form's "What's it about?" label, under its title.
LABEL = (92, 495, 400, 531)
# Ink draws Settings without views to find, so the rows go by place: Send feedback's first and second rows. Each has the title
# its form must show, by where the title ends.
ROWS = [("Report a bug", 455, lambda edge: edge < 650), ("Suggest an idea", 598, lambda edge: edge > 710)]


def main():
    for name, y, title_fits in ROWS:
        open_page("/settings/feedback")
        tap(300, y)
        for _ in range(10):
            time.sleep(.5)
            pixels = screen()
            if accent_count(pixels, *LABEL) > 100 and title_fits(title_right_edge(pixels)):
                break
        else:
            raise SystemExit(f"FAIL: {name} didn't open its report form")
        print(f"PASS: {name} opens the report form", flush=True)
        # Leave it unsent.
        back()
        time.sleep(1)
    print("SKIP: email as the backup needs a failed upload; feedback.test.ts checks the form offers it", flush=True)
    open_page("/settings/feedback", wait=0)


if __name__ == "__main__":
    main()
