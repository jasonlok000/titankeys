# #56 study: a better Mandarin first choice

The Orchestrator, 2026-11-17: Mandarin, the same way as Cantonese ([56-cantonese-dictionary.md](56-cantonese-dictionary.md)).
Measure first-choice exact on held-out phrases typed without spaces, in Pinyin and Zhuyin. Then try word-count weights and
an octagram grammar from sources with clear licences, and report the gain, size and licence of each before building any
in. No app code changed.

## Answer

**Add lotem's Mandarin grammar for librime-octagram. Leave the word weights as they are.**

| Change | Pinyin first choice exact | Zhuyin first choice exact | APK cost | Licence |
|---|---|---|---|---|
| Shipped (luna/terra-pinyin, rime-essay's weights) | 54.0% | 49.5% | — | — |
| **+ lotem's Traditional word grammar** (`zh-hant-t-essay-bgw`) | **59.9%** (+5.9) | **55.4%** (+5.9) | **35.5 MB** (41.4 MB on the phone) | **LGPL-3.0**, as Rime's other data |
| + jieba's word counts (MIT), at 0.3 | 53.5% | 48.3% | under 1 MB | MIT |
| + jieba's word counts, at 1 | 52.4% | 46.8% | under 1 MB | MIT |
| + Common Voice zh-TW's counts (CC0) | 53.6% | 48.6% | none | CC0 |
| lotem's grammar + jieba at 0.3 | 59.5% | 54.9% | 35.5 MB | LGPL-3.0 + MIT |
| lotem's grammar, pairs of single characters counting (`collocation_min_length: 2`) | 56.0% | 48.7% | 35.5 MB | LGPL-3.0 |
| lotem's character grammar (`zh-hant-t-essay-bgc`) | 53.0% | 43.4% | 8.0 MB | LGPL-3.0 |

- **The weights:**
  - rime-essay's word weights are already good for Mandarin, unlike rime-cantonese's.
  - Adding jieba's counts, or Common Voice's, makes the first choice slightly worse.
- **The grammar:** lotem's word grammar, as upstream Rime ships it, is the one change that helps, and it helps most where the owner types:
  - phrases of 11 characters or more go from 42.9% to 53.4% in Pinyin, and from 36.3% to 46.1% in Zhuyin;
  - phrases of 7 to 10 characters go from 52.4% to 58.5% in Pinyin.
- **Settings:** octagram's defaults. Counting pairs of single characters, which helped Cantonese, hurts here.
- **One model for both:** Pinyin and Zhuyin share one dictionary, so one grammar file serves both.

**The cost is size:** 35.5 MB compressed in the APK, 41.4 MB on the phone after the first-use copy. For the Play build, it
goes into `rime_pack` with Rime's other data (docs/play/asset-packs.md), so the base download doesn't grow.

## How it was measured

- **The held-out text:**
  - Tatoeba's Mandarin sentences with their Pinyin transcriptions (CC BY 2.0 FR).
  - Each sentence is cut at punctuation into phrases whose characters match their syllables one to one: 70,250 phrases, about half Simplified and half Traditional.
  - The test is a fixed sample of 3,000 of up to 16 characters (seed 56).
  - Rime's Pinyin and Zhuyin conversion data (rime-luna-pinyin, rime-terra-pinyin, rime-essay) isn't made from Tatoeba. Only the next-word tables are, and they play no part in conversion.
- **Typed as the owner types:**
  - The toneless codes run together, then one Space, through `rime_tool space` on TitanKeys' own schemas.
  - Learning and next-word suggestions are off, in the Simplified variant.
  - Zhuyin's codes are the same syllables through `tk_zhuyin`'s own spelling rules and Hsu keys (她在家吗 → tyzijeymy).
- **The score:** the committed phrase, and the expected one, folded to Simplified characters with OpenCC's TSCharacters, so the test's two scripts compare with each other.
- **What the score can't see:** a choice that's right but written differently (的/得 in casual text, 那/哪) counts as a miss. The differences between rows are what matter.

## The sources

| Source | Licence | Size | Used as |
|---|---|---|---|
| [lotem/rime-octagram-data](https://github.com/lotem/rime-octagram-data) `hant` branch (`97bf5504`): `zh-hant-t-essay-bgw.gram` | LGPL-3.0 (its `LICENSE`) | 41.4 MB, SHA-256 `0488ebd6…59c1` | the grammar |
| the same repository's `zh-hant-t-essay-bgc.gram` | LGPL-3.0 | 10.3 MB | a smaller character grammar: no help |
| [jieba](https://github.com/fxsjy/jieba)'s `jieba/dict.txt` (`67fa2e36`) | MIT (its `LICENSE`) | 349,046 words with counts, 5.1 MB, Simplified (turned Traditional with OpenCC's STPhrases and STCharacters) | word weights: no help |
| Common Voice's zh-TW sentences (`4ea23d2d`) | CC0 | 15,580 sentences | word weights: no help |

Two caveats about lotem's grammar:
- Its README says only that it was refined automatically from open internet text, without naming the corpus. The licence is clear (LGPL-3.0); the corpus isn't. This is the model upstream Rime offers for Mandarin, and rime-cantonese's own schema includes it.
- The Simplified model (`zh-hans`) wouldn't fit: Rime's Mandarin dictionaries are Traditional, and the grammar is applied before OpenCC turns choices Simplified. So the Traditional model serves every variant.

**Not tried:** a grammar counted from Chinese Wikipedia, as Cantonese's is.
- The dump is about 3 GB, and its model would carry CC BY-SA 4.0.
- With lotem's ready and LGPL-3.0, it's worth trying only if the size must come down: a pruned model of our own could be smaller than 41 MB.

## How it would go in

All of it is in the octagram plugin's place, which the Cantonese grammar already set up:
- `data.sh` downloads `zh-hant-t-essay-bgw.gram`, pinned by commit and SHA-256, and ships it beside `tk_yue.gram`.
- `tk_pinyin` and `tk_zhuyin` gain `grammar: { language: zh-hant-t-essay-bgw }`, with octagram's defaults.
- SOURCES.md, Settings > Licences and the two input methods' NOTICE credit it as LGPL-3.0.
- data.sh's checks gain a long phrase or two.

## Files beside this doc (`56-mandarin-dictionary/`)

- `prep_cmn.py`: the held-out phrases, with Pinyin and Zhuyin codes.
- `score_cmn.py`: the scores.
- `mk_cmn.py`: jieba's and Common Voice's counts merged into the weights.

The Cantonese study's harness runs them, with `tk_pinyin` and `tk_zhuyin` in place of `tk_jyutping`.
