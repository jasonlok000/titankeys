#!/usr/bin/env python3
"""Writes tk-inject scripts: touchpad gestures and key presses for the Titan 2 Elite, as the hardware itself sends them.

Every position is chosen in TitanKeys' own touchpad space (0 to 1 across the ten columns and down the four rows, after the
built-in calibration), the space that Layout.kt, DeleteSwipe.kt and CursorGesture.kt decide in. The script turns it back into the
touchpad's raw steps through the calibration in android/assets/calibration, so a swipe over "go" lands where TitanKeys reads G
and O.

Usage: gestures.py [--keymap FILE] [--out FILE] GESTURE... with GESTUREs in order, and +MS between two for a pause:
  swipe:WORD[:SPEED]      a swipe through WORD's keys, SPEED in keys a millisecond (default .012)
  flick[:KEYS[:MS]]       a level delete flick to the left from Backspace (default 3.2 keys in 90 ms)
  space:N                 N taps on Space's touch zone (1, 2 or 3)
  key:NAME[:HOLD[:AFTER]] a key pressed with the finger on its spot: held HOLD ms (default 70); AFTER is what the finger then
                          does: lift (default), rest (stays 700 ms), slide (moves a key right, then lifts) or none (no touch)
  hold:LETTER[:MS]        a letter held down (default 800 ms)
  touch:X,Y[:MS]          a still touch at a touchpad position (default 300 ms)
  replay-getevent:FILE    a capture from capture.sh, played as it was
  replay-rawlog:FILE[:FROM[:TO]]  the touches and keys of a raw log export, between two uptimes in ms (default all)
Key names: a to z, space, enter, del, shift, alt, sym, fn. Letters and the rest have the Linux codes; sym and fn come from the
phone's key layout (keymap.sh), passed with --keymap.
"""
import argparse
import json
import math
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.normpath(os.path.join(HERE, "../../.."))
PROFILE = os.path.join(REPO, "android/assets/calibration/titan-2-saved.profile")

# The touchpad's raw range: Touchpad.kt reads x over 1079 steps and y over RAW_HEIGHT - 1 = 599 (tests/test_touch.py checks).
RAW_X_MAX = 1079
RAW_Y_MAX = 599
# The touchpad reports about every 9.5 ms (getevent captures of real swipes).
FRAME_MS = 9.5
# Contact sizes (ABS_MT_TOUCH_MAJOR) of real touches: 2 or 3 while swiping, 1 or 2 while still (#83's captures).
MOVING_MAJOR = (2, 3)
STILL_MAJOR = (1, 2)

EV_SYN, EV_KEY, EV_ABS = 0, 1, 3
SYN_REPORT, SYN_MT_REPORT = 0, 2
BTN_TOUCH = 0x14A
ABS_MT_TOUCH_MAJOR, ABS_MT_POSITION_X, ABS_MT_POSITION_Y = 0x30, 0x35, 0x36
TOUCHPAD, KEYBOARD = 0, 1

# Layout.kt's keys: three rows of ten, then the bottom row with its widths.
ROWS = ["QWERTYUIOP", "ASDFGHJKL⌫", "@ZXCVBNM$↵"]  # @ is Alt, $ is Sym
BOTTOM = ["⇧", "○", "◁", "space", "▦", "fn", "⇧r"]
BOTTOM_WIDTHS = [1.2, 1.2, 1.2, 2.8, 1.2, 1.2, 1.2]

# Linux key codes (input-event-codes.h) for the keys that have the usual ones.
LINUX_KEYS = {**{c: code for c, code in zip("qwertyuiop", range(16, 26))},
              **{c: code for c, code in zip("asdfghjkl", range(30, 39))},
              **{c: code for c, code in zip("zxcvbnm", range(44, 51))},
              "space": 57, "enter": 28, "del": 14, "shift": 42, "alt": 56}


# --- The calibration, as TouchAlignment.java maps it, and back. ---

