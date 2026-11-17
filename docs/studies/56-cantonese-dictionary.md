# #56 study: a better Cantonese dictionary

The owner, 2026-11-17: "we shd also see if we could get a better dictionary. the default Rime dictionary is actually pretty
shit." The owner types a whole phrase without spaces and presses Space once at the end ("diuneiloumo"). The question is
which openly licensed Cantonese data would make that first choice right more often, by how much, and what it would cost.
This is a study: no app code changed.

## Answer

**Two steps, both measured, both open:**

1. **Now, data only: reweight rime-cantonese's words with words.hk's word counts.** These are public domain, with no app
   code to change. They take the first choice from 23.2% to 27.1% of phrases exactly right (+3.9 points).
2. **Next, with app work: a Cantonese grammar model for librime-octagram,** built from Cantonese Wikipedia.
   - On its own, it takes the first choice to 27.7% (+4.4).
   - With step 1, it reaches 31.4% (+8.2), and phrases of 11 or more characters go from 11.9% to 20.4% right.
   - It costs a BSD-3 plugin in our Rime build and about 23 MB more APK (28 MB on the phone).
   - It has a licence condition: the model is made from CC BY-SA text (below).

Space already takes the whole phrase. In every test, one Space committed the full phrase and left nothing behind, so the
gap is only which words come first. Commit 9a58779c checks that in data.sh.

## How it was measured

- **The held-out text** is Tatoeba's Cantonese sentences with their Jyutping transcriptions:
  - Each sentence is cut at punctuation into phrases whose characters match their syllables one to one.
  - That gives 44,958 phrases. The test is a fixed sample of 3,000 of up to 16 characters, 11.5 on average.
  - None of the sources below include Tatoeba. Any Common Voice line holding a test phrase of 4 or more characters was
    left out of training.
- **Each phrase is typed as the owner types:**
  - Toneless Jyutping, no spaces, then one Space, through `rime_tool space`. It's the same Rime the app runs, with learning
    off so earlier phrases can't teach later ones.
  - Jyutping, because the transcriptions are Jyutping. Yale reads the same dictionary and weights, so the gains carry
    over to Yale.
- **The score:**
  - "Exact" means the committed phrase equals the original.
  - "Characters" counts characters right in phrases of the right length.
- **One correction:** Tatoeba's transcriptions read 佢 as heoi; the dictionary has keoi.
  - Unfixed, those 458 codes counted as misses that a user typing keoi wouldn't have.
  - The table uses the corrected codes. The uncorrected run shows the same order: 21.2% shipped, 23.3% with words.hk.
- **What the score can't see:**
  - A different but equally right form counts as a miss: 呀/啊, 搵/揾, 說/説 under the Hong Kong variant.
  - So do Tatoeba's occasional odd readings.
  - The absolute numbers are therefore low. The differences between rows are what matter.

| | Exact | Characters | 2–3 chars | 4–6 | 7–10 | 11+ |
|---|---|---|---|---|---|---|
| **Shipped** (rime-cantonese 259f0e48 + cantonese-weights.tsv) | 23.2% | 72.9% | 61.0% | 33.3% | 21.4% | 11.9% |
| + words.hk counts, at 0.3 of the weights' total | **27.1%** | 74.6% | 65.8% | 36.8% | 25.6% | 15.5% |
| + words.hk counts, at 3× | 26.3% | 74.8% | 65.1% | 35.7% | 24.8% | 15.1% |
| + words.hk, HKCanCor and Common Voice | 27.0% | 75.1% | 65.8% | 36.7% | 26.3% | 14.5% |
| Grammar: Common Voice + HKCanCor | 24.1% | 73.6% | 61.0% | 34.2% | 22.2% | 13.0% |
| Grammar: lotem's Mandarin zh-hant (what upstream rime-cantonese suggests) | 25.2% | 74.2% | 63.7% | 35.8% | 22.7% | 14.4% |
| Grammar: Cantonese Wikipedia, 6.6 MB (collocations seen 5+ times) | 25.4% | 74.4% | 61.0% | 35.0% | 23.9% | 14.5% |
| Grammar: Cantonese Wikipedia, 28 MB | 27.1% | 75.3% | 63.0% | 37.1% | 25.5% | 15.8% |
| Grammar: Cantonese Wikipedia, 28 MB, two-character collocations count | **27.7%** | 75.8% | 63.0% | 37.4% | 26.7% | 16.2% |
| **words.hk counts + the Wikipedia grammar** | **31.4%** | 77.5% | 67.1% | 41.0% | 29.9% | 20.4% |

Earlier runs on the uncorrected codes, which held in the same order:
- HKCanCor alone: 22.4%.
- Common Voice alone: 21.7%.
- words.hk at 1×: 22.9%.
- Shipped: 21.2%.

The owner's kind of phrase, shipped then with both steps:

