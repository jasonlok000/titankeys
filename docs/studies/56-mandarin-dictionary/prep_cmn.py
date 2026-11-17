# Held-out Mandarin test: Tatoeba's cmn sentences with their Pinyin transcriptions, cut at punctuation into phrases whose
# characters match their syllables one to one. Prints "pinyin<TAB>zhuyin-keys<TAB>phrase", codes toneless, no spaces.
# python3 prep_cmn.py cmn_sentences.tsv transcriptions.csv > test-cmn.tsv
import re, sys

CJK = re.compile(r'[㐀-鿿\U00020000-\U0003ffff]')
PUNCT_TEXT = re.compile(r'[，。？！、,.?!：:；;「」『』（）()…～~"“”‘’《》\s]+')
SYL = re.compile(r"(?:[a-zü]|u:)+[1-5]")

# tk_zhuyin.schema.yaml's speller algebra, as Rime applies it to a toneless terra_pinyin spelling, then its Hsu keys.
RULES = [(r'^r$', 'er'), (r'iu', 'iou'), (r'ui', 'uei'), (r'ong', 'ung'), (r'^yi?', 'i'), (r'^wu?', 'u'), (r'iu', 'v'),
         (r'^([jqx])u', r'\1v'), (r'([iuv])n', r'\1en'), (r'^zhi?', 'Z'), (r'^chi?', 'C'), (r'^shi?', 'S'), (r'^([zcsr])i', r'\1'),
         (r'ai', 'A'), (r'ei', 'I'), (r'ao', 'O'), (r'ou', 'U'), (r'ang', 'K'), (r'eng', 'G'), (r'an', 'M'), (r'en', 'N'),
         (r'er', 'R'), (r'eh', 'E'), (r'([iv])e', r'\1E')]
SRC = 'bpmfdtnlgkhjqxZCSrzcsiuvaoeEAIOUMNKGR'
DST = 'bpmfdtnlgkhjvcjvcrzasexuyhgeiawomnkll'
XLIT = str.maketrans(SRC, DST)


def zhuyin(syllable):
    s = syllable
    if re.fullmatch(r'm', s):
        s = 'mu'
    for a, b in RULES:
        s = re.sub(a, b, s)
    return s.translate(XLIT)


text = {}
for line in open(sys.argv[1], encoding='utf-8'):
    p = line.rstrip('\n').split('\t')
    if len(p) >= 3 and p[1] == 'cmn':
        text[p[0]] = p[2]
seen = set()
kept = 0
for line in open(sys.argv[2], encoding='utf-8'):
    p = line.rstrip('\n').split('\t')
    if len(p) < 5 or p[1] != 'cmn' or p[2] != 'Latn' or p[0] not in text:
        continue
    chunks = [c for c in PUNCT_TEXT.split(text[p[0]]) if c]
    jchunks = [c.strip() for c in re.split(r'(?:^|\s)[,.?!:;"…~、，。？！]+(?=\s|$)', p[4].lower()) if c.strip()]
    if len(chunks) != len(jchunks):
        continue
    for c, j in zip(chunks, jchunks):
        if not all(CJK.match(ch) for ch in c) or len(c) < 2 or c in seen:
            continue
        syls = [re.sub(r'[1-5]$', '', s).replace('u:', 'v').replace('ü', 'v') for s in SYL.findall(j)]
        if len(syls) != len(c):
            continue
        seen.add(c)
        kept += 1
        print(''.join(syls) + '\t' + ''.join(zhuyin(s) for s in syls) + '\t' + c)
print(f'{kept} phrases', file=sys.stderr)
