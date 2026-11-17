# How-tos for each input method (drafts)

The owner (2026-11-17): "we shd have basic instructions in text in each language specifically." One short how-to per input
method in `android/assets/im/`, for Settings > Languages. In English, for people new to the phone, and only what's special
to that input method. Typing, swiping, the switcher on a held Sym and the gestures are the guide's job, so none of that is
here.

The facts are from [languages.md](languages.md) and each `im.json` (accents, keys, rules). G wires them in; nothing here is
in the app yet.

## Latin letters with accents

These share one template. Only the examples change, taken from each `im.json`'s `accents`.

> Hold a letter for its accents: {examples}. They show on the bar over the keys. Rest a finger on the key under the one you
> want, then press it. Shift gives the capital one. Holding gives accents here, so symbols are on Alt and Sym.

| id | {examples} | Extra line |
| --- | --- | --- |
| `es` | hold N for ñ, A for á, U for ú or ü | A sentence that opens with ¿ or ¡ still gets its capital. |
| `fr` | hold E for é, è, ê or ë, C for ç, O for ô or œ | |
| `de` | hold S for ß, A for ä, O for ö, U for ü | |
| `pt` | hold A for á, à, â or ã, C for ç, O for õ | Brasil or Portugal? Pick your spelling in the language's page. |
| `it` | hold E for é or è, A for à, O for ò | |
| `nl` | hold E for é or ë, I for ï, O for ö | |
| `pl` | hold A for ą, L for ł, Z for ź or ż | |
| `tr` | hold I for ı or î, G for ğ, S for ş | Shift + I gives İ, with its dot. For I without it, hold I with Shift on and pick it. |
| `sv` | hold A for å or ä, O for ö | |
| `da` | hold A for æ or å, O for ø | |
| `nb` | hold A for æ or å, O for ø | |
| `nn` | hold A for æ or å, O for ø | |
| `fi` | hold A for ä or å, O for ö | |
| `cs` | hold C for č, R for ř, U for ú or ů | |
| `sk` | hold C for č, L for ĺ or ľ, O for ó or ô | |
| `ro` | hold A for ă or â, S for ș, T for ț | |
| `hu` | hold O for ó, ö or ő, U for ú, ü or ű | |
| `hr` | hold C for č or ć, D for đ, Z for ž | |
| `bs` | hold C for č or ć, D for đ, Z for ž | |
| `sr-latn` | hold C for č or ć, D for đ, Z for ž | |
| `sl` | hold C for č, S for š, Z for ž | |
| `lv` | hold A for ā, E for ē, K for ķ | |
| `lt` | hold A for ą, E for ę or ė, U for ų or ū | |
| `et` | hold O for õ or ö, A for ä, U for ü | |
| `ca` | hold A for à, E for é or è, C for ç | |
| `gl` | hold N for ñ, A for á, O for ó | |
| `is` | hold D for ð, T for þ, A for á or æ | |
| `af` | hold E for é, ê or ë, O for ó or ö | |
| `eo` | hold C for ĉ, G for ĝ, U for ŭ | |
| `az` | hold E for ə, I for ı, G for ğ | |
| `ga` | hold A for á, E for é, O for ó | |
| `eu` | hold N for ñ, C for ç | |

## Latin letters without accents

### `id` Indonesian

> Types the way English does: no accents, and nothing to hold for them. Hold a letter for its symbol, as in English.

## Cyrillic

### `ru` Russian

> The keys follow the Russian PC layout (ЙЦУКЕН): Q is й, W is ц, A is ф. The letters without a key of their own are held:
> ж or э on L, б or ю on M, х or ъ on P, ё on T. Rest a finger on the key under the letter on the bar, then press it.

### `uk` Ukrainian

> The keys follow the Ukrainian PC layout (ЙЦУКЕН): Q is й, W is ц, S is і. The letters without a key of their own are held:
> ж or є on L, б or ю on M, х or ї on P, ґ on U. Rest a finger on the key under the letter on the bar, then press it.

### `be` Belarusian

> The keys follow the Belarusian PC layout (ЙЦУКЕН): Q is й, B is і, O is ў. The letters without a key of their own are
> held: ж or э on L, б or ю on M, х on P, ё on T. Rest a finger on the key under the letter on the bar, then press it.

### `kk` Kazakh

> The keys follow the Russian PC layout (ЙЦУКЕН): Q is й, W is ц. Kazakh's own letters are held: ә on F, і on S, ң on Y,
> ғ on U, ү or ұ on E, қ on R, ө on J, һ on H. So are ж or э on L, б or ю on M, х or ъ on P, ё on T. Rest a finger on the
> key under the letter on the bar, then press it.

### `bg` Bulgarian

> Each key types the letter that sounds like it: A is а, B is б, D is д. The odd ones: Q is ч, W is ш, X is ж, Y is ъ.
> Hold M for ь or ю, and P for я or щ.

### `sr` Serbian, Cyrillic

> Each key types the letter that sounds like it: A is а, J is ј, Y is з. Q is љ, W is њ, X is џ, Z is ж. Hold L for ч or
> ћ, and P for ш or ђ.