| Typed (Jyutping, then Space) | Shipped | words.hk + grammar |
|---|---|---|
| neisikzofaanmei | 你識咗反美 | **你食咗飯未** |
| ngosingzo | 我星座 | 我成咗 (meant: 我醒咗) |
| ngolamgan | 我臨近 | 我臨近 (meant: 我諗緊) |
| lokzojyu | 落助於 | 落助於 (meant: 落咗雨) |
| diuneiloumou | 屌你老母 | 屌你老母 |
| ngodeijatcaiheoisikfaan | 我哋一齊去食飯 | 我哋一齊去食飯 |

Both steps together still miss common spoken phrases. Wikipedia is written Cantonese, so spoken text would help most, but
little of it is openly licensed. HKCanCor and Common Voice are the open spoken text, and they're small.

## The sources

| Source | Licence | Size | Readings | Frequencies | Use |
|---|---|---|---|---|---|
| **words.hk analysis: `existingwordcount.csv`** | public domain ("Data License: public domain. Credits to words.hk appreciated.") | 36,601 words, 409 KB | no | yes: each word's count in words.hk's Cantonese text collection (粵文庫) | **reweights the dictionary's words** (step 1) |
| words.hk full dictionary | words.hk Non-Commercial Open Data License 1.0, by request | the whole dictionary | yes | no | **not usable:** non-commercial, no sublicensing |
| HKCanCor (Hong Kong Cantonese Corpus) | CC BY 4.0 (its `LICENSE`) | ~230,000 words of speech, segmented, 961 KB | Jyutping | yes, by counting | weights or grammar; too small to move much alone |
| Common Voice zh-HK sentences | CC0 (Sentence Collector only takes CC0) | 19,020 sentences, 631 KB | no | by counting | weights or grammar; too small alone |
| Cantonese Wikipedia (zh_yuewiki) | CC BY-SA 4.0 | 130 MB dump; 6.3 million runs of text | no | by counting | **the grammar model** (step 2) |
| CC-Canto and CC-CEDICT Cantonese readings | CC BY-SA 3.0 | ~22,000 entries, 1–2 MB | Jyutping | no | coverage only; rime-cantonese already covers most of it |
| Tatoeba Cantonese | CC BY 2.0 FR | 20,854 sentences | Jyutping transcriptions | — | **the held-out test**, so never trained on |
| lotem's rime-octagram-data, zh-hant | LGPL-3.0 | 41 MB | — | — | a grammar, but Mandarin: +2.0 points, less than ours |
| MYCanCor, CantoMap | CC BY (data "to appear"); GPL-3.0 (on request) | hours of speech | — | — | not usable as text |

words.hk links:
- word counts: https://words.hk/faiman/analysis/existingwordcount.csv
- the licence page for the full data: https://words.hk/base/hoifong/

## How each would go into our Rime build

**Step 1, the words.hk weights (data only).** data.sh already patches `essay-cantonese.txt`, rime-cantonese's word
weights, from `native/rime/cantonese-weights.tsv`. It would also:
- pin the CSV by its SHA-256;
- scale its counts so their total is 0.3 of the essay's total, and add each word's scaled count to its weight;
- add words the essay lacks;
- run the same `check` lines, and record the CSV in SOURCES.md and the licences.

The shipped data grows by well under 1 MB, since the words are already in the dictionary. `mkessay.py` beside this doc does
the merge.

**Step 2, the grammar (app work):**
- **Build:** librime-octagram (lotem/librime-octagram 57d18b9f, BSD-3-Clause) goes into our librime as a merged plugin.
  It's the plugin Trime ships and our CMakeLists.txt leaves out today. Its `build_grammar` tool turns
  "collocation count" lines into a `.gram` file.
- **Data:** `gram.py` beside this doc counts, from Cantonese Wikipedia, each word after the 1 to 3 characters before it,
  the shape octagram looks up. Collocations seen twice or more give a 28 MB model, 22.9 MB compressed (it barely compresses); the 6.6 MB model, collocations seen 5+ times, is 5.6 MB compressed and gives +2.2 points instead of +4.4.
- **Schema:** `tk_yale` and `tk_jyutping` gain `grammar: { language: …, collocation_min_length: 2 }`. With octagram's
  default of 3, a two-character pair like 咗 + 雨 can't help.
- **Licence:** a model counted from Wikipedia text is arguably a derived work. CC BY-SA 4.0 is one-way compatible with
  GPL-3.0, so it would ship with attribution and under CC BY-SA. That's a call for the owner.
- **The open alternative:** HKCanCor + Common Voice is clean (CC BY 4.0 + CC0), but small. It gave +0.9 points.

## Calls for the owner

1. **Step 1 now?** The words.hk weights: +3.9 points, public domain, data only.
2. **Step 2 later?** The Cantonese grammar: about +4.4 points on its own, and +8.2 with step 1. It costs about
   23 MB of APK (or 5.6 MB for the smaller model, at about half the gain), a native plugin, and CC BY-SA attribution for the model.

## Files beside this doc (`56-cantonese-dictionary/`)

- `prep.py`: builds the held-out test from Tatoeba's sentences and transcriptions.
- `fixk.py`: reads 佢 as keoi.
- `score.py`: the scores in the table.
- `mkessay.py`: merges word counts into the weights.
- `gram.py`: counts collocations for `build_grammar`.

The data they read stays out of the repo.
