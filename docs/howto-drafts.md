# How-tos for each input method (drafts)

The owner (2026-11-17): "we shd have basic instructions in text in each language specifically." One short how-to per input
method in `android/assets/im/` (54 on 340455b4), for each language's page in Settings. They're in English, for people new
to the phone, and say only what's special to that input method. Typing, swiping, the switcher on a held Sym and the gestures
are the guide's job, so none of that is here.

The facts are from [languages.md](languages.md) and each `im.json` (holds, keys, rules) on 340455b4. C copies the lines into
`tools/im/howto.json`.

## Latin letters with accents

These share one template. Only the examples change, taken from each `im.json`'s `accents`.

> Hold a letter for its accents: {examples}. They show on the bar over the keys. Rest a finger on the key under the one you
> want, then press it. Shift gives the capital one. Holding gives accents here, so symbols are on Alt and Sym.

| id | {examples} | Extra line |
| --- | --- | --- |
| `es` | hold N for ñ, A for á, U for ú or ü | A sentence that opens with ¿ or ¡ still gets its capital. |
| `fr` | hold E for é, è, ê or ë, C for ç, O for ô or œ | |
| `de` | hold S for ß, A for ä, O for ö, U for ü | |
| `pt` | hold A for á, à, â or ã, C for ç, O for õ | Brasil or Portugal? Pick your spelling on this page. |
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
| `ca` | hold A for à or á, E for é or è, C for ç | |
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

The first-use hint for these seven is "Missing a letter? Hold a key to find it." in place of "Hold a letter for its
accents.", since a hold here gives letters the keys have no room for, not accents. Greek keeps the accents line.

Each how-to ends with the same line: "Rest a finger on the key under the letter on the bar, then press it."

### `ru` Russian

> The keys follow the Russian PC layout (ЙЦУКЕН): Q is й, W is ц, A is ф. The letters without a key of their own are held:
> ж or э on L, б or ю on M, х or ъ on P, ё on T.

### `uk` Ukrainian

> The keys follow the Ukrainian PC layout (ЙЦУКЕН): Q is й, W is ц, S is і. The letters without a key of their own are held:
> ж or є on L, б or ю on M, х, ї or the apostrophe on P, ґ on U.

### `be` Belarusian

> The keys follow the Belarusian PC layout (ЙЦУКЕН): Q is й, B is і, O is ў. The letters without a key of their own are
> held: э or ж on L, б or ю on M, х or the apostrophe on P, ё on T.

### `kk` Kazakh

> The keys follow the Russian PC layout (ЙЦУКЕН): Q is й, W is ц. Kazakh's own letters are held: ә on F, і on S, ң on Y,
> ғ on U, ұ or ү on E, қ on R, ө on J, һ on H. So are ж or э on L, б or ю on M, х or ъ on P, ё on T.

### `bg` Bulgarian

> Each key types the letter that sounds like it: A is а, B is б, D is д. The odd ones: Q is ч, W is ш, X is ж, Y is ъ.
> Hold M for ю or ь, and P for я or щ.

### `sr` Serbian, Cyrillic

> Each key types the letter that sounds like it: A is а, J is ј, Y is з. Q is љ, W is њ, X is џ, Z is ж. Hold L for ч or
> ћ, and P for ш or ђ.

### `mk` Macedonian

> Each key types the letter that sounds like it: A is а, J is ј, Y is з. Q is љ, W is њ, X is џ, Z is ѕ. Hold L for ч or
> ќ, and P for ш, ж or ѓ.

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
> between words, so the bar finds the word you're typing, and next words come after a space. Double Space types two
> spaces, as Thai has no full stop.

## Hindi (experimental)

### `hi` Hindi

> Type Hindi by how it sounds, in English letters: namaste becomes नमस्ते. Capitals are sounds of their own (namaH,
> maChiyoM), so Shift changes the letter. The bar starts with what Space will write, then words that fit, then a likely
> word if you slipped. Tap one to pick it, or swipe up for more. Double Space types "। ", and a word ending in ~ keeps its
> last virama (agar~).

## Hebrew (experimental)

### `he` Hebrew