class Curve:
    def __init__(self, anchors, targets):
        self.source = anchors
        target = list(targets)
        last = len(target) - 1
        if last >= 3:
            target[0] = Curve.edge(target[1], target[2], anchors[1], anchors[2], anchors[1] - anchors[0])
            target[last] = Curve.edge(target[last - 1], target[last - 2], anchors[last - 1], anchors[last - 2],
                                      anchors[last] - anchors[last - 1])
        self.target = target
        secant = [(target[i + 1] - target[i]) / (anchors[i + 1] - anchors[i]) for i in range(last)]
        slope = [0.0] * (last + 1)
        slope[0], slope[last] = secant[0], secant[last - 1]
        for i in range(1, last):
            before, after = anchors[i] - anchors[i - 1], anchors[i + 1] - anchors[i]
            w1, w2 = 2 * after + before, after + 2 * before
            slope[i] = (w1 + w2) / (w1 / secant[i - 1] + w2 / secant[i])
        self.slope = slope

    @staticmethod
    def edge(outer, inner, outer_source, inner_source, distance):
        spacing = abs(outer - inner)
        side = math.copysign(1, outer - inner) if outer != inner else 0
        room = 1 - outer if side > 0 else outer
        if room > spacing * .75:
            return outer + side * spacing
        speed = abs((outer - inner) / (outer_source - inner_source))
        return clamp(outer + side * speed * distance)

    def map(self, value):
        s, i = self.source, 1
        while i < len(s) - 1 and value > s[i]:
            i += 1
        h = s[i] - s[i - 1]
        t = (value - s[i - 1]) / h
        t2, t3 = t * t, t * t * t
        return clamp((2 * t3 - 3 * t2 + 1) * self.target[i - 1] + (t3 - 2 * t2 + t) * h * self.slope[i - 1]
                     + (-2 * t3 + 3 * t2) * self.target[i] + (t3 - t2) * h * self.slope[i])


def clamp(value):
    return max(0.0, min(1.0, value))


class Alignment:
    def __init__(self, path=PROFILE):
        parts = open(path, encoding="utf-8").readline().strip().split(";")
        if len(parts) != 11 or parts[0] != "1":
            raise ValueError("unknown calibration profile")
        nums = [[float(v) for v in p.split(",") if v] for p in parts[1:]]
        self.rows = [Curve(nums[r * 2], nums[r * 2 + 1]) for r in range(4)]
        self.vertical = Curve(nums[8], nums[9])

    def y(self, y):
        return self.vertical.map(clamp(y))

    def x(self, x, y):
        x = clamp(x)
        row = max(0.0, min(3.0, self.y(y) * 4 - .5))
        low = min(2, int(row))
        t = row - low
        t = t * t * (3 - 2 * t)
        a = self.rows[low].map(x)
        return a + (self.rows[low + 1].map(x) - a) * t

    @staticmethod
    def _solve(f, goal):
        low, high = 0.0, 1.0
        for _ in range(48):
            mid = (low + high) / 2
            if f(mid) < goal:
                low = mid
            else:
                high = mid
        return (low + high) / 2

    def raw_pad(self, x, y):
        """The touchpad's own position (before calibration, 0 to 1) that TitanKeys reads as x, y."""
        pad_y = self._solve(self.y, y)
        pad_x = self._solve(lambda v: self.x(v, pad_y), x)
        return pad_x, pad_y

    def raw(self, x, y):
        pad_x, pad_y = self.raw_pad(x, y)
        return round(pad_x * RAW_X_MAX), round(pad_y * RAW_Y_MAX)


# --- Where keys are, in TitanKeys' touchpad space. ---

def key_centre(name):
    name = name.lower()
    if len(name) == 1 and name.isalpha():
        for row, keys in enumerate(ROWS):
            column = keys.find(name.upper())
            if column >= 0:
                return (column + .5) / 10, (row + .5) / 4
    special = {"del": (1, 9), "backspace": (1, 9), "alt": (2, 0), "sym": (2, 8), "enter": (2, 9)}
    if name in special:
        row, column = special[name]
        return (column + .5) / 10, (row + .5) / 4
    bottom = {"shift": "⇧", "space": "space", "fn": "fn"}
    if name in bottom:
        index = BOTTOM.index(bottom[name])
        left = sum(BOTTOM_WIDTHS[:index]) / 10
        return left + BOTTOM_WIDTHS[index] / 20, .875
    raise ValueError(f"no key {name!r}")


