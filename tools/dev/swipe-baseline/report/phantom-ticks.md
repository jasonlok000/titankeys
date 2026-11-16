# Phantom buzzes and the first swipe tick

Master's request, 2026-11-10. Counts only.

## Method

- **Data:** raw-log-17, 12,979 contacts.
- **Replay:** the contacts were replayed through the keyboard's own Contact class, with the swipe tick as it is today: the first tick plays at 0.6 keys from landing (#30).
- **Tools:** `tools/HapticReplay.kt` (its third argument is the first-tick distance) and `tools/tick_wait.py`.
- **Numbers:** `report/tick-wait.json`.
- **Phantom buzz:** the touchpad ticked and nothing was typed. Swipes with no recognition are counted apart, because the real keyboard doesn't tick in the Sym panel or with Alt on.

## Today

- **Phantom buzzes:** 145, or 3.9 per 100 swipes. That is half the 7.7 per 100 before #30's 0.6-key wait.
- **Ticks:** 141 of the 145 tick once.
- **Key presses:** 117 of the 145 (81%) have a physical key pressed during the contact. The finger slides on the pad while it pushes a key.
- **Start speed separates them best:**
  - meant swipes and flicks reach 0.6 keys within 85 ms of landing (median; 90th percentile 144 ms)
  - phantoms take 200 ms (median; 25th percentile 134 ms)
  - after a key is pressed, phantoms follow a median of 166 ms later

## First tick from a fixed distance (the owner's idea)

"Meant input losing every tick" is out of 4,674 swipes and flicks that vibrate. "Later" is how much later the first tick plays for meant input, as median / 90th percentile.

| First tick from | Phantoms silenced | Meant input losing every tick | Later |
|---|---|---|---|
| 0.8 keys | 30 | 37 | 10 / 19 ms |
| 1.0 | 49 | 103 | 19 / 29 ms |
| 1.5 | 75 | 497 (223 delete flicks) | 37 / 67 ms |
| 2.0 | 98 | 1,108 (24%; 638 flicks, 262 kept words) | 48 / 162 ms |
| 2.5 | 107 | 1,816 | 67 / 238 ms |

At 2 keys, a quarter of real swipes and flicks would lose their tick. Most of those are short delete flicks and short words.

## Rules that use more than distance

| Rule | Phantoms silenced | Meant input losing every tick | Later |
|---|---|---|---|
| **0.6 keys if reached within 160 ms of landing, otherwise 2 keys (built)** | **54 (37%)** | **67 (1.4%)** | 0 |
| 0.6 keys within 130 ms, otherwise 2 keys | 70 | 146 (3.1%) | 0 |
| First tick held 50 ms, and a key press cancels it | 28 | 0 | +50 ms on every first tick |
| First tick held 80 ms, and a key press cancels it | 41 | 0 | +80 ms |
| The 160 ms rule, and a hold of 50 ms that a key press cancels | 70 | 67 | +50 ms |
| The 160 ms rule, and a hold of 80 ms that a key press cancels | 77 | 67 | +80 ms |
| After 3 s with no swipe or key, 2 keys; otherwise the 160 ms rule | 73 | 213 | 0 |

## Built

The first rule in the table above is built, on branch typing-2.1:

- A finger that reaches a swipe's distance quickly ticks exactly as before.
- One that sets off slowly, as from resting on a key, ticks only once it has gone 2 keys.
- This is the owner's 2 keys, applied only to the slow starts where the phantoms are.

**Optional next step:** hold the first tick for 50 to 80 ms and let a key press cancel it. That takes phantoms from 54 to 70–77 silenced, at the cost of a slightly later first tick on every swipe. Whether that delay is worth it is the owner's call after feeling it.