> Writes right to left, and the keys follow the Israeli PC layout: A is ש, E is ק. Q types ת, and W types ', for words
> like צ'יפס. Hold P for ף, M for ץ, and Q for /. Hebrew has no capitals, so Shift types the same letter. The bar reads right
> to left too: the best word stays in the middle, the next one on the right.

## Korean

### `ko` Korean

> The keys follow Korean's 2-set layout (두벌식): Q is ㅂ, A is ㅁ, K is ㅏ. Shift gives ㅃ ㅉ ㄸ ㄲ ㅆ on Q to T, and ㅒ ㅖ on
> O and P. The syllable builds above the bar and goes in once the next letter can't join it. The bar starts with what Space
> will write, then words that fit, then a likely word if you slipped. Tap one to pick it, or swipe up for more.

## Vietnamese

### `vi` Vietnamese

> Type in Telex. Letters: aa is â, aw ă, ee ê, oo ô, ow ơ, uw ư, dd đ. Tones go after the word's letters: s sắc, f huyền,
> r hỏi, x ngã, j nặng, and z takes the tone off. Press the same key again to undo its mark. The bar starts with what Space
> will write, then words that fit, then a likely word if you slipped. Tap one to pick it, or swipe up for more.

## Chinese

The six share Chinese's bar. Each has its own first line, then the shared block.

### `yue-yale` Cantonese, Yale

> Type Cantonese in Yale, no tones needed: neihou for 你好. Z works for j, and n and l stand in for each other. A whole phrase can go in one run,
> no spaces: diuneiloumou.

### `yue-jyutping` Cantonese, Jyutping

> Type Cantonese in Jyutping, no tones needed: neihou for 你好. A whole phrase can go in one run, no spaces.

### `zh-pinyin` Mandarin, Pinyin

> Type Mandarin in Pinyin, no tones needed: nihao for 你好. A whole phrase can go in one run, no spaces.

### `zh-cangjie` Chinese, Cangjie

> Type each character's Cangjie code, up to five keys: the code shows as its radicals as you type (hqi is 竹手戈, for 我).

### `zh-quick` Chinese, Quick

> Type Quick (速成): each character is the first and last keys of its Cangjie code, two at most. Many characters share a
> code, so you'll pick from the bar more often than in Cangjie.

### `zh-zhuyin` Mandarin, Zhuyin (Hsu layout)

> Type Zhuyin on the Hsu (許氏) layout, not the Daqian one: each key types the symbol that sounds like it, and some keys have
> two. Tones are optional: D for ˊ, F for ˇ, J for ˋ, S for ˙. The code shows as Zhuyin as you type (ㄋㄧˇ ㄏㄠˇ).

**Then, for all six:**

> First, pick your characters on this page: Simplified, Hong Kong or Taiwan. Your code floats above the bar and the
> choices sit over the top row. Space types the first one, and a tap picks any. For more, press Shift, swipe up on the
> keys, or tap ⋯. Enter or a swipe turns the page. Next words follow each pick. Enter types the code as letters, and double
> Space types 。.

## Japanese (experimental)

### `ja` Japanese

> Type Japanese in romaji: kyou becomes きょう, nn ん, and - ー. Choices sit over the top row, whole sentences first. Space
> types the first one, and a tap picks any. For more, press Shift, swipe up on the keys, or tap ⋯. Enter or a swipe turns
> the page. Next words follow each pick. Enter types the romaji as letters, and double Space types 。.

## To check before they go in

- **Yale's z, n and l:** from `tk_yale.schema.yaml` (`derive/^j/z/`, and n and l both ways, initials only).
- **Quick:** "first and last keys of its Cangjie code" is Quick's standard rule; languages.md only says "two letters a
  character".
- **Cangjie's example:** hqi is languages.md's own (竹手戈), and 我 is its Cangjie code. Worth a type on the phone.
- **Zhuyin:** "some keys have two" follows the schema's comments (ㄐㄓ on J, ㄑㄔ on V, ㄒㄕ on C, ㄥㄦ on L).
- **Accented capitals:** "Shift gives the capital one" follows "with Shift or Caps on, the accents show in capitals".
