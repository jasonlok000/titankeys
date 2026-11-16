"""The phone lock for Python scripts that talk to the phone, as tools/phone-lock.sh is for shell scripts: the same lock file, the
same rules. Builds use $TK_BUILD/tk-tools/.lock and never this one.

    import phone_lock
    phone_lock.hold()   # held until this process ends

It does nothing where the lock's folder doesn't exist (a contributor's computer), nor when a parent already holds it
(TK_PHONE_LOCKED=1). TK_PHONE_LOCK_WAIT=SECONDS waits at most that long, then exits with 3.
"""
import fcntl
import os
import subprocess
import sys
import time

_held = None


def path():
    build = os.environ.get("TK_BUILD", os.path.expanduser("~/build"))
    return os.environ.get("TK_PHONE_LOCK", os.path.join(build, "tk-tools", ".phone.lock"))


def hold():
    global _held
    if _held is not None or os.environ.get("TK_PHONE_LOCKED") == "1":
        return
    lock = path()
    if not os.path.isdir(os.path.dirname(lock)):
        return
    # The adb server starts first: one started while the lock is open could inherit it.
    try:
        subprocess.run(["adb", "start-server"], capture_output=True, timeout=30)
    except (OSError, subprocess.SubprocessError):
        pass
    # Python opens it so that no program started from here inherits it: the lock goes when this process ends.
    handle = open(lock, "w")
    try:
        fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError:
        print(f"waiting for the phone: another phone run holds {lock}", file=sys.stderr, flush=True)
        wait = os.environ.get("TK_PHONE_LOCK_WAIT")
        deadline = time.monotonic() + float(wait) if wait else None
        while True:
            try:
                fcntl.flock(handle, fcntl.LOCK_EX | (fcntl.LOCK_NB if deadline else 0))
                break
            except BlockingIOError:
                if time.monotonic() > deadline:
                    print("phone busy", flush=True)
                    sys.exit(3)
                time.sleep(1)
    _held = handle
    os.environ["TK_PHONE_LOCKED"] = "1"