def key_at(x, y):
    """Layout.keyAt, for the checks: the key under a touchpad position."""
    if not (0 <= x <= 1 and 0 <= y <= 1):
        return ""
    column, row = x * 10, min(3, int(y * 4))
    if row < 3:
        key = ROWS[row][min(9, int(column))]
        return {"@": "alt", "$": "sym", "⌫": "del", "↵": "enter"}.get(key, key.lower())
    right = 0.0
    for index, width in enumerate(BOTTOM_WIDTHS):
        right += width
        if column < right:
            return BOTTOM[index]
    return BOTTOM[-1]


# --- The script. ---

class Script:
    def __init__(self, alignment, keymap):
        self.alignment = alignment
        self.keymap = keymap
        self.lines = []
        self.t = 0.0
        self.touching = False
        self.frame = 0
        self.uses_keys = False
        # Positions in TitanKeys' space, with times, for the checks.
        self.trace = []

    def _event(self, dev, type_, code, value):
        self.lines.append((round(self.t, 3), dev, type_, code, value))

    def touch(self, x, y, major):
        if not self.touching:
            self._event(TOUCHPAD, EV_KEY, BTN_TOUCH, 1)
            self.touching = True
        rx, ry = self.alignment.raw(x, y)
        self._event(TOUCHPAD, EV_ABS, ABS_MT_TOUCH_MAJOR, major)
        self._event(TOUCHPAD, EV_ABS, ABS_MT_POSITION_X, rx)
        self._event(TOUCHPAD, EV_ABS, ABS_MT_POSITION_Y, ry)
        self._event(TOUCHPAD, EV_SYN, SYN_MT_REPORT, 0)
        self._event(TOUCHPAD, EV_SYN, SYN_REPORT, 0)
        self.trace.append((self.t, x, y))
        self.frame += 1

    def touch_raw(self, rx, ry, major):
        if not self.touching:
            self._event(TOUCHPAD, EV_KEY, BTN_TOUCH, 1)
            self.touching = True
        self._event(TOUCHPAD, EV_ABS, ABS_MT_TOUCH_MAJOR, major)
        self._event(TOUCHPAD, EV_ABS, ABS_MT_POSITION_X, max(0, min(RAW_X_MAX, rx)))
        self._event(TOUCHPAD, EV_ABS, ABS_MT_POSITION_Y, max(0, min(RAW_Y_MAX, ry)))
        self._event(TOUCHPAD, EV_SYN, SYN_MT_REPORT, 0)
        self._event(TOUCHPAD, EV_SYN, SYN_REPORT, 0)

    def lift(self):
        if not self.touching:
            return
        self._event(TOUCHPAD, EV_KEY, BTN_TOUCH, 0)
        self._event(TOUCHPAD, EV_SYN, SYN_MT_REPORT, 0)
        self._event(TOUCHPAD, EV_SYN, SYN_REPORT, 0)
        self.touching = False

    def key(self, name, down):
        code = self.keymap.get(name.lower())
        if code is None:
            raise SystemExit(f"no key code for {name!r}: pass --keymap from keymap.sh")
        self.uses_keys = True
        self._event(KEYBOARD, EV_KEY, code, 1 if down else 0)
        self._event(KEYBOARD, EV_SYN, SYN_REPORT, 0)

    def wait(self, ms):
        self.t += ms

    def text(self):
        out = ["# tk-inject script, from tools/dev/touch/gestures.py"]
        # Only the devices it uses: where the injector makes twins (see inject.c), an unused one would come and go for nothing.
        if any(dev == TOUCHPAD for _, dev, *_ in self.lines) or not self.uses_keys:
            out.append("dev 0 touchPad")
        if self.uses_keys:
            out.append("dev 1 TitanKey")
        out += [f"{t:.3f} {d} {ty} {c} {v}" for t, d, ty, c, v in self.lines]
        return "\n".join(out) + "\n"

    # --- Gestures. ---

    def still(self, x, y, ms):
        """A touch that stays put for ms, reporting every frame like the hardware, with the size wobbling a little."""
        end = self.t + ms
        while True:
            self.touch(x, y, STILL_MAJOR[self.frame % 2])
            if self.t + FRAME_MS > end:
                break
            self.wait(FRAME_MS)
        self.wait(max(0.0, end - self.t))

    def move(self, points, speed, major=MOVING_MAJOR):
        """Moves through points (TitanKeys' space) at speed in keys a millisecond, a frame at a time, without lifting."""
        path = smooth(points)
        lengths = [0.0]
        for (x0, y0), (x1, y1) in zip(path, path[1:]):
            lengths.append(lengths[-1] + math.hypot((x1 - x0) * 10, (y1 - y0) * 4))
        total = lengths[-1]
        if total == 0:
            return
        frames = max(2, math.ceil(total / speed / FRAME_MS))
        for frame in range(1, frames + 1):
            self.wait(FRAME_MS)
            want = total * frame / frames
            i = next((k for k in range(1, len(lengths)) if lengths[k] >= want), len(lengths) - 1)
            span = lengths[i] - lengths[i - 1] or 1
            t = (want - lengths[i - 1]) / span
            x = path[i - 1][0] + (path[i][0] - path[i - 1][0]) * t
            y = path[i - 1][1] + (path[i][1] - path[i - 1][1]) * t
            self.touch(x, y, major[self.frame % 2])

    def swipe(self, word, speed=.012):
        keys = []
        for letter in word.lower():
            if not letter.isalpha():
                continue
            if not keys or keys[-1] != letter:
                keys.append(letter)
        if not keys:
            raise SystemExit(f"swipe: no letters in {word!r}")
        points = [key_centre(k) for k in keys]
        if len(points) == 1:
            x, y = points[0]
            points = [(x - .02, y), (x + .02, y)]
        self.touch(*points[0], STILL_MAJOR[1])
        self.wait(FRAME_MS)
        self.touch(*points[0], MOVING_MAJOR[0])
        self.move(points, speed)
        self.wait(FRAME_MS)
        self.lift()

    def flick(self, keys=3.2, ms=90.0):
        # DeleteSwipe.kt's GUIDE_FLICK_START: on Backspace, in the middle of the delete zone, on the middle row.
        x0, y = (.875 + .95) / 2, (.25 + .56) / 2
        x1 = x0 - keys / 10
        self.touch(x0, y, MOVING_MAJOR[0])
        self.move([(x0, y), (x1, y)], keys / ms)
        self.lift()

    def space_taps(self, count):
        # Inside CursorGesture's zone, on the Space bar; each tap under FIRST_TAP_MILLIS (130), gaps under MAX_GAP_MILLIS (150).
        x, y = key_centre("space")
        for n in range(count):
            if n:
                self.wait(90)
            self.still(x, y, 70)
            self.lift()

    def press(self, name, hold=70.0, after="lift"):
        if after != "none":
            x, y = key_centre(name)
            self.still(x, y, 40)
        self.key(name, True)
        if after != "none":
            self.still(x, y, hold)
        else:
            self.wait(hold)
        self.key(name, False)
        if after == "rest":
            self.still(x, y, 700)
        elif after == "slide":
            self.touch(x, y, MOVING_MAJOR[0])
            self.move([(x, y), (min(1.0, x + .1), y)], .006)
        elif after == "lift":
            # The finger leaves the pad just after the key comes up, as it does from a real press.
            self.wait(6)
            self.touch(x, y, STILL_MAJOR[0])
        elif after != "none":
            raise SystemExit(f"key: AFTER is lift, rest, slide or none, not {after!r}")
        self.lift()

    def tap_at(self, x, y, ms=300.0):
        self.still(x, y, ms)
        self.lift()

    def replay_getevent(self, path):
        """A capture from capture.sh: '# device PATH NAME' lines, then getevent -lt lines, each maybe with its device first."""
        devices, start = {}, None
        pattern = re.compile(r"^(?:(/dev/input/event\d+):\s*)?\[\s*([\d.]+)\]\s+(\S+)\s+(\S+)\s+(\S+)")
        base = self.t
        for raw in open(path, encoding="utf-8", errors="replace"):
            if raw.startswith("# device "):
                _, _, dev_path, name = raw.split(None, 3)
                devices[dev_path] = name.strip()
                continue
            m = pattern.match(raw)
            if not m:
                continue
            dev_path, when, type_name, code_name, value = m.groups()
            name = devices.get(dev_path, "touchPad") if dev_path else "touchPad"
            dev = {"touchPad": TOUCHPAD, "TitanKey": KEYBOARD}.get(name)
            if dev is None:
                continue
            when = float(when) * 1000
            start = when if start is None else start
            self.t = base + when - start
            type_ = {"EV_SYN": EV_SYN, "EV_KEY": EV_KEY, "EV_ABS": EV_ABS}.get(type_name)
            if type_ is None:
                continue
            code = EVENT_CODES.get(code_name)
            if code is None:
                try:
                    code = int(code_name, 16)
                except ValueError:
                    continue
            value = {"DOWN": 1, "UP": 0, "REPEAT": 2}[value] if value in ("DOWN", "UP", "REPEAT") else int(value, 16)
            if dev == KEYBOARD:
                self.uses_keys = True
            if type_ == EV_KEY and code == BTN_TOUCH:
                self.touching = value == 1
            self._event(dev, type_, code, value)

    def replay_rawlog(self, path, start=None, end=None):
        """A raw log export's touches ('m' lines, through their calibrated samples) and keys ('key' lines, by Android code)."""
        android_to_linux = {v: self.keymap[k] for k, v in ANDROID_CODES.items() if k in self.keymap}
        base, first = self.t, None
        for raw in open(path, encoding="utf-8", errors="replace"):
            # Only touch and key lines are read; the rest, which can hold typed text, is skipped unread.
            if not raw.startswith(('{"k":"m",', '{"k":"key",')):
                continue
            line = json.loads(raw)
            kind = line.get("k")
            t = line.get("t")
            if t is None or start is not None and t < start or end is not None and t > end:
                continue
            first = t if first is None else first
            if kind == "m":
                sizes = line.get("sz") or []
                for i, (x, y, at) in enumerate(line.get("s") or []):
                    if x is None or y is None:
                        continue
                    self.t = max(self.t, base + at - first)
                    size = sizes[i] if i < len(sizes) and sizes[i] is not None else None
                    self.touch(x, y, sensor_step(size))
                if line.get("a") in (1, 3):  # ACTION_UP, ACTION_CANCEL
                    self.t = max(self.t, base + t - first)
                    self.lift()
            elif kind == "key" and not line.get("g"):
                code = android_to_linux.get(line.get("c"))
                if code is None:
                    continue
                self.t = max(self.t, base + t - first)
                self.uses_keys = True
                self._event(KEYBOARD, EV_KEY, code, 1 if line.get("d") else 0)
                self._event(KEYBOARD, EV_SYN, SYN_REPORT, 0)
        self.lift()