### `mk` Macedonian

> Each key types the letter that sounds like it: A is а, J is ј, Y is з. Q is љ, W is њ, X is џ, Z is ѕ. Hold L for ч or
> ќ, and P for ш, ѓ or ж.

## Greek

### `el` Greek

> The keys follow the Greek PC layout: A is α, V is ω, U is θ. W types ς, the final sigma, and Q types ;, Greek's question
> mark. Hold a vowel for its accent: ά, έ, ή, ί, ό, ύ, and ώ on V. Hold I or Y for ϊ or ϋ too.

## Georgian

### `ka` Georgian

> Each key types the Georgian letter that sounds like it: A is ა, B is ბ. Georgian has no capitals, so Shift types a
> second letter on seven keys: ჭ on W, ღ on R, თ on T, შ on S, ჟ on J, ძ on Z, ჩ on C. No accents here, so holding a key
> still gives its symbol.

## Thai (experimental)

### `th` Thai

> The keys follow Thai's Kedmanee layout, and Shift gives each key's second letter. The keys a PC has and the Titan
> doesn't, digits included, are held: hold a key for its third letter, and Shift + hold for its fourth. Thai has no spaces
> between words, so the bar finds the word you're typing. Double Space types two spaces, as Thai has no full stop.

## Hindi (experimental)

### `hi` Hindi

> Type Hindi by how it sounds, in English letters: namaste becomes नमस्ते. Capitals are sounds of their own (namaH,
> maChiyoM), so Shift changes the letter. The word builds above the bar, and words that fit show on the bar. Double Space
> types "। ". To keep a word's last virama, end it with ~ (agar~).

## Hebrew (experimental)

### `he` Hebrew

> Writes right to left, and the keys follow the Israeli PC layout: A is ש, E is ק. Q types ת, and W types ', for words
> like צ'יפס. Hold P for ף, M for ץ, and Q for /. Hebrew has no capitals, so Shift types the same letter. The bar reads right
> to left too: the best word stays in the middle, the next one on the right.

## Korean

### `ko` Korean

> The keys follow Korean's 2-set layout (두벌식): Q is ㅂ, A is ㅁ, K is ㅏ. Shift gives ㅃ ㅉ ㄸ ㄲ ㅆ on Q to T, and ㅒ ㅖ on
> O and P. Type the letters one by one: the syllable builds above the bar and goes in once the next letter can't join it.
> Backspace takes off the last letter typed.

## Vietnamese

### `vi` Vietnamese

> Type in Telex. Letters: aa is â, aw ă, ee ê, oo ô, ow ơ, uw ư, dd đ. Tones go after the word's letters: s sắc, f huyền,
> r hỏi, x ngã, j nặng, and z takes the tone off. Press the same key again to undo its mark. The word builds above the bar,
> and words that fit show on the bar. Backspace takes off the last key.

## Chinese

The three share Chinese's bar. Only the first line differs.

### `yue-yale` Cantonese, Yale

> Type Cantonese in Yale, with no tones: neih hou for 你好.

### `yue-jyutping` Cantonese, Jyutping

> Type Cantonese in Jyutping, with no tones: nei hou for 你好.

### `zh-pinyin` Mandarin, Pinyin

> Type Mandarin in Pinyin, with no tones: ni hao for 你好.

**Then, for all three:**

> First, pick your characters in the language's page: Simplified, Hong Kong or Taiwan. Your code floats above the bar and
> the choices sit over the top row. Space types the first one. For another, tap it, or flick up from the key under it. Sym
> shows more choices: Enter or a swipe up turns the page. Enter types the code as letters. Double Space types 。.

## Japanese (experimental)

### `ja` Japanese

> Type Japanese in romaji: kyou becomes きょう, nn ん, and - ー. Choices sit over the top row, whole sentences first. Space
> types the first one. For another, tap it, or flick up from the key under it. Sym shows more choices: Enter or a swipe up
> turns the page. Enter types the romaji as letters. Double Space types 。.

## To check before G wires these in

- **Picking from the row:** languages.md's "Chinese on the bar" (2026-11-17) says Space, a tap, or a flick up from the key
  under a choice, with Sym for the bigger menu while a code is typed. Its Japanese section still says "hold P for the bigger
  menu" and "Shift picks". The drafts follow the newer Chinese section.
- **"Swipe up for more":** swipes on the keys never turn the row's pages. They do turn the bigger menu's, so the drafts say
  "Sym shows more choices: Enter or a swipe up turns the page".
- **Vietnamese and Hindi choices:** languages.md says Space alone writes Telex's word as typed, and that Hindi's Space goes
  through the rules. Whether their choices are picked like Chinese's (tap or flick up) needs a look on the phone, so the
  drafts only say "words that fit show on the bar".
- **Jyutping with no tones:** rime-cantonese takes tones as letters (v, x, q) but doesn't need them. To confirm on the phone.
- **Accented capitals:** "Shift gives the capital one" follows "with Shift or Caps on, the accents show in capitals".
