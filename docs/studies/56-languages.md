# Study: languages for 3.0 (#56)

M1's language study, 2026-11-16. No app code. Everything downloaded sits in WSL under `~/build/lang/`, and nothing
downloaded is in the repo. The framework that builds on this is in [docs/languages.md](../languages.md).

## In short

- **43 languages work from data alone** with the planned framework: 34 in Latin script and 9 in Cyrillic, Greek or
  Georgian. Each needs a keymap, its accents and a word list. All 43 have a word list whose licence can ship in a GPL-3.0
  app, except three marked "to check" (Serbian Latin, Macedonian, Georgian).
- **A word list costs about 0.5 MB in the APK** when cut to its 100,000 most common words. All 43 add about 22 MB.
- **Chinese costs about 22 MB in the APK:** 4.5 MB of library (librime and OpenCC) and 17.2 MB of data for Yale, Jyutping
  and Pinyin with all three variants. It unpacks to 40 MB on the phone. Dropping rime-cantonese's long-phrase list brings
  the data to 11.3 MB, with little change in the first choices.
- **The total fits Google Play's 200 MB:** about 116 + 22 + 22 = 160 MB, with every data-only language included. Nothing
  needs downloading later.
- **Rime works.** librime and OpenCC build for Android arm64 the way Trime does it. The test program types "nei hou" and
  "ni hao" and gets 你好 in every variant. Taiwan's phrases work: 软件 / 軟件 / 軟體, 打印机 / 打印機 / 印表機.
