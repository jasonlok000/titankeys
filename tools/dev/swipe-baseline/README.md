# Swipe baseline tools

These tools measure the swipe engine on the owner's recorded swipes, and rebuild engine 2.0's models. Nothing here changes the app.

- `BASELINE.md`: the first baseline, 2026-11-09.
- `ENGINE-2.0.md`: the engine 2.0 proposal, and what was built.
- `report/`: the numbers. They hold counts only, no typed text.
- `tools/`: the scripts.

## Setup

Everything runs in WSL, in the distro and user that build TitanKeys. Paths are as WSL sees them.

The tools read their machine facts from `tools/dev/env.sh`. Copy `tools/dev/local.env.example` to `tools/dev/local.env` and set what differs on your machine. Source it before you run a Python tool:

```bash
. tools/dev/env.sh
```

The shell tools source it themselves.

| Variable | Holds |
|---|---|
| `TK_REPO` | This repo. The shell tools use it as the default source tree |
| `TK_PRIVATE` | The recordings and labelled tables (see below) |
| `TK_DATA` | The raw logs exported from the phone |
| `TK_ORT` | ONNX Runtime for Android (see below) |
| `TK_BUILD` | The Kotlin compiler (`kotlin-2.1.20/`), the phone lock (`tk-tools/.lock`) and the desktop ONNX Runtime jar |
| `TK_OUT` | Scratch output, such as the edge-variant engine copies |
| `TK_ADB_SERIAL` | The phone, for `tk_adb` |
| `TK_GPU_HOST`, `TK_GPU_KEY` | The GPU machine for `tools/gpu`, reached over SSH |

`ANDROID_HOME` comes from the build environment script (`TK_ENV_SCRIPT`).

Python needs `numpy`, `onnxruntime`, `lightgbm`, `scikit-learn` and `transformers`, and `torch` for `export_lm.py`.

### The recordings stay private

`$TK_PRIVATE` holds the attempt tables, the corpus and the replays. They contain the owner's typed text. They live only on the owner's machine and never go in git. `.gitignore` also blocks `tools/dev/swipe-baseline/private/`.

The Python tools that take a `baseline-dir` argument read their inputs from `$TK_PRIVATE` and write their reports to `baseline-dir/report`. Pass `tools/dev/swipe-baseline` as `baseline-dir`.

### ONNX Runtime for Android

The phone tools use ONNX Runtime 1.20.0 for Android, from Maven Central:

- package `com.microsoft.onnxruntime:onnxruntime-android:1.20.0`
- file `onnxruntime-android-1.20.0.aar`

An AAR is a zip file. Put these files from it in `$TK_ORT`:

```bash
mkdir -p "$TK_ORT"
unzip -o onnxruntime-android-1.20.0.aar 'jni/arm64-v8a/*' classes.jar -d "$TK_ORT"
cp "$TK_ORT/classes.jar" "$TK_ORT/onnxruntime-android-1.20.0-classes.jar"
```

`$TK_ORT` then holds:

- `onnxruntime-android-1.20.0-classes.jar`, for `replay.sh`, `evidence.sh` and `make_swipe2.py`
- `classes.jar`, for `ort_bench.sh "$TK_ORT" ...`
- `jni/arm64-v8a/libonnxruntime.so` and `libonnxruntime4j_jni.so`, for `ort_bench.sh` and `make_swipe2.py`

`completion_phone.sh`, `engine2_phone.sh`, `saved_phone.sh`, `sweep_phone.sh` and `autocorrect_eval.sh` use the source tree's own runtime in `android/libs` and `android/jniLibs` instead.

`completion_pilot.sh` runs on the PC. It needs the desktop jar `com.microsoft.onnxruntime:onnxruntime:1.20.0` at `$TK_BUILD/ort-desktop/onnxruntime-1.20.0.jar`.

## Rebuild the baseline from a new raw log

