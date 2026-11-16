# Swipe engine 2.0 proposal

> **Built, 2026-11-09 night.** Engine 2.0 is in branch `tk/swipe-join`, uncommitted. On held-back word positions it takes first choice from 71.7% to 78.9% and top 3 from 81.5% to 85.7%. It also puts a stronger next-word model into next-word suggestions and typed autocorrect, and logs contact size. See "Engine 2.0 as built" at the end for what shipped, every choice made and the evidence for it.

Written 2026-11-09, from the baseline in `BASELINE.md`. Everything marked **measured** ran on your real strokes. Everything marked **target** has not been tested yet. Results are in `report/`.

## The short answer

**90% first and 95% top 3 cannot be reached by tuning what exists.** Stroke repair, extra retrieval, a learned ranker, a different open model and next-word correction were all tested on your strokes. Together they reach about 80% first and 89% top 3 on prompted text, and they barely move everyday text.

The one large lever left was **a recognition model trained for the Titan touchpad**. A first pilot has now run, and it does not yet justify a full training run: phone swipes do not transfer on their own, and simulated touchpad damage made things worse. Fine-tuning on your real strokes did help, but mostly for words it had already seen. The next step is more real strokes from your keyboard, not more GPU time. See "Pilot result" below.

**95% fewer accidental inputs cannot be demonstrated from this log.** Stroke features alone do not separate accidents from real words, and the log has no clean accident labels. The plan collects the two missing things and changes how uncertain strokes are handled.

## What was measured

Prompted text is 404 words. Everyday hand-corrected text is 323 words with confirmed tests removed. Rows that say "all 344" include those tests.

### Can the right word be found at all?

The right word must be retrieved before any ranking can pick it. This caps top-3 accuracy.

| Source | Prompted | Everyday (all 344) | Your 300-word recording |
|---|---|---|---|
| Current decoder | 87.9% | 82.0% | 80.0% |
| CleverKeys' open model, same dictionary | 87.1% | 77.9% | 74.7% |
| Current decoder, 2 readings of the stroke | 90.1% | 84.0% | 81.7% |
| Current decoder, 3 readings | 91.1% | 84.3% | 82.3% |
| Current decoder, 12 readings | 92.1% | 86.0% | 83.3% |
| **Everything combined** | **93.3%** | **87.8%** | **85.0%** |

Two independent models stop at nearly the same place, so the limit is mostly the strokes as the touchpad reports them. The most valuable second reading shifts the stroke 0.6 keys to the right, which matches the leftward bias in the baseline.

### Each lever on its own

| Lever | Result | Verdict |
|---|---|---|
| Join a stroke to the next one, gap 150 ms or less | Fixes 10 of 13 wrong words (6 prompted, 4 everyday). Would cost 10 right words their first place if always joined | **Build**, with a check that compares both readings |
| Join over gaps of 151-400 ms, or join backwards | Breaks far more than it fixes | Drop |
| Learned ranker, tested on words it never trained on | Prompted 78.2 / 87.4. Everyday 67.4 / 79.9 | Promising but unstable, see below |
| Same ranker, trained on other sets | Prompted 79.2 / 85.9. Everyday 62.8 / 74.4, **worse than today** | Do not ship yet |
| Same ranker, trained only on earlier text | Later everyday text: 66.7 / 79.0 against today's 71.0 / 80.4, **worse** | Needs far more data |
| Personal words and past corrections, added to that ranker | +2.1 / +0.7 points, 138 words | Small but real |
| Next word corrects the previous word, given the true next word | +0.8 points at most, none on held-back words | Not with this language model |
| Different open engine (CleverKeys) | Prompted 70.8 / 82.4 against FUTO's raw decoder at 75.5 / 83.7. On your isolated 300 words, 64.3 / 73.0 beats the raw decoder (60.7 / 72.0) but not the full current engine (67.3 / 75.0) | No swap |
| Whole-dictionary shape matcher | 53.7% first, 67.8% top 3 alone | Useful only as an extra source |

CleverKeys ran without a language model, which favours FUTO on sentences. It also got its best frequency weight for each set, which favours CleverKeys.

### Dictionary