- **Yale, the owner's input method, comes first.** The owner's Weasel setup is `yale.schema.yaml` from
  [rime/rime-jyutping](https://github.com/rime/rime-jyutping), byte for byte (LGPL-3.0, so it can ship). TitanKeys' Yale
  (`tk_yale`) uses its spelling rules on rime-cantonese's newer word list, which Jyutping shares, so Yale adds only 0.1 MB.

## 1. Generic languages

### Where the data comes from

| What | Source | Licence | Ships in GPL-3.0? |
| --- | --- | --- | --- |
| Letters and accents | [Unicode CLDR](https://github.com/unicode-org/cldr-json) 49.0.0, `cldr-misc-full`, `characters.json`: `exemplarCharacters` (letters the language needs) and `auxiliary` (letters from loanwords) | Unicode License v3 | Yes |
| Word lists with counts, AOSP | [AOSP LatinIME `dictionaries/`](https://android.googlesource.com/platform/packages/inputmethods/LatinIME/+/refs/heads/main/dictionaries/): cs da de el en es fi fr hr it iw lt lv nb nl pl pt_BR pt_PT ro ru sl sr sv tr. The same format as `english.combined` today | Apache-2.0 | Yes |
| Word lists with counts, more languages | [HeliBoard's aosp-dictionaries](https://codeberg.org/Helium314/aosp-dictionaries) (commit ff0cd52, 2026-10-08). It repackages AOSP's lists and adds about 60 languages, most built from the Leipzig Corpora Collection. Its README lists each list's source and licence | Per list: Apache-2.0, CC BY 4.0, CC BY-SA 4.0, LGPL-3.0, GPLv2, a few unstated | Per list (see the table) |
| Leipzig Corpora Collection | [wortschatz.uni-leipzig.de](https://wortschatz.uni-leipzig.de/en/usage): its terms say the downloadable corpora are CC BY. The site's other data is CC BY-NC | CC BY 4.0 | Yes, with credit |
| Word frequencies, fallback | [wordfreq](https://github.com/rspeer/wordfreq) 3.x: 40+ languages, among them Malay and Filipino, which HeliBoard lacks. Frozen at 2021 data | Code Apache-2.0, data CC BY-SA 4.0 | Yes (CC BY-SA 4.0 is one-way compatible with GPL-3.0) |
| Spelling dictionaries | Hunspell lists (LibreOffice's dictionaries). Words without counts, so they can't rank suggestions | Varies: GPL, LGPL, MPL, BSD | Mostly. Not needed: every language here has a list with counts |
| Hardware keyboard layouts | [Android's `InputDevices` layouts](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/InputDevices/res/raw/) (`keyboard_layout_*.kcm`, 50 layouts) | Apache-2.0 | Yes |

Licence notes:
- CC BY 4.0 and CC BY-SA 4.0 data can ship in a GPL-3.0 app. Both need credit in the app's licences page.
- **GPLv2 lists (the Indic project's)** can ship only if they're "GPLv2 or later". HeliBoard says "GPLv2". To check per list
  before any Indic language ships.
- **Slovak's main HeliBoard list is CC BY-NC 4.0** and can't ship. Its experimental list (FrequencyWords, from OpenSubtitles)
  is used instead. HeliBoard calls that one CC BY 4.0; FrequencyWords' own README says CC BY-SA 4.0. Either works.
- Lists with no licence stated (Serbian Latin, Macedonian, Georgian, Hungarian's old list, Luxembourgish) stay out until
  checked, or use a Leipzig list instead.

### The table

"In APK" is the size after deflate (gzip -9), which is what the APK stores: `english.combined` is 7.2 MB raw and 0.9 MB in
today's APK. "Top 100k" keeps the 100,000 most common words. Next-word pairs are dropped, since other input methods have no
next word. Letters come from CLDR's exemplar set, grouped by the key they'd be held on.

| Code | Language | Script | Word list (source, licence) | Words | Raw MB | In APK, all MB | In APK, top 100k MB | Letters beyond a-z (CLDR), by key |
| --- | --- | --- | --- | ---: | ---: | ---: | ---: | --- |
| es | Spanish | Latin | AOSP LatinIME, Apache-2.0 | 236,193 | 11.09 | 1.04 | 0.49 | a: á e: é i: í n: ñ o: ó u: úü |
| pt-BR | Portuguese (Brazil) | Latin | AOSP LatinIME, Apache-2.0 (HeliBoard fixes) | 165,069 | 7.33 | 0.78 | 0.49 | a: áàâã c: ç e: éê i: í o: óôõ u: ú |
| pt-PT | Portuguese (Portugal) | Latin | AOSP LatinIME, Apache-2.0 | 218,456 | 9.66 | 1.00 | 0.50 | a: áàâã c: ç e: éê i: í o: óôõ u: ú |
| fr | French | Latin | AOSP LatinIME, Apache-2.0 | 190,113 | 8.64 | 1.01 | 0.52 | a: àâæ c: ç e: éèêë i: îï o: ôœ u: ùûü y: ÿ |
| de | German | Latin | AOSP LatinIME, Apache-2.0 | 205,914 | 9.65 | 1.17 | 0.55 | a: ä o: ö s: ß u: ü |
| it | Italian | Latin | AOSP LatinIME, Apache-2.0 | 172,831 | 7.78 | 0.85 | 0.49 | a: à e: éè i: ì o: ò u: ù |
| nl | Dutch | Latin | AOSP LatinIME, Apache-2.0 | 178,444 | 8.19 | 0.96 | 0.55 | a: áä e: éë i: íï o: óö u: úü |
| sv | Swedish | Latin | AOSP LatinIME, Apache-2.0 | 196,739 | 9.03 | 1.03 | 0.54 | a: àåä e: é o: ö |
| da | Danish | Latin | AOSP LatinIME, Apache-2.0 | 178,449 | 8.12 | 0.92 | 0.52 | a: æå o: ø |
| nb | Norwegian Bokmål | Latin | AOSP LatinIME, Apache-2.0 | 171,008 | 7.86 | 0.87 | 0.52 | a: àæå e: é o: óòôø |
| nn | Norwegian Nynorsk | Latin | Wikipedia via HeliBoard, CC BY-SA 4.0 | 1,033,892 | 57.55 | 6.73 | 0.55 | a: àåæ e: éèê o: óòôø |
| fi | Finnish | Latin | AOSP LatinIME, Apache-2.0 | 223,363 | 10.30 | 1.15 | 0.52 | s: š z: ž a: åä o: ö |
| pl | Polish | Latin | AOSP LatinIME, Apache-2.0 | 195,099 | 8.73 | 0.99 | 0.51 | a: ą c: ć e: ę l: ł n: ń o: ó s: ś z: źż |
| cs | Czech | Latin | AOSP LatinIME, Apache-2.0 | 171,544 | 7.68 | 0.86 | 0.51 | a: á c: č d: ď e: éě i: í n: ň o: ó r: ř s: š t: ť u: úů y: ý z: ž |
| sk | Slovak | Latin | FrequencyWords (OpenSubtitles), CC BY-SA 4.0 | 750,261 | 15.74 | 4.44 | 0.52 | a: áä c: č d: ď e: é i: í l: ĺľ n: ň o: óô r: ŕ s: š t: ť u: ú y: ý z: ž |
| hu | Hungarian | Latin | Leipzig Corpora via HeliBoard, CC BY 4.0 | 1,058,067 | 30.90 | 7.54 | 0.61 | a: á e: é i: í o: óöő u: úüű |
| ro | Romanian | Latin | AOSP LatinIME, Apache-2.0 | 1,125,204 | 50.73 | 3.36 | 0.38 | a: ăâ i: î s: ș t: ț |
| hr | Croatian | Latin | AOSP LatinIME, Apache-2.0 | 210,081 | 9.26 | 0.91 | 0.47 | c: čć d: đ s: š z: ž |
| sl | Slovenian | Latin | AOSP LatinIME, Apache-2.0 | 59,998 | 2.63 | 0.28 | 0.28 | c: č s: š z: ž |
| bs | Bosnian | Latin | ebukva/bs_BA, LGPL-3.0 | (not parsed) | 4.93 | 0.59 | 0.59 | c: čć d: đ s: š z: ž |
| sr-Latn | Serbian (Latin) | Latin | AOSP sr transliterated (HeliBoard), Apache-2.0: to check | 191,578 | 8.62 | 0.83 | 0.46 | c: čć d: đ s: š z: ž |
| tr | Turkish | Latin | AOSP LatinIME, Apache-2.0 | 180,841 | 8.34 | 0.84 | 0.48 | c: ç g: ğ i: ı o: ö s: ş u: ü |
| ca | Catalan | Latin | Leipzig Corpora via HeliBoard, CC BY 4.0 | 239,857 | 6.98 | 1.37 | 0.56 | a: à c: ç e: éè i: íï o: óò u: úü |
| gl | Galician | Latin | Leipzig Corpora via HeliBoard, CC BY 4.0 | 170,290 | 5.34 | 0.94 | 0.55 | a: á e: é i: íï n: ñ o: ó u: úü |
| eu | Basque | Latin | AnySoftKeyboard lists, Apache-2.0 | 106,787 | 2.23 | 0.56 | 0.52 | c: ç n: ñ |
| et | Estonian | Latin | Leipzig Corpora via HeliBoard, CC BY 4.0 | 724,326 | 19.92 | 4.66 | 0.54 | s: š z: ž o: õö a: ä u: ü |
| lv | Latvian | Latin | AOSP LatinIME, Apache-2.0 | 200,570 | 9.08 | 0.86 | 0.47 | a: ā c: č e: ē g: ģ i: ī k: ķ l: ļ n: ņ s: š u: ū z: ž |
| lt | Lithuanian | Latin | AOSP LatinIME, Apache-2.0 | 198,160 | 8.94 | 0.89 | 0.48 | a: ą c: č e: ęė i: į s: š u: ųū z: ž |
| is | Icelandic | Latin | Leipzig Corpora via HeliBoard, CC BY 4.0 | 289,184 | 8.93 | 1.79 | 0.57 | a: áæ d: ð e: é i: í o: óö u: ú y: ý t: þ |
| ga | Irish | Latin | aosp-gaeilge, CC BY 4.0 | 841,939 | 18.54 | 5.06 | 0.56 | a: á e: é i: í o: ó u: ú |
| af | Afrikaans | Latin | Leipzig Corpora via HeliBoard, CC BY 4.0 | 215,326 | 6.56 | 1.39 | 0.60 | (none in CLDR's main set; the word list uses é ê ë ï ó ö, which CLDR lists as auxiliary) |
| id | Indonesian | Latin | Leipzig Corpora via HeliBoard, CC BY 4.0 | 229,483 | 6.21 | 1.18 | 0.52 | (none) |
| eo | Esperanto | Latin | Leipzig Corpora via HeliBoard, CC BY 4.0 | 249,767 | 7.33 | 1.36 | 0.52 | c: ĉ g: ĝ h: ĥ j: ĵ s: ŝ u: ŭ |
| az | Azerbaijani | Latin | aosp-dict-az, CC BY-SA 4.0 + Leipzig | 50,000 | 1.04 | 0.28 | 0.28 | c: ç e: ə g: ğ i: ı o: ö s: ş u: ü |
| ru | Russian | Cyrillic | AOSP LatinIME, Apache-2.0 | 220,485 | 11.73 | 1.26 | 0.57 | Layout below |
| uk | Ukrainian | Cyrillic | Leipzig Corpora via HeliBoard, CC BY 4.0 | 1,347,649 | 50.93 | 9.24 | 0.58 | Layout below |
| be | Belarusian | Cyrillic | GrammarDB, CC BY-SA 4.0 | 3,979,928 | 232.43 | 9.06 | 0.19 | Layout below. Word forms without real counts |
| bg | Bulgarian | Cyrillic | Leipzig Corpora via HeliBoard, CC BY 4.0 | 898,049 | 31.78 | 5.70 | 0.64 | Layout below |
| sr | Serbian (Cyrillic) | Cyrillic | AOSP LatinIME, Apache-2.0 | 191,608 | 9.92 | 0.95 | 0.53 | Layout below |
| mk | Macedonian | Cyrillic | MatejKovacic list, licence not stated: to check | 256,146 | 6.76 | 1.53 | 0.57 | Layout below |
| el | Greek | Greek | AOSP LatinIME, Apache-2.0 | 184,303 | 9.83 | 1.03 | 0.60 | Layout below |
| kk | Kazakh | Cyrillic | Leipzig Corpora via HeliBoard, CC BY 4.0 | 1,184,682 | 42.68 | 8.10 | 0.61 | Layout below (42 letters) |
| ka | Georgian | Georgian | openboard (not in AOSP), licence not stated: to check | 100,000 | 3.66 | 0.56 | 0.56 | Layout below |
| vi | Vietnamese | Latin | Leipzig Corpora via HeliBoard, CC BY 4.0 (11k words only) | 11,755 | 0.51 | 0.06 | 0.06 | a: àảãáạăằẳẵắặâầẩẫấậ d: đ e: èẻẽéẹêềểễếệ i: ìỉĩíị o: òỏõóọôồổỗốộơờởỡớợ u: ùủũúụưừửữứự y: ỳỷỹýỵ |
| th | Thai | Thai | PyThaiNLP, CC0-1.0 | 87,336 | 2.87 | 0.49 | 0.49 | 44 consonants and the vowel marks |
| he | Hebrew | Hebrew | AOSP LatinIME, Apache-2.0 | 94,799 | 4.44 | 0.42 | 0.42 | Right to left |
| ar | Arabic | Arabic | Leipzig Corpora via HeliBoard, CC BY 4.0 | 470,783 | 18.42 | 2.51 | 0.52 | Right to left |
| fa | Persian | Arabic | Leipzig Corpora via HeliBoard, CC BY 4.0 | 1,520,799 | 52.08 | 9.98 | 0.56 | Right to left |
| hi | Hindi | Devanagari | Indic project, GPLv2: ships only if "or later" | 47,977 | 1.51 | 0.28 | 0.28 | Abugida |

Notes on the table:
- Where AOSP has a list, it's used: it's Apache-2.0, already in English's format and cleaner than the large corpus lists.
- "(not parsed)" means the list's lines differ from the usual format; its size is the whole file's.
- Malay, Albanian, Swahili and Maltese have no list in HeliBoard. wordfreq covers Malay (CC BY-SA 4.0). The others need a
  native speaker's list, so they wait.
- Korean, Japanese and Chinese aren't in this table: Korean needs no word list to type, Japanese needs an engine, and
  Chinese is Rime's (part 2).

### How the letters sit on the Titan's keys

**Latin script.** The letters are on the keys as printed. Each language's extra letters go on hold of their base letter
(hold a for á, à...), in the order CLDR gives, which is the language's alphabetical order. Letters with no base letter go on
the key they sound or look like:

| Letter | Held on | Languages |
| --- | --- | --- |
| ß | s | German |
| æ, å | a | Danish, Norwegian, Icelandic (æ), Swedish and Finnish (å) |
| ø, œ | o | Danish, Norwegian (ø), French (œ) |
| ł | l | Polish |
| đ, ð | d | Croatian, Bosnian, Serbian Latin (đ), Icelandic (ð) |
| þ | t | Icelandic |
| ə | e | Azerbaijani |
| ı | i | Turkish, Azerbaijani |

Turkish and Azerbaijani need one rule beyond data: capitals follow the language (i ↔ İ, ı ↔ I). Android's
`String.uppercase(Locale("tr"))` already does this, so the input method's locale is enough.

**Cyrillic and Greek.** Each key types the letter that Android's national layout puts on that key position, the way Android
does it for a USB keyboard (`keyboard_layout_*.kcm`). The Titan has 26 letter keys, so the letters that national layouts put
on punctuation keys go on hold of the nearest key in the same row. The hold for symbols is off in these input methods, as in
any input method with accents.

| Key | Q | W | E | R | T | Y | U | I | O | P | A | S | D | F | G | H | J | K | L | Z | X | C | V | B | N | M |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Russian | й | ц | у | к | е | н | г | ш | щ | з | ф | ы | в | а | п | р | о | л | д | я | ч | с | м | и | т | ь |
| Ukrainian | й | ц | у | к | е | н | г | ш | щ | з | ф | і | в | а | п | р | о | л | д | я | ч | с | м | и | т | ь |
| Belarusian | й | ц | у | к | е | н | г | ш | ў | з | ф | ы | в | а | п | р | о | л | д | я | ч | с | м | і | т | ь |
| Bulgarian (BDS) | , | у | е | и | ш | щ | к | с | д | з | ь | я | а | о | ж | г | т | н | в | ю | й | ъ | э | ф | х | п |
| Bulgarian (phonetic) | ч | ш | е | р | т | ъ | у | и | о | п | а | с | д | ф | г | х | й | к | л | з | ж | ц | в | б | н | м |
| Greek | ; | ς | ε | ρ | τ | υ | θ | ι | ο | π | α | σ | δ | φ | γ | η | ξ | κ | λ | ζ | χ | ψ | ω | β | ν | μ |

Letters that need a hold (from the national layout's punctuation keys, and letters with marks):

| Language | Holds |
| --- | --- |
| Russian | х, ъ on з (P); ж, э on д (L); б, ю on ь (M); ё on е |
| Ukrainian | х, ї on з (P); ж, є on д (L); б, ю on ь (M); ґ on г |
| Belarusian | х on з (P); ж, э on д (L); б, ю on ь (M); ё on е; ' (apostrophe) on ў |
| Bulgarian (phonetic) | я, щ on п (P); ь, ю on м (M) |
| Greek | ά έ ή ί ό ύ ώ ϊ ϋ ΐ ΰ on their vowels. Q types ";" (Greek's question mark), W types ς |
| Serbian, Macedonian | Android's Serbian layout has its own positions (Q љ, W њ...), with Shift written differently in its file. Its six letters on punctuation keys go on holds the same way. To map next night |

Bulgarian has two national layouts in Android. The phonetic one needs only four holds and follows most of the Latin letters printed
on the keys, so it's the proposed default.

Kazakh (42 letters) and Georgian (33 letters) fit the same way, with more holds. Georgian has no capitals, and Android's
layout puts its extra letters on Shift (Shift+W types ჭ, Shift+T types თ); TitanKeys can do the same.

### Ranking

**(a) Works from data alone** with the planned framework: a keymap, accents on hold, a word list and a few settings.
- Latin: Spanish, Portuguese (Brazil, Portugal), French, German, Italian, Dutch, Swedish, Danish, Norwegian (Bokmål,
  Nynorsk), Finnish, Polish, Czech, Slovak, Hungarian, Romanian, Croatian, Slovenian, Bosnian, Serbian (Latin), Turkish,
  Catalan, Galician, Basque, Estonian, Latvian, Lithuanian, Icelandic, Irish, Afrikaans, Indonesian, Esperanto,
  Azerbaijani.
- Cyrillic, Greek, Georgian: Russian, Ukrainian, Belarusian, Bulgarian, Serbian (Cyrillic), Macedonian, Greek, Kazakh,
  Georgian.

**(b) Needs a little code:**
- **Korean:** Hangul syllables built from jamo as you type (about 200 lines; the 2-set layout fits 26 keys with Shift for
  the doubled consonants). No word list needed.
- **Vietnamese:** a letter takes two marks (ơ, then a tone: ớ), so holds alone are clumsy. Telex (aa → â, ow → ơ, s → acute)
  is the usual way and is a small state machine. Its word list is small (11k words), but Vietnamese words are single
  syllables, so that's enough to start.
- **Thai:** no code, but 44 consonants and the vowel and tone marks don't fit 26 keys without a second layer (Shift and
  holds). Its Kedmanee layout is in Android's files. It's (b) for the layout work.

**(c) Needs a lot:**
- Right to left (Hebrew, Arabic, Persian, Urdu): the bar, the word menu and steering with Space all assume left to right.
  Hebrew is the easiest of them (27 letters on 26 keys, an AOSP word list).
- Indic scripts (Hindi, Bengali, Tamil...): letters combine into clusters, and their lists are GPLv2 (to check).
- Japanese: kana to kanji needs Mozc or similar.

### Size: what N languages add

| What | In the APK | On the phone |
| --- | --- | --- |
| Today | about 116 MB | |
| One data-only language (top 100k words) | 0.3 to 0.6 MB, 0.5 on average | the same, read from the APK |
| All 43 (a) languages | about 22 MB | about 22 MB |
| Rime library, arm64 (librime, OpenCC, their deps), sharing TitanKeys' `libc++_shared.so` | 4.5 MB (native libraries are stored uncompressed) | 4.5 MB |
| Chinese data: Yale, Jyutping, Pinyin, three variants | 17.2 MB | 40.2 MB, unpacked once on first use |
| Chinese data without rime-cantonese's long-phrase list | 11.3 MB | 23.9 MB |
| **Total with everything** | **about 160 MB** | |

Google Play's limit is 200 MB for the download of the base app. Every (a) language and Chinese fit, with about 40 MB to
spare. Nothing needs downloading later. Should the app ever pass 200 MB, Play Asset Delivery can move the word lists into
asset packs that install with the app, which TitanKeys can do without touching the network itself.

The word list's memory on the phone matters more than its size in the APK. Only one input method is loaded at a time, and a
100k-word list loads in the same way and in about the same memory as `english.combined` does today.

### The proposed 3.0 set and order

1. **The framework with three to prove it:** Spanish, French, German. They cover accents on hold, letters with no base (ß)
   and the accent-aware bar.
2. **The rest of the big Latin languages:** Portuguese (Brazil and Portugal, as one input method with two spellings, like
   English's), Italian, Dutch, Polish, Turkish, Swedish, Danish, Norwegian Bokmål, Finnish, Czech, Romanian, Hungarian,
   Indonesian.
3. **Chinese through Rime:** Yale first, then Jyutping and Pinyin, with the three variants. It can run alongside 2, since it shares only the
   picker and the overlay.
4. **Cyrillic and Greek:** Russian, Ukrainian, Greek, Bulgarian, Serbian, Belarusian.
5. **The remaining data-only languages:** Slovak, Croatian, Slovenian, Bosnian, Catalan, Galician, Basque, Estonian, Latvian,
   Lithuanian, Icelandic, Irish, Afrikaans, Esperanto, Azerbaijani, Norwegian Nynorsk, Kazakh, Macedonian and Georgian once
   their licences are checked.
6. **If time allows:** Korean and Vietnamese, then Thai.

Not in 3.0: right-to-left scripts, Indic scripts and Japanese.

Each input method is checked by one shared check of its files (letters match CLDR's, every accent is reachable, the word list
parses and is under its size cap, the licence is listed), and over adb by typing its letters and accents.

## 2. Chinese

### The build

librime and OpenCC were built for Android arm64 with Trime's own build files and the exact commits Trime pins today.

| Part | Version | Licence (checked in the source) |
| --- | --- | --- |
| [Trime](https://github.com/osfans/trime) (build files only, not shipped) | 2e2b4d5, 2026-11-12 | GPL-3.0-or-later |
| [librime](https://github.com/rime/librime) | bb56876, 2026-11-11 | BSD-3-Clause |
| OpenCC (librime's `deps/opencc`) | 025f371 | Apache-2.0 |
| marisa-trie | 3e87d53 | BSD-2-Clause or LGPL-2.1+ |
| leveldb | bb74ef7 | BSD-3-Clause |
| yaml-cpp | 56e3bb5 | MIT |
| spdlog | 79524dd | MIT |
| snappy | 9c28114 | BSD-3-Clause |
| Boost (cmake release) | 1.92.0, SHA-256 checked as Trime does | Boost Software License 1.0 |

How it was built, in `~/build/lang/rime-build/`:
- `CMakeLists.txt` is Trime's `app/src/main/jni/CMakeLists.txt` and `cmake/` folder, with two changes:
  - **No plugins.** Trime adds librime-lua, librime-octagram and librime-predict. TitanKeys' schemas need none of them, and
    leaving them out saves size. rime-cantonese's grammar model (octagram) is optional and is skipped.
  - **No JNI.** A stand-in library, `libtkrime.so`, exports the Rime API so its size is what the app would ship. The app's
    JNI layer comes in M2.
- NDK 29.0.14206865 (already in `~/android-sdk`; Trime uses NDK 30), `ANDROID_PLATFORM=android-34` (TitanKeys' minSdk),
  Release, `-DANDROID_STL=c++_shared` to share TitanKeys' `libc++_shared.so`, and `c++_static` for the test program.
- The same tree builds for the PC (x86-64), which compiles the schemas and OpenCC's dictionaries.

Library sizes (arm64, stripped):

| File | Stripped | Deflated |
| --- | ---: | ---: |
| `libtkrime.so`, `c++_shared` | 4.51 MB | 1.56 MB |
| `libtkrime.so`, `c++_static` | 5.48 MB | 1.85 MB |
| `rime_test` (test program, `c++_static`) | 3.36 MB | 1.38 MB |

TitanKeys stores native libraries uncompressed in the APK (as the 1.2.0 APK shows), so the library adds 4.5 MB. Its
`libc++_shared.so` comes from NDK r27c (`native/build.sh`), and it must be at least as new as the NDK Rime is built with. So
the app's build of Rime uses r27c, or `c++_static` (1 MB more). NDK 29 already links with 16 KB pages, as Android 15+ wants.

### The schemas

| Schema | Built from | Licence |
| --- | --- | --- |
| `tk_jyutping` | [rime-cantonese](https://github.com/rime/rime-cantonese) `jyut6ping3` (259f0e4, 2026-08-13): its dictionary, spelling rules and preedit | CC BY 4.0; `jyut6ping3.maps` (place names) ODbL 1.0 |
| `tk_yale` | The spelling rules of `yale.schema.yaml` from [rime/rime-jyutping](https://github.com/rime/rime-jyutping) (ee5e72b, 2025-12-30; author 幸延), the owner's own schema, on rime-cantonese's `jyut6ping3` word list | LGPL-3.0 + CC BY 4.0 |
| `tk_pinyin` | [rime-luna-pinyin](https://github.com/rime/rime-luna-pinyin) (56b934b) with [rime-essay](https://github.com/rime/rime-essay) (d95eeae) for word counts | LGPL-3.0 |
| shared | [rime-prelude](https://github.com/rime/rime-prelude) (082425e): punctuation, symbols, key bindings | LGPL-3.0 |

What was found:
- **Where the owner's Yale comes from.** The owner types with Weasel 0.16.1 and the schema list set to `yale` only. Their
  files (`yale.schema.yaml`, `jyutping.schema.yaml`, `hkcantonese.schema.yaml` and the three `jyutping*.dict.yaml`) are
  byte-identical to [rime/rime-jyutping](https://github.com/rime/rime-jyutping) at ee5e72b. That repository is
  LGPL-3.0, so the Yale schema can ship. Its word list is rime-cantonese's older, toneless predecessor; rime-jyutping's
  README points to rime-cantonese for the maintained one.
- **How Yale works there.** The schema turns each Jyutping syllable into its Yale spelling (`xform`: jy → y, j → y, z → j,
  c → ch, final aa → a, eu → iu, eo and oe → eu, em → im, ep → ip) and adds initials as abbreviations (ng, ch, gw, kw).
  Only Yale spellings are accepted, and tones aren't typed. rime-cantonese itself has no Yale schema; it points to
  [CanCLID/rime-cantonese-schemes](https://github.com/CanCLID/rime-cantonese-schemes)' patch (LGPL-3.0), which adds Yale
  spellings to Jyutping's instead. The owner's schema was preferred, since it's what the owner types every day.
- **TitanKeys' Yale** (`tk_yale`) is the owner's rules, with one rule added to drop rime-cantonese's tone digits first, on
  rime-cantonese's word list. It shares Jyutping's table. Its reverse lookup (Mandarin) is dropped like the others.
- **Compared on the PC** (Hong Kong variant, first choices), TitanKeys' Yale and the owner's exact setup agree on most codes.
  Where they differ, it's the order: `yut yu` gives 月餘 粵語 against the owner's 粵語 月餘, and `ngo dei` gives 我地 first
  against the owner's 我哋. `chin` gives 錢 against 前. The phone test carries both (`tk_yale` and `tk_yale_owner`), so the
  owner can judge. Shipping the owner's exact word list as well would add 10.7 MB to the APK.
- **Jyutping's tones are letters, not digits.** In rime-cantonese, v, x, q are tones 1, 2, 3 and vv, xx, qq are 4, 5, 6
  (`neixxhoux` is nei5 hou2). Digits pick a choice. Tones are optional. Yale has no tones, as in the owner's setup.
- **rime-cantonese already has variants.** Its schema switches between OpenCC Traditional, Hong Kong, Taiwan and Simplified.
  TitanKeys keeps that idea with one shared option set (below).
- rime-cantonese's schema pulls in reverse lookups (Mandarin, stroke, Cangjie, loengfan). The TitanKeys schemas drop them
  to keep the app small. They can come back later as their own input methods.
- Rime compiles the schemas on the PC, and the compiled tables ship. The word lists' YAML sources don't need to ship: with
  them removed, Rime checks its data in 0.3 to 0.6 s on the PC and uses the compiled tables.

The three schema files and the shared files are in `~/build/lang/rime-test/tk-rime-test/shared/` (`tk_*.yaml`).

### The variants

One option set, shared by every Chinese schema (`tk_variants.yaml`): `zh_hk`, `zh_tw`, `zh_hans`. The app sets exactly
one of them on every Rime session from Settings. There's no default (owner, 2026-11-16): the user picks the variant, the
first time a Chinese input method is turned on. Rime's word lists are Traditional, and each variant converts the choices
with OpenCC.

| Variant | Cantonese (Yale, Jyutping) | Mandarin (Pinyin) |
| --- | --- | --- |
| Simplified (mainland China) | OpenCC `t2s` | OpenCC `t2s` |
| Traditional (Hong Kong) | rime-cantonese's own `t2hkf` (its Hong Kong set) | OpenCC `t2hk` |
| Traditional (Taiwan) | OpenCC `t2tw` (characters only) | `tk_t2twp`: OpenCC's `t2tw` plus Taiwan's phrases (`TWPhrases`) |

OpenCC has no ready "Traditional to Taiwan with phrases" config (its `s2twp` starts from Simplified), so `tk_t2twp.json` is
written for TitanKeys from OpenCC's own dictionaries. Taiwan's phrases are Mandarin words (軟件 → 軟體), so Cantonese uses
Taiwan's characters only.

Each variant's own word list: rime-cantonese is Hong Kong Cantonese, and luna_pinyin is general Traditional Mandarin. No
separate Taiwan or Hong Kong Mandarin list exists in Rime's official schemas. For Simplified Mandarin, the popular
community lists are [rime-ice](https://github.com/iDvel/rime-ice) and [rime-frost](https://github.com/gaboolic/rime-frost),
both GPL-3.0 and both larger than luna_pinyin (rime-ice's base list alone is 16.6 MB of YAML). They're the candidates if
luna_pinyin's Simplified choices feel off. Not measured tonight.

Results on the PC, with the same data that goes to the phone (first choices):

| Schema, code | Simplified | Hong Kong | Taiwan |
| --- | --- | --- | --- |
| Jyutping, nei hou | 你好 您好 妳好 | 你好 您好 妳好 | 你好 您好 妳好 |
| Jyutping, gwong dung waa | 广东话 广东 光 | 廣東話 廣東 光 | 廣東話 廣東 光 |
| Jyutping, sik faan | 食饭 食返 | 食飯 食返 | 食飯 食返 |
| Jyutping, m goi saai | | 唔該晒 唔該 唔 | |
| Jyutping, neixxhoux (tones) | 你好 您好 | 你好 您好 | 你好 您好 |
| Yale, nei hou | 你好 您好 | 你好 您好 | 你好 您好 |
| Yale, jung man | 中文 仲问 | 中文 仲問 | 中文 仲問 |
| Yale, deui m jyu | 对唔住 | 對唔住 | 對唔住 |
| Yale, yau mou gaau cho | 有无搞错 有冇搞错 | 有無搞錯 有冇搞錯 | 有無搞錯 有冇搞錯 |
| Pinyin, ni hao | 你好 妳好 | 你好 妳好 | 你好 妳好 |
| Pinyin, ruan jian | 软件 | 軟件 | 軟體 |
| Pinyin, xin xi | 信息 | 信息 | 資訊 |
| Pinyin, da yin ji | 打印机 | 打印機 | 印表機 |
| Pinyin, li mian | 里面 | 裏面 | 裡面 |

Typing a code takes 0.1 to 6 ms on the PC (up to 37 ms for a three-syllable phrase in Jyutping). The phone will be slower;
the test program prints the time.

### The sizes

Compiled data (`build/`), the same on the phone (little-endian, 64-bit):

| File | Raw | Deflated (APK) | xz -9 |
| --- | ---: | ---: | ---: |
| `jyut6ping3.table.bin` (Jyutping and Yale share it) | 26.99 MB | 10.58 MB | 7.64 MB |
| `luna_pinyin.table.bin` | 13.01 MB | 6.17 MB | 4.59 MB |
| `jyut6ping3.reverse.bin` | 0.19 MB | 0.08 MB | 0.07 MB |
| `luna_pinyin.reverse.bin` | 0.25 MB | 0.09 MB | 0.08 MB |
| `tk_jyutping.prism.bin` | 0.23 MB | 0.05 MB | 0.03 MB |
| `tk_yale.prism.bin` | 0.11 MB | 0.02 MB | 0.01 MB |
| `tk_pinyin.prism.bin` | 0.03 MB | 0.01 MB | 0.01 MB |
| compiled schemas and `default.yaml` | 0.18 MB | 0.05 MB | 0.03 MB |
| OpenCC dictionaries (`.ocd2`) and configs, for all three variants | 0.15 MB | 0.06 MB | |
| **Total** | **40.2 MB** | **17.2 MB** | **12.4 MB** |

Without rime-cantonese's long-phrase list (`jyut6ping3.phrase`, 330,000 lines), the Jyutping table drops to 10.65 MB raw
(4.68 MB deflated). The first choices barely change: in five Cantonese phrases tested, only one changed order (有冇搞錯 moved
from second to first). The full list is proposed for 3.0, since it fits.

Rime opens its tables as files (memory-mapped), so they can't be read from inside the APK. The app copies them out once, on
the first use of a Chinese input method: 40 MB in the app's files. The other way is to ship the YAML sources (7.4 MB
deflated) and let Rime compile them on the phone; that took 12 s on the PC, so likely a minute on the phone, once.

### The test program

At `~/build/lang/rime-test/`, with a README and the exact adb commands for the Master:
- `tk-rime-test/rime_test`: arm64, static C++ runtime, no libraries beyond Android's own (3.4 MB).
- `tk-rime-test/shared/`: the schemas, compiled tables and OpenCC data (40 MB).
- `tk-rime-test/run.sh`: types every test code and prints the first 6 choices in each variant.
- `main.cc`: the source.

It runs from `/data/local/tmp/tk-rime-test` and is deleted afterwards. It wasn't run on the phone or under an emulator: an
Android binary needs Android's linker and libraries, which qemu-user in WSL doesn't have, and WSL has no root to install an
emulator. The PC build of the same program, with the same data, gives the results above.

## Calls to confirm

Calls made tonight, for the owner to confirm or change:
1. **Word list source:** AOSP's own list wherever it has one (24 languages, Apache-2.0); HeliBoard's Leipzig lists (CC BY 4.0)
   for the rest. Lists cut to their 100,000 most common words, without next-word pairs.
2. **Accents:** CLDR's main letters for each language, plus any letter its word list uses often (Afrikaans' ê ë, for
   instance), in CLDR's order.
3. **Cyrillic and Greek:** each country's standard layout on the key positions, as Android does for a USB keyboard, with
   the letters that don't fit on 26 keys on holds. Bulgarian uses the phonetic layout. Typing by sound (as the earlier plan
   said) stays an option for later.
4. **Ranking:** 43 languages in (a); Korean, Vietnamese and Thai in (b); right-to-left, Indic and Japanese in (c), not in
   3.0.
5. **Order:** Spanish, French, German first; then the big Latin languages; Chinese alongside; then Cyrillic and Greek; then
   the rest of (a); Korean and Vietnamese if time allows.
6. **Portuguese** is one input method with Brazil and Portugal as spellings, like English's. Norwegian Bokmål and Nynorsk
   are two input methods, since they're two written languages.
7. **Everything included in the app,** no downloads: about 160 MB against Play's 200 MB.
8. **Rime without plugins** (no Lua, no grammar model, no prediction), to keep it small.
9. **Rime's C++ runtime shared** with TitanKeys' `libc++_shared.so` (saves 1 MB), if the NDK versions allow.
10. **Chinese schemas trimmed:** no reverse lookups (Mandarin inside Cantonese, stroke, Cangjie), no emoji choices.
11. **Yale is the owner's schema** (rime-jyutping's rules) on rime-cantonese's newer word list, sharing Jyutping's table,
    rather than the owner's exact older word list (+10.7 MB). The phone test has both to compare.
12. **Variants:** Cantonese Hong Kong uses rime-cantonese's own set; Taiwan uses Taiwan characters for Cantonese and Taiwan
    characters plus phrases for Mandarin.
13. **rime-cantonese's long-phrase list kept** (6 MB more in the APK than without it).
14. **Compiled tables ship** and are copied out on first use (40 MB on the phone), rather than compiled on the phone.
15. **Simplified Mandarin uses luna_pinyin converted to Simplified** for now; rime-ice or rime-frost (GPL-3.0) are the
    candidates for its own list if the owner finds the choices off.
16. **Yale first:** the first Chinese input method, in the docs, the order of work and Settings' list.
17. **No default variant:** turning on the first Chinese input method opens the variant page, and it stays off until one
    is picked.

## How to rebuild

Everything is in WSL `atlas-build`, user `dev`:
- `~/build/lang/src/`: the sources, each a shallow clone at the commit above.
- `~/build/lang/rime-build/`: the superbuild. `out-host/` (PC), `out-android/` (`c++_static`), `out-android-shared/`.
- `~/build/lang/rime-data/`: the deployed data (`shared/` with sources, `nosrc/` without).
- `~/build/lang/gen/`: HeliBoard's lists, CLDR, Android's layouts and the analysis output (`out/dicts.json`).
- `~/build/lang/rime-test/`: the phone test.

The scripts that made them (build, data, analysis) are kept with the test, in `~/build/lang/rime-test/scripts/`.
