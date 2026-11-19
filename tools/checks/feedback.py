#!/usr/bin/env python3
"""Check Settings > Send feedback on the Titan 2's 1076 x 1200 display: Report a bug and Suggest an idea each open the report
form (#97), each under its own title. Nothing is sent: the check leaves the form with Back.

Ink draws its pages without views that uiautomator can see, so the form goes by its look: its orange "What's it about?" label,
where Send feedback has none, and its title's length ("Report a bug" ends near x 600, "Suggest an idea" near x 730, and
"Send feedback" near x 692). The pixels are read only while the app is in front. The guarded runner uses a temporary owned
Settings crop.

Email is only the backup, offered when a report can't go (#74). That needs the upload to fail, which can't be done here without
sending or cutting the connection, so this skips it; tools/checks/feedback.test.ts checks the form offers it.

APP=com.titankeys.debug checks the debug build instead. Every press goes through tools/checks/phone.py's guards.
"""

import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time

from phone import accent_count, back, open_page, screen, tap, title_right_edge

# The form's "What's it about?" label, under its title.
LABEL = (92, 495, 400, 531)
# Ink draws Settings without views to find, so the rows go by place: Send feedback's first and second rows. Each has the title
# its form must show, by where the title ends.
ROWS = [("Report a bug", 455, lambda edge: edge < 650), ("Suggest an idea", 598, lambda edge: edge > 710)]
UNOBSERVABLE = 77


class Unobservable(Exception):
    pass


def guarded_pixels(runner):
    """Read only the owned Settings surface when the private phone guard is in use."""
    from phone import APP, NotInFront, front_window, guard
    if APP != "com.titankeys.debug" or os.environ.get("TK_OWN_SURFACE") != "1":
        raise Unobservable("owned Settings capture unavailable")
    try:
        guard("reading the owned Settings surface")
    except NotInFront as error:
        raise Unobservable("Debug is no longer in front") from error
    if front_window() != APP + "/com.vandam.ink.MainActivity":
        raise Unobservable("Settings is no longer in front")
    sys.path.insert(0, runner)
    try:
        from own_surface import Unavailable, capture
        from PIL import Image
    except ImportError as error:
        raise Unobservable("owned Settings capture tools unavailable") from error

    def adb(*args, timeout):
        return subprocess.check_output(["adb", *args], text=True, timeout=timeout)

    output = os.environ.get("TK_OUT")
    with tempfile.TemporaryDirectory() as scratch:
        os.environ["TK_OUT"] = scratch
        try:
            frame = capture(adb, "settings", "feedback-form")
            x0, y0, x1, y1 = frame["bounds"]
            if not (0 <= x0 <= 80 and 0 <= y0 <= 165 and 1000 <= x1 <= 1076 and 531 <= y1 <= 1200):
                raise Unobservable("owned Settings surface does not cover the report labels")
            with Image.open(Path(frame["screenshot"])) as image:
                pixels = Image.new("RGBA", (1076, 1200))
                pixels.paste(image.convert("RGBA"), (x0, y0))
                return 1076, 1200, pixels.tobytes()
        except (Unavailable, OSError) as error:
            raise Unobservable("owned Settings capture unavailable") from error
        finally:
            if output is None:
                os.environ.pop("TK_OUT", None)
            else:
                os.environ["TK_OUT"] = output


def main():
    runner = os.environ.get("TK_SAFE_RUNNER")
    for name, y, title_fits in ROWS:
        open_page("/settings/feedback")
        tap(300, y)
        for _ in range(10):
            time.sleep(.5)
            pixels = guarded_pixels(runner) if runner else screen()
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
    try:
        main()
    except Unobservable as error:
        print(f"LOOK: {error}", flush=True)
        sys.exit(UNOBSERVABLE)
