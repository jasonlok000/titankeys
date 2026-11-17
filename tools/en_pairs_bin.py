#!/usr/bin/env python3
"""English's word-pair counts in the compact form PairCounts reads (android/kotlin/com/titankeys/autocorrect/PairCounts.kt).

    python3 -I tools/en_pairs_bin.py <en_pairs.txt> <en_pairs.bin>      text to compact
    python3 -I tools/en_pairs_bin.py --text <en_pairs.bin> <en_pairs.txt>  compact back to text, for review

The text is "first second<TAB>count" lines ('#' lines skipped), as en_pairs_NOTICE.txt describes. The compact file is
"TKPC", a version, the counts V (words), B (bytes of words) and P (pairs), then the V+1 starts of each word in the bytes, the
B bytes of every word in UTF-8 sorted by their bytes, the P pair keys (first word's number << 20 | second's) sorted, and the
P counts, big-endian. Lines are read by PairCounts.parse's rules, and counts capped as it caps them.
"""
import struct
import sys

ID_BITS = 20
MAX_COUNT = (1 << 23) - 1
VERSION = 1


def read_text(path):
    pairs = []
    for line in open(path, encoding="utf-8"):
        line = line.rstrip("\n")
        if not line or line[0] == "#":
            continue
        tab, space = line.find("\t"), line.find(" ")
        if tab <= 0 or space <= 0 or space > tab:
            continue
        try:
            count = int(line[tab + 1:].strip())
        except ValueError:
            continue
        if count > 0:
            pairs.append((line[:space], line[space + 1:tab], min(count, MAX_COUNT)))
    return pairs


def write_binary(pairs, path):
    words = sorted({w for a, b, _ in pairs for w in (a, b)}, key=lambda w: w.encode("utf-8"))
    number = {w: i for i, w in enumerate(words)}
    blob, starts = bytearray(), [0]
    for w in words:
        blob += w.encode("utf-8")
        starts.append(len(blob))
    rows = sorted(((number[a] << ID_BITS) | number[b], n) for a, b, n in pairs)
    with open(path, "wb") as f:
        f.write(b"TKPC" + struct.pack(">4i", VERSION, len(words), len(blob), len(rows)))
        f.write(struct.pack(f">{len(starts)}i", *starts))
        f.write(bytes(blob))
        f.write(struct.pack(f">{len(rows)}q", *(k for k, _ in rows)))
        f.write(struct.pack(f">{len(rows)}i", *(n for _, n in rows)))
    return len(words), len(rows)


def read_binary(path):
    b = open(path, "rb").read()
    assert b[:4] == b"TKPC", "not a pair table"
    version, v, nbytes, p = struct.unpack(">4i", b[4:20])
    assert version == VERSION
    at = 20
    starts = struct.unpack(f">{v + 1}i", b[at:at + 4 * (v + 1)]); at += 4 * (v + 1)
    blob = b[at:at + nbytes]; at += nbytes
    keys = struct.unpack(f">{p}q", b[at:at + 8 * p]); at += 8 * p
    counts = struct.unpack(f">{p}i", b[at:at + 4 * p]); at += 4 * p
    assert at == len(b)
    word = [blob[starts[i]:starts[i + 1]].decode("utf-8") for i in range(v)]
    mask = (1 << ID_BITS) - 1
    return [(word[k >> ID_BITS], word[k & mask], n) for k, n in zip(keys, counts)]


def main():
    if sys.argv[1] == "--text":
        with open(sys.argv[3], "w", encoding="utf-8", newline="\n") as f:
            f.write("# English word-pair counts, decoded from en_pairs.bin. See en_pairs_NOTICE.txt.\n")
            for a, b, n in sorted(read_binary(sys.argv[2])):
                f.write(f"{a} {b}\t{n}\n")
        return
    pairs = read_text(sys.argv[1])
    v, p = write_binary(pairs, sys.argv[2])
    back = read_binary(sys.argv[2])
    assert sorted(back) == sorted(pairs), "the compact file doesn't read back the same pairs"
    print(f"{sys.argv[1]}: {p} pairs, {v} words -> {sys.argv[2]}, read back identical")


if __name__ == "__main__":
    main()