- The word list is dated 2014 and has 147,068 words. Only 12,397 have an outline for the shape matcher, and the context model knows 31,758.
- Prompted text: every word was in the dictionary. Everyday text: 7 words were missing, all 7 failed and none could ever have been offered, which costs about 2 points.
- 22 of 120 everyday misses were words with no outline, and 17 were outside the context model's vocabulary.
- Accuracy falls steeply with rarity: 87% first at the top frequency band, 54-56% at the bottom.
- 46 of 120 everyday misses are on 20 words that fail again and again.

### Mistouches and accidental input

- **Today's touch fixes:** replaying the log through the latest touch code, 549 of 551 kept words still work. But 83 of 86 likely accidental words still register as swipes, and all 20 flicks that deleted a right word still delete. Those fixes targeted other problems.
- **The list is contaminated.** After removing the confirmed tests and the 10:12-11:05 stretch that looks like delete testing, 50 likely accidents remain in 1,006 strokes. Only 9 are from prompted text, where we are sure.
- **Stroke features can't separate them.** A trained classifier reaches AUC 0.77. Catching 95% of them would also flag 65% of real words. The best simple rule (under 150 ms and under 2.5 keys) catches 16% and costs 2% of real words.

## Follow-up measurements

### Below the top 3

| First tries | Shown first | Shown 2nd or 3rd | Not shown | Of those not shown: rerank dropped it from decoder's top 3 | Decoder rank 4-100 | Decoder never listed it |
|---|---|---|---|---|---|---|
| Prompted (404) | 309 | 26 | 69 | 11 | 10 | 48 |
| Everyday (344) | 224 | 40 | 80 | 9 | 14 | 57 |
| 300-word recording | 202 | 23 | 75 | 10 | 10 | 55 |

A longer strip would rescue few words. Most unshown words were never found at all. Of those, extra readings find 16 of 48 prompted, 11 of 57 everyday and 5 of 55 in the recording.

### Next-word prediction as a shortcut

| Right word in the top 3 before swiping | Prompted | Everyday |
|---|---|---|
| Phone's current model | 24.0% | 16.6% |
| GPT-2 Medium (1.5 GB test model, too large for the phone) | 53.7% | 23.8% |

Of the words a swipe got wrong, the strong model has 37.9% (prompted) and 12.5% (everyday) in its top 3 before the swipe. Using the stroke's start and end raises those to 41.1% and 22.5%. It is worth having alongside swiping, mainly for common words, but it cannot carry the targets. Everyday text is hard to predict.

### Edge blind spots

| Words | Right first | Top 3 |
|---|---|---|
| Containing P (91) | 54.9% | 59.3% |
| Containing none of Q, A, P (637) | 71.9% | 79.7% |

Only 18 of 41 strokes starting on P land nearest P, and 3 of 18 ending on P. The sensor stops at about P's centre. A shows the same effect more mildly: 38% of A strokes are pinned to the left edge, and words starting with A are right 89.1% of the time when the stroke reaches the edge and 66.7% when it does not. Q cannot be judged, with 2 words.

Software readings did not fix P. Stretching strokes towards the sensor edges gained nothing (0 words better, 5 worse). A reading shifted 0.6 keys to the right adds words to the strip but leaves first choice level. On the branch `tk/swipe-join` it fills only the 2nd and 3rd places: top 3 goes from 82.9% to 84.2% on prompted text, 76.7% to 77.6% on everyday text and 75.0% to 77.0% on the 300-word set, with first choice unchanged. Words with P only move from 59.3% to 60.4% in the top 3, so P is still open.

### Your skewed layout

Giving the model the real skewed key positions did not help: 68.6 / 80.9 on prompted, against 70.8 / 82.4 with today's calibration. Fitting key positions to your strokes was worse too, so calibration is not the bottleneck.

### Learning, prediction and shape memory

**Why so few words were learned.** `SwipeTyping.typed` and `confirm` skip any word that is already in the 147,068-word dictionary, so learning only ever adds words that are missing altogether. The list holds 256 words, needs 3 uses, and fades one use every 30 days. Swipe-shape learning was removed in an earlier version. In this log only 36 distinct kept words were missing from the dictionary, mostly your abbreviations and some fragments, and only one was used twice.

These were tested in time order: the latest 40% of first tries (275 prompted, 145 everyday), using only earlier strokes for learning. First / top 3:

