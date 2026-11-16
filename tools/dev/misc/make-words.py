"""Generates the recorder's prompt list (RecorderWords.kt) from the keyboard's own dictionary, with a fixed seed so sessions compare.
Usage: python3 -I make-words.py android/assets/swipe/english.combined android/debug/recorder/RecorderWords.kt"""
import random
import re
import sys

source, output = sys.argv[1:3]
words = []
for line in open(source, encoding="utf-8"):
    match = re.match(r"^ word=([a-z]+),f=(\d+)", line)
    if match and "offensive" not in line and "not_a_word" not in line:
        words.append(match.group(1))

rng = random.Random(20261108)


def pick(pool, count, chosen):
    pool = [w for w in pool if w not in chosen]
    rng.shuffle(pool)
    chosen.update(pool[:count])
    return pool[:count]


chosen = set()
result = []
result += pick([w for w in words[:800] if 2 <= len(w) <= 3], 25, chosen)
result += pick([w for w in words[:400] if 2 <= len(w) <= 10], 85, chosen)
result += pick([w for w in words[400:3000] if 3 <= len(w) <= 12], 130, chosen)
result += pick([w for w in words[:8000] if 7 <= len(w) <= 14], 60, chosen)
rng.shuffle(result)
assert len(result) == 300, len(result)

lines, current = [], "       "
for word in result:
    if len(current) + len(word) + 1 > 104:
        lines.append(current)
        current = "       "
    current += " " + word
lines.append(current)

with open(output, "w", encoding="utf-8", newline="\n") as out:
    out.write("package com.atlas.keyboard\n\n")
    out.write("/** The 300 prompts, generated once from Atlas's dictionary with a fixed seed so sessions compare. */\n")
    out.write("internal object RecorderWords {\n")
    out.write('    val LIST: List<String> = """\n')
    out.write("\n".join(lines) + "\n")
    out.write('    """.trim().split(Regex("\\\\s+"))\n')
    out.write("}\n")
print(len(result), result[:30])
