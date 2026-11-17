"""Cantonese's grammar model for librime-octagram (#56): which words follow which, counted from Cantonese text, for Space to
put the likelier phrase first (docs/studies/56-cantonese-dictionary.md). data.sh runs it, then the plugin's build_grammar.

  python3 grammar.py essay-cantonese.txt yuewiki.xml.bz2 hkcancor-utf8.zip cv-zh-hk.txt > counts.txt

Each line is "key count": the last 1 to 3 characters of the text before a word, then the word's first 3 characters, as
Octagram::Query looks them up. Runs of Chinese characters are cut into words by the longest word in essay-cantonese.txt
(rime-cantonese's word weights), except HKCanCor's, which come cut. Only keys seen twice or more are kept.

The text: Cantonese Wikipedia's articles (CC BY-SA 4.0), roughly cleaned of markup; HKCanCor's transcripts (CC BY 4.0);
Common Voice's zh-HK sentences (CC0). The model is counted from them, so it carries Wikipedia's CC BY-SA 4.0
(native/rime/SOURCES.md, android/assets/rime/NOTICE).
"""
import bz2
import html
import re
import sys
import zipfile
from collections import Counter

RUN = re.compile(r'[㐀-鿿\U00020000-\U0003ffff]+')
LONGEST = 6
TEXT = re.compile(r'<text[^>]*>(.*?)</text>', re.S)


def vocabulary(path):
    words = set()
    for line in open(path, encoding='utf-8'):
        word = line.split('\t', 1)[0]
        if RUN.fullmatch(word):
            words.add(word)
    return words


def segment(run, words):
    out, i = [], 0
    while i < len(run):
        for n in range(min(LONGEST, len(run) - i), 0, -1):
            if n == 1 or run[i:i + n] in words:
                out.append(run[i:i + n])
                i += n
                break
    return out


def wikipedia(path):
    """The articles' text, a line at a time: templates, references, tags and tables dropped, links as their text."""
    buf = ''
    with bz2.open(path, 'rt', encoding='utf-8') as f:
        for chunk in iter(lambda: f.read(1 << 20), ''):
            buf += chunk
            while True:
                m = TEXT.search(buf)
                if not m:
                    break
                text = html.unescape(m.group(1))
                buf = buf[m.end():]
                if text.startswith('#'):
                    continue
                for _ in range(3):
                    text = re.sub(r'\{\{[^{}]*\}\}', '', text)
                text = re.sub(r'<ref[^>]*/>|<ref.*?</ref>|<[^>]+>', '', text, flags=re.S)
                text = re.sub(r'\[\[(?:[^|\]]*\|)?([^\]]*)\]\]', r'\1', text)
                for line in text.split('\n'):
                    if line and not line.startswith(('{|', '|', '!', '*[', '[[')):
                        yield line
            if len(buf) > 400000:
                buf = buf[-200000:]


def hkcancor(path):
    """HKCanCor's sentences as its own words: word/tag/Jyutping/ lines between <sent_tag> and </sent_tag>."""
    with zipfile.ZipFile(path) as z:
        for name in sorted(z.namelist()):
            if not name.startswith('utf8/') or name.endswith('/'):
                continue
            words = []
            for line in z.read(name).decode('utf-8').split('\n'):
                line = line.strip()
                if line.startswith('</sent_tag>'):
                    if words:
                        yield words
                    words = []
                parts = line.split('/')
                if len(parts) >= 3 and parts[0]:
                    if RUN.fullmatch(parts[0]):
                        words.append(parts[0])
                    elif words:
                        yield words
                        words = []


def main():
    essay, dump, cancor, voice = sys.argv[1:5]
    words = vocabulary(essay)
    counts = Counter()

    def count(sentence):
        text = ''
        for word in sentence:
            for k in range(1, min(3, len(text)) + 1):
                counts[text[-k:] + word[:3]] += 1
            text += word

    for line in open(voice, encoding='utf-8'):
        for run in RUN.findall(line):
            count(segment(run, words))
    for sentence in hkcancor(cancor):
        count(sentence)
    for line in wikipedia(dump):
        for run in RUN.findall(line):
            count(segment(run, words))
    kept = 0
    out = sys.stdout
    for key, n in counts.items():
        if n > 1:
            out.write(f'{key} {n}\n')
            kept += 1
    print(f'grammar: {len(counts)} keys, {kept} kept', file=sys.stderr)


if __name__ == '__main__':
    main()
