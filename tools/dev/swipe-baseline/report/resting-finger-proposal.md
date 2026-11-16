# Resting fingers: a soft size rule (proposal, no code)

**Evidence** (#30, `tools/rest_rules.py`, `tools/real_sizes.py`):
- The rule catches a touch whose sensor step reaches 6, or whose landing step is 2+ above the usual step at that spot.
- It catches 15 of the 29 resting touches that TitanKeys read as input.
- In every log with sizes (Nov 9 evening to Nov 10 midday), it catches none of the owner's 811 kept swipes, 85 taps or 48 deleted swipes, and 2 of 775 touches that typed nothing.
- It cannot catch short light touches (step 2, about 0.25 s), which look exactly like real taps.
- The sizes come from one day. Real swipes reach step 5, one step below the rule.
- The touchpad reports one pointer only, so two thumbs show up as one contact, and the logs cannot show multi-finger moments directly.

## What the user sees

- **A caught swipe is not typed.** Its best word appears on the strip, in the first place, marked as an offer (for example in the strip's dimmer text colour). One tap, or the usual pick gesture, types it as if it had been swiped. Typing anything else, or touching again, drops the offer silently. Nothing is deleted, because nothing was typed.
- **A caught tap is left out silently.** A single letter offered on the strip would be noise, and a resting thumb's taps are almost always unwanted. If the owner meant it, they tap again with a lighter touch.
- **A caught touch that would move the cursor or delete** (cursor control, delete swipe) is left out. Those must never happen by accident.
- **No sound or vibration** for a caught touch, so a resting hand stays quiet.

## Recovering a wrong catch

- **Swipe:** one tap on the offered word. Its learning, joining and look-alike checks run as for any swipe.
- **Tap:** tap again.
- If the user picks offered swipes several times in a short time, the rule is catching their real typing. TitanKeys should then raise its threshold for them (see below) rather than keep asking.

## With the light-touch taps setting

- The size rule only judges touches that would type, so it works the same with the setting on or off.
- With tap keys on, light taps are wanted input. They are step 1-2 and the rule never catches them, so the two do not conflict.
- The rule must not slow taps down. It decides at lift-off, from sizes TitanKeys already has, so no delay is needed.

## Fixed table, or learned for each user?

- **Recommendation: learn it, starting from the table.** The usual step depends on the sensor, the owner's grip and their thumbs. A user with heavier thumbs would be caught by the fixed table.
- How:
  - Keep a running mean step for each of the 40 touchpad cells, from touches that typed a kept word.
  - Start each cell from the #52 table.
  - Catch only if the landing step is 2+ above the learned mean, or the step reaches 6 and is 2+ above that user's own 99th percentile.
  - Store it with the other learned data, cleared by "Clear learning".
- It needs about 200 kept swipes to settle. Until then, use the fixed table.

## Before building

1. A few more days of logs with sizes, to see whether real touches ever reach 6, including two-thumb typing.
2. One recording of deliberate flat-thumb and two-finger swiping, labelled as real, to set the upper limit.
3. Then a phone replay: count caught real swipes and taps (must be 0 or nearly) against caught resting touches.