# Android key codes for the keymap's names, for the raw log's key lines.
ANDROID_CODES = {**{c: 29 + i for i, c in enumerate("abcdefghijklmnopqrstuvwxyz")},
                 "space": 62, "enter": 66, "del": 67, "shift": 59, "alt": 57, "sym": 63, "fn": 119}
EVENT_CODES = {"SYN_REPORT": SYN_REPORT, "SYN_MT_REPORT": SYN_MT_REPORT, "BTN_TOUCH": BTN_TOUCH,
               "ABS_MT_TOUCH_MAJOR": ABS_MT_TOUCH_MAJOR, "ABS_MT_POSITION_X": ABS_MT_POSITION_X,
               "ABS_MT_POSITION_Y": ABS_MT_POSITION_Y,
               **{f"KEY_{c.upper()}": code for c, code in LINUX_KEYS.items() if len(c) == 1},
               "KEY_SPACE": 57, "KEY_ENTER": 28, "KEY_BACKSPACE": 14, "KEY_LEFTSHIFT": 42, "KEY_LEFTALT": 56}


# The raw log's "sz" is Android's touch major, which is scaled to the screen: its range is the screen's diagonal, over the
# sensor's 255 steps (android/debug/recorder/README.md). The panel is 1080 x 1200.
SCREEN_DIAGONAL = math.hypot(1080, 1200)


