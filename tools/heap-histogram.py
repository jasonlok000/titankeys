#!/usr/bin/env python3
"""A class histogram of a heap dump from TitanKeys Debug's leak check: per class, the instance count and their shallow size, the
largest first. Names and numbers only, never a string's or an array's contents: the dump holds typed text, so it never goes into
git, an issue or a report; delete it when done (2026-11-17's OutOfMemoryError hunt).

    python3 tools/heap-histogram.py leaks/leak-<time>.hprof [--top 25] [--all-heaps]

Only Android's app heap is counted unless --all-heaps: the zygote's and the boot image's objects are shared by every app.
Plain Python, no packages.
"""
import argparse
import collections
import mmap
import struct
import sys

OBJECT = 2
SIZES = {4: 1, 5: 2, 6: 4, 7: 8, 8: 1, 9: 2, 10: 4, 11: 8}
PRIMITIVE_NAMES = {4: "boolean[]", 5: "char[]", 6: "float[]", 7: "double[]", 8: "byte[]", 9: "short[]", 10: "int[]", 11: "long[]"}
ROOT_SKIP = {0xFF: 0, 0x01: 1, 0x02: 0, 0x03: 0, 0x04: 0, 0x05: 0, 0x06: 0, 0x07: 0, 0x08: 0, 0x89: 0, 0x8A: 0, 0x8B: 0,
             0x8C: 0, 0x8D: 0, 0x8E: 0}
ROOT_EXTRA = {0x02: 8, 0x03: 8, 0x04: 4, 0x06: 4, 0x08: 8, 0x8E: 8}
APP_HEAP = 0x41  # 'A'


def histogram(path, all_heaps):
    with open(path, "rb") as handle:
        data = mmap.mmap(handle.fileno(), 0, access=mmap.ACCESS_READ)
    end = data.find(b"\0")
    if not data[:end].startswith(b"JAVA PROFILE"):
        sys.exit(f"{path} is not a heap dump")
    ID = struct.unpack_from(">I", data, end + 1)[0]
    fmt = ">I" if ID == 4 else ">Q"

    def rid(at):
        return struct.unpack_from(fmt, data, at)[0]

    def size(kind):
        return ID if kind == OBJECT else SIZES[kind]

    strings, name_ids = {}, {}
    count, total = collections.Counter(), collections.Counter()
    at = end + 1 + 4 + 8
    while at < len(data):
        tag = data[at]
        length = struct.unpack_from(">I", data, at + 5)[0]
        body, at = at + 9, at + 9 + length
        if tag == 0x01:
            strings[rid(body)] = bytes(data[body + ID:at])
        elif tag == 0x02:
            name_ids[rid(body + 4)] = rid(body + 8 + ID)
        elif tag in (0x0C, 0x1C):
            p, heap = body, APP_HEAP
            while p < at:
                sub = data[p]
                p += 1
                if sub in ROOT_SKIP:
                    p += ID * (1 + ROOT_SKIP[sub]) + ROOT_EXTRA.get(sub, 0)
                elif sub == 0x90:
                    p += ID
                elif sub == 0xFE:
                    heap = struct.unpack_from(">I", data, p)[0]
                    p += 4 + ID
                elif sub == 0x20:
                    p += 7 * ID + 4 + 4
                    n = struct.unpack_from(">H", data, p)[0]
                    p += 2
                    for _ in range(n):
                        p += 3 + size(data[p + 2])
                    n = struct.unpack_from(">H", data, p)[0]
                    p += 2
                    for _ in range(n):
                        p += ID + 1 + size(data[p + ID])
                    n = struct.unpack_from(">H", data, p)[0]
                    p += 2 + n * (ID + 1)
                elif sub == 0x21:
                    cls, n = rid(p + ID + 4), struct.unpack_from(">I", data, p + ID + 4 + ID)[0]
                    if all_heaps or heap == APP_HEAP:
                        count[cls] += 1
                        total[cls] += n
                    p += ID + 4 + ID + 4 + n
                elif sub == 0x22:
                    n, cls = struct.unpack_from(">I", data, p + ID + 4)[0], rid(p + ID + 4 + 4)
                    if all_heaps or heap == APP_HEAP:
                        count[cls] += 1
                        total[cls] += n * ID
                    p += ID + 4 + 4 + ID + n * ID
                elif sub == 0x23:
                    n, kind = struct.unpack_from(">I", data, p + ID + 4)[0], data[p + ID + 8]
                    if all_heaps or heap == APP_HEAP:
                        count[PRIMITIVE_NAMES[kind]] += 1
                        total[PRIMITIVE_NAMES[kind]] += n * SIZES[kind]
                    p += ID + 4 + 4 + 1 + n * SIZES[kind]
                elif sub == 0xC3:
                    p += ID + 4 + 4 + 1
                else:
                    sys.exit(f"unknown heap record {sub:#x} at {p - 1}")

    def name(cls):
        if isinstance(cls, str):
            return cls
        raw = strings.get(name_ids.get(cls))
        return raw.decode("utf-8", "replace").replace("/", ".") if raw is not None else f"class@{cls:x}"

    return [(name(cls), count[cls], total[cls]) for cls in total], sum(total.values())


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("dump")
    parser.add_argument("--top", type=int, default=25)
    parser.add_argument("--all-heaps", action="store_true")
    args = parser.parse_args()
    rows, whole = histogram(args.dump, args.all_heaps)
    rows.sort(key=lambda row: -row[2])
    print(f"{'shallow MB':>10} {'instances':>10}  class   ({whole / 1e6:.1f} MB in all, "
          f"{'every heap' if args.all_heaps else 'the app heap'})")
    for cls, n, size in rows[:args.top]:
        print(f"{size / 1e6:10.2f} {n:10d}  {cls}")


if __name__ == "__main__":
    main()
