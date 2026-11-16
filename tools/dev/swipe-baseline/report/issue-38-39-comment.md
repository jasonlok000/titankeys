**Engine 2.1 pilots for #38 and #39, 2026-11-10.** On the PC with the app's own `TypedPredictions` and next-word model (tools: `tools/dev/swipe-baseline/tools/CompletionPilot.java`, `history.py`, `score_pilot.py`). Each word was typed one letter at a time. "Letters typed" is how many letters it took before the word was in the top 3 (fewer is better). Each word's history is only what the owner kept before it, from other fields. Parts:
- tuning: Nov 9, not held back
- held: engine 2.0's frozen 540
- new: the raw log after engine 2.0's training data

Counts only.

| Letters typed | 2.0.0 | Every word ranked, frequency weighted by letters (#39 idea 1) | Learned pairs after letters (#39 idea 2) | **Personal counts ×0.5 (#38)** | Personal counts ×1 | Pairs + counts ×1 |
|---|---|---|---|---|---|---|
| Everyday, tuning (597) | 2.039 | 2.025 | 2.010 | **1.980** | 1.972 | 1.953 |
| Everyday, held (302) | 1.964 | 1.924 | 1.921 | **1.894** | 1.864 | 1.851 |
| Everyday, new (549) | 2.115 | 2.077 | 2.095 | **2.044** | 2.031 | 2.027 |
| Prompted, tuning (268) | 1.354 | 1.373 | 1.362 | **1.366** | 1.384 | 1.399 |
| Prompted, held (136) | 1.360 | 1.382 | 1.382 | **1.368** | 1.404 | 1.426 |

Per word, personal counts ×0.5 against 2.0.0:
- Everyday, held: 25 words need fewer letters, 9 need more (sign z +2.7).
- Everyday, new: 45 fewer, 18 more (z +3.4).
- Prompted, held: 2 and 2.

×1 gains about the same on everyday text but costs prompted text. Pairs after letters help at 3 letters on new text (top 1: 38.8 → 44.9) but cost 4-5 points at 1 letter on prompted text.

Top 3 by letters typed, everyday, new text, 2.0.0 → personal counts ×0.5:
- 1 letter: 51.2 → 51.2
- 2 letters: 65.9 → 70.0
- 3 letters: 77.1 → 80.0
- 4 letters: 79.1 → 81.4

No number of letters is worse.

**Decision (ship gate):** personal counts ×0.5 ship, in typed completions only.
- The count of times each word was kept comes from the learned pairs that #27 already stores (every kept word is the second word of a pair), so there is no new table and nothing new to clear.
- Each kept word that starts with the typed letters gets min(3, 0.5 × ln(1 + times kept)) on top of its log chance.
- Learned pairs after letters, and frequency weighting by letters, do not ship.
- #38's swipe part does not ship: use counts in the swipe ranker gave nothing (76.0 / 84.0 without, 75.8 / 84.4 with).

**About the drop after 3 letters in #39:** in this PC replay, 2.0.0 is at 77.4% top 3 after 3 letters on everyday text. The phone's 73.3% included the fallback to the FUTO context model when the next-word model holds no word. The phone replay (`completion_phone.sh`) gives the final numbers.