def sensor_step(touch_major):
    """The sensor's size step (1 to 255) for the raw log's touch major; 2, a fingertip's, where the log has none."""
    if touch_major is None or touch_major <= 0:
        return 2
    return max(1, min(255, round(touch_major * 255 / SCREEN_DIAGONAL)))


def smooth(points):
    """A path through the key centres that rounds its corners a little, as a finger does, still passing every centre."""
    if len(points) < 3:
        return list(points)
    out = [points[0]]
    for i in range(len(points) - 1):
        p0 = points[max(0, i - 1)]
        p1, p2 = points[i], points[i + 1]
        p3 = points[min(len(points) - 1, i + 2)]
        for step in range(1, 9):
            t = step / 8
            t2, t3 = t * t, t * t * t
            out.append(tuple(.5 * (2 * p1[k] + (-p0[k] + p2[k]) * t + (2 * p0[k] - 5 * p1[k] + 4 * p2[k] - p3[k]) * t2
                                   + (-p0[k] + 3 * p1[k] - 3 * p2[k] + p3[k]) * t3) for k in (0, 1)))
    return out


def read_keymap(path):
    keymap = dict(LINUX_KEYS)
    if path:
        for raw in open(path, encoding="utf-8"):
            raw = raw.split("#")[0].strip()
            if "=" in raw:
                name, code = raw.split("=", 1)
                keymap[name.strip().lower()] = int(code)
    return keymap


