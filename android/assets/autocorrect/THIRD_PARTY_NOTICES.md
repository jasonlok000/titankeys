# Numen autocorrect data

TitanKeys copies the autocorrect from [Numen](https://github.com/jlo-aug/numen) (GPL-3.0, read at commit `7b1391c`), which is a modified version of [Pastiera](https://github.com/palsoftware/pastiera) (GPL-3.0). The code is in `android/kotlin/com/atlas/autocorrect`. The GPL-3.0 text is in `Numen-GPL-3.0.txt` and `Pastiera-GPL-3.0.txt`.

The word data is in `android/assets/autocorrect`. It has these attributions and licences. A copy of this file is next to the data, so it is also in the app.

## English word-pair counts (`en_pairs.txt`)

Word-pair counts built by Numen's `tools/build_context_pairs.py` from:

- Simple English Wikipedia (https://simple.wikipedia.org), text under the Creative Commons Attribution-ShareAlike 4.0 License (CC BY-SA 4.0, https://creativecommons.org/licenses/by-sa/4.0/)
- Tatoeba English sentences (https://tatoeba.org), Creative Commons Attribution 2.0 France License (CC BY 2.0 FR, https://creativecommons.org/licenses/by/2.0/fr/)
- Google Books Ngram top English 2-grams via https://github.com/orgtre/google-books-ngram-frequency, Creative Commons Attribution 3.0 Unported License (CC BY 3.0, https://creativecommons.org/licenses/by/3.0/)
- Hand-written common pairs from Numen

Only aggregated counts are included. The file is distributed under CC BY-SA 4.0. See `en_pairs_NOTICE.txt`.

## English word-triple counts (`en_triples.txt`)

Built by Numen's `tools/build_context_triples.py` from Tatoeba English sentences (https://tatoeba.org), CC BY 2.0 FR. Only aggregated counts are included. See `en_triples_NOTICE.txt`.

## English real-word confusions (`en_confusions.txt`, `en_short_words.txt`)

`en_confusions.txt` is built by Numen's `tools/build_confusions.py` from the Aalto ITE typing dataset (Aalto University, doi:10.5281/zenodo.12528163), Creative Commons Attribution 4.0 International License (CC BY 4.0, https://creativecommons.org/licenses/by/4.0/). Only aggregated counts are included, together with counts from the word pairs. See `en_confusions_NOTICE.txt`. `en_short_words.txt` comes from the same tool and from the word pairs.

## English common misspellings (`en_misspellings.txt`)

Built by Numen's `tools/build_misspellings.py` from Wikipedia's "Lists of common misspellings/For machines" (https://en.wikipedia.org/wiki/Wikipedia:Lists_of_common_misspellings/For_machines), CC BY-SA 4.0 (https://creativecommons.org/licenses/by-sa/4.0/). The file is distributed under CC BY-SA 4.0.

## English first names (`en_names.txt`)

Built by Numen's `tools/build_first_names.py` from US Social Security Administration baby-name data (public domain, a work of the US government), via the top-1000-per-year table at https://github.com/hadley/data-baby-names.

## Slang (`en_slang.txt`)

Numen's list of English texting slang and everyday tech words (`en_extra.json`), under Numen's GPL-3.0.

## English recognised words (`en_known.txt`)

Built by Numen's `tools/build_known_words.py` from SCOWL size 60 (http://wordlist.aspell.net).

Copyright 2000-2018 by Kevin Atkinson

Permission to use, copy, modify, distribute and sell these word lists, the associated scripts, the output created from the scripts, and its documentation for any purpose is hereby granted without fee, provided that the above copyright notice appears in all copies and that both that copyright notice and this permission notice appear in supporting documentation. Kevin Atkinson makes no representations about the suitability of this array for any purpose. It is provided "as is" without express or implied warranty.

SCOWL also includes material from the Moby Words II lists (public domain), the ENABLE word list and 12Dicts by Alan Beale (public domain), and the UK English Wordlist with Frequency Classification by Brian Kelk. See the SCOWL Copyright file for the full attributions.

## Typing research used for tuning (not included in the app)

Numen fitted and tested its scoring weights on two research datasets, which TitanKeys does not ship: the Aalto ITE typing dataset (Aalto University, CC BY 4.0, https://doi.org/10.5281/zenodo.12528163) and the 136M keystrokes dataset (Dhakal et al. 2018, Aalto University, https://userinterfaces.aalto.fi/136Mkeystrokes/, research and non-commercial use with attribution). This matters if TitanKeys is ever charged for.

## Test data (not in the app)

- `tests/autocorrect/tatoeba_heldout_en.txt`: English sentences from Tatoeba (https://tatoeba.org) by Tatoeba contributors, CC BY 2.0 FR. They are held out of the word-pair counts.
- `tests/autocorrect/chat_messages_en.txt`: messages written for Numen's tests.
