# Delete flick from a zone over I, O, M and Sym

Master's request, 2026-11-10. Findings only, no code. Tool: `tools/delete_zone.py`. Numbers: `report/delete-zone.json`.

## Data and method

- **Logs:** all of the owner's raw logs, from titankeys-raw-log and raw-log-2 to raw-log-14. Each swipe is counted once, even though exports overlap.
- **Sample:** 20,977 swipes. Every log has today's delete tuning (versions 1.2.0 and 2.0.0).
- **How each swipe is sorted, by what followed it:**
  - **word:** a word was recognised, and nothing removed it before the next word (12,317).
  - **removed:** a word was recognised, then Backspace or a delete flick removed it (2,670). Missed delete flicks are in here, together with wrong words.
  - **no word:** nothing was recognised (5,990). Today's 4,921 delete flicks are in here. The rest are mostly swipes followed by more touches, not delete attempts.
- **The new rectangle:** the same size as today's shaded zone (.24 wide by .445 high), moved left to x .68–.92 and y .175–.62. It covers I and O (the lower strip of the top row), K, L, M and Sym, plus edges of U, J and N.
- **Today's rule** reads 4,921 delete flicks. It wrongly deletes 19 kept words, and fires on 34 words that were removed anyway.

## Results

"Kept words hit" counts real words that the rule would delete. "Possible missed flicks" counts swipes the new rule adds that were later removed. That number is an upper bound: some of those swipes were just wrong words.

| Rule | Real flicks lost | Kept words hit (start keys) | Possible missed flicks |
|---|---|---|---|
| A: the rectangle replaces today's zone (today's edge rules) | **2,085 of 4,921 (42%)**, the flicks that start on the right half of Backspace | +206 | +64 |
| B: today's zone or the rectangle (edge rules) | 0 | **+206**: I 67, O 47, K 33, M 33, N 14, J 9, U 3 | +64 |
| C: B, but level, like today's inner box (tilt ≤ .1, wander ≤ 1.1) | 0 | +69: I 41, K 13, J 9, O 6 | +27 |
| D: C, and no starts on I or O | 0 | +22: K 13, J 9 | +17 |
| E: D, and at most 6 keys long and 400 ms | 0 | +22: K 13, J 9 | +10 |

The swipes with no word that each rule adds (98 for A and B, 83 for C to E) are left out of the table. They start mostly on J and K and are followed by more touches. None of them is a clear delete attempt, so I counted them as neither cost nor gain.

## Findings

- **Moving the zone (A) is not possible.** About two in five of the owner's real delete flicks start on the right half of Backspace, outside the moved rectangle.
- **Adding the rectangle with today's rules (B) is too costly.** It would wrongly delete 206 kept words, 1 in 60 of all kept swiped words and 11 times today's rate, to win at most 64 flicks.
  - Many words start on I, O or M and run left along a row. Examples are "in", "it", "on", "of", "or", "my" and "me", whose next key is to the left.
- **The level variants still cost more than they gain.**
  - C: 69 words lost for at most 27 flicks gained.
  - D: 22 for at most 17.
  - E: 22 for at most 10.
  - Even the safest variant costs about as many real words as the flicks it might save.
- **Few flicks are missed today.** Fewer than 64 possible misses start in the new area, against 4,921 flicks that work. The current zone already catches the owner's real flicks.

## Recommendation

- Keep today's gesture zone.
- If the guide should show the flick over I, O, M and Sym, change only the drawing. The real start zone stays where it is.
- If the owner still wants a bigger zone, the only defensible version is D or E: the new rectangle without I and O, level and short. Even then, expect about one wrongly deleted word for each recovered flick.