def build(args, alignment=None):
    script = Script(alignment or Alignment(), read_keymap(args.keymap))
    for gesture in args.gestures:
        if gesture.startswith("+"):
            script.wait(float(gesture[1:]))
            continue
        name, _, rest = gesture.partition(":")
        parts = rest.split(":") if rest else []
        if name == "swipe":
            script.swipe(parts[0], float(parts[1]) if len(parts) > 1 else .012)
        elif name == "flick":
            script.flick(*(float(p) for p in parts))
        elif name == "space":
            count = int(parts[0]) if parts else 1
            if count not in (1, 2, 3):
                raise SystemExit("space: 1, 2 or 3 taps")
            script.space_taps(count)
        elif name == "key":
            script.press(parts[0], float(parts[1]) if len(parts) > 1 else 70.0, parts[2] if len(parts) > 2 else "lift")
        elif name == "hold":
            script.press(parts[0], float(parts[1]) if len(parts) > 1 else 800.0)
        elif name == "touch":
            x, y = (float(v) for v in parts[0].split(","))
            script.tap_at(x, y, float(parts[1]) if len(parts) > 1 else 300.0)
        elif name == "replay-getevent":
            script.replay_getevent(rest)
        elif name == "replay-rawlog":
            file, *span = rest.split(":")
            script.replay_rawlog(file, *(int(v) for v in span))
        else:
            raise SystemExit(f"unknown gesture {gesture!r}")
        # A gap between gestures, so they don't run together unless a +MS says so.
        script.wait(FRAME_MS)
    return script


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--keymap", help="key=code lines from keymap.sh")
    parser.add_argument("--out", help="where to write the script (default: standard output)")
    parser.add_argument("gestures", nargs="+")
    args = parser.parse_args()
    text = build(args).text()
    if args.out:
        with open(args.out, "w", encoding="utf-8") as out:
            out.write(text)
    else:
        sys.stdout.write(text)


if __name__ == "__main__":
    main()