| Approach | Prompted | Everyday |
|---|---|---|
| Today's engine | 75.3 / 82.2 | 69.7 / 80.0 |
| Learned ranker | 78.2 / 87.3 | 68.3 / 77.9 |
| Ranker + strong language model (GPT-2 Medium, 1.5 GB, PC only) | **86.2 / 90.5** | 70.3 / 80.0 |
| Ranker + your past swipe shapes and use counts | 78.9 / 86.9 | 67.6 / 78.6 |
| All three | 86.5 / 90.9 | 69.0 / 80.7 |

- **A strong language model combined with shape is the biggest gain measured so far on written prose**, about 11 points at first choice. On everyday text it barely moves, because short messages give it little to go on.
- **A simple memory of past swipe shapes did not help**, and nor did counting how often you use a word. The neural fine-tune did help words it had seen, so shape learning probably needs a learned model, not stored outlines.
- GPT-2 Medium is too big for the phone. The phone-sized DistilGPT-2 (82 million parameters, roughly 85 MB once compressed) keeps about half the first-choice gain: prompted 81.8 / 90.5, everyday 69.0 / 78.6.
- Everyday text gives the model little to work with: 41 characters of context on average against 270 for prompted text, and 16.6% of words have none at all. The model ranks the right word first among the candidates 24.7% of the time there, against 55.2% for prompted text. This is not a test fault. Short messages, which the keyboard sees without the message they answer, carry little context.

## Engine 2.0 design

Each layer can ship alone and be measured with `tools/replay.sh`.

**0. Capture.** Log `TOUCH_MAJOR` (contact size) with every point. The sensor reports it and nothing records it. Add a quick "that was accidental" mark to the raw log.

**1. Intent gate.** Keep today's touch state machine as the base. Add a score for word, delete, cursor, resting or brush, using contact size, timing and shape, once labels exist. An uncertain stroke does not write to the text: it appears in the strip for one tap, and nothing happens if ignored. Add a one-flick undo for anything the keyboard did in the last few seconds. A delete flick within about 350 ms of a committed word needs a stronger movement, since that timing shows up in the prompted right-word deletes.

**2. Stroke repair.** Join strokes at 150 ms or less, and read both joined and separate, keeping the better score.

**3. Recognition.** Keep FUTO's decoder and runtime. If the second pilot passes, replace the encoder with a Titan model that uses the same input format, so it drops in with no native rework. Use 2-3 readings per stroke instead of today's 1-2. Recognition takes about 42 ms in total now, so the cost of each extra reading has to be measured against a budget of about 100 ms.

**4. Dictionary 2.0.** Rebuild frequencies from modern text and refresh the word list. Give every word an outline. Grow the context vocabulary. Feed in your own vocabulary and past corrections, especially for the words that fail repeatedly.

**5. Ranking.** Simplify the current hand-written rerank, which changes 66 first words for a net gain of 4. Move to a learned ranker only after about 5,000 labelled strokes.

**6. Decision.** Use the engine's score gap. Below a gap of 5, the first word is right only about 42% of the time, so that is where alternatives belong.

## Pilot result

A model of about 490,000 parameters, close to FUTO's encoder, was trained on 43,000 of FUTO's phone swipes on this PC's CPU. The results below are all on held-back Titan strokes, first / top 3, and the pilot models have no language model or ranking layers.

| Model | Prompted (136) | Everyday (110) | 300-word set (100) |
|---|---|---|---|
| Today's full engine | 76.5 / 81.6 | 70.9 / 79.1 | 72.0 / 81.0 |
| CleverKeys, phone-trained | 69.9 / 80.9 | 54.5 / 69.1 | 71.0 / 81.0 |
| A: trained on phone swipes as recorded | 66.9 / 77.2 | 56.4 / 67.3 | 62.0 / 73.0 |
| B: trained with simulated touchpad damage | 55.1 / 69.1 | 48.2 / 60.0 | 42.0 / 59.0 |
| A, then fine-tuned on 695 of your real strokes | 71.0 / 80.1 | 61.4 / 74.5 | 65.0 / 76.0 |

What this shows:

- **FUTO's phone swipes are not enough alone.** The same open model scores 95.4% first on phone strokes and 69.9% on yours.
- **The simulator was misleading.** Adding my simulated damage to phone strokes reproduced your accuracy (95.4% fell to 64.8%), with shortened strokes causing most of it. But a model trained on that damage got worse on your real strokes. It learned the simulator, not your touchpad.
- **Real strokes matter, but mostly for words already seen.** After fine-tuning on 695 strokes, words the model saw during fine-tuning went from 66.5 to 75.5 first. Words it never saw did not move at first choice (58.6 to 58.6), and top 3 rose only from 68.6 to 71.5.
- **More real strokes keep helping, but only past a few hundred.** On prompted text the score is 66.9 with no fine-tuning, then 57.0 at 100 strokes, 61.0 at 200, 65.8 at 400 and 71.0 at 695. Small amounts made it worse.

**Compute:** this PC handled the pilot at about 900 strokes a second, so a full run on FUTO's million strokes would take roughly 9-17 hours. The 3090 would do it in about 3-4 hours, but a faster GPU does not solve a shortage of real Titan strokes, so it is not the bottleneck today.

**Licences:** FUTO's library is GPL-3.0, its models need a visible credit under their own licence, and the dataset is MIT. A model trained by us would carry only the dataset's terms and any code we reuse. CleverKeys' training repository shows no licence, so we would write our own training code.

## Second pilot: fine-tuning CleverKeys' pre-trained model

Run on the 3090 on 2026-11-09 with the newer log (`raw-log-9`): 1,303 tuning strokes, judged on 535 held-back first tries. The ONNX model was converted to PyTorch after fixing two converter bugs (`tools/gpu/fix_onnx2torch.py`). The converted model matches the original to 0.00003 on CPU. Encoder and dictionary only, no language model. First / top 3:

| Model | Prompted (136) | Everyday (299) | 300-word set (100) | **Words never seen in fine-tuning (231)** | Words seen (304) |
|---|---|---|---|---|---|
| CleverKeys as shipped | 69.9 / 80.9 | 59.9 / 74.9 | 71.0 / 81.0 | 62.3 / 77.1 | 66.1 / 78.0 |
| CleverKeys fine-tuned, rate 3e-5 | 72.8 / 83.8 | 66.9 / 80.9 | 77.0 / 85.0 | 67.1 / 80.5 | 72.7 / 83.9 |
| **CleverKeys fine-tuned, rate 1e-4** | **75.0 / 83.8** | **70.9 / 80.6** | 75.0 / 83.0 | **68.4 / 80.1** | 76.0 / 83.2 |
| CleverKeys fine-tuned, rate 3e-4 | 73.5 / 82.4 | 69.6 / 80.3 | 71.0 / 83.0 | 64.5 / 77.9 | 75.7 / 83.9 |
| Our pilot model, fine-tuned, rate 4e-4 | 65.4 / 78.7 | 66.6 / 78.9 | 71.0 / 79.0 | 61.5 / 73.6 | 71.4 / 82.9 |

- **Fine-tuning a strong pre-trained model works, including on new words.** Words the fine-tuning never saw rose 6.1 points at first choice (62.3 to 68.4), which passes the second pilot's gate. The gentler rate, which was not picked by looking at these results, still gave 4.8.
- Fine-tuning took about 2 minutes on the 3090. It needs no full training run.
- The rate was chosen from three by these same held-back strokes, so treat about a point of the gain as optimistic.
- Not yet tested: this encoder combined with the language model and ranking, and how it runs on the phone (ONNX Runtime, or an export to ExecuTorch, plus a beam search in Java). CleverKeys is GPL-3.0, but whether the model file has its own licence needs checking.

## Plan and gates

| Phase | Work | Must show before moving on |
|---|---|---|
| **0. Instrument and collect** | Log contact size and accident marks, keep the raw log on, and record a focused session of P, Q and A words plus one labelled mistouch session | At least 3,000 labelled real strokes and a clean accident count |
| **1. Repair and retrieval** | Stroke joining, sensor-edge handling for P and A, 2-3 readings, dictionary gaps | No word worse overall on held-back strokes, under 100 ms |
| **2. Dictionary 2.0** | Frequencies, full outlines, personal words | At least 2 points on everyday text |
| **3. Second pilot** | Fine-tune on the larger real set, and judge words it never saw | **At least 5 points on first choice for unseen words.** Only then use the 3090 |
| **4. Full Titan model** | Train on the 3090, fine-tune at home, export, replace encoder | Beats phase 2 on every set |
| **5. Mistouch** | Intent gate, soft commit, undo, delete guard | 95% fewer accidental text changes on the labelled session, with under 1% of real words delayed |

