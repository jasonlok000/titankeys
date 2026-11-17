# #56 study: Chinese next-word suggestions, Rime's add-on or a small language model

The owner asked: "study whether a quick add on or a small chinese LLM is better. and whether adding a small chinese LLM is easy. Check if our previous english LLM lesson would help with the implementation." The question is the bar's suggestions after a word is committed: after 你, offer 好, 哋, 的…. Reranking Rime's own choices by the sentence comes up at the end.

## Answer

**The add-on, librime-predict, with a table of word pairs counted from public text.** It's the better choice today, and the easier one.

- **Better for Cantonese, by a lot:** the right next character is in its first 3 suggestions 30.9% of the time. The best model gets 23.0%, and the Traditional Chinese models only 12.8%.
- **Close for Mandarin:** 18.9% against 22.7–25.0% for the models.
- **It costs almost nothing:** a few MB, a lookup instead of a model run, and a BSD-3 plugin that our Rime build already knows how to build.
- **A model doesn't pay for itself yet:**
  - The usable ones are 60–104 MB, 25–40 ms a call on the PC, and weak in Cantonese.
  - The one that's best in Mandarin, Qwen2.5-0.5B, is 771 MB and 160 ms.
  - The best small one, uer's, states no licence, so it can't ship.
- **Adding a model would be easy:** most of the English path carries over (below). It's the quality, size and licences that hold it back, not the work.

## The candidates

