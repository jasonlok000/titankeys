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
- **If time allows:** Korean and Vietnamese, which need a little code each.
- **Not in 3.0:** right-to-left scripts, Indic scripts, Japanese, handwriting, swiping and a next-word model in other
  languages, and translating the app, the guide and the playground.

The order: Spanish, French and German first to prove the framework; the other big Latin languages; Chinese alongside; then
Cyrillic and Greek; then the rest.

## Words

- **Input method:** one way of typing, such as English, Spanish, Cantonese (Yale) or Mandarin (Pinyin). It has one entry in
  the switch overlay and in Android's keyboard list. A language can have several.
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
    "a": "á", "e": "é", "i": "í", "n": "ñ", "o": "ó", "u": "úü",
    "?": "¿", "!": "¡"
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
| `accents` | For each key, the letters held on it, in the order they show on the bar. Any character is allowed, so punctuation can be held too (¿ on ?) |
| `words` | The word list's file, or `null` |
| `variants` | `null`, or a list of `{ "id", "name", "words" }` for spellings with their own word lists (Portuguese: `pt-BR`, `pt-PT`) |
| `rules` | `capitalise` after a sentence's end, the characters that end a sentence, marks that open one (¿¡), and whether apostrophes are part of words (French: `l'eau`) |
| `engine` | `null`, `"rime"` with its schema (`{ "rime": "tk_yale" }`), or the name of a built-in engine (`"hangul"`, `"telex"`) |
| `source` | Where the letters and words come from, for the licences page and for contributors |

English is the built-in input method `en`. It keeps its own code and assets and gets an `im.json` only for its name,
label, locale and variants (British, American, Canadian).

Rules for a valid input method, checked by `tools/checks/im.test.ts` on every `bun run check`:
- the JSON parses, and every field has the right type;
- every letter in `keys` and `accents` is in CLDR's letters for the locale (main or auxiliary), or listed in an `extra`
  field with a reason;
- every letter the word list uses often (20 times in its top 50,000 words) can be typed, from a key or a hold;
- the word list parses, has at most 100,000 words, and its file is at most 3 MB;
- `NOTICE` exists and names a licence on the allowed list (Apache-2.0, MIT, BSD, CC0, CC BY 4.0, CC BY-SA 4.0, LGPL,
  GPL-3.0, GPL-2.0-or-later, Unicode);
- labels are unique, and at most 3 characters wide.

A script, `tools/im/new.ts <locale>`, makes a new input method's folder from CLDR's letters and a word list, so adding a
data-only language is one command plus a check by eye. Where the data comes from is in the study.

## How an input method declares itself

- **In the app:** a generated index, `android/assets/im/index.json`, lists every folder's `id`, `name`, `label`, `locale` and
  `script`. The build writes it from the folders (`tools/im/index.ts`), so adding a folder is all it takes. The keyboard
  reads only the index at start, and an input method's own files when it's switched on.
- **In Android's keyboard list:** one `<subtype>` per input method in `input_method.xml`, written by the same script:

  ```xml
  <subtype android:label="@string/im_es" android:imeSubtypeLocale="es"
      android:languageTag="es" android:imeSubtypeMode="keyboard"
      android:imeSubtypeExtraValue="im=es" android:subtypeId="0x7f0e0001" />
  ```

  - `subtypeId` is fixed per input method (a hash of its `id`), so Android keeps the user's choices across updates.
  - Settings > Languages decides which input methods are in the overlay. Android's own list of enabled subtypes is kept
    the same as it, through `InputMethodManager.setExplicitlyEnabledInputMethodSubtypes` (API 34), so the two never
    disagree.
  - When the input method changes, the keyboard calls `InputMethodService.switchInputMethod(id, subtype)`, so apps see the
    language in `getCurrentInputMethodSubtype()` and pick their own spell checker and hints from it. This is #76's label.
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
- It takes a list and a page: accents fit on one page; Chinese gets pages.
- **Chinese:** the choices show as you type the code. Space types the first. Rest a finger and press the key under a choice
  to pick it. Swiping up or down on the keys turns the page, like the Sym panel. Back clears the code.

**Accents from the word list** (input methods with a word list):
- The bar's suggestions ignore accents when matching: typing "cafe" offers **café**.
- Autocorrect fills in an accent only when the plain form isn't a word itself (si / sí stays as typed). Autocorrect is off
  by default, so by default it's suggestions only.

The bar, its hover and the picking come from the word menu's code (see "Where it goes in the code").

## Switching: the overlay on a held Sym

- **Holding Sym opens the overlay** over the top row, like the word menu: each input method's label over a key, W to O
  (8 places), the current one marked.
- With 4 or fewer on, each spans two keys.
- It stays open after Sym is let go. Rest a finger to mark, press to switch. Back or Sym closes it with no change. A short
  Sym press still opens the Sym panel, as today.
- The order is Settings > Languages' order.
- With only one input method on, holding Sym does nothing.
- The current input method's label shows on the bar's left while you type in anything but English, so you know where you
  are. Its accessibility text says it in full: "Typing in Spanish".
- Alt + Sym keeps going to Android's next keyboard, as today.

## Settings > Languages

A new page, between Typing and Shortcuts:

```
Languages
  Input methods            English, Español, 粵 Yale
  Chinese characters       Traditional (Hong Kong)      (only when a Chinese input method is on)
  English spelling         British                      (moves here from Typing)
