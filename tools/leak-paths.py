#!/usr/bin/env python3
"""Name what holds each destroyed guide and playground in a heap dump from TitanKeys Debug's leak check (#85).

    adb shell am broadcast -n com.titankeys.debug/com.titankeys.keyboard.LeakCheck --ez dump true
    adb pull /sdcard/Android/data/com.titankeys.debug/files/leaks/
    python3 tools/leak-paths.py leaks/leak-<time>.hprof

For each destroyed activity of the class (GuideActivity by default) it prints the shortest chain of strong references from
something the collector never frees (a class's static field, a thread, a JNI global, ...) to it, by class and field names. The
link where that chain should have been cut is the leak. Weak, soft and phantom references don't count, as they hold nothing.

It prints names only, never a string's or an array's contents: the dump holds typed text, so the dump itself never goes into git,
an issue or a report. Delete it when done. Plain Python, no packages. Reads Android's dumps and the JVM's.

- --class: the class to look for, by its full name or the end of it. Default com.titankeys.keyboard.GuideActivity.
- --all: every instance of the class, not only destroyed activities.
"""
import argparse
import collections
import mmap
import struct
import sys

OBJECT = 2
SIZES = {4: 1, 5: 2, 6: 4, 7: 8, 8: 1, 9: 2, 10: 4, 11: 8}
# Heap dump records that name a root: tag -> (name, bytes after the object's id, in ids and in plain bytes).
ROOTS = {0xFF: ("unknown", 0, 0), 0x01: ("JNI global", 1, 0), 0x02: ("JNI local", 0, 8), 0x03: ("Java frame", 0, 8),
         0x04: ("native stack", 0, 4), 0x05: ("system class", 0, 0), 0x06: ("thread block", 0, 4), 0x07: ("monitor", 0, 0),
         0x08: ("thread", 0, 8), 0x89: ("interned string", 0, 0), 0x8A: ("finalizing", 0, 0), 0x8B: ("debugger", 0, 0),
         0x8C: ("reference cleanup", 0, 0), 0x8D: ("VM internal", 0, 0), 0x8E: ("JNI monitor", 0, 8)}
# Roots that say little, tried last so that a path through a real field wins at the same length.
VAGUE_ROOTS = {"unknown", "VM internal", "system class", "interned string", "reference cleanup"}
# A reference object's own target holds nothing; the rest of it is ordinary fields.
REFERENCE_FIELDS = {"referent", "zombie"}
REFERENCE_CLASS = "java.lang.ref.Reference"


