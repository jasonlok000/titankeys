#!/usr/bin/env python3
"""Offline checks for the touch simulator: no phone. Run by test/run.sh, which passes the Java dump and the host injector.

Usage: test_touch.py ALIGNMENT_DUMP HOST_INJECTOR
"""
import math
import os
import re
import subprocess
import sys
import tempfile
from types import SimpleNamespace

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
import gestures as g  # noqa: E402

REPO = g.REPO
failures = 0


def check(ok, what):
    global failures
    if not ok:
        failures += 1
        print("FAIL", what)


def source(path):
    return open(os.path.join(REPO, path), encoding="utf-8").read()


def build(*gestures, keymap=None):
    return g.build(SimpleNamespace(keymap=keymap, gestures=list(gestures)))


def touches(script):
    """Each touch from BTN_TOUCH down to up, as [(ms, raw x, raw y, major)], from the script's own events."""
    out, current, frame = [], None, {}
    for t, dev, type_, code, value in script.lines:
        if dev != g.TOUCHPAD:
            continue
        if type_ == g.EV_KEY and code == g.BTN_TOUCH:
            if value:
                current = []
            else:
                out.append(current)
                current = None
        elif type_ == g.EV_ABS:
            frame[code] = value
        elif type_ == g.EV_SYN and code == g.SYN_REPORT and current is not None and g.ABS_MT_POSITION_X in frame:
            current.append((t, frame[g.ABS_MT_POSITION_X], frame[g.ABS_MT_POSITION_Y], frame[g.ABS_MT_TOUCH_MAJOR]))
            frame = {}
    return out


def read_back(alignment, rx, ry):
    """What TitanKeys reads for a raw position: Touchpad.kt's division, then the calibration."""
    px, py = rx / g.RAW_X_MAX, ry / g.RAW_Y_MAX
    return alignment.x(px, py), alignment.y(py)