## Expected results

| Stage | Prompted first / top 3 | Everyday first / top 3 | Status |
|---|---|---|---|
| Today | 76.5 / 82.9 | 64.7 / 76.8 | Measured |
| Phases 1-2 | about 80 / 89 | about 66-68 / 78-81 | Pieces measured, combination not tested |
| If a Titan model passes the second pilot | target 85-90 / 92-95 | target 75-85 / 85-90 | **Target, now less certain** |

90 / 95 on prompted text sits at the top of that range. About 15 prompted strokes (3.7%) were cut off or were wrong words, so they carry too little information for any model. Everyday text reaching 90 / 95 would need more than this plan can promise, because rarer words and unusual vocabulary pull it down.

## Limits of this evidence

- One person, one phone, one day, about 1,050 labelled first tries. The ranker results show how much a small set can mislead.
- The combined 80 / 89 adds separately measured gains. Overlap could make it lower.
- Accident labels are upper bounds.
- The everyday text still contains unclassified testing.

Sources: [FUTO Swipe](https://swipe.futo.tech/), [CleverKeys](https://github.com/tribixbite/CleverKeys), [HeliBoard](https://github.com/HeliBorg/HeliBoard) (its gesture typing needs a closed Google library, so it was ruled out).

## Engine 2.0 as built

Built on 2026-11-09 in branch `tk/swipe-join`, on top of the stroke-joining work. It is not committed and not on the phone. All results below are on the held-back word positions (`slot % 3 == 2`: 540 first tries, 136 prompted, 304 everyday, 100 from the 300-word recording) unless marked otherwise. "First / top 3" throughout.

### What a swipe now goes through

1. The FUTO engine reads the stroke as before (plain, stretched if fast, and shifted right), now 48 words deep.
2. **Fine-tuned CleverKeys** reads the same stroke (`CtcReader`, ONNX Runtime) and a Java word search (`CtcWords`) finds up to 80 words. The search matches `ck_core.py` exactly on 300 strokes.
3. **DistilGPT-2**, 8-bit per channel, gives each candidate's chance after the last 32 tokens of text (`NextWordModel`, `ByteBpe`). It is worked out after the previous word, so a swipe does not wait for it.
4. A **ranker** of 900 LightGBM trees (`SwipeRanker`) weighs all of that and picks the top 3. Its Java features and scores match the training script to 1e-13 on 300 strokes.
5. If any of the new models fails to load or run, the FUTO engine's own order is shown, as before.

| Held back | All | Prompted | Everyday | Recording | Right word anywhere in the candidates |
|---|---|---|---|---|---|
| Engine before this work | 72.6 / 81.5 | 76.5 / 81.6 | 71.1 / 81.6 | 72.0 / 81.0 | |
| FUTO order in this branch | 71.7 / 81.5 | 75.7 / 81.6 | 69.7 / 81.2 | 72.0 / 82.0 | 89.6 |
| **Engine 2.0** | **78.9 / 85.7** | **86.0 / 89.7** | **76.3 / 84.2** | **77.0 / 85.0** | 89.6 |

Top 3 can't go above the last column: in about 1 in 10 strokes no source offers the right word at all. That is the next ceiling to lift.

### Choices made, and why

| Choice | Options measured | Picked | Evidence |
|---|---|---|---|
| Base recognition model | Fine-tuned CleverKeys; our model trained from scratch on FUTO's 783,000 swipes, then fine-tuned; both together | **Fine-tuned CleverKeys** | Full stack: 78.9 / 85.7, from scratch 78.0 / 86.7, both 79.1 / 86.9. A tie within noise, so the smallest (6 MB, one encoder) wins. From scratch is kept as the upgrade path: 94.9 / 98.4 on FUTO's held-out phone swipes, and better on words it never saw (68.4 against about 66 first). |
| How long to fine-tune | 250, 500, 1,000 steps | **250** | 71.0 / 82.2 against 70.1 / 81.5 and 66.1 / 77.4 (two seeds each). Longer overfits. |
| More of your strokes? | 25, 50, 75, 100% of them | **Yes, keep collecting** | 66.5 → 70.1 → 70.0 → 71.0 first. Still rising at 100%. |
| Predictor | Pre-trained; fine-tuned on SODA conversations (8,000 and 20,000 steps); each with a light fine-tune on your own messages | **Pre-trained DistilGPT-2** | Full stack 78.9 / 85.7; SODA 20k 77.6 / 85.7; SODA 8k 0.6 below the pre-trained model in the same run. Next word in top 3, everyday: 20.1, SODA 8k 19.8, SODA 20k 18.3. Your own messages (153 messages, 996 words, the earliest 60%) gave 19.8 against 18.9 on the later 354 words: noise. |
| Predictor format | full precision, 8-bit, 8-bit per channel; last 16, 32, 64 tokens | **8-bit per channel, 32 tokens** | 79.8 / 85.6 against 79.1 / 86.5 at full precision; plain 8-bit lost about a point. 83 MB, 42-52 ms on 4 threads. |
| Your use counts in the ranker | with and without | **Without** | Later 40% of everyday strokes: 76.0 / 84.0 without, 75.8 / 84.4 with. The new ranker already has what they gave. |
| Next-word suggestions | phone's current model, predictor | **Predictor**, with your learned word pairs still first | Right word in the top 3 before any letter: prompted 24.0 → 37.9, everyday 16.6 → 20.1. |
| Completions after letters | current context model; predictor's whole words then frequency; both blended by place (2 settings) | **Predictor**, words it splits into pieces by frequency | Run on the phone with the app's own TypedPredictions (`completion_phone.sh`, 1,046 first tries). Letters typed before the word is in the top 3: all 2.51 → 2.34 (letters saved 46.8 → 50.4%), prompted 1.72 → 1.37, everyday 2.09 → 2.08. Top 3 after 1 letter: prompted 60.1 → 75.5, everyday 42.5 → 47.2; after 3 letters everyday falls 78.3 → 73.3. Blends gained under a point on everyday and lost 2-3 on prompted. 0.6 ms a keystroke. Added 2026-11-10. |
| Typed autocorrect | predictor weight 0 to 1 | **0.3** | 816 Titan-style slips in your own sentences, each also typed right. Everyday slips fixed 65.6 → 69.5%, prompted 71.4 → 78.4%, wrong fixes 4.6 → 4.6% and 2.2 → 1.9%, right words changed 0.2 → 0.2% and 0 → 0. 0.5 and up added wrong fixes. Used only when the chances are ready, so typing never waits. |
| Dictionary learning | | **Learn on the 2nd use, keep up to 1,000 words** (was 3 and 256) | Only words missing from the dictionary are learned, and in the log few came up 3 times. |
| Mistouch filter | simple rules | **None shipped yet; contact size is now logged** | The best rules catch 10-16% of accidents and also hold back 0.3-2.2% of real words. Contact size is the missing signal: `tools/contact_size.py` judges it once a few days of logs have it. |

### On the phone

Run with the app's own code through `app_process` (`tools/engine2_phone.sh`), CPU only, nothing installed, all 1,608 first tries:

- **Speed:** whole swipe 69 ms on average (FUTO engine 53, CleverKeys 8, ranker 8). The predictor takes 35-52 ms after the word before.
- **Memory:** loading engine 2.0 adds about 390 MB. The process then holds steady at 700-900 MB, and the Java heap stays flat at about 55 MB over 1,608 swipes. The keyboard uses about 506 MB today, of 11.7 GB on the phone. Most of the extra is the predictor (+175 MB loaded, up to +339 MB after long contexts). Turning ONNX Runtime's memory arena off saves about 200 MB but doubles the predictor's time, so it stays on.
- **App size:** about 95 MB more, mostly the 83 MB predictor.

### Still to do

- **Install and test on the phone**, which the Issues session does: `tests/swipe`, keyboard show and hide, and the app screens.
- **More labelled strokes, then retrain.** The learning curve is still rising. The steps: rebuild the corpus, cross-fit and fine-tune on the 3090 (`gpu/ck_curve.py`), `train_ranker.py`, then `make_swipe2.py`.
- **Mistouches:** after a few days of logs with contact size, run `contact_size.py`, then build the intent gate from section 1 of the design.
- **Personal predictor:** run `personal_lm.py` again once there are some tens of thousands of words of your own text. A model tuned on your messages must never go into a public build.
- **The 10% ceiling:** strokes where no source has the right word, from dictionary gaps and badly broken strokes.
