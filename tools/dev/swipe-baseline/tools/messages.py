#!/usr/bin/env python3
"""The owner's own messages, in time order: each field's final text from the raw log, as the keyboard saw it. Prompted
test passages and recorder sessions are left out. Private: the output stays in private/ on this PC.
Usage: messages.py raw-log.jsonl [raw-log.jsonl ...] out.jsonl"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rawlog as R


class FieldTexts(R.Replayer):
    def __init__(self, events):
        super().__init__(events)
        self.finals = []
        self.prompted = False

    def on_start(self, e):
        self.keep()
        super().on_start(e)
        self.started = e['t']
        self.prompted = False

    def on_mark(self, e):
        super().on_mark(e)
        if e['kind'].startswith('style') or e['kind'] in ('start', 'done', 'redo', 'skip'):
            self.prompted = True

    def on_end(self, e):
        self.keep()

    def keep(self):
        text = self.text().strip()
        if text and not self.prompted and self.field >= 0:
            if not self.finals or self.finals[-1]['field'] != self.field:
                self.finals.append({'field': self.field, 'wall': self.clock.wall(getattr(self, 'started', 0)), 'text': text})
            else:
                self.finals[-1]['text'] = text


*paths, out_path = sys.argv[1:]
rows = []
for path in paths:
    rep = FieldTexts(R.load(path)).run()
    rep.keep()
    rows += rep.finals
rows.sort(key=lambda r: r['wall'])
# A field reopened with the same text is one message.
seen, unique = set(), []
for r in rows:
    if r['text'] not in seen:
        seen.add(r['text'])
        unique.append(r)
with open(out_path, 'w', encoding='utf-8') as f:
    for r in unique:
        f.write(json.dumps(r, ensure_ascii=False) + '\n')
words = sum(len(r['text'].split()) for r in unique)
print('messages', len(unique), 'words', words)
