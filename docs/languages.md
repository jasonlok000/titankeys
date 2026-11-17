# Language support (3.0 plan)

The plan for typing in languages other than English, set with the owner in
[issue #56](https://github.com/jasonlok000/titankeys/issues/56) (2026-11-14 to 2026-11-16). The study behind the numbers
here is [docs/studies/56-languages.md](studies/56-languages.md). This page is the design to build in M2.

## The goal

As many languages as we can, working first. 3.0 ships them "generic": built from open data, without waiting for a native
speaker. Native speakers refine their language later, through a pull request. Every choice below favours ease of building
and of contributing.

Ground rules already set in #56:
- **Holding Sym is reserved** for switching input methods (since 2026-11-11).
- **Rime for Chinese.** Cantonese and Mandarin use [Rime](https://rime.im)'s engine.
- **English doesn't change.** It keeps swiping, the next word, autocorrect and holding for symbols, and becomes the first
  input method.
- **Other input methods are basic:** no swiping, no next-word guesses, and autocorrect is optional and off by default.
- **Everything is in the app.** No downloads for typing.

## What's in 3.0

From the study's ranking:
- **Data-only input methods (43):** Latin script (Spanish, French, German, Portuguese, Italian, Dutch, the Nordic
  languages, Polish, Czech, Slovak, Hungarian, Romanian, Croatian, Slovenian, Bosnian, Serbian Latin, Turkish, Catalan,
  Galician, Basque, Estonian, Latvian, Lithuanian, Icelandic, Irish, Afrikaans, Indonesian, Esperanto, Azerbaijani), and
  Cyrillic, Greek and Georgian (Russian, Ukrainian, Belarusian, Bulgarian, Serbian, Macedonian, Greek, Kazakh, Georgian).
- **Chinese through Rime:** Cantonese in Yale, the owner's input method and the first Chinese one, then Jyutping, and
  Mandarin in Pinyin, with the Chinese variant setting.
- **Korean and Vietnamese,** each through a small engine of TitanKeys' own ("Korean and Vietnamese" below).
- **Thai** (experimental), Kedmanee with a third layer of held keys and a word break for suggestions ("Thai" below).
- **Hindi** (experimental), typed by sound through Wikimedia's jquery.ime rules ("Hindi" below).
- **Hebrew** (experimental), the first right-to-left language ("Right to left: Hebrew" below).
- **Japanese through Mozc,** experimental, on Chinese's bar ("Japanese" below; owner, 2026-11-16: "starting with japanese
  hindi thai etc, yes go ahead").
- **Not in 3.0:** handwriting, swiping and a next-word model in other
  languages, and translating the app, the guide and the playground.

The order: Spanish, French and German first to prove the framework; the other big Latin languages; Chinese alongside; then
Cyrillic and Greek; then the rest.

## Words

- **Language:** what Settings > Languages lists and the held Sym's switcher shows first (owner, 2026-11-16: the Gboard way).
  Serbian has two input methods (Cyrillic and Latin), Chinese three (Yale, Jyutping, Pinyin); every other language has
  one. Each index entry names its language (`make.py`'s `LANGUAGES`). Every language but English and Chinese is marked
  experimental in Settings.
- **Input method:** one way of typing, such as English, Spanish, Cantonese (Yale) or Mandarin (Pinyin). A language can have
  several. The `input_methods` setting lists the ones that are on; a language is on when any of its input methods is, so
  settings and backups from before languages read the same.
- **Variant:** a spelling of the same input method's text. English has British, American and Canadian. Portuguese has
  Brazil and Portugal. Chinese has Simplified, Hong Kong and Taiwan, shared by every Chinese input method.
- **Engine:** code an input method uses to turn keys into text with choices. English's engine is today's code. Rime is
  Chinese's. Most input methods have none: their keys type letters directly.

## The input method file

Each input method is one folder in the app's assets, `android/assets/im/<id>/`, with a JSON file and an optional word list:

```
android/assets/im/es/
  im.json          what the input method is, its keys, accents and rules
  words.combined   its word list (AOSP's format, the same as english.combined), at most 100,000 words
  NOTICE           where the word list comes from, and its licence
```

`im.json`:

```json
{
  "id": "es",
  "name": "Español",
  "nameEnglish": "Spanish",
  "label": "ES",
  "locale": "es",
  "script": "Latn",
  "keys": "qwerty",
  "accents": {
    "a": "á", "e": "é", "i": "í", "n": "ñ", "o": "ó", "u": "úü"
  },
  "words": "words.combined",
  "variants": null,
  "rules": {
    "capitalise": true,
    "sentenceEnd": ".?!",
    "openingMarks": "¿¡",
    "apostrophes": false
  },
  "engine": null,
  "source": {
    "letters": "CLDR 49 exemplarCharacters",
    "words": "AOSP LatinIME es_wordlist.combined (Apache-2.0)"
  }
}
```

The fields:

| Field | What it is |
| --- | --- |
| `id` | The folder's name: a BCP 47 tag, lower case (`es`, `pt`, `sr-latn`, `yue-yale`) |
| `name`, `nameEnglish` | Its own name, shown in the overlay and Settings, and the English one for Settings' second line |
| `label` | Two or three characters for the overlay and the bar: `ES`, `FR`, `RU`, `粵`, `耶`, `拼` |
| `locale` | Android's locale for its keyboard-list entry, capitals and sorting (`tr` gives i ↔ İ) |
| `script` | ISO 15924: `Latn`, `Cyrl`, `Grek`, `Geor`, `Hani` |
| `keys` | `qwerty` (the letters as printed) or a map from each of the 26 keys to its letter, lower case: `{ "q": "й", "w": "ц", ... }`. Upper case comes from the locale. A key can have a different letter with Shift, written `"w": "წ/ჭ"`, for scripts without capitals |
| `accents` | For each of the 26 letter keys, the letters held on it, in the order they show on the bar: at most 10, one per key column. Only letter keys can be held, since the Titan's punctuation is on Alt |
| `words` | The word list's file, or `null` |
| `variants` | `null`, or a list of `{ "id", "name", "words" }` for spellings with their own word lists (Portuguese: `pt-BR`, `pt-PT`) |
| `rules` | `capitalise`: the script has capitals, after a sentence's end and with Shift (Georgian's `false`: no sentence capitals, and Shift types the key's Shift letter or its own letter); the characters that end a sentence, marks that open one (¿¡), and whether apostrophes are part of words (French: `l'eau`) |
| `engine` | `null`, `"rime"` with its schema (`{ "rime": "tk_yale" }`), or the name of a built-in engine (`"hangul"`, `"telex"`) |
| `source` | Where the letters and words come from, for the licences page and for contributors |

English is the built-in input method `en`. It keeps its own code and assets and gets an `im.json` only for its name,
label, locale and variants (British, American, Canadian).

Rules for a valid input method, checked by `tools/checks/im.test.ts` on every `bun run check`:
- the JSON parses, and every field has the right type;
- every letter in `keys` and `accents` is in CLDR's letters for the locale (main or auxiliary), or listed in an `extra`
  field with a reason;
- every letter the word list uses often (20 times in its top 50,000 words) can be typed, from a key or a hold;
- the word list parses, has at most 100,000 words, and its file is at most 4 MB (Cyrillic and Greek take two bytes a letter);
- `NOTICE` exists and names a licence on the allowed list (Apache-2.0, MIT, BSD, CC0, CC BY 4.0, CC BY-SA 4.0, LGPL,
  GPL-3.0, GPL-2.0-or-later, Unicode);
- labels are unique, and at most 3 characters wide.

A script, `tools/im/make.py <data dir> [id...]`, makes an input method's folder from CLDR's letters and a word list, so adding a
data-only language is one command plus a check by eye. Where the data comes from is in the study.

## How an input method declares itself

- **In the app:** a generated index, `android/assets/im/index.json`, lists every folder's `id`, `name`, `label`, `locale` and
  `script`. `tools/im/make.py` writes it from the folders, with Android's keyboard list, so adding a folder and running it is all it takes. The keyboard
  reads only the index at start, and an input method's own files when it's switched on.
- **In Android's keyboard list:** one `<subtype>` per input method in `input_method.xml`, written by the same script:

  ```xml
  <subtype android:label="@string/im_es" android:imeSubtypeLocale="es"
      android:languageTag="es" android:imeSubtypeMode="keyboard"
      android:imeSubtypeExtraValue="im=es" android:subtypeId="1234567890" />
  ```

  - `subtypeId` is fixed per input method (a hash of its `id`), so Android keeps the user's choices across updates.
  - **One entry in Android's list** (owner, 2026-11-16: the list is TitanKeys and the other keyboards; languages switch
    inside TitanKeys). Only the input method typing now is enabled, through
    `InputMethodManager.setExplicitlyEnabledInputMethodSubtypes` (API 34), so Android's list shows TitanKeys once, under
    its name. The keyboard enables the new one on every switch, and checks at every field start that Android's list is
    that one entry; Settings does it when the input methods change. Alt + Sym still opens Android's list.
  - When the input method changes, the keyboard calls `InputMethodService.switchInputMethod(id, subtype)`, so apps see the
    language in `getCurrentInputMethodSubtype()` and pick their own spell checker and hints from it. This is #76's label.
  - Which types as the keyboard starts: the `current_input_method` setting, unless Android's list was switched to
    another entry since the keyboard last set it (`KeyboardListSync`, kept in `keyboard_list`, which isn't backed up).
    Android keeps its own entry and says it as the keyboard starts, so after a restore, or a change in Settings while the
    keyboard was off, its old entry would otherwise undo the setting.
  - To check on the phone first: that switching subtypes this way doesn't restart the input view or flash the keyboard.
    If it does, the keyboard switches inside itself and only updates the subtype.
  - English's entry follows its spelling: `en_GB`, `en_US` or `en_CA`.

## Accents and the picker on the bar

**Holding a letter shows its accents on the bar,** in the input method's order:
- The held letter is typed at once, as today. At the hold time, the bar shows the letter's accents over the keys, one per
  key, starting above the held key and spreading out to fit.
- **Picking is the word menu's way:** rest a finger on a key to mark the accent above it, press the key to pick it. The
  accent replaces the typed letter. Releasing the held key without a press keeps the plain letter.
- With Shift or Caps on, the accents show in capitals.
- The first time an input method with accents is used, the bar says once: "Hold a letter for its accents."
- In an input method with accents, **hold for symbols is off on every key**, so a hold always means one thing. Symbols stay
  on Alt + key and the Sym panel. Input methods without accents keep hold for symbols.

**The picker** is one component for accents and for Chinese:
- A row of choices over the keys, each with the key under it. Keys under no choice do nothing while it's open.
- Accents fit on one row. Chinese, and any engine with choices, has its own row and a bigger menu: see "Chinese on the
  bar".

**Accents from the word list** (input methods with a word list):
- The bar's suggestions ignore accents when matching: typing "cafe" offers **café**.
- Autocorrect fills in an accent only when the plain form isn't a word itself (si / sí stays as typed). Autocorrect is off
  by default, so by default it's suggestions only.

The bar, its hover and the picking come from the word menu's code (see "Where it goes in the code").

## Chinese on the bar

The owner's design (2026-11-16, `docs/owner-decisions-2.1.md`: "Chinese choices" rounds 1 to 3, "Chinese picking, the
concept test" and "The rest of the Chinese proposal agreed"). It holds for every Chinese input method (Yale, Jyutping,
Pinyin); the code is `ChineseRow`, `ChineseMenu`, `ChineseKeys` and `EnglishOffer` in `keyboard/`.

**The bar while a code is typed:**
- **The code** floats on a thin line just above the bar's left edge, muted, only while a code is typed. It's drawn in the
  see-through layer over the app (the word menu's effects layer), so the app never resizes. With no code, nothing floats,
  and the bar shows its suggestions: no language name, no tint ("keep it using the orange black theme").
- **The language mark at Q:** the input method's label (耶, 粵, 拼) in the accent over Q, as Shift's arrow and Caps show
  today, while the choices are on the bar.
- **The choices sit over W to O,** from the left, each as wide as it needs: one key for one or two characters, two keys for
  three or four (屌你老母 over two), and so on (`ChineseRow.keys`: a Chinese character counts two, a Latin letter one, four to
  a key). They fill W to O in order; a choice too wide for the room left waits for the bigger menu, and shorter ones after it
  still take the row (a Japanese sentence is wide, and its parts follow it). There's
  no paging in the row.
- **P has no choice.** It shows a small "more" cue (`⋯`) when the engine has more choices than the row shows.

**Picking from the row,** all of these at once:
- **Space** picks the first choice. A resting finger doesn't change that ("no marking first").
- **A tap on the screen** on a choice picks it.
- **Shift + the key under a choice** picks it (the top two rows, W to O and S to L, as the word menu's buttons). Shift alone
  does nothing special: Chinese has no capitals, and English is its own keyboard.
- **Holding the key under a choice** picks it (a concept test, owner: "we can remove it if it doesn't work well"). The letter
  the press typed into the code goes again first. Hold for symbols is off in every Chinese input method, so a hold means
  one thing.
- **A flick up on the touchpad** from the key under a choice picks it (also a concept test). Swipes on the keys never turn
  the row's pages.
- A resting finger still marks the choice over its key, in the word menu's pill, to show what Shift, a hold or a flick
  would pick.

**The bigger menu: hold P.** Like the Sym panel's emoji and symbol pages, in the panel's place:
- The choices fill the three letter rows over the keys (W to O, S to L, Z to M), each as wide as it needs, as in the row.
  P shows the page ("2/5").
- A press of the key under a choice picks it, and so does a tap on the screen. A resting finger highlights the choice over
  its key.
- Enter turns to the next page and Backspace to the one before, the Sym panel's own keys; a swipe up on the keys turns to
  the next and down to the one before.
- Sym is the ✕: it closes the menu, and so does Back. A pick closes it too.
- Only holding P opens it: no flick from P, no swipe ("actions told apart only by speed are hard to learn").

**Keys while a code is typed:**
- **Enter** types the code as letters. **Backspace** takes off the last code letter.
- **Punctuation:** Chinese full-width marks by default (，。？！：；、 and Hong Kong's 「」 for quotes in Hong Kong and Taiwan
  characters; “” in Simplified), from Rime's punctuator, Alt's symbols included. With a code pending, a punctuation key types
  the first choice, then the mark. Settings: "Plain punctuation" types ASCII marks instead (Rime's `ascii_punct`).
- **Digits stay plain** (1, 2, 3, never full-width). With a code pending, a digit types the first choice, then the digit.
- **English words:** the typed letters are offered as an English word, as the second choice, never the first, only when
  they're a word in English's list and Rime can't read them as whole syllables: Rime reads nearly any letters through
  abbreviations, so the test is Rime's own split of the code having a piece that isn't a whole syllable (`EnglishOffer`).
  "hello" (Rime: he l lo) is offered; "man" and Pinyin's "sun" aren't. Picking it types the word as typed and clears the code.

**Not in the row or the menu:** the variant isn't shown (it's in Settings). Shift's own bar never shows in Chinese, so it
doesn't cover the choices; Alt's and Ctrl's still do while they're down, for their symbols and shortcuts.

**Checks and renders:** `tests/chinese` checks the places, the pages, the keys that pick, the marks and the English word;
`tests/rime` the engine's keys; `tools/checks/chinese.test.ts` keeps the keyboard's wiring to this design. `bash
tests/chinese-render/run.sh [folder]` draws the row and the menu with the app's own rules and Rime's real choices.
**Not yet:** a Settings row for "Plain punctuation" (the setting is `chinese_plain_punctuation`), and the phone test.

## Switching: the overlay on a held Sym

- **Holding Sym opens the switcher** over the top row, like the word menu: each language that's on, its label over W to O
  (8 places), the current one marked. They're spread evenly: two take four keys each, three take two each with the rest
  empty, four take two each, more take one (owner, 2026-11-16).
- **Picking a language with more than one input method on** (Serbian, Chinese) shows a second row with them, spread the
  same way, the one typing now marked; picking the language typing now does the same. A language with one input method
  switches to it. With one language on that has several, the switcher opens on its row. There's no hold-Q.
- It stays open after Sym is let go. Rest a finger to mark, press to switch. Back or Sym closes it with no change. A short
  Sym press still opens the Sym panel, as today.
- The order is Settings > Languages' order.
- With only one input method on, holding Sym does nothing.
- The experimental mark stays in Settings: the switcher shows labels only.
- **Two languages on:** a held Sym switches to the other one at once, with no switcher, to the input method last used in
  it (or its first), as `SwitchOverlay.toggle` decides (owner, 2026-11-16). Three or more open the switcher. The input
  methods last used are remembered while the keyboard runs.
- After a switch, the bar says nothing (owner, 2026-11-16: "I don't like the XX it is wording"): the language mark at Q
  shows which types. A screen reader hears "Typing in Spanish" (F: draft).
- Alt + Sym keeps going to Android's next keyboard, as today.

## Settings > Languages

Languages, then input methods, the Gboard way (owner, 2026-11-16). Settings' Languages section, first in Settings, has one row, Languages,
with the names of the ones that are on.

**Languages** (`app/settings/languages/index.tsx`):
- Every language, grouped by script; the ones that are on sit at the top in the switcher's order. Each row shows its
  English name, with "· experimental" for every language but English and Chinese (F: draft), or the input methods that
  are on when it has several.
- **A language's page** (`app/settings/languages/language.tsx`, the route's `language` parameter): its input methods, each with a
  switch, and its own options: English's Spelling, Chinese's Characters. An experimental language says once, under its
  name, what that means (F: draft).
- English can't be turned off while it's the only one.
- **Names:** each row shows the input method's own name, which is CLDR's name for its language in that language,
  capitalised (Français, Русский, Indonesia); `tools/im/make.py` stops if one differs, and `tests/languages` checks the
  files. Chinese input methods are named for their spelling (漢語拼音, 粵拼, 耶魯拼音). Ink draws the page with the app's
  text fonts (`assets/fonts`, built by `tools/im/fonts.py`): Inter with Latin and its extensions, Greek and Cyrillic, and
  Noto Sans Georgian's letters, and the few Hangul and Chinese characters the app's text uses, from Noto Sans CJK (Ink's
  fallback to Android's CJK font didn't reach the Titan 2 Elite, so 한국어 and the Chinese names were blank). A letter
  none of them has is left blank, which is why Русский was blank while the fonts were Latin-1 only; `tests/languages`
  fails on a name with a letter the fonts lack, and `tools/im/fonts.py` adds it.
- **Headers:** each script has a header (Latin letters, Cyrillic, Greek, Georgian, Korean, Chinese); `tools/checks/im.test.ts`
  fails on a script without one, which would show its four-letter code.
- At most 8 on, the overlay's places. The 9th switch says why it won't turn on: "Eight at a time. Turn one off first."
- Each input method's page shows its name, its source and licence, its variants when it has them (Portuguese), and
  "Fix typos as you type" for it, off by default.

**Chinese characters** (`app/settings/chinese.tsx`), a ChoicePage like English spelling:
- Traditional (Hong Kong), Traditional (Taiwan), Simplified (mainland China).
- One setting for every Chinese input method.
- **Traditional is 正體 (owner, 2026-11-16: "For traditional chinese, call it 正體中文 instead of 繁體中文").** Wherever
  TitanKeys names Traditional characters in Chinese, it's 正體中文 (香港正體, 臺灣正體 on Rime's variant switch), never
  繁體中文. The Settings page's English labels stay.
- **No default (owner, 2026-11-16): you choose.** The first time the keyboard switches to a Chinese input method with no
  variant picked, it opens this page, once (`ChineseSetup`; Reset most things clears the once with the variant). Until a
  variant is picked, the bar's only choice says to pick one. Nothing is guessed from the phone's region.
  The page's subtitle: "Pick how your characters come out. You can change it any time."

The settings live in `typing_settings` with the others: `inputMethods` (the ordered list of ids that are on),
`imVariant.<id>`, `imAutocorrect.<id>` and `chineseVariant` (`zh_hk`, `zh_tw`, `zh_hans`).

## How Rime plugs in

- **The library:** librime with OpenCC, built from source with Trime's CMake files and pins, without plugins, as
  `android/jniLibs/arm64-v8a/librime_tk.so` (3.1 MB, sharing the app's `libc++_shared.so` from NDK r27c). It builds in WSL
  with `native/rime/build.sh`, and is checked in prebuilt, so a normal build doesn't need the NDK. Two runs give the same
  bytes. The sources, versions and licences: `native/rime/SOURCES.md`.
- **JNI:** a thin layer, `native/rime/rime_jni.cc`, registered on `com.titankeys.rime.RimeNative`: `start(sharedDir, userDir)`,
  `selectSchema`, `setOption`, `key(keysym, mask)`, `state()` (the text to commit, the code, how Rime spells it, the page and
  its choices), `pick(index)` on the page, `choices(start, count)` and `pickAt(index)` across every page (the row and the
  bigger menu read past Rime's 8 a page), `clear()`. Trime's `rime_jni` is the reference.
- **The engine:** `com.titankeys.rime.RimeEngine`, made by name with a `(Context)` constructor (`InputEngines`).
- **Every engine by name:** `EngineClasses` (`InputEngine.kt`) names each engine's class: Rime's, Korean's and Telex's.
  R8 can't see a class made by name, and left Korean's and Telex's out of the APK, so they typed English on the phone;
  `android/proguard-rules.pro` keeps every `InputEngine` and its `(Context)` constructor. `tests/languages` checks that each
  engine an `im.json` names is in `EngineClasses`, and `bash tests/languages/apk.sh <apk>` that a built APK has each class
  and constructor, read from its minified dex.
- **Data:** `android/assets/rime/` holds the schemas (`tk_yale`, `tk_jyutping`, `tk_pinyin`, `tk_variants`), Rime's shared
  files, the compiled tables and OpenCC's dictionaries, built by `native/rime/data.sh` on the PC, 8 choices a page.
  `android/assets/rime.sha256` lists them. On the first use of a Chinese input method, `RimeEngine.start()` copies them to
  `files/engines/rime/shared` (40 MB; Rime opens its tables as files) and checks each file, in the background. Rime
  never deploys on the phone: it opens the shipped tables as they are and compiles nothing. The
  user's learned words go to `files/engines/rime/user`. Back up everything takes them as Rime's own text snapshots
  (`RimeBackup`), and a restore leaves them in Rime's sync folder, merged in the next time a Chinese input method starts.
  While an engine's input method takes over half a second to start, the bar says it's getting ready.
- **Variants:** the app sets exactly one of `zh_hk`, `zh_tw`, `zh_hans` on the engine whenever it starts or the setting
  changes. With none on, the code still builds, but the only choice says to pick one, and nothing Chinese is typed.
- **Keys:** a letter goes to Rime, which returns the code and the choices for the picker. Space picks the first choice.
  Enter types the code as typed letters. Backspace deletes from the code, or from the text when the code is empty.
  Punctuation goes through Rime's punctuator, which gives Chinese marks (，。); digits never do. The rest, as the bar works
  with them: "Chinese on the bar".
- **Engines in general:** Rime, Korean and Telex implement one interface (`keyboard/InputEngine.kt`): a key, Space, Enter
  or Backspace gives an `EngineState` with the text to commit, the text being built (floating above the bar) and the
  picker's choices. A state that isn't `handled` may still commit: the keyboard writes that first, then handles the key as
  usual (Korean's syllable before a space, Rime's code before a capital). Data-only input methods use no engine.

## Thai

- **Keys: Kedmanee** (TIS 820-2538), read by `tools/im/make.py` from xkeyboard-config's `symbols/th`. The Titan's 26 letter
  keys keep Kedmanee's letter and Shift letter. The 21 keys a PC has and the Titan doesn't (the number row, the punctuation
  keys, ` and \) are a third layer: holding a letter key types the missing key's letter, and Shift + hold its Shift letter.
  The number row sits over Q to P, then S and D; backslash on A, the bracket keys on F and G, ; and ' on H and J, ` on Z,
  and , . / on B, N and M. An `im.json` key is then a list, `[letter, Shift, held, Shift held]`, as Kedmanee uses /.
  A held Thai key types its third letter whatever hold for symbols is set to; Alt keeps its symbols.
- **Measured** (`python3 -I tools/im/measure.py <data> th`): all 94 of Kedmanee's places are on the keys; every one of the
  word list's 87,336 words can be typed; 27.1% of key presses are holds and 9.6% use Shift, weighted by each word's count.
- **Words:** PyThaiNLP's list (CC0), via HeliBoard's aosp-dictionaries. Thai writes no spaces between words, so the word
  being typed is found in the list (`WordBreak`, `"wordBreak": true` in `rules`): the run of letters before the cursor is
  split into the fewest words, and the last piece is the one the bar completes. After one of the 2,000 commonest words,
  `tests/languages` finds the next one 97.8% of the time when whole and 84.3% at any start (a letter or two may also
  continue the word before: แม้ + น is แม้น). Folding for the bar now keeps marks outside U+0300 to U+036F, so Thai's
  vowels and tones stay part of a word.
- No sentence capitals or apostrophes. Settings shows ไทย with Noto Sans Thai's letters; Ink places no marks, so only names.

## Hindi

- **Typing by sound:** Wikimedia's jquery.ime transliteration rules (`hi-transliteration`, GPL-3.0), read by
  `tools/im/make.py` into `android/assets/im/hi/rules.json` and run by the keyboard's own rule runner (`keyboard/Rules.kt`,
  `"engine": {"rules": "rules.json"}`): each key is added to the word's last letters, and the first rule that matches the
  end rewrites them, as jquery.ime does (namaste is नमस्ते). The same runner takes any other script's rule file later.
- **The word being typed** is the engine's code, on the bar, worked out again from the keys, so Backspace takes the last key
  off. Space goes through the rules too, so a word's last virama goes (agar is अगर, agar~ keeps it), as jquery.ime does.
  The word list's words that start with it are the choices, as Telex's are. Shift letters are their own sounds (namaH,
  maChiyoM), so there are no sentence capitals.
- **Measured** (`tests/languages`): jquery.ime's own Hindi test cases pass 100 of 101 when exported to `$HI_FIXTURES` (the
  one left is a browser's line break, \r written as \n); typing the 300 commonest words by sound, a search finds keys
  for 290 (96.7%), 1.17 keys a letter. The ones left need chandrabindu or nukta keys the search doesn't try.
- **Words:** FrequencyWords' Hindi (CC BY-SA 4.0), 20,026 words. HeliBoard's Hindi list says only "GPLv2", which may not
  combine with TitanKeys' GPL-3.0, so it isn't used.
- **Its name:** Ink draws text without shaping, so हिन्दी's vowel sign would sit after its letter. Settings shows Devanagari
  names in English ("Hindi") until it can (`UNSHAPED` in `app/settings/languages.tsx`); Android's own list shows हिन्दी.

## Right to left: Hebrew

- **Keys: SI-1452,** read by `tools/im/make.py` from xkeyboard-config's `symbols/il`: 24 of its 27 letters where a PC has
  them. The three on keys the Titan lacks: ת (the comma key) on Q, in place of the standard's /, as it's common; the
  finals ף and ץ (; and .) held on their own letters, P and M. / is held on Q. Shift types the same letter, as Hebrew has no
  capitals (the standard's Shift gives Latin ones), and W types the geresh ', part of a word (צ'יפס).
- **Measured** (`python3 -I tools/im/measure.py <data> he`): all 27 letters on the keys; every one of the word list's
  100,000 words can be typed; 0.4% of key presses are holds (the two finals), weighted by each word's count.
- **Words:** FrequencyWords' Hebrew (CC BY-SA 4.0), the 100,000 commonest.
- **The bar:** right to left, the second best word goes on the right, where reading starts, and the third on the left; the
  best stays in the middle (`BarOrder`, `Strip.rightToLeft`, set as the input method changes).
- **Steering:** Android moves the cursor visually, so the arrow keys already go the finger's way. The keyboard peeks at the
  next letter to feel spaces and the text's ends, and now peeks on the side the cursor really goes: in a paragraph whose
  first letter reads right to left, a step right goes back in the text (`RightToLeft`).
- **Not yet:** the word menu's buttons keep their left-to-right order; mixed Hebrew and Latin in one line follows Android.
- Android shapes and orders the letters; the keyboard only sends them. Settings shows עברית from Noto Sans Hebrew's letters.

## Japanese

Japanese types through Mozc, Google's open-source engine (BSD-3-Clause), as the engines study recommends
([docs/studies/56-engines.md](studies/56-engines.md)): 20 of 22 first choices right on the PC, against 12 for Rime with a
Japanese schema. One input method, 日本語, its mark あ, marked experimental like every language but Chinese.

**On the bar, exactly as Chinese** ("Chinese on the bar"), through `InputEngine`: the text being built floats above the bar
(hiragana, with the romaji not yet a kana at its end), the mark sits over Q, the choices over W to O as wide as they need,
the five ways to pick, and hold P for the bigger menu. `ChineseKeys.usesBar` names the engines that type this way: Rime and
Mozc. The English word is Chinese's alone: Mozc offers the letters as they are among its own choices.

**Mozc's Android way:** choices as you type, whole sentences first, and the parts of a sentence among them. A pick of a
part types it and leaves the rest to convert, so a sentence that's wrong in one place is fixed there. Mozc learns what's
picked.

**Keys:**
- Letters are romaji through Mozc's QWERTY table (`kyou` → きょう, `nn` → ん, `-` → ー inside a word). No capitals: Shift
  picks, as in Chinese.
- **Space** types the first choice. **Enter** types the romaji as typed. **Backspace** takes off the last romaji letter:
  Mozc starts again from the letters left, so it never takes a whole kana.
- **Punctuation** is Japanese, full-width (、。「」・〜, and ？！ and the rest in their full-width forms), like Chinese's;
  with text being built, its first choice goes first. **Digits stay plain.**

**The bridge:**
- `native/mozc/tk/`: TitanKeys' own small layer in Mozc's tree. `tk_core` drives Mozc's `SessionHandler` in its Android way
  with the QWERTY romaji table; `tk_jni` registers `com.titankeys.mozc.MozcNative` (`start`, `stop`, `key`, `pickAt`,
  `clear`, `state`); `mozc_tool` is the same core on the PC, for the checks. Kotlin never sees Mozc's protobuf, and the
  library exports only `JNI_OnLoad` (`tk.map`), keeping Mozc's own libc++ inside.
- `native/mozc/build.sh` builds it in WSL from the pinned Mozc commit, with Mozc's own pinned dependencies (NDK r29, Bazel
  through bazelisk), into `android/jniLibs/arm64-v8a/libmozc_tk.so` and `android/assets/mozc/mozc.data`, listed in
  `android/assets/mozc.sha256`. Sources and licences: `native/mozc/SOURCES.md`.
- `com.titankeys.mozc.MozcEngine` copies `mozc.data` out once on the first use (Mozc opens it by path), through Rime's checked,
  all-or-nothing copy (`RimeData`), into `files/engines/mozc/shared`; the user's history is in `files/engines/mozc/user`.

**Checks:** `tests/japanese` (the engine's keys with a stand-in for Mozc, and the romaji readings turn into);
`tests/japanese/corpus.sh`, Tatoeba's reviewed Japanese sentences (CC BY 2.0 FR) typed as romaji through this path, against
the sentence as written and Mozc's own desktop conversion. On 2,000 of them (seed 56, 2026-11-16):

| | Exactly as written | Characters right |
| --- | ---: | ---: |
| TitanKeys (Android's way, the first choice of each part between marks) | 66.6% | 95.3% |
| Mozc's own desktop conversion (the whole reading, Space) | 66.9% | 95.4% |

TitanKeys gives the same text as Mozc's desktop for 98.1% of them: Mozc's own quality. The rest of "exactly as written" is
mostly spelling style that's right either way (子ども for 子供, 来る for くる) and Mozc's open dictionary's homophones (変える
for 買える, 景観 for 警官), the second choice or a pick away.

**Size** (measured in this build's APK, 2026-11-16): `libmozc_tk.so` 2.83 MB, stored as it is (the study's 5.33 MB before
only `JNI_OnLoad` was exported), and `mozc.data` 18.96 MB, 13.19 MB compressed: 16.0 MB in the APK, and 19 MB copied out on
the phone on the first use. The whole debug APK is 167.4 MB, under Google Play's 200 MB.

## Korean and Vietnamese

Both are engines of TitanKeys' own, in plain Kotlin and checked on the JVM (`tests/languages`), named in `im.json` as
`"engine": "hangul"` and `"engine": "telex"`.

- **Korean (`keyboard/Hangul.kt`):** the 2-set (Dubeolsik) layout on the printed keys, with Shift for ㅃ ㅉ ㄸ ㄲ ㅆ and
  ㅒ ㅖ. The syllable being built shows on the bar; once the next jamo can't join it, it's written to the field (가 + ㅂ +
  ㅏ is 가바). Backspace takes off the last jamo typed. Space, Enter, digits and marks write the syllable and go on as
  usual. No sentence capitals, since Shift is a letter key's other jamo. No word list yet: none with a clear licence was
  found, and typing comes first.
- **Vietnamese (`keyboard/Telex.kt`):** Telex. aa â, ee ê, oo ô, aw ă, ow ơ, uw ư (uow ươ), dd đ; s f r x j for the five
  tones, z to take the tone off; the same key again undoes its mark and types the key. The tone goes where the spelling
  puts it, in the older style most people write (hòa, thúy). The word being typed shows on the bar, worked out again from
  the keys typed, so Backspace takes the last key off. The word list's words that are the code without marks, or start
  with it, are its choices, picked with the finger and Space; Space alone writes the word as typed. Telex is an engine
  rather than letters typed into the field and then rewritten, because a tone typed at the end of a word changes a vowel
  in its middle: the engine keeps the whole word until it's done, and the keyboard's own suggestions never see half a word.

## Where it goes in the code

Paths are under `android/kotlin/com/titankeys/`. Line numbers are from 2026-11-16's main.

- **`InputMethods`** (new, `keyboard/InputMethods.kt`): reads the index, keeps the current input method, loads its
  `im.json` and word list when it's picked, and frees the last one. English stays as it is: `SwipeTyping` keeps loading its
  models at start (`engine/SwipeTyping.java:151`), and `AutocorrectLoader` keeps loading English's lists per field.
- **Letters:** a letter key becomes text in `TitanKeysIme.key()` (`keyboard/TitanKeysIme.kt:3465`), from
  `event.getUnicodeChar(...)`, then `Composer.type()` (`keyboard/Composer.kt:636`). For a non-`qwerty` input method,
  `key()` asks `InputMethods` for the key's letter instead, and upper-cases it with the input method's locale. TitanKeys
  writes straight into the field with no composing text, so this is the only change for data-only input methods.
- **Holds:** `HoldEngine` (`engine/HoldEngine.java`) already times a held letter, and `canHold()` (`TitanKeysIme.kt:3134`)
  decides which keys can be held. In an input method with accents, `canHold()` accepts every key with accents (instead of
  every key with an Alt symbol), and the hold callback (`replaceHeldLetter`, `TitanKeysIme.kt:3173`) opens the picker instead
  of calling `Composer.replaceLetter()`. Picking an accent then calls `Composer.replaceLetter()` with it, as a symbol does
  today.
- **The picker:** a new `Strip.Notice.Picker(choices, page)`, drawn by `Strip` (`keyboard/Strip.kt`) next to the word menu's
  `drawMenu` (`Strip.kt:1037`), with `MenuKeys.slot()` lining each choice up over its key. Marking uses the bar's
  `SteadyKey` hover (`barHover`, `TitanKeysIme.kt:134`, through `followRestingKey` and `markMenu`), and the key press is taken
  the way `menuAction` (`TitanKeysIme.kt:1887`) takes the word menu's. Unlike the word menu, every letter key can be a choice.
- **The overlay on a held Sym:** Sym is handled in `Modifiers.down()` (`keyboard/Modifiers.kt:262`), which opens the Sym
  panel on the first press (`pressSym()`, `Modifiers.kt:211`). A held Sym isn't used for anything yet (`symHeld` only blocks
  repeats). With two or more input methods on, `pressSym()` moves to Sym's release, and a held Sym (the hold time,
  `TypingSettings.holdSymbolMillis`) opens the overlay instead. With only one input method on, Sym works exactly as today,
  so English-only users see no delay. The overlay is another `Strip.Notice`, like the picker.
- **The bar's words:** for input methods with a word list, `Composer.show()` (`Composer.kt:1860`) gets suggestions from the
  current input method's list (prefix matches, accents ignored, ranked by count) instead of `TypedWords`' English ones.
  Swiping, the next word and English's autocorrect rules stay English-only.
- **Chinese:** while a Rime input method is current, `TitanKeysIme.key()` sends letters, Space, Enter and Backspace to the
  Rime engine before `Composer`, and only committed text reaches `Composer.type()`. The code being typed shows on the bar
  with the picker (the field has no composing text today, and Chinese doesn't need it).
- **Settings:** a setting touches `engine/TypingSettings.java` (SharedPreferences `typing_settings`), the bridge in
  `com/vandam/ink/AppNativeAdapters.kt` (state around :514, set around :660), `lib/titankeys.ts` (`State`, `Settings`),
  `keyboard/SettingsReset.kt` and `keyboard/RawLog.kt`. The Chinese page is a `ChoicePage` (`lib/choice.tsx`) like
  `app/settings/spelling.tsx`. New pages get their parent in `lib/settings-tree.ts`.
- **English's hard-coded locale:** `Locale.UK` in `keyboard/ConnectionEditor.kt:148` and `engine/SwipeTyping.java` stays for
  English. The other input methods use their own locale.

## Contributing a language

- CONTRIBUTING.md's Languages section changes: 3.0 ships generic languages, and native speakers improve them.
- **"Improve a language" page** (`docs/add-a-language.md`): how to change an input method's `im.json` (accents' order, a
  missing letter), how to replace its word list and what licences are fine, how to run `bun run check`, and how to open the
  pull request. Adding a new data-only language is a line in `tools/im/make.py` plus the same steps.
- Going deeper (autocorrect rules, an engine) is up to the native speaker, discussed in #56 first.

## Size

| What | In the APK | On the phone |
| --- | --- | --- |
| Today | about 116 MB | |
| 43 data-only input methods, 100k words each at most | about 22 MB | read from the APK |
| Rime library | 3.1 MB | 3.1 MB |
| Chinese data (three schemas, three variants) | 17.2 MB | 40 MB after the first use |
| **Total** | **about 160 MB** | |

Google Play's limit is 200 MB, so everything is included. The size check (`tools/checks/size.test.ts`) fails the build
above 190 MB, to keep a margin. If the app ever needs more, rime-cantonese's long-phrase list can go (−6 MB), word lists
can drop to 50,000 words (−11 MB), or the word lists can move into an install-time Play asset pack.

## To check while building

- TitanKeys' `libc++_shared.so` comes from NDK r27c (`native/build.sh`). The study built Rime with NDK 29, so the app's build
  of Rime uses r27c to share it, or `c++_static` (+1 MB). Either way with 16 KB pages (`-z max-page-size=16384`), like the
  swipe library.
- The licences marked "to check" in the study, settled 2026-11-16: Serbian Latin is AOSP's Serbian list in Latin letters
  (Apache 2.0), Macedonian and Bosnian come from FrequencyWords (CC BY-SA 4.0), and Georgian from the Leipzig Corpora
  Collection's kat_newscrawl_2016_1M (CC BY 4.0). FrequencyWords' Georgian list is mis-encoded, Macedonian's AOSP-format
  list names no licence, and HeliBoard's Bosnian list has no real counts.
- How rime-cantonese's tones as letters (v, x, q) feel to the owner, who may type tones as digits.
- The Rime test on the phone: its speed and that the compiled tables are reused (`~/build/lang/rime-test`).
