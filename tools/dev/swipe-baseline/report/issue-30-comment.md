**Engine 2.1 investigation, 2026-11-10: touch size (pressure) in swiping.** Data: raw log of 2026-11-10, 829 swipes with touch size, 440 of them kept words that no model trained on. Counts only. Tools: `tools/dev/swipe-baseline/tools/size_battery.py`, `size_reading.py`, `size_letters.py`, `contact_size.py`.

**The reading (settled for #52):** the sensor step is `round(getSize() * 255)` in the app, and `sz / 1.125` in the raw log (every logged value is an exact multiple of 1.125). Steps seen: 1 (6,759 points), 2 (28,471), 3 (15,714), 4 (3,011), 5 (159), 6 (2).

**1. Size depends on where the finger is.** Mean step: top row 1.5-1.9, middle 1.9-2.8, bottom 2.4-3.1. It also falls by 0.5-1 step from left to right. Every test below corrects for position first.

**2. Within a swipe, pressure barely changes.**
- At the letters you mean: -0.08 steps.
- Corrected for position, intended letters are no larger than the path between them: +0.03 steps, 181 swipes against 165 (chance).
- Turns and slowdowns: no change.
- The clear pattern: a fade of about 0.2 steps in the last tenth, as the finger lifts.

**3. Across swipes:**
- The lightest quarter, engine 2.0 live: 80.9% first, against 68-71% for the other quarters.
- Pressure rises later in the day: rho +0.18, z +3.8.
- Heavier swipes end lower: rho +0.19, z +3.9.
- Raw size against distance from the intended keys: rho +0.20, z +4.2.
- Pressure level against right first: rho -0.09, z -1.9.

**4. Broken swipes (n = 105): pressure fades before the finger is lost.**
- 50-100 ms before the end: -0.10 against +0.06 for whole swipes.
- Last 50 ms: -0.37 against -0.24.
- For swipes that end on the top row, the end fade goes with a break: rho -0.15, z -3.0.
- The overall pressure level of broken swipes is no different.

**5. Using it:**
- Mistouches: AUC 0.50-0.57, and 0% of accidents caught at a 1% cost to real words.
- Reshaping swipes by pressure before the encoder: all worse. Plain 67.5 / 75.2 first / top 3; drop light tail 55.2 / 67.5; drop light head 60.0 / 69.3; pressure-stretched time 64.8-65.2 / 73.0-73.6.
- Pressure features in a ranker, 5 time-ordered folds: 69.1 → 69.3 first, 7 wins and 6 losses. No gain.

**6. The owner's flat-finger idea** (a flat finger is read at the middle of the contact, not where it aims). Tool: `size_offset.py`.
- Raw numbers: the read point falls below the intended key more as the touch grows. All letters: -0.02, +0.14, +0.18, +0.28 rows for steps 1, 2, 3, 4+. Top-row keys: +0.15, +0.45, +0.77, +0.98 rows. P is read 1.5-3.4 keys to the left, worse when larger.
- Most of that is position, since size grows lower on the pad. Compared at the same read spot, it remains for top-row keys: a larger-than-usual touch was aimed higher (rho +0.27, z +8.4, n = 993) and slightly further left (z -2.4). For all letters it is weak (z +2.5); for edge keys, not significant.
- The size is small: about 0.1 row between larger and smaller touches.
- Correcting for it does not help. Moving points up by their extra size, near the top, fitted on the earlier half: the best fit (+1.4 first there) lost on the later half, 70.5 → 69.1 first (6 wins, 9 losses). Stretching swipe ends outward by size, and shifting by raw size, were also no better. The encoder was fine-tuned on the owner's own swipes, so it has already learned their average offset.

**7. Stroke joining with the end fade.** Tool: `join_pressure.py`.
- Of 109 labelled pairs, the keyboard made 100 right joins, 6 right separates and 3 wrong joins, and missed no break.
- The fade does not separate them (rho -0.09, z -0.9). Joining is already nearly always right in this log, so there is nothing for pressure to fix.

**Result:** touch size is a real signal of pressure and finger posture, but none of the seven uses tested raises accuracy on swipes that no model saw. Nothing from it ships in engine 2.1 (ship gate). The logging stays, and the tools can test it again on more data.
