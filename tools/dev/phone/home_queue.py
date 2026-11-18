#!/usr/bin/env python3
"""Run one already configured, guarded phone job when the owner leaves the phone on Home."""
import argparse
import fcntl
import os
from pathlib import Path
import re
import signal
import subprocess
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
import phone_lock

POLL_SECONDS = 20
HOME_SECONDS = 120
COMPONENT = r"[\w.]+/[\w.$]+"
FOCUS = re.compile(rf"^\s*mCurrentFocus=Window\{{\S+ u0 ({COMPONENT})\}}", re.M)
LOCK_FLAGS = (
    r"mShowingLockscreen|mDreamingLockscreen|isKeyguardShowing|mKeyguardShowing|"
    r"mKeyguardShowingAndNotOccluded|showingAndNotOccluded|mShowingDream|inputRestricted|dreaming"
)


def expanded(component):
    package, activity = component.split("/", 1)
    return package + "/" + (package + activity if activity.startswith(".") else activity)


def at_home(window, home):
    """Require the resolved Home activity, an awake screen, and explicit unlocked window state."""
    focus = FOCUS.findall(window)
    if not home or len(focus) != 1 or expanded(focus[0]) != expanded(home):
        return False
    if re.search(r"\bmTopFocusedDisplayId=(?!0\b)\d+", window):
        return False
    display = re.findall(r"\bmAwake=(true|false) mScreenOnEarly=(true|false) mScreenOnFully=(true|false)", window)
    if display != [("true", "true", "true")]:
        return False
    if re.search(rf"\b(?:{LOCK_FLAGS})=true\b", window):
        return False
    delegate = re.search(r"^[ \t]*KeyguardServiceDelegate[ \t]*\n[ \t]+showing=(true|false)\b", window, re.M)
    if delegate and delegate[1] == "true":
        return False
    return bool(delegate or re.search(r"\b(?:mShowingLockscreen|isKeyguardShowing|mKeyguardShowing)=false\b", window))


def wait_for_home(read_home, *, monotonic=time.monotonic, sleep=time.sleep, expired=lambda: False):
    """Read only the caller's window predicate every 20 s. A false, failed, or missed sample restarts the 120 s."""
    since = last = None
    samples = 0
    while not expired():
        now = monotonic()
        if last is not None and now - last >= 2 * POLL_SECONDS:
            since = None
            samples = 0
        last = now
        try:
            ready = read_home()
        except (OSError, subprocess.SubprocessError):
            ready = False
        if ready:
            if since is None:
                since = now
            samples += 1
            if now - since >= HOME_SECONDS and samples >= HOME_SECONDS // POLL_SECONDS + 1:
                return not expired()
        else:
            since = None
            samples = 0
        sleep(max(0, POLL_SECONDS - (monotonic() - now)))
    return False


def adb_read(*args):
    serial = os.environ.get("TK_ADB_SERIAL")
    return subprocess.run(["adb", *(["-s", serial] if serial else []), "shell", *args], text=True,
                          capture_output=True, check=True, timeout=10).stdout.replace("\r", "")


def resolve_home():
    output = adb_read("cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.MAIN",
                      "-c", "android.intent.category.HOME")
    home = next((expanded(line.strip()) for line in reversed(output.splitlines())
                 if re.fullmatch(COMPONENT, line.strip())), "")
    return "" if home.startswith(("android/", "com.android.systemui/")) else home


def run_job(command):
    """Keep the lock until the guarded job exits, including its restoration after a signal."""
    env = dict(os.environ, TK_PHONE_LOCKED="1", TK_WAIT_FOR_OWNER="1")
    with subprocess.Popen(command, env=env, start_new_session=True) as job:
        def forward(signum, _):
            try:
                os.killpg(job.pid, signum)
            except ProcessLookupError:
                pass

        handlers = {sig: signal.signal(sig, forward) for sig in (signal.SIGINT, signal.SIGTERM)}
        try:
            status = job.wait()
            return status if status >= 0 else 128 - status
        finally:
            for sig, handler in handlers.items():
                signal.signal(sig, handler)


def queued(command, *, read_window=lambda: adb_read("dumpsys", "window"), home_resolver=resolve_home,
           wait=wait_for_home, run=run_job, expired=lambda: False):
    """Take the same lock as the regular phone job, then repeat the Home check before handing it over."""
    home = home_resolver()
    if not home:
        raise RuntimeError("Android didn't identify its Home activity")
    print("Waiting for two minutes of awake, unlocked Home. No input will be sent.", flush=True)
    while wait(lambda: at_home(read_window(), home), expired=expired):
        # Open without truncating or waiting inside flock. A competing run may have changed windows.
        with open(phone_lock.path(), "a") as lock:
            try:
                fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError:
                continue
            try:
                ready = not expired() and home_resolver() == home and at_home(read_window(), home)
            except (OSError, subprocess.SubprocessError):
                ready = False
            if ready and not expired():
                return run(command)
    return 3


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", nargs=argparse.REMAINDER, help="the existing guarded phone job and its arguments")
    args = parser.parse_args()
    command = args.command[1:] if args.command[:1] == ["--"] else args.command
    if not command:
        parser.error("give the guarded phone job after --")
    if os.environ.get("TK_PHONE_LOCKED") == "1":
        parser.error("this watcher takes the job lock itself; import wait_for_home if the caller holds it")
    try:
        cutoff = int(os.environ["TK_CUTOFF_EPOCH"]) if os.environ.get("TK_CUTOFF_EPOCH") else None
        return queued(command, expired=lambda: cutoff is not None and time.time() >= cutoff)
    except subprocess.SubprocessError:
        print("STOP: queued job couldn't read Android's Home state", file=sys.stderr)
        return 3
    except (ValueError, OSError, RuntimeError) as error:
        print(f"STOP: queued job: {error}", file=sys.stderr)
        return 3
    except KeyboardInterrupt:
        return 130


if __name__ == "__main__":
    sys.exit(main())
