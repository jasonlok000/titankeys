#!/usr/bin/env python3
"""A long list of names on this phone for the list-size check: first names from en_names.txt (public, US SSA) that the base
list lacks, as PhoneNames would take them, each at PhoneNames.FREQUENCY (104). A fixed seed picks them.
Usage: names_list.py en_names.txt english.combined count out.tsv"""
import random
import re
import sys

names_path, dictionary_path, count, out_path = sys.argv[1], sys.argv[2], int(sys.argv[3]), sys.argv[4]
base = set()
for line in open(dictionary_path, encoding='utf-8'):
    m = re.match(r'^ word=([^,]+),', line)
    if m:
        base.add(m.group(1).lower())
names = sorted({l.strip().lower() for l in open(names_path, encoding='utf-8')
                if l.strip() and not l.startswith('#') and re.fullmatch(r'[a-z]{2,24}', l.strip().lower())} - base)
random.Random(7).shuffle(names)
chosen = sorted(names[:count])
with open(out_path, 'w', encoding='utf-8', newline='\n') as out:
    for name in chosen:
        out.write(f'{name}\t104\n')
print(f'names not in the base list: {len(names)}, chosen: {len(chosen)}')