| | Licence | Size on the phone | What it knows |
|---|---|---|---|
| **librime-predict** ([rime/librime-predict](https://github.com/rime/librime-predict), `920bd41`) | BSD-3-Clause | the table: about 2–5 MB of text before compiling (below) | only what the table holds: the last committed word, then what followed it |
| ckiplab/gpt2-tiny-chinese | GPL-3.0, as TitanKeys | 12 MB, 8-bit | Traditional Chinese (Taiwan), 4 layers |
| ckiplab/gpt2-base-chinese | GPL-3.0 | 104 MB, 8-bit | Traditional Chinese (Taiwan), 12 layers |
| uer/gpt2-distil-chinese-cluecorpussmall | **none stated** | 60 MB, 8-bit | Simplified Chinese web text, 6 layers |
| Qwen/Qwen2.5-0.5B | Apache-2.0 | 771 MB, 4-bit weights | many languages, mostly Simplified Chinese |

- **The table needs data:** librime-predict ships none. Its builder makes the table from two things:
  - a word list, where each word's start offers its rest (相 → 關);
  - word pairs counted from text.
- **None of the models is trained on Cantonese.**

## Numbers

**The test:**
- **Text:** held-out Tatoeba sentences (CC BY 2.0 FR). That's all 4,100 held-out Cantonese ones, and 7,634 held-out Mandarin ones in Traditional characters, picked out with OpenCC's character tables.
- **Words:** sentences are cut into words the way Rime commits them, the longest word in its word lists. Those lists are rime-cantonese's for Cantonese, rime-essay for Mandarin.
- **The score:** after each word, is the next character first among the suggestions (top 1), or among the first 3 (top 3)?
- **Sample:** 3,000 places per language, the same ones for every method.
- **The word pairs** are counted on the other 80% of the sentences.

| | Cantonese top 1 | top 3 | Mandarin (Traditional) top 1 | top 3 |
|---|---|---|---|---|
| table: word list only | 0.0% | 0.0% | 0.3% | 0.7% |
| **table: Tatoeba word pairs, then the word list** | **19.4%** | **30.9%** | 12.4% | 18.9% |
| ckip-tiny, 8-bit | 6.6% | 12.3% | 8.9% | 17.3% |
| ckip-base, 8-bit | 7.1% | 12.8% | 12.7% | 22.7% |
| uer-distil, 8-bit | 12.7% | 23.0% | 13.7% | 23.9% |
| Qwen2.5-0.5B, 4-bit weights | 8.0% | 13.4% | **14.5%** | **25.0%** |

**Each next-character call** at 20 tokens of context, on the PC (WSL), 1 and 4 threads:

| | 1 thread | 4 threads |
|---|---|---|
| ckip-tiny | 9 ms | 9 ms |
| uer-distil | 36 ms | 25 ms |
| ckip-base | 54 ms | 40 ms |
| Qwen2.5-0.5B | 347 ms | 158 ms |

The English model, 83 MB, runs in the same range as ckip-base. The phone will be slower than the PC.

- **8-bit costs little:** on 1,000 places, uer-distil gives 20.7% against 20.9% in 32-bit (Cantonese) and 25.3% against 26.1% (Mandarin). ckip-base gives 12.2% against 12.4%, and 23.7% against 22.1%.
- **The word list alone scores 0, mostly because of the test.** Cutting by the longest word means the next word is almost never the rest of a longer word, which is all the word list offers. It mainly helps after a single character is committed on its own. It isn't the part that suggests the next word.
- **The table's size**, as text before compiling, built from all of Tatoeba:
  - Cantonese: 34,229 words with 329,582 next words, 4.9 MB;
  - Mandarin: 29,135 words with 117,964 next words, 2.0 MB.
  Compiled, it's a double-array trie and a string table; that wasn't built here, so its size isn't measured.

## How each would plug in

**librime-predict:**
- **Building it:** add it to `native/rime/CMakeLists.txt`, which leaves Rime's plugins out today; Trime's CMake files, which ours is based on, already build it. Its builder, `build_predict`, compiles the table on the PC in `data.sh`.
- **Turning it on:** in our three schemas, add `predictor` and `predict_translator`, and a `prediction` switch.
- **Its suggestions:** they come back as Rime choices with nothing typed. The bar shows choices only while a code is typed today, so it needs that case. `RimeEngine` reads them from the menu as it reads choices now.
- **Characters:** the table is in Traditional. Simplified goes through Rime's own OpenCC option, as typed words do now.
- **Days:** about 3–5 in all:
  - 1 for the build;
  - 1 for the table in `data.sh`;
  - 1–2 for the bar;
  - 1 for checks and the phone.

**A model:** about 4–6 days, with 60–104 MB more in the APK, and the ckiplab or uer licence question first.

## What the English path gives a Chinese model

- **Carries over as it is:**
  - the ONNX runner, `NextWordModel.run` and `after`, with its kept last answer, its thread setting and #85's no-spinning setting. It takes ids and gives next-token chances, whatever the language.
  - The export: `tools/dev/swipe-baseline/tools/export_lm.py`'s recipe (eager attention, the output layer on the last position only, the dynamo exporter) exported all four models unchanged. Its 8-bit per-channel step works for the GPT-2 models, at most 1.6 points lost.
- **Doesn't carry over:**
  - The 8-bit recipe ruined Qwen: it gave the same answer for any text. Qwen needs weights-only quantization, and even then its 151,936-token embedding stays large.
  - `ByteBpe`: the ckiplab and uer models use BERT's Chinese vocabulary, one id per character, so their tokenizer is a plain lookup of about 30 lines. Only Qwen uses byte BPE, with a different split rule and its own 151,643-token vocabulary.
  - The word helpers (`likelyWords`, `wordsStarting`) look for tokens that start with a space, which Chinese doesn't have. For Chinese, the top tokens are already characters.
- **Reranking Rime's choices** is where the English lesson fits best. `ContinuationScorer` and its margins carry over: score each choice after the committed text, and move one up only when the model is surer by a margin, as the apostrophe fixes do. This study didn't measure it. It needs a model, so it can wait until a model has earned its place.

## Limits

- **Tatoeba's sentences are short and repetitive.** The word pairs come from the same kind of text they're tested on, which flatters the table. Chat may score lower for the table and differently for the models.
- **Real commits vary:** Rime often commits several words at once, and the add-on looks up the whole commit. That makes the table find less in real use than here.
- **The next character only:** suggesting whole words (好 against 好多) isn't measured.
- **The models' cost on the phone:** speeds are on the PC only.
- **Next steps:**
  1. Build the table and try it on the phone.
  2. If Mandarin users want more, try ckip-base, GPL like TitanKeys and trained on Traditional Chinese, for Mandarin only, and for reranking.
  3. Watch for an openly licensed small Cantonese model.

## Data and tools

All of it is downloaded with the owner's yes (2026-11-16), from the official sources, into WSL at `~/data/zh`, outside the repo:
- Tatoeba's `cmn` and `yue` exports (CC BY 2.0 FR);
- librime-predict;
- the four models from their publishers' Hugging Face pages;
- a Python venv for the conversion.

**The tool:** [`56-chinese-prediction.py`](56-chinese-prediction.py):
- `export` makes the ONNX files;
- `eval` prints every number above except the 8-bit check, which was a one-off comparison with the 32-bit originals.

Its file names end in `-int8pc` for every model, including Qwen's 4-bit one.