def main():
    dump, injector = sys.argv[1], sys.argv[2]
    alignment = g.Alignment()

    # The copy of the calibration is the app's: same numbers on a 41 x 41 grid.
    worst = 0.0
    for line in open(dump):
        x, y, ax, ay = (float(v) for v in line.split())
        worst = max(worst, abs(alignment.x(x, y) - ax), abs(alignment.y(y) - ay))
    check(worst < 1e-5, f"calibration differs from TouchAlignment.java by {worst}")

    # The numbers copied from the app still match it.
    touchpad = source("android/kotlin/com/titankeys/keyboard/Touchpad.kt")
    check("AXIS_X, 1079f)" in touchpad and "RAW_HEIGHT = 600f" in touchpad, "Touchpad.kt's raw ranges changed")
    layout = source("android/kotlin/com/titankeys/keyboard/Layout.kt")
    check('listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P")' in layout
          and 'listOf("A", "S", "D", "F", "G", "H", "J", "K", "L", BACKSPACE)' in layout
          and 'listOf(ALT, "Z", "X", "C", "V", "B", "N", "M", SYM, ENTER)' in layout
          and 'listOf("⇧", "○", "◁", SPACE, "▦", FN, "⇧")' in layout
          and "floatArrayOf(1.2f, 1.2f, 1.2f, 2.8f, 1.2f, 1.2f, 1.2f)" in layout, "Layout.kt's keys changed")
    delete = source("android/kotlin/com/titankeys/keyboard/DeleteSwipe.kt")
    for constant in ["RIGHT_EDGE = .875f", "EDGE_START_TOP = .1f", "EDGE_START_BOTTOM = .62f", "MIDDLE_ROW_TOP = .25f",
                     "INNER_START_BOTTOM = .56f", "MAX_PATH_KEYS = 5.5f", "MAX_FLICK_MILLIS = 600L", "CRUNCH_KEYS = 1.5f",
                     "CRUNCH_MILLIS = 60L"]:
        check(constant in delete, f"DeleteSwipe.kt changed: {constant}")
    cursor = source("android/kotlin/com/titankeys/keyboard/CursorGesture.kt")
    for constant in ["FIRST_TAP_MILLIS = 130L", "MAX_GAP_MILLIS = 150L", "ZONE_TOP = .5f", "ZONE_LEFT = .36f", "ZONE_RIGHT = .64f",
                     "TAP_KEYS = .3f"]:
        check(constant in cursor, f"CursorGesture.kt changed: {constant}")
    check("SETTLE_MILLIS = 30L" in source("android/kotlin/com/titankeys/keyboard/Contact.kt"), "Contact.kt's settle time changed")

    # Every key's centre goes to raw steps and reads back on that key.
    for name in list("abcdefghijklmnopqrstuvwxyz") + ["del", "sym", "enter", "alt", "space", "fn", "shift"]:
        x, y = g.key_centre(name)
        rx, ry = alignment.raw(x, y)
        bx, by = read_back(alignment, rx, ry)
        check(0 <= rx <= g.RAW_X_MAX and 0 <= ry <= g.RAW_Y_MAX, f"{name}: raw {rx},{ry} out of range")
        expected = {"space": "space", "fn": "fn", "shift": "⇧"}.get(name, name)
        check(g.key_at(bx, by) == expected, f"{name}: reads back as {g.key_at(bx, by)!r} at {bx:.3f},{by:.3f}")
        check(abs(bx - x) < .012 and abs(by - y) < .012, f"{name}: reads back {bx - x:+.3f},{by - y:+.3f} off its centre")

    # A swipe passes over each of its letters in order, at the speed asked, a frame every 9.5 ms, with real contact sizes.
    for word in ["go", "on", "in", "to", "is", "it", "its", "the", "quick", "typewriter", "zap", "pool", "minimum"]:
        script = build(f"swipe:{word}")
        [touch] = touches(script)
        read = [read_back(alignment, rx, ry) for _, rx, ry, _ in touch]
        keys = [g.key_at(x, y) for x, y in read]
        letters = [c for i, c in enumerate(word) if i == 0 or word[i - 1] != c]
        at = 0
        for key in keys:
            if at < len(letters) and key == letters[at]:
                at += 1
        check(at == len(letters), f"swipe {word}: keys {''.join(k if len(k) == 1 else '?' for k in keys)} miss {letters[at:]}")
        check(keys[0] == letters[0] and keys[-1] == letters[-1], f"swipe {word}: starts on {keys[0]}, ends on {keys[-1]}")
        gaps = [b[0] - a[0] for a, b in zip(touch, touch[1:])]
        check(all(8 <= gap <= 11 for gap in gaps), f"swipe {word}: frame gaps {min(gaps)}..{max(gaps)} ms")
        check(all(1 <= m <= 3 for *_, m in touch), f"swipe {word}: contact sizes out of 1..3")
        path = sum(math.hypot((b[0] - a[0]) * 10, (b[1] - a[1]) * 4) for a, b in zip(read, read[1:]))
        speed = path / (touch[-1][0] - touch[0][0])
        check(.006 < speed < .02, f"swipe {word}: {speed:.4f} keys/ms")

    # Two quick swipes with a pause between them are two touches, the pause as asked.
    script = build("swipe:go", "+120", "swipe:on")
    first, second = touches(script)
    lifted = next(t for t, d, ty, c, v in script.lines if ty == g.EV_KEY and c == g.BTN_TOUCH and v == 0)
    check(118 <= second[0][0] - lifted <= 140, f"swipe pause: {second[0][0] - lifted} ms between lift and touch")

    # The flick passes DeleteSwipe's rules: starts on Backspace's edge zone, level, short, quick, and crunches in time.
    [touch] = touches(build("flick"))
    read = [(t, *read_back(alignment, rx, ry)) for t, rx, ry, _ in touch]
    (t0, x0, y0), (t1, x1, y1) = read[0], read[-1]
    check(x0 >= .875 and .175 <= y0 < .62 and .175 <= y1 < .625, f"flick: from {x0:.3f},{y0:.3f} to {x1:.3f},{y1:.3f}")
    check(x0 - x1 >= .1 and all(x >= x1 - .05 for _, x, _ in read), "flick: not a level run to the left")
    path = sum(math.hypot((b[1] - a[1]) * 10, (b[2] - a[2]) * 4) for a, b in zip(read, read[1:]))
    check(path <= 5.5 and t1 - t0 <= 600, f"flick: {path:.2f} keys in {t1 - t0:.0f} ms")
    crunch = next((t for t, x, y in read if math.hypot((x - x0) * 10, (y - y0) * 4) >= 1.5), None)
    check(crunch is not None and crunch - t0 <= 60, f"flick: 1.5 keys after {None if crunch is None else crunch - t0} ms")

    # A flick up starts on its key and goes straight up a row: ChineseKeys.flickColumn wants up more than across.
    [touch] = touches(build("flickup:f"))
    read = [(t, *read_back(alignment, rx, ry)) for t, rx, ry, _ in touch]
    (t0, x0, y0), (t1, x1, y1) = read[0], read[-1]
    check(.3 <= x0 < .4 and .25 <= y0 < .5, f"flickup:f: starts at {x0:.3f},{y0:.3f}, not on F")
    check((y1 - y0) * 4 <= -.8 and abs(x1 - x0) * 10 < abs(y1 - y0) * 4 / 4, f"flickup:f: to {x1:.3f},{y1:.3f}, not straight up")
    check(t1 - t0 <= 150, f"flickup:f: {t1 - t0:.0f} ms")

    # Space taps: in CursorGesture's zone, each tap short and still, the gaps inside its window.
    for count in (1, 2, 3):
        taps = touches(build(f"space:{count}"))
        check(len(taps) == count, f"space:{count}: {len(taps)} touches")
        for tap in taps:
            x, y = read_back(alignment, tap[0][1], tap[0][2])
            check(y >= .5 and .36 <= x < .64, f"space:{count}: tap at {x:.3f},{y:.3f}, outside the zone")
            check(tap[-1][0] - tap[0][0] < 130, f"space:{count}: a tap of {tap[-1][0] - tap[0][0]} ms")
        for a, b in zip(taps, taps[1:]):
            gap = b[0][0] - a[-1][0]
            check(30 <= gap <= 150, f"space:{count}: gap {gap} ms")

    # Space held: the taps, then the last one stays past the double-tap window and slides as far as asked.
    taps = touches(build("spacehold:2:-3"))
    check(len(taps) == 2 and taps[0][-1][0] - taps[0][0][0] < 130, f"spacehold: {len(taps)} touches")
    held = taps[1]
    x0, _ = read_back(alignment, held[0][1], held[0][2])
    x1, _ = read_back(alignment, held[-1][1], held[-1][2])
    check(abs((x0 - x1) * 10 - 3) < .3, f"spacehold slid {(x0 - x1) * 10:.2f} keys, not 3")
    check(held[-1][0] - held[0][0] > 150 + 500, "spacehold: the last tap isn't held, then slid")
    check(len(touches(build("spacehold:3"))) == 3, "spacehold:3 isn't three touches")

    # Keys: the finger lands first, the key goes down and up, then the finger does as asked.
    keymap = os.path.join(tempfile.mkdtemp(), "keys.txt")
    with open(keymap, "w") as out:
        out.write("sym=127\nfn=464\n")
    for after in ("lift", "rest", "slide", "none"):
        script = build(f"key:f:70:{after}", keymap=keymap)
        keys = [(t, v) for t, d, ty, c, v in script.lines if d == g.KEYBOARD and ty == g.EV_KEY]
        check([v for _, v in keys] == [1, 0] and keys[1][0] - keys[0][0] == 70, f"key {after}: {keys}")
        taps = touches(script)
        if after == "none":
            check(taps == [], "key none: touched")
            continue
        [touch] = taps
        check(touch[0][0] < keys[0][0] and touch[-1][0] >= keys[1][0], f"key {after}: the finger isn't on the key while it's down")
        tail = touch[-1][0] - keys[1][0]
        check({"lift": tail < 15, "rest": tail >= 650, "slide": tail >= 50}[after], f"key {after}: {tail} ms after the key")
    hold = [(t, v) for t, d, ty, c, v in build("hold:e", keymap=keymap).lines if d == g.KEYBOARD and ty == g.EV_KEY]
    check(hold[1][0] - hold[0][0] == 800, f"hold: {hold}")
    sym = [c for t, d, ty, c, v in build("key:sym:600", keymap=keymap).lines if d == g.KEYBOARD and ty == g.EV_KEY]
    check(sym == [127, 127], f"sym held: codes {sym}")
    chord = [(round(t), c, v) for t, d, ty, c, v in build("chord:alt+sym", keymap=keymap).lines if d == g.KEYBOARD and ty == g.EV_KEY]
    check(chord == [(0, 56, 1), (30, 127, 1), (110, 127, 0), (130, 56, 0)], f"chord alt+sym: {chord}")
    check(all(d == g.KEYBOARD for _, d, *_ in build("chord:shift+w").lines), "a chord touches the touchpad")
    check("dev 0 touchPad" not in build("chord:shift+w").text(), "a script of keys alone declares the touchpad")
    try:
        build("key:sym")
        check(False, "sym without a keymap should stop")
    except SystemExit:
        pass

    # A getevent capture replays as it was: the same events at the same gaps.
    capture = os.path.join(tempfile.mkdtemp(), "capture.txt")
    with open(capture, "w") as out:
        out.write("# device /dev/input/event4 touchPad\n")
        for t, ev, code, value in [(5.0, "EV_KEY", "BTN_TOUCH", "DOWN"), (5.0, "EV_ABS", "ABS_MT_POSITION_X", "000001e1"),
                                   (5.0, "EV_SYN", "SYN_REPORT", "00000000"), (5.0095, "EV_ABS", "ABS_MT_POSITION_X", "000001e2"),
                                   (5.0095, "EV_SYN", "SYN_REPORT", "00000000"), (5.05, "EV_KEY", "BTN_TOUCH", "UP"),
                                   (5.05, "EV_SYN", "SYN_REPORT", "00000000")]:
            out.write(f"/dev/input/event4: [ {t:12.6f}] {ev:12} {code:20} {value}\n")
    replay = build(f"replay-getevent:{capture}").lines
    check([(round(t, 1), c, v) for t, d, ty, c, v in replay if ty != g.EV_SYN] ==
          [(0.0, g.BTN_TOUCH, 1), (0.0, g.ABS_MT_POSITION_X, 0x1e1), (9.5, g.ABS_MT_POSITION_X, 0x1e2), (50.0, g.BTN_TOUCH, 0)],
          f"getevent replay: {replay}")

    # A raw log replays its touches through the calibration, and never reads its other lines.
    rawlog = os.path.join(tempfile.mkdtemp(), "raw.jsonl")
    gx, gy = g.key_centre("g")
    with open(rawlog, "w") as out:
        out.write('{"k":"sel","text":"never read"}\n')
        out.write('{"k":"m","a":0,"t":1000,"s":[[%f,%f,1000]],"sz":[19]}\n' % (gx, gy))
        out.write('{"k":"m","a":2,"t":1020,"s":[[%f,%f,1010],[%f,%f,1020]],"sz":[3,3]}\n' % (gx + .03, gy, gx + .06, gy))
        out.write('{"k":"m","a":1,"t":1030,"s":[[%f,%f,1030]],"sz":[2]}\n' % (gx + .06, gy))
        out.write('{"k":"key","c":62,"d":true,"t":1100,"g":false}\n{"k":"key","c":62,"d":false,"t":1160,"g":false}\n')
    script = build(f"replay-rawlog:{rawlog}")
    [touch] = touches(script)
    check([round(t) for t, *_ in touch] == [0, 10, 20, 30], f"rawlog replay times: {[t for t, *_ in touch]}")
    x, y = read_back(alignment, touch[0][1], touch[0][2])
    check(g.key_at(x, y) == "g", f"rawlog replay lands on {g.key_at(x, y)}")
    check(touch[0][3] == 3, f"rawlog size: touch major 19 should be sensor step 3, not {touch[0][3]}")
    keys = [(round(t), c, v) for t, d, ty, c, v in script.lines if d == g.KEYBOARD and ty == g.EV_KEY]
    check(keys == [(100, 57, 1), (160, 57, 0)], f"rawlog keys: {keys}")

    # The injector reads what the generator writes, and refuses what it must.
    def inject(text):
        with tempfile.NamedTemporaryFile("w", suffix=".tki", delete=False) as out:
            out.write(text)
        return subprocess.run([injector, "--dry-run", out.name], capture_output=True, text=True)

    text = build("swipe:quick", "+80", "flick", "space:2", "key:sym:600:rest", keymap=keymap).text()
    result = inject(text)
    check(result.returncode == 0, f"injector refused a generated script: {result.stderr}")
    played = [line for line in result.stdout.splitlines() if not line.startswith("dev ")]
    wanted = [line for line in text.splitlines() if line and not line.startswith(("#", "dev "))]
    check(len(played) == len(wanted) and all(
        [float(a.split()[0])] + a.split()[1:] == [float(b.split()[0])] + b.split()[1:] for a, b in zip(played, wanted)),
        "injector's dry run differs from the script")
    for bad, why in [("dev 0 gpio-keys\n0 0 1 30 1\n", "only touchPad and TitanKey"),
                     ("dev 0 touchPad\n5 0 0 0 0\n4 0 0 0 0\n", "time goes back"),
                     ("dev 0 touchPad\n0 1 0 0 0\n", "undeclared"),
                     ("dev 0 touchPad\n0 0 4 0 0\n", "only SYN, KEY and ABS"),
                     ("dev 0 touchPad\n60001 0 0 0 0\n", "longer than 60 s"),
                     ("dev 1 TitanKey\n0 1 1 116 1\n", "never sent"),
                     ("dev 1 TitanKey\n0 1 1 102 1\n", "never sent"),
                     ("dev 1 TitanKey\n0 1 1 158 1\n", "never sent")]:
        result = inject(bad)
        check(result.returncode != 0 and why in result.stderr, f"injector should refuse ({why}): {result.stderr!r}")

    # The feed's frames: one D as the finger lands, M as it moves, U as it lifts, with the same times and positions.
    script = build("swipe:go", "+120", "key:f:70:rest", keymap=keymap)
    feed = script.feed_text().split(";")
    taps = touches(script)
    want = []
    for tap in taps:
        want += ["D"] + ["M"] * (len(tap) - 1) + ["U"]
    check([f[0] for f in feed] == want, f"feed frames: {[f[0] for f in feed]}")
    first = feed[0].split()
    check([float(first[1]), int(first[2]), int(first[3])] == [round(taps[0][0][0], 1), taps[0][0][1], taps[0][0][2]],
          f"feed's first frame: {first}")
    keys_only = script.text(touches=False)
    check("dev 0 touchPad" not in keys_only and "dev 1 TitanKey" in keys_only, "the keys' script still has the touchpad")

    # No twin of the touchpad, ever: it would set the real touchpad's height to 0 (inject.c, open_twin).
    with tempfile.NamedTemporaryFile("w", suffix=".tki", delete=False) as out:
        out.write("dev 0 touchPad\n0 0 0 0 0\n")
    twin = subprocess.run([injector, out.name], capture_output=True, text=True)
    check(twin.returncode != 0 and "no twin of touchPad" in twin.stderr, f"the injector made a touchpad twin: {twin.stderr!r}")
    serve_pad = subprocess.run([injector, "--serve", "touchPad"], capture_output=True, text=True, input="quit\n")
    check(serve_pad.returncode != 0 and "no twin of touchPad" in serve_pad.stderr, "--serve made a touchpad twin")

    serve = subprocess.run([injector, "--serve", "gpio-keys"], capture_output=True, text=True, input="quit\n")
    check(serve.returncode != 0 and "only touchPad and TitanKey" in serve.stderr, f"--serve took another device: {serve.stderr!r}")

    print("Touch simulator checks passed" if failures == 0 else f"{failures} touch simulator checks failed")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
