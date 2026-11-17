# Held-out Cantonese test: Tatoeba's yue sentences with their Jyutping transcriptions, cut at punctuation into phrases
# whose characters match their syllables one to one. Prints "code<TAB>phrase", the code toneless and without spaces.
# python3 prep.py yue_sentences.tsv transcriptions.csv > test.tsv
import re, sys

CJK = re.compile(r'[㐀-鿿\U00020000-\U0003ffff]')
PUNCT_TEXT = re.compile(r'[，。？！、,.?!：:；;「」『』（）()…～~"“”‘’\s]+')
SYL = re.compile(r'[a-z]+[1-6¹²³⁴⁵⁶]')
TONE = re.compile(r'[1-6¹²³⁴⁵⁶]')

text = {}
for line in open(sys.argv[1], encoding='utf-8'):
    p = line.rstrip('\n').split('\t')
    if len(p) >= 3 and p[1] == 'yue':
        text[p[0]] = p[2]
seen = set()
kept = dropped = 0
for line in open(sys.argv[2], encoding='utf-8'):
    p = line.rstrip('\n').split('\t')
    if len(p) < 5 or p[1] != 'yue' or p[0] not in text:
        continue
    chunks = [c for c in PUNCT_TEXT.split(text[p[0]]) if c]
    # The transcription's punctuation tokens are its own words: split on them.
    jchunks = [c.strip() for c in re.split(r'(?:^|\s)[,.?!:;"…~、，。？！]+(?=\s|$)', p[4]) if c.strip()]
    if len(chunks) != len(jchunks):
        dropped += 1
        continue
    for c, j in zip(chunks, jchunks):
        if not all(CJK.match(ch) for ch in c):
            continue
        syls = SYL.findall(j)
        if len(syls) != len(c) or len(c) < 2 or c in seen:
            continue
        seen.add(c)
        kept += 1
        print(TONE.sub('', ''.join(syls)) + '\t' + c)
print(f'{kept} phrases, {dropped} sentences dropped', file=sys.stderr)
