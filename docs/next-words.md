# Next words in every language

Each input method with a word list has a next-word table: for each word, the 8 words that most often follow it. The suggestion bar reads it. The owner asked for suggestions and basic correction in every language (2026-11-17), and said not to worry about size yet.

## What's built

- **The files:**
  - `android/assets/im/<id>/pairs.bin`, one per input method;
  - Portuguese has `pairs-pt-BR.bin` and `pairs-pt-PT.bin`, one for each word list, each from its own country's corpus.
- **What they hold:**
  - the 8 most common next words per word, each seen at least twice, in lower case;
  - `<s>` as the first word for a sentence's start.
- **The format is compact,** for the phone's small heap (`NextWords`, in `LanguageLearning.kt`):
  - every word once, in UTF-8, in one byte array, with the first words sorted by their bytes for a binary search;
  - each first word's next words as word numbers, commonest first.
  - Counts aren't kept: the bar only needs the order.
  - `tools/im/next_words.py`'s `write_binary` describes the layout. `--text` also writes the readable form, English's `en_pairs.txt` format (`first second<TAB>count`), and `convert` turns that form into `.bin`.
- **The source:** the Leipzig Corpora Collection, CC BY 4.0. Each folder's NOTICE names its corpus.
  - Each language's newest corpus of 1 million sentences.
  - 300,000 for Bosnian, Basque, Irish, Galician, Macedonian and Nynorsk; Thai's largest is 100,000.
  - `tools/im/next_words_corpora.json` lists them.
- **What's left out:** Japanese, Chinese and Cantonese have their own engines' predictions.
- **Belarusian** is built against C's ranked word list. The one before stopped alphabetically at "б".

## How it's made

`tools/im/next_words.py` reads the sentences and splits them into words the way each word list spells them:

- Letters and combining marks, so Devanagari's vowel signs stay inside a word.
- Apostrophes and hyphens inside a word.
- Each word takes the list's spelling, then goes to lower case.
- A hyphenated word not in the list splits at its hyphens.
- A word not in the list breaks the chain: no pair crosses it.
- **Thai,** written without spaces, splits by the longest word-list match.
- **Korean** uses its word list.
- **Serbian Latin's** text is written in Latin letters letter for letter, as its word list is.

`tools/im/next_words_all.sh` rebuilds every table and NOTICE line from the sentences in WSL's `~/build/lang/leipzig/`. The sentences stay outside the repository.

## How good

The check holds out every 10th sentence, builds from the rest, and asks how often the held-out next word is the table's first, or among its first three. It counts only words in the list.

| language | inside a sentence: first | top 3 | at a sentence's start: first | top 3 |
|---|---|---|---|---|
| Hindi | 20.4% | 35.6% | 5.6% | 12.2% |
| Vietnamese | 18.8% | 31.2% | 3.8% | 9.5% |
| Spanish | 16.6% | 28.6% | 10.9% | 26.5% |
| Thai (100,000 sentences) | 18.8% | 27.4% | 9.4% | 22.2% |
| Russian | 14.9% | 23.3% | 5.1% | 10.6% |
| Korean | 13.0% | 22.3% | 2.9% | 6.3% |
| Belarusian | 13.8% | 22.0% | 6.5% | 14.5% |
| German | 10.7% | 20.1% | 10.6% | 21.3% |
| Georgian | 12.1% | 20.1% | 2.9% | 7.4% |

- Inside a sentence, the right next word is among the first three about a fifth to a third of the time.
- Languages that pack grammar into word endings (Korean, Georgian, Russian, German) score lower: there are more words to choose from.
- A held-out first word has a table 97.3% to 100% of the time.

## Size

| | all 48 files | largest |
|---|---|---|
| as text (the first build) | 195 MB | Georgian 13.9 MB, Korean 8.4 MB, Serbian 8.1 MB |
| compact, as shipped | 84 MB | Georgian 3.9 MB, Korean 3.2 MB, Serbian 3.0 MB |

The compact files hold every pair the text did: each was decoded and compared word for word.

## Memory

The phone's heap is 256 MB, and switching languages must never pile up lists or tables (the OutOfMemoryError of 2026-11-16). `tests/languages/run.sh` measures it on the PC, through the app's own readers:

| language | table on the heap: as text | compact | table load: as text | compact | word list | list and table together |
|---|---|---|---|---|---|---|
| Georgian | 18.6 MB | 3.7 MB | 2,410 ms | 127 ms | 18.8 MB | 25.2 MB |
| Korean | 23.3 MB | 3.1 MB | 746 ms | 93 ms | 20.0 MB | 23.6 MB |
| Serbian | 18.0 MB | 2.9 MB | 584 ms | 91 ms | 17.7 MB | 22.2 MB |
| Greek | 14.2 MB | 2.3 MB | 482 ms | 79 ms | 17.7 MB | 20.0 MB |
| Russian | 10.9 MB | 2.1 MB | 483 ms | 68 ms | 17.0 MB | 19.2 MB |

- **How it's measured:** compact tables by their arrays. Load times are the second load, once the JIT has warmed up; the first is up to 186 ms.
- **Load times above** were read through WSL's mount of the Windows drive, which is slow, for text and compact alike. From WSL's own disk (`try.sh`), the compact tables load in 4 to 13 ms, the word lists in 143 to 203 ms, and English's text in 231 to 374 ms.
- **The budget,** checked by the test: 40 MB for a list and its table, and 300 ms for a table's load.
- **English's** `en_pairs.txt`, read by `PairCounts`: 9.3 MB and 689 to 845 ms. It loads once with the English engine, not on a switch.
- **Switching:** the test makes 30 switches through 44 input methods, overtaken loads included. It holds one word list and one table at most, and the heap after 30 switches stays within 6 MB of the heap after 5. Its peak, 86 to 131 MB, is read just after a load, with that load's garbage not yet collected.
- **Loading:** `Languages` drops the table as a switch begins, never reads one for a switch overtaken before its turn, and keeps a load only if it's still the latest switch's. Its loader is a single thread.

## A size cap, not used

`--max-mb` drops the rarest pairs until a table fits. The share of next words in the top 3, inside a sentence:

| language | full | 3 MB | 1.5 MB |
|---|---|---|---|
| German | 20.1% | 20.1% | 19.6% |
| Korean | 22.3% | 20.8% | 19.3% |
| Georgian | 20.1% | 18.0% | 16.4% |

So a 3 MB cap costs German nothing, and Korean and Georgian about 2 points. No cap is used (owner: don't worry about size yet).