```

**Input methods** (`app/settings/languages.tsx`):
- A list of every input method, grouped by script, each with a switch. The ones that are on sit at the top in the
  overlay's order, and can be moved up and down.
- English can't be turned off while it's the only one.
- At most 8 on, the overlay's places. The 9th switch says why it won't turn on: "Eight at a time. Turn one off first."
- Each input method's page shows its name, its source and licence, its variants when it has them (Portuguese), and
  "Fix typos as you type" for it, off by default.

**Chinese characters** (`app/settings/chinese.tsx`), a ChoicePage like English spelling:
- Traditional (Hong Kong), Traditional (Taiwan), Simplified (mainland China).
- One setting for every Chinese input method.
- **No default (owner, 2026-11-16): you choose.** The first time a Chinese input method is turned on, its switch opens
  this page, and it stays off until a variant is picked. Nothing is guessed from the phone's region.
  The page's subtitle: "Pick how your characters come out. You can change it any time."

The settings live in `typing_settings` with the others: `inputMethods` (the ordered list of ids that are on),
`imVariant.<id>`, `imAutocorrect.<id>` and `chineseVariant` (`zh_hk`, `zh_tw`, `zh_hans`).

## How Rime plugs in

- **The library:** librime with OpenCC, built from source with Trime's CMake files, without plugins, as
  `android/jniLibs/arm64-v8a/librime_tk.so` (4.5 MB). It builds in WSL with `native/rime/build.sh`, like the swipe library,
  and is checked in prebuilt, so a normal build doesn't need the NDK. The study's build is in `~/build/lang/rime-build`.
- **JNI:** a thin layer (`native/rime/rime_jni.cc`, `com.atlas.engine.Rime`), with: `start(sharedDir, userDir)`,
  `select(schema)`, `setOption(name, on)`, `key(code)`, `state()` (the code typed so far, the choices on this page, the page
  number, the text to commit), `pick(index)`, `page(down)`, `clear()`. Trime's `rime_jni` is the reference, and only what
  the picker needs is copied.
- **Data:** `android/assets/rime/` holds the schemas (`tk_yale`, `tk_jyutping`, `tk_pinyin`, `tk_variants`), Rime's shared
  files, the compiled tables and OpenCC's dictionaries, built by `native/rime/data.sh` on the PC. On the first use of a
  Chinese input method, the app copies them to `files/rime/shared` (40 MB; Rime opens its tables as files) in the
  background, and the bar says it's getting ready. The user's learned words go to `files/rime/user`, which TitanKeys' backup
  includes.
- **Variants:** the app sets exactly one of `zh_hk`, `zh_tw`, `zh_hans` on the Rime session whenever it starts or the
  setting changes.
- **Keys:** a letter goes to Rime, which returns the code and the choices for the picker. Space picks the first choice.
  Enter types the code as typed letters. Backspace deletes from the code, or from the text when the code is empty.
  Punctuation goes through Rime's punctuator, which gives Chinese marks (，。).
- **Engines in general:** Rime, and later Korean and Telex, implement one interface:
  `key(code, shift) -> State`, `pick(i)`, `page(dir)`, `reset()`, where `State` has the text to commit, the text being
  built (shown underlined in the field) and the picker's choices. Data-only input methods use no engine.

## Where it goes in the code

Paths are under `android/kotlin/com/atlas/`. Line numbers are from 2026-11-16's main.

- **`InputMethods`** (new, `keyboard/InputMethods.kt`): reads the index, keeps the current input method, loads its
  `im.json` and word list when it's picked, and frees the last one. English stays as it is: `SwipeTyping` keeps loading its
  models at start (`engine/SwipeTyping.java:151`), and `AutocorrectLoader` keeps loading English's lists per field.
- **Letters:** a letter key becomes text in `AtlasIme.key()` (`keyboard/AtlasIme.kt:3465`), from
  `event.getUnicodeChar(...)`, then `Composer.type()` (`keyboard/Composer.kt:636`). For a non-`qwerty` input method,
  `key()` asks `InputMethods` for the key's letter instead, and upper-cases it with the input method's locale. TitanKeys
  writes straight into the field with no composing text, so this is the only change for data-only input methods.
- **Holds:** `HoldEngine` (`engine/HoldEngine.java`) already times a held letter, and `canHold()` (`AtlasIme.kt:3134`)
  decides which keys can be held. In an input method with accents, `canHold()` accepts every key with accents (instead of
  every key with an Alt symbol), and the hold callback (`replaceHeldLetter`, `AtlasIme.kt:3173`) opens the picker instead
  of calling `Composer.replaceLetter()`. Picking an accent then calls `Composer.replaceLetter()` with it, as a symbol does
  today.
- **The picker:** a new `Strip.Notice.Picker(choices, page)`, drawn by `Strip` (`keyboard/Strip.kt`) next to the word menu's
  `drawMenu` (`Strip.kt:1037`), with `MenuKeys.slot()` lining each choice up over its key. Marking uses the bar's
  `SteadyKey` hover (`barHover`, `AtlasIme.kt:134`, through `followRestingKey` and `markMenu`), and the key press is taken
  the way `menuAction` (`AtlasIme.kt:1887`) takes the word menu's. Unlike the word menu, every letter key can be a choice.
- **The overlay on a held Sym:** Sym is handled in `Modifiers.down()` (`keyboard/Modifiers.kt:262`), which opens the Sym
  panel on the first press (`pressSym()`, `Modifiers.kt:211`). A held Sym isn't used for anything yet (`symHeld` only blocks
  repeats). With two or more input methods on, `pressSym()` moves to Sym's release, and a held Sym (the hold time,
  `TypingSettings.holdSymbolMillis`) opens the overlay instead. With only one input method on, Sym works exactly as today,
  so English-only users see no delay. The overlay is another `Strip.Notice`, like the picker.
- **The bar's words:** for input methods with a word list, `Composer.show()` (`Composer.kt:1860`) gets suggestions from the
  current input method's list (prefix matches, accents ignored, ranked by count) instead of `TypedWords`' English ones.
  Swiping, the next word and English's autocorrect rules stay English-only.
- **Chinese:** while a Rime input method is current, `AtlasIme.key()` sends letters, Space, Enter and Backspace to the
  Rime engine before `Composer`, and only committed text reaches `Composer.type()`. The code being typed shows on the bar
  with the picker (the field has no composing text today, and Chinese doesn't need it).
- **Settings:** a setting touches `engine/TypingSettings.java` (SharedPreferences `typing_settings`), the bridge in
  `com/vandam/ink/AppNativeAdapters.kt` (state around :514, set around :660), `lib/atlas.ts` (`State`, `Settings`),
  `keyboard/SettingsReset.kt` and `keyboard/RawLog.kt`. The Chinese page is a `ChoicePage` (`lib/choice.tsx`) like
  `app/settings/spelling.tsx`. New pages get their parent in `lib/settings-tree.ts`.
- **English's hard-coded locale:** `Locale.UK` in `keyboard/ConnectionEditor.kt:148` and `engine/SwipeTyping.java` stays for
  English. The other input methods use their own locale.

## Contributing a language

- CONTRIBUTING.md's Languages section changes: 3.0 ships generic languages, and native speakers improve them.
- **"Improve a language" page** (`docs/add-a-language.md`): how to change an input method's `im.json` (accents' order, a
  missing letter), how to replace its word list and what licences are fine, how to run `bun run check`, and how to open the
  pull request. Adding a new data-only language is `tools/im/new.ts` plus the same steps.
- Going deeper (autocorrect rules, an engine) is up to the native speaker, discussed in #56 first.

## Size

| What | In the APK | On the phone |
| --- | --- | --- |
| Today | about 116 MB | |
| 43 data-only input methods, 100k words each at most | about 22 MB | read from the APK |
| Rime library | 4.5 MB | 4.5 MB |
| Chinese data (three schemas, three variants) | 17.2 MB | 40 MB after the first use |
| **Total** | **about 160 MB** | |

Google Play's limit is 200 MB, so everything is included. The size check (`tools/checks/size.test.ts`) fails the build
above 190 MB, to keep a margin. If the app ever needs more, rime-cantonese's long-phrase list can go (−6 MB), word lists
can drop to 50,000 words (−11 MB), or the word lists can move into an install-time Play asset pack.

## To check while building

- TitanKeys' `libc++_shared.so` comes from NDK r27c (`native/build.sh`). The study built Rime with NDK 29, so the app's build
  of Rime uses r27c to share it, or `c++_static` (+1 MB). Either way with 16 KB pages (`-z max-page-size=16384`), like the
  swipe library.
- The licences marked "to check" in the study (Serbian Latin, Macedonian, Georgian).
- How rime-cantonese's tones as letters (v, x, q) feel to the owner, who may type tones as digits.
- The Rime test on the phone: its speed and that the compiled tables are reused (`~/build/lang/rime-test`).
