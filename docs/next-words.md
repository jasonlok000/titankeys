# Next words in every language

Each input method with a word list has a next-word table: for each word, the 8 words that most often follow it. The suggestion bar reads it. The owner asked for suggestions and basic correction in every language (2026-11-17), and said not to worry about size yet.

## What's built

- **The files:**
  - `android/assets/im/<id>/pairs.txt`, one per input method;
  - Portuguese has `pairs-pt-BR.txt` and `pairs-pt-PT.txt`, one for each word list, each from its own country's corpus.
- **The format** is the same as English's `android/assets/autocorrect/en_pairs.txt`, so `PairCounts` reads it:
  - `first second<TAB>count`, in lower case;
  - `<s>` as the first word for a sentence's start;
  - the 8 most common next words per word, each seen at least twice.
  - The format was agreed with the bar's reader.
- **The source:** the Leipzig Corpora Collection, CC BY 4.0. Each folder's NOTICE names its corpus.
  - Each language's newest corpus of 1 million sentences.
  - 300,000 for Bosnian, Basque, Irish, Galician, Macedonian and Nynorsk; Thai's largest is 100,000.
  - `tools/im/next_words_corpora.json` lists them.
- **What's left out:** Japanese, Chinese and Cantonese have their own engines' predictions.
- **Belarusian** is built against the fixed word list on `claude/m3-be-words`. The old list stopped alphabetically at "б", so its table needs that list to land first.

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
| as built | 195 MB (about 48 MB compressed in the APK) | Georgian 13.9 MB, Korean 8.4 MB, Serbian 8.1 MB |
| capped at 3 MB a file | 131 MB | |
| capped at 1.5 MB a file | 71 MB | |

`--max-mb` drops the rarest pairs until a table fits. The share of next words in the top 3, inside a sentence:

| language | full | 3 MB | 1.5 MB |
|---|---|---|---|
| German | 20.1% | 20.1% | 19.6% |
| Korean | 22.3% | 20.8% | 19.3% |
| Georgian | 20.1% | 18.0% | 16.4% |

So a 3 MB cap costs German nothing, and Korean and Georgian about 2 points.
