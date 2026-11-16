# Mistaps and mistypes in real use (#30, owner's steer), 2026-11-10

Data: raw-log-12, which holds every log since Nov 9 07:02 (Nov 9 has no touch sizes; sizes start with 2.0.0 that evening). Every touchpad contact was classified. Counts and features only. Tool: `tools/mistakes.py`; numbers: `report/mistakes-v12.json`.

## What happened, per day

| Day | Swipes | Real (kept as shown) | Mistypes (changed within 5 s) | Mistaps (deleted within 5 s, nothing kept) | Changed later | Removed later |
|---|---|---|---|---|---|---|
| Nov 9 | 2,277 | 1,250 | 617 (27.1 per 100) | 116 (5.1 per 100) | 199 | 95 |
| Nov 10 (to 12:00) | 844 | 467 | 243 (28.8 per 100) | 40 (4.7 per 100) | 87 | 7 |

"Changed later" is rewording rather than a mistake.

- **Mistypes are the engine's wrong words.** They match its first-choice error rate on everyday text. They look exactly like real swipes (every AUC 0.48-0.52), so they are an accuracy problem (#31), not a touch problem.
- **Mistaps are about 5 per 100 swipes, steady across both days.** They are shorter than real swipes (median 3.5 keys of travel against 7.0; 34 of 156 under 1.5 keys) and come a little later after the previous swipe (1.3 s against 0.7 s). Nothing else separates them:
  - travel: AUC 0.36
  - duration: 0.40
  - largest size: 0.46
  - landing size over usual: 0.51
  - size jump: 0.49
  - time since a key: 0.55
- **Touchpad taps type nothing.** The tap-keys setting is off, so no text is ever committed by a touchpad tap (the log has 7,404 key commits, 3,079 swipe commits and 0 tap commits). Tap mistaps cannot happen with this setting.

## Typing on the keys against intentional swiping (point 4)

- 2,165 contacts overlapped a physical key press: the finger on the pad while pushing keys.
- **None of them became a swipe.** No real, mistapped or mistyped swipe overlapped a key press. TitanKeys' after-key rules already keep key typing out of swiping.
- These contacts barely move (median 0.01 keys) and their size does not jump during the push (step range 0). Against kept short swipes (under 3 keys): travel AUC 0.09, speed 0.06, size 0.45. Movement separates them, and size adds nothing.
- So there is nothing to fix here today. The resting-touch recording's phase 6 contacts that pressed keys physically register as key presses, which is unaffected: the size rule would only judge touchpad input.

## The resting recording against real use

- The recording, which the owner says was exaggerated, had 29 resting touches read as input out of 344 (8%).
- Real use: 156 swipe mistaps in about 3,250 swipes over a day and a half.
- The size rule (step 6+ or landing 2+ over usual) caught 15 of the recording's 29 and none of the real typing. **But none of the 40 real-use mistaps with sizes reaches the rule's sizes** (median largest step 3, landing over usual +0.23). Real-use mistaps are not resting thumbs: they are light, short swipes that look like real ones.

## Recommendation

1. **Do not build the size rule yet.** It would remove resting-thumb input (rare in real use, frequent in the recording) and does not touch the 5-per-100 mistaps of real use. Keep it for later, once a few days of logs show how often resting thumbs actually type.
2. **For the real mistaps, make recovery cheaper rather than detection.** No measured feature separates them. A one-flick undo for the last swipe (from the Engine 2.0 design) fixes a mistap or a mistype in one move. Each undo is also a free label, which would let a learned "intent" score be trained later on the owner's own mistakes.
3. **Mistypes belong to accuracy (#31).** At 27-29 per 100 swipes they are the larger cost, about six times the mistaps.

## Other findings

- A mistap comes about 1.3 s after the previous swipe, against 0.7 s for real swipes. Mistaps happen in pauses, not mid-flow.
- Real swipes start higher on the pad (median row 0.9 against 1.2 for mistaps). Too weak alone (AUC 0.50), but worth keeping as a feature.
- About 2,350 contacts typed nothing and touched no key (median 0.13 s, 1.8 keys). TitanKeys already ignores these.
