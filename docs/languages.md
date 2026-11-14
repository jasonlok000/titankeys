# Language support (3.0 plan)

The plan for typing in languages other than English, set with the owner on 2026-11-14. The discussion is in
[issue #56](https://github.com/jasonlok000/titankeys/issues/56). Nothing here is built yet.

## The goal

The aim is to make as many languages usable as we can. "Usable" means basic and working, not the best keyboard for each
language. The framework comes first: going deeper in a language needs a native speaker, and whatever a native speaker
builds later will likely redo that language's early version anyway. So every choice below favours ease of development.

Ground rules already set in #56:
- **A language needs a native speaker.** Only someone who speaks it can contribute it and keep it right. The owner doesn't
  build languages they don't speak.
- **Holding Sym is reserved** for switching between input methods (since 2026-11-11), so no other feature takes it.
- **Rime for Chinese.** Cantonese and Mandarin use [Rime](https://rime.im)'s engine. The owner uses Rime with Yale
  Cantonese every day.

## What it is and isn't

- **One input method at a time, each built for its language.** TitanKeys never types several languages in one mode: a
  Spanish speaker in Spanish gets only Spanish (its accents, its words, its rules).
- **Input methods, not languages.** Some languages have several. Cantonese can have Yale and Jyutping, and Mandarin can
  have Pinyin. Each is its own entry.
- **English doesn't change.** English keeps everything it has today: swiping, the next word, autocorrect and holding for
  symbols. It becomes the first input method.
- **Other input methods are basic:**
  - no swiping;
  - no next-word guesses;
  - autocorrect is optional and off by default.
- **Code is fine.** An input method can be just data, or have its own code where the language needs it: Rime for Chinese,
  syllables built from letters for Korean.

## How English is built in today

English is the only language, and the code assumes it everywhere. There is no idea of a language or an input method in the
code: the English files load when the keyboard starts and are always on. About 40 MB of the app is English-only.

| Part | What it is | Files |
| --- | --- | --- |
| Android's keyboard list | One entry, "English (United Kingdom)" (#76) | `android/src/main/res/xml/input_method.xml` (en_GB) |
| Dictionary | The open Android English dictionary and FUTO's English word list | `android/assets/swipe/main_en_gb.dict`, `english.combined` |
| Swiping | FUTO Swipe's models, the CleverKeys encoder trained on the Titan, and the ranker, all trained on English words | `android/assets/swipe/`, `swipe2/ck-titan.onnx`, `swipe-ranker.txt` |
| Next word | DistilGPT-2 and FUTO's context model, both English | `android/assets/swipe2/predictor/`, `context_lm.pte` |
| Autocorrect | Lists of known words, misspellings, names, word pairs and triples, confusions, shorthand and slang. The code also has English-only rules: apostrophes (its/it's), capital I, Mr, and British, American and Canadian spelling | `android/assets/autocorrect/en_*.txt`, `TypedCorrector` and friends |
| Holding for symbols | The symbols printed on the Titan's keys | `HoldEngine` |
| Text | The app, the guide, the playground and the idle games | — |

What already works in any language, because it doesn't depend on one: the Sym panel, emoji, the clipboard, snippets,
flick to delete and holding the flick, steering and selecting with Space, Exact typing, haptics, and the bar with its
overlays. That includes the word menu's way of picking with the keys (rest a finger on a key to mark, press it to pick),
which the pickers below reuse.

## The framework

1. **An input method switch inside the keyboard.**
   - English loads its files and runs swiping, the next word and autocorrect as it does today.
   - Any other input method loads only what it needs.
2. **One picker on the bar.** Accents and Chinese characters both show on the bar, and you pick them with the keys, using
   the same hover as the word menu. Chinese uses the same picker, with pages.
3. **The switch overlay**, opened by holding Sym (see Switching).
4. **One entry per input method** in Android's keyboard list, so apps know which language you're typing (#76).
5. **A simple shape for an input method.** Each one has:
   - its name, and a short label for the overlay (EN, ES, FR, 粵);
   - its letters' accents, in the language's own order;
   - optionally, a word list with counts. The open Android dictionary set that English already uses has dozens of
     languages;
   - its own rules, such as whether to capitalise after a full stop and which punctuation ends a sentence;
   - optionally, an engine: code that takes key presses and returns the text being built and a list of choices (Rime,
     Korean).
6. **Easy contribution.** An "Add a language" page, and a check that tests an input method's files. A native speaker
   copies an existing input method, changes it and opens a pull request. Going deeper for a language is up to them.

## Accents

**Holding a letter shows its accents on the bar.**
- Hold e, and the bar shows é è ê ë, the most common first.
- Rest a finger on the key under one to mark it, and press to pick it, the same way as the word menu.
- Only the input method's own accents show, in its own order. For example:
  - Spanish: á é í ó ú ñ ü, with ¿ and ¡;
  - German: ä ö ü, with ß on s;
  - French: é è ê ë à â ç î ï ô û ù œ.
- The first time you use an input method with accents, the bar says once: "Hold a letter for its accents."

**Hold for symbols is off on every key in an input method that holds for accents**, not only on the keys that have
accents, so a hold always means one thing. Symbols stay on Alt + key and the Sym panel. Input methods without accents,
and English, keep hold for symbols.

**Accents from the word list** are optional, for input methods that have a word list.
- Typing "cafe" offers **café** on the bar. The match ignores accents.
- **Autocorrect fills an accent in only when the plain form isn't a word itself.** Some words are real with and without
  the accent and mean different things: Spanish si / sí, el / él, esta / está and tu / tú, French a / à and ou / où. Those
  are only ever offered on the bar, never changed.
- With autocorrect off, the default, it's only suggestions.

## Switching

**Holding Sym opens an overlay over the keys**, like the word menu and the Sym panel.
- Each input method sits over a key, and the current one is highlighted.
- Rest a finger on a key to mark one and press it to switch. Back or Sym closes it.
- **8 places, over W to O.** Q and P aren't used.
- **When 4 or fewer are on, each choice spans two keys,** so they're easier to hit.
- It might use the bottom row instead, like the paste menu. That's to decide when it's designed.
- The order is set in Settings > Languages, where input methods are turned on and off.
- With only English on, holding Sym does nothing, so English-only users never see it.

Holding Sym won't clash with the clipboard. The clipboard is meant as Sym, then A: two presses, one after the other. Holding Sym while pressing A also happens to work today, but it isn't taught.

## Chinese and Cantonese

These are where TitanKeys can be fancier, as the owner can test them properly.
- **Engine:** [librime](https://github.com/rime/librime) (BSD-3), built for Android as [Trime](https://github.com/osfans/trime) does it.
- **Schemas:** one engine covers several schemas, among them Cantonese (Jyutping, and Yale through a schema), Mandarin
  Pinyin, Cangjie and Zhuyin. The schemas' data has its own licences, to check for each one, such as
  [rime-cantonese](https://github.com/rime/rime-cantonese).
- **Choices:** they go in the bar's picker with pages, picked with the keys.
- **Each schema is its own input method** in the overlay: for example, 粵 Yale, 粵 Jyutping, 拼 Pinyin.

## Order

1. **The framework:** the input method switch, the bar's picker, the overlay, and the keyboard list entries (#76).
2. **Two or three Latin-script input methods to prove the shape,** for example Spanish, French and German. Each still
   needs a native speaker to check it.
3. **Cantonese and Mandarin through Rime.**
4. **More, as native speakers come forward in #56.** Russian and Greek can map each key to a letter by sound, since the
   physical keys print Latin letters. Korean needs its syllable-building code.

## Not in 3.0

- **Japanese.** [Mozc](https://github.com/google/mozc) (BSD) is the open engine, but it's heavy to build for Android.
- **Handwriting.** The open option is [Zinnia](https://taku910.github.io/zinnia/) (BSD; Chinese and Japanese). Google's
  ML Kit handwriting covers far more languages, but it isn't open source and it downloads its models. Writing characters on
  the touchpad, rather than the screen, would be a Titan-only feature for a later big release.
- **Swiping in other languages,** a next-word model in other languages, and translating the app, the guide and the
  playground.

## To check before building

- What the open Android dictionary set has for each first language, and its licence (English's is Apache 2.0).
- How Trime builds and embeds librime, and the size it adds to the app.
- Which Rime schemas, and their data licences, Cantonese Yale and Jyutping need.
- What Android's keyboard list needs for each input method, and how Settings > Languages turns them on.
