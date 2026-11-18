#!/usr/bin/env python3
"""Writes each input method's blocked words: the words its bar never offers (the native review, 2026-11-17).

    python3 -I tools/im/blocklist.py <LDNOOBW folder>

<LDNOOBW folder> is a clone of https://github.com/LDNOOBW/List-of-Dirty-Naughty-Obscene-and-Otherwise-Bad-Words (CC BY 4.0),
one file a language. With tools/im/blocklist-extra.tsv, a short hand list of self-harm, sexual-violence and the swears the
native review found that it lacks, it writes android/assets/im/<id>/blocked.txt for every input method whose language either
has, and a "Blocked words" paragraph in its NOTICE. The keyboard reads it (BlockedWords): starters, next words and completions
leave those words out. It also drops from Rime's next-word sources (native/rime/predict/yue.txt and cmn.txt) every pair whose
first or next word holds a blocked Chinese word; native/rime/data.sh compiles them.
"""
import json
import os
import sys

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
ASSETS = os.path.join(ROOT, "android", "assets", "im")
EXTRA = os.path.join(os.path.dirname(os.path.abspath(__file__)), "blocklist-extra.tsv")
PREDICT = os.path.join(ROOT, "native", "rime", "predict")
# LDNOOBW's file for each input method, by its language; the rest have none there.
SAME = ["ar", "cs", "da", "de", "en", "eo", "es", "fa", "fi", "fr", "hi", "hu", "it", "ja", "ko", "nl", "pl", "pt", "ru", "sv",
        "th", "tr"]
SOURCE = {**{code: code for code in SAME}, "nb": "no", "nn": "no", "yue-yale": "zh", "yue-jyutping": "zh", "zh-pinyin": "zh",
          "zh-cangjie": "zh", "zh-quick": "zh", "zh-zhuyin": "zh", "uk": "uk"}
NOTICE_HEAD = "Blocked words"
CREDIT = ("Blocked words (blocked.txt), never offered on the bar: LDNOOBW's List of Dirty, Naughty, Obscene and Otherwise Bad "
          "Words ({file}, https://github.com/LDNOOBW/List-of-Dirty-Naughty-Obscene-and-Otherwise-Bad-Words), CC BY 4.0, "
          "with TitanKeys' own short list (tools/im/blocklist-extra.tsv).")
CREDIT_OWN = ("Blocked words (blocked.txt), never offered on the bar: TitanKeys' own short list (tools/im/blocklist-extra.tsv).")


def read_list(path):
    words = []
    if os.path.isfile(path):
        for line in open(path, encoding="utf-8"):
            word = line.strip()
            if word and not word.startswith("#"):
                words.append(word.lower())
    return words


def extra():
    """The hand list: words to block by language, and words of LDNOOBW's that are everyday ones, kept ("!zh" lines)."""
    out, keep = {}, {}
    for line in open(EXTRA, encoding="utf-8"):
        if line.startswith("#") or "\t" not in line:
            continue
        code, word = line.rstrip("\n").split("\t", 1)
        if code.startswith("!"):
            keep.setdefault(code[1:], set()).add(word.strip().lower())
        else:
            out.setdefault(code, []).append(word.strip().lower())
    return out, keep


def write_notice(folder, credit):
    path = os.path.join(folder, "NOTICE")
    if not os.path.isfile(path):
        return
    parts = [p for p in open(path, encoding="utf-8").read().strip("\n").split("\n\n") if not p.startswith(NOTICE_HEAD)]
    if credit:
        parts.append(credit)
    open(path, "w", encoding="utf-8", newline="\n").write("\n\n".join(parts) + "\n")


def main():
    ldnoobw = sys.argv[1]
    own, keep = extra()
    zh = set()
    for ident in sorted(os.listdir(ASSETS)):
        folder = os.path.join(ASSETS, ident)
        if not os.path.isfile(os.path.join(folder, "im.json")):
            continue
        code = SOURCE.get(ident)
        listed = read_list(os.path.join(ldnoobw, code)) if code else []
        words = sorted(set(listed + own.get(code or ident, [])) - keep.get(code or ident, set()))
        path = os.path.join(folder, "blocked.txt")
        if not words:
            if os.path.exists(path):
                os.remove(path)
            write_notice(folder, None)
            continue
        if code == "zh":
            zh.update(words)
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write("# Never offered on the bar: see NOTICE. tools/im/blocklist.py writes it.\n")
            f.write("".join(word + "\n" for word in words))
        write_notice(folder, CREDIT.format(file=code) if listed else CREDIT_OWN)
        print(ident, len(words))
    # Rime's next words: a pair whose first or next word holds a blocked word goes (屌 你老, 你條 仆街, 我都想 自殺).
    for name in ("yue", "cmn"):
        path = os.path.join(PREDICT, f"{name}.txt")
        lines = open(path, encoding="utf-8").read().splitlines()
        # As the keyboard matches (BlockedWords): one character only as the whole word, longer ones inside a phrase too.
        def blocked(field):
            return field in zh or any(len(word) > 1 and word in field for word in zh)
        kept = [line for line in lines if not any(blocked(field) for field in line.split("\t")[:2])]
        open(path, "w", encoding="utf-8", newline="\n").write("\n".join(kept) + "\n")
        print(f"predict {name}: {len(lines) - len(kept)} pairs dropped")


if __name__ == "__main__":
    main()
