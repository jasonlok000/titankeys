# #73 study: the apostrophe fix on swiped words

The owner asked for the settle-later apostrophe fix to cover swiped words too: "why doesn't it apply to swipes? it should. same with other apostrophe rule." A swipe picks its word from the words before it only, and "its", "your" and "were" are often settled by the words after ("its going"). This checks the owner's swiped words against the fix, as it stands for typed words. It's now built.

## Answer

**Yes, at the same margins as typed words.** No swiped word was changed wrongly. The highest margin for a word that should stay was −8.4, far below today's 1.0, so swipes need no extra margin.

## How it works in the keyboard

- A swiped word waits for the 2 words after it, or a mark, like a typed one. The words after can be typed or swiped.
- Only the swipe's own pick waits:
  - a word chosen from the bar never changes;
  - a swiped word fixed by hand never changes;
  - a swipe that goes on into a longer word is read again, and the old word no longer waits.
- The rest is as for typed words:
  - it only adds the apostrophe;
  - Backspace right after puts the swiped word back;
  - it's off in the guide;
  - it's under Settings > What gets fixed > Use context for its, lets, your….
- The swipe engine's own look-alike check is unchanged. It can swap "were" and "we're" both ways once the next word is in. When it changes a word first, that word no longer waits.
- The bar's "other form" offer stays in typing only, as the owner asked on 2026-11-14. This study gives no reason to add it for swipes.

## Data and method

- **The owner's logs** (`.dev/swipe-baseline`, private, counts only): every swiped word in the apostrophe study, judged by reading the whole context.
- **No other data fits.** The swipe recordings (`corpus.jsonl` and the others) have no text after the word, and the fix needs that text. There's no newer log on the computer, and getting one needs the phone.
- **The fix's rule:** the same as for typed words (`ApostropheContext.kt`):
  - the on-device DistilGPT-2;
  - the window back to the last sentence mark, then 2 words after;
  - 1.0 nats, or 2.0 with Only fix when sure;
  - "were" needs 2.0 more.
- **Right and wrong:** a change is right only if the judged meaning is the apostrophe form. A swipe that misread another word ("hell" for "heel") counts as wrong if it changes.
- **Tool:** [`73-swipes-study.py`](73-swipes-study.py). It prints counts and margins only, never the text.

## Numbers

**112 swiped words:**

- 93 with an apostrophe already. The fix never touches those.
- 1 that the fix doesn't look at.
- 18 that it looks at:

| put in | meant | count | margins, in nats |
|---|---|---|---|
| its | it's | 1 | +4.1 |
| their | their | 4 | −18.8 to −11.7 (3 scored) |
| there | there | 5 | −14.7, −12.0 (2 scored) |
| there | misread or unclear | 1 | too thin |
| your | your | 4 | −20.1 to −11.5 |
| well | well | 1 | −13.1 |
| hell | misread | 2 | −9.3, −8.4 |

- 13 were scored. The other 5 had too few words around them.
- The owner kept 10 as put in, 1 with the apostrophe and 1 as another word. 6 were unreadable.

**Changes:** 1 right and 0 wrong at every margin from 0 to 3, today's 1.0 and Only fix when sure's 2.0 among them. The one right change is the "its" the owner meant as "it's".

## Limits

- 18 words is a small sample, and none of them is a swiped "were". The swipe almost always gets the apostrophe form right itself: 93 of the 112.
- The margin isn't swipe-specific: it scores the text, whichever way it was entered. The typed-word studies back it up: 0 wrong changes in 277 judged cases, and 0 for "were" in 42 sentences and 535 triples ([73-were.md](73-were.md)).
- With newer logs, re-run the tool on them. If a swiped word that should stay scores above about 0.5, give swipes extra margin.