class Dump:
    def __init__(self, path):
        with open(path, "rb") as handle:
            self.data = mmap.mmap(handle.fileno(), 0, access=mmap.ACCESS_READ)
        data = self.data
        end = data.find(b"\0")
        if not data[:end].startswith(b"JAVA PROFILE"):
            sys.exit(f"{path} is not a heap dump")
        self.id_size = struct.unpack_from(">I", data, end + 1)[0]
        self.id_format = ">I" if self.id_size == 4 else ">Q"
        self.strings = {}
        self.class_name_ids = {}
        self.classes = {}  # class id -> (super id, [(name, type)], [(name, value id)])
        self.objects = {}  # object id -> offset of its record, after the sub-tag
        self.roots = []  # (object id, root name)
        self.layouts = {}
        self.load(end + 1 + 4 + 8)

    def read_id(self, at):
        return struct.unpack_from(self.id_format, self.data, at)[0]

    def size(self, kind):
        return self.id_size if kind == OBJECT else SIZES[kind]

    def load(self, at):
        data, ID = self.data, self.id_size
        while at < len(data):
            tag = data[at]
            length = struct.unpack_from(">I", data, at + 5)[0]
            body = at + 9
            at = body + length
            if tag == 0x01:
                self.strings[self.read_id(body)] = data[body + ID:at].decode("utf-8", "replace")
            elif tag == 0x02:
                self.class_name_ids[self.read_id(body + 4)] = self.read_id(body + 8 + ID)
            elif tag in (0x0C, 0x1C):
                self.heap(body, at)

    def heap(self, p, stop):
        data, ID = self.data, self.id_size
        while p < stop:
            sub = data[p]
            p += 1
            if sub in ROOTS:
                name, ids, extra = ROOTS[sub]
                self.roots.append((self.read_id(p), name))
                p += ID * (1 + ids) + extra
            elif sub == 0x90:  # Android: unreachable, not a root
                p += ID
            elif sub == 0xFE:  # Android: which heap follows
                p += 4 + ID
            elif sub == 0x20:
                cls = self.read_id(p)
                superclass = self.read_id(p + 4 + ID)
                p += 7 * ID + 4 + 4
                count = struct.unpack_from(">H", data, p)[0]
                p += 2
                for _ in range(count):  # the constant pool: index, type, value
                    p += 3 + self.size(data[p + 2])
                count = struct.unpack_from(">H", data, p)[0]
                p += 2
                statics = []
                for _ in range(count):
                    name, kind = self.read_id(p), data[p + ID]
                    p += ID + 1
                    if kind == OBJECT:
                        statics.append((name, self.read_id(p)))
                    p += self.size(kind)
                fields = []
                count = struct.unpack_from(">H", data, p)[0]
                p += 2
                for _ in range(count):
                    fields.append((self.read_id(p), data[p + ID]))
                    p += ID + 1
                self.classes[cls] = (superclass, fields, statics)
            elif sub == 0x21:
                self.objects[self.read_id(p)] = p
                p += ID + 4 + ID + 4 + struct.unpack_from(">I", data, p + ID + 4 + ID)[0]
            elif sub == 0x22:
                self.objects[self.read_id(p)] = p
                p += ID + 4 + 4 + ID + ID * struct.unpack_from(">I", data, p + ID + 4)[0]
            elif sub == 0x23:
                count, kind = struct.unpack_from(">I", data, p + ID + 4)[0], data[p + ID + 8]
                p += ID + 4 + 4 + 1 + count * self.size(kind)
            elif sub == 0xC3:  # Android: a primitive array without its data
                p += ID + 4 + 4 + 1
            else:
                sys.exit(f"unknown heap record {sub:#x} at {p - 1}")

    def class_name(self, cls):
        return self.strings.get(self.class_name_ids.get(cls), f"class@{cls:x}").replace("/", ".")

    def string(self, name_id):
        return self.strings.get(name_id, "?")

    def ancestry(self, cls):
        while cls in self.classes:
            yield cls
            cls = self.classes[cls][0]

    def class_of(self, obj):
        p = self.objects[obj]
        sub = self.data[p - 1]
        return self.read_id(p + self.id_size + 4 + (4 if sub == 0x22 else 0))

    def references(self, obj):
        """(label, object id) for every strong reference that [obj] holds."""
        data, ID = self.data, self.id_size
        if obj in self.classes:  # a class object: its statics
            for name, target in self.classes[obj][2]:
                if target:
                    yield f"static {self.string(name)}", target
            return
        p = self.objects[obj]
        if data[p - 1] == 0x22:
            count = struct.unpack_from(">I", data, p + ID + 4)[0]
            first = p + ID + 4 + 4 + ID
            for index in range(count):
                target = self.read_id(first + index * ID)
                if target:
                    yield f"[{index}]", target
            return
        start = p + ID + 4 + ID + 4
        for offset, name in self.layout(self.read_id(p + ID + 4)):
            target = self.read_id(start + offset)
            if target:
                yield name, target

    def layout(self, cls):
        """Where each counted reference field of an instance of [cls] is, after the record's header: (offset, name)."""
        if cls not in self.layouts:
            chain = list(self.ancestry(cls))
            is_reference = any(self.class_name(c) == REFERENCE_CLASS for c in chain)
            fields, offset = [], 0
            for c in chain:
                for name_id, kind in self.classes[c][1]:
                    name = self.string(name_id)
                    if kind == OBJECT and not (is_reference and name in REFERENCE_FIELDS) and not name.startswith("shadow$"):
                        fields.append((offset, name))
                    offset += self.size(kind)
            self.layouts[cls] = fields
        return self.layouts[cls]

    def boolean_field(self, obj, wanted):
        p = self.objects[obj]
        q = p + self.id_size + 4 + self.id_size + 4
        for c in self.ancestry(self.class_of(obj)):
            for name_id, kind in self.classes[c][1]:
                if kind == 4 and self.string(name_id) == wanted:
                    return self.data[q] != 0
                q += self.size(kind)
        return None


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("dump")
    parser.add_argument("--class", dest="cls", default="com.titankeys.keyboard.GuideActivity")
    parser.add_argument("--all", action="store_true")
    args = parser.parse_args()
    dump = Dump(args.dump)

    wanted = {cls for cls in dump.classes if dump.class_name(cls) == args.cls or dump.class_name(cls).endswith("." + args.cls)}
    if not wanted:
        sys.exit(f"no class {args.cls} in the dump")
    targets = set()
    for obj, p in dump.objects.items():
        if dump.data[p - 1] != 0x21 or obj in dump.classes:
            continue
        if wanted & set(dump.ancestry(dump.class_of(obj))):
            destroyed = dump.boolean_field(obj, "mDestroyed")
            if args.all or destroyed:
                targets.add(obj)
    print(f"{len(targets)} {'' if args.all else 'destroyed '}{args.cls} in the dump")
    if not targets:
        return

    # Breadth first from the roots: class statics and the clear roots first, then the vague ones, so the shortest chain wins and
    # a named root wins a tie.
    parents = {}
    queue = collections.deque()
    seeds = [(cls, f"class {dump.class_name(cls)}") for cls in dump.classes]
    seeds += [(obj, name) for obj, name in dump.roots if name not in VAGUE_ROOTS]
    seeds += [(obj, name) for obj, name in dump.roots if name in VAGUE_ROOTS]
    for obj, name in seeds:
        if obj not in parents and (obj in dump.objects or obj in dump.classes):
            parents[obj] = (None, name)
            queue.append(obj)
    left = set(targets)
    while queue and left:
        obj = queue.popleft()
        if obj not in dump.objects and obj not in dump.classes:
            continue
        for label, target in dump.references(obj):
            if target in parents or (target not in dump.objects and target not in dump.classes):
                continue
            parents[target] = (obj, label)
            left.discard(target)
            # The path stops at the first target it reaches: past it, the rest is that one's own.
            if target not in targets:
                queue.append(target)

    for target in sorted(targets):
        print()
        print(f"{dump.class_name(dump.class_of(target))} @{target:x}:")
        if target not in parents:
            print("  held only by weak references or by nothing: the collector frees it")
            continue
        chain = []
        obj = target
        while True:
            parent, label = parents[obj]
            if parent is None:
                chain.append(f"root: {label}")
                break
            holder = dump.class_name(parent) if parent in dump.classes else dump.class_name(dump.class_of(parent))
            chain.append(f"{holder} .{label}")
            obj = parent
        for line in reversed(chain):
            print(f"  {line}")
        print(f"  -> {dump.class_name(dump.class_of(target))}")


if __name__ == "__main__":
    main()
