**Engine 2.1 retraining and refreshed stats, 2026-11-10** (raw log of 2026-11-10; counts only).

**Data:**
- 3,062 swipes labelled, 922 of them after engine 2.0's training data (829 with touch size). 550 of those are first tries.
- Splits, fixed once (`tools/split_v21.py`):
  - held: engine 2.0's 540, frozen by stroke time (`private/frozen-540.json`)
  - test: the later half of the new first tries (283), never trained on
  - train: everything else

**1. Engine 2.0 on new text, paired** (the same 550 new first tries on the phone, both orders):

| | First | Top 3 |
|---|---|---|
| FUTO order | 60.0 | 72.5 |
| Engine 2.0 | 70.0 | 76.7 |

- First choice: engine 2.0 wins 63 and loses 8 (z +6.5). Top 3: 26 and 3.
- The live numbers looked weak only because the new text is harder: the FUTO order falls from 69.6 on Nov 9 text to 60.0 here.
- Speed: 78 ms median per swipe.

**2. Retraining with the new strokes** (CleverKeys fine-tune on the 3090 and the ranker; `tools/gpu/ck_v21.py`, `train_ranker.py` with `SPLITS`):
- Fine-tune length: 250 steps. Both folds on the train part chose it over 400.

| Paired against engine 2.0 | First, 2.0 → 2.1 | Wins / losses | Top 3, 2.0 → 2.1 | Wins / losses |
|---|---|---|---|---|
| Twin, frozen 540 | 78.9 → 78.5 | 6 / 8 | 85.7 → 86.5 | 7 / 3 |
| Twin, clean test | 71.4 → 71.0 | 5 / 6 | 79.9 → 78.4 | 0 / 4 |
| Release-style (540 included), clean test | 71.4 → 70.0 | 2 / 6 | 79.9 → 79.5 | 1 / 2 |

**Decision (ship gate):** the retrained models do not ship; engine 2.0's encoder and ranker stay. About 280 more labelled strokes are not enough to beat it. The learning curve suggested gains would need many more labelled strokes.

**3. Also done in engine 2.1:**
- Touch size (#30): no use raised accuracy.
- Personal counts in completions (#38, #39): ships.
- ONNX Runtime 1.23.2 with 16 KB pages (#36): built and tested; phone parity pending.

**Not done:** the FUTO engine parameter sweeps (`CANDIDATES`, `SHIFT_RIGHT`, `SWIPE_STRETCH`, `FIRST_WORD_CONTEXT`). They need about one phone run each, plus a ranker retrain per value.