```bash
. tools/dev/env.sh
B=$TK_REPO/tools/dev/swipe-baseline
T=$TK_REPO
LOG=$TK_DATA/20261109/raw-log-4.jsonl

python3 -I $B/tools/build.py $LOG $T/android/debug/reference/java/com/titankeys/reference/Passages.java $TK_PRIVATE
python3 -I $B/tools/analyze.py $TK_PRIVATE $T/android/assets/swipe/english.combined $B/report
python3 -I $B/tools/analyze.py $TK_PRIVATE $T/android/assets/swipe/english.combined $B/report hand
python3 -I $B/tools/micro.py $TK_PRIVATE $LOG $B/report
python3 -I $B/tools/touches.py $TK_PRIVATE $LOG $B/report
python3 -I $B/tools/export_corpus.py $TK_PRIVATE $T/android/debug/recorder/samples/session-20261108-114501.jsonl $TK_PRIVATE/corpus.jsonl
```

`analyze.py` in `hand` mode writes the word tables to `$TK_PRIVATE/words-hand.json`, and its report holds counts only.

## Compare an engine change

`replay.sh` runs the corpus on the phone. It adds no input, does not restart the keyboard, and waits for the shared phone lock.

```bash
bash $B/tools/replay.sh /path/to/changed/tree $TK_PRIVATE/corpus.jsonl $TK_PRIVATE/replay-new.jsonl
python3 -I $B/tools/baseline.py $TK_PRIVATE/replay-new.jsonl $TK_PRIVATE/corpus.jsonl $B/report/baseline-new.json
python3 -I $B/tools/score_replay.py $TK_PRIVATE/corpus.jsonl $TK_PRIVATE/replay-new.jsonl $B/report/replay-new.json
```

Compare `baseline-new.json` with `report/baseline.json`. Tune only on strokes with `slot % 3 != 2`, and judge on the held-back third.

## Files

| File | Holds |
|---|---|
| `rawlog.py` | Rebuilds the text buffer and links each swipe to what happened to its word |
| `build.py` | Writes `$TK_PRIVATE/attempts.jsonl`, the labelled attempt table |
| `analyze.py` | Accuracy tables and failure causes. Add `hand` for hand-corrected text |
| `micro.py` | Individual failures, offsets by finger style, broken strokes |
| `touches.py` | Mistouches, accidental swipes, delete flicks, taps |
| `signals.py` | Sensor mapping, rhythm, throughput, engine confidence |
| `export_corpus.py` | Writes the strokes the replay runs |
| `BaselineRunner.java`, `replay.sh` | Runs the corpus through the real `SwipeRecognizer` on the phone |
| `score_replay.py`, `baseline.py` | Score a replay |

The phone keeps the runner's libraries and models in `/data/local/tmp/tk-baseline` so that later replays start fast. To remove them: `tk_adb shell rm -rf /data/local/tmp/tk-baseline`.

## Building engine 2.0's models again

Run these when there are more labelled strokes. `$TK_PRIVATE` stays on the owner's machine. The GPU machine only gets `titan-strokes.jsonl` (strokes and words, no sentences) and public data.

| Step | Tool | Where |
|---|---|---|
| 1. Record the engine evidence the ranker reads, from the app's own code | `evidence.sh` and `EvidenceRunner.java` | phone, CPU only |
| 2. Fine-tune CleverKeys, cross-fit it and export `ck-titan.onnx` | `gpu/ck_curve.py` (also the learning curve) | GPU machine |
| 3. Export the predictor (8-bit per channel, tokenizer files) | `export_lm.py` | this PC |
| 4. Train the ranker, judge it on the held-back third, write Java parity cases | `train_ranker.py` | this PC |
| 5. Put the models in a TitanKeys tree with their manifest | `make_swipe2.py` | this PC |
| 6. Run the whole engine on the phone with the app's code | `engine2_phone.sh`, `Engine2Runner.java`, `score_engine2.py` | phone, CPU only |

`gpu/overnight.cmd` runs on a Windows GPU machine, from the folder that holds the `gpu` scripts.

Other engine 2.0 tools:

| Tool | Does |
|---|---|
| `ck_parity.py` | Reference cases for the Java word search (`CtcWords`) |
| `ort_bench.sh`, `OrtBench.java` | Speed and memory of an ONNX model on the phone, with runtime settings |
| `next_phone.py` | Next-word suggestions before any letter: current model against the predictor |
| `completion_phone.sh`, `CompletionRunner.java`, `score_completions.py` | Completions after 1, 2, 3 letters: current context model against the predictor, on the phone |
| `autocorrect_cases.py`, `autocorrect_eval.sh`, `AutocorrectEval.kt` | Tune the predictor's weight in typed autocorrect on slips in your own sentences |
| `messages.py`, `personal_lm.py` | Your messages in time order, and the personal predictor test (this PC only) |
| `contact_size.py`, `accident_rules_base.py` | Does contact size tell accidental strokes apart? Run after a few days of logs |
| `gpu/train_scratch.py`, `gpu/scratch_crossfit.py`, `gpu/prep_futo.py` | Our own encoder, trained from scratch on FUTO's MIT swipes: the upgrade path |
| `gpu/lm_finetune.py` | Predictor fine-tune on SODA conversations (did not help; kept for later) |
| `fullstack.py` | The first full-stack test, with the older candidate dump |
| `name_corpus.py`, `saved_phone.sh`, `score_saved.py` | Swipes of one name, and saved words with a boost. Strokes of the name have ids that start with `name-` |
| `touch_replay.sh`, `Replay.kt` | Replays a log's touches through today's `Contact` and `SwipePieces` |

Engine 2.0 experiments: `make_readings.py` and `CandidateRunner.java` (12 readings of each stroke), `ranker_experiment.py` (retrieval ceiling and learned ranker), `ck_eval.py` (CleverKeys head-to-head), `stitch_export.py` (joining strokes), `nextword.py` and `NextWordRunner.java` (next-word correction), `intent_experiment.py` and `accident_rules.py` (accidental strokes), `touch_replay.sh` and `touch_compare.py` (today's touch code), `dictionary.py`. The proposal is `ENGINE-2.0.md`.

The `phone_job_*.sh` scripts take an optional source tree. Without one they use `$TK_REPO`.

## Reproduce the paper's numbers

### Baseline: 76.5% first, 82.9% top 3 on 404 prompted words

Inputs in `$TK_PRIVATE`: `attempts.jsonl` and `extras.json`, written by `build.py` from `raw-log-4.jsonl`.

```bash
. tools/dev/env.sh
B=$TK_REPO/tools/dev/swipe-baseline
python3 -I $B/tools/analyze.py $TK_PRIVATE $TK_REPO/android/assets/swipe/english.combined $B/report
```

In `report/metrics.json`, `all` reads `n` 404, `top1` 309 (76.5%) and `top3` 335 (82.9%).

### Engine 2.0 on the held-back third: 78.9% first, 85.7% top 3 on 540 words

Inputs in `$TK_PRIVATE/v9`: `corpus.jsonl`, `evidence.jsonl` (from `evidence.sh` on the phone) and `ck-crossfit2.jsonl` (from `gpu/ck_curve.py`).

The predictor directory needs the 8-bit model and the Hugging Face tokenizer files. `export_lm.py` writes both. Then copy in the shipped model, so the weights are the ones the numbers came from:

```bash
. tools/dev/env.sh
B=$TK_REPO/tools/dev/swipe-baseline
LM=$TK_DATA/lm-distilgpt2
# The tokenizer files from Hugging Face, with the shipped model and its lm-info.txt (no torch needed).
# export_lm.py distilgpt2 $LM makes the same folder from scratch, with torch.
python3 -c "from transformers import AutoTokenizer; AutoTokenizer.from_pretrained('distilgpt2').save_pretrained('$LM')"
cp $TK_REPO/android/assets/swipe2/predictor/{model-int8pc.onnx,lm-info.txt} $LM/
python3 -I $B/tools/train_ranker.py $B $TK_REPO/android/assets/swipe/english.combined $LM \
  $TK_PRIVATE/v9/ck-crossfit2.jsonl $TK_OUT/ranker
```

The defaults are right: `V_DIR=v9`, `LM_FILE=model-int8pc.onnx`, `LM_CAP=32`. The script writes `report/ranker-train.json`. Its `ranker.all` should read 78.9 first and 85.7 top 3, as in `report/ranker-train-final.json`. It also writes the ranker to `$TK_OUT/ranker/swipe-ranker.txt` and parity cases to `$TK_OUT/ranker/ranker-parity.jsonl`. Those hold strokes and context from the recordings, so keep them out of the repo.
