# Study: open-source engines for the languages that need one (#56)

M3's engine study, 2026-11-16. No app code, no phone. The owner: "for the languages, if it needs a new engine, like
Japanese, we could find open source engines like how we adapt rime for chinese, as a shortcut." Everything downloaded sits
in WSL under `~/build/lang/ja/`, and nothing downloaded is in the repo. It follows [56-languages.md](56-languages.md),
which put Japanese, Indic and right-to-left scripts outside 3.0.

## In short

- **Japanese: Mozc.** Google's engine, BSD-3-Clause, with permissive dictionary data (IPAdic's terms). It still keeps an
  Android build of its native library (`libmozc.so`), checked by its own CI on every push. Its conversion understands
  Japanese grammar, which matters on every sentence. About 2 weeks of work and about 20 to 25 MB in the APK. Not built
  here: the build needs Bazel, which this session wasn't allowed to download and run (see "What wasn't tried").
- **Rime with a Japanese schema is not the shortcut it looks like.** It was the cheapest to try, and it was tried: the
  maintained schema (rime-jaroomaji, Apache-2.0) runs on TitanKeys' own librime with no plugins, and gets single words
  right (会社, 記者, 新幹線, 電話番号, コンピューター). But Rime has no Japanese grammar, so sentences break: "kyou ha ii tenki
  desu ne" gives 教派いい天気ですね instead of 今日はいい天気ですね, and "toukyou ni ikimasu" gives 東京新木ます. Its table is also
  bigger than Mozc's data: 26 MB in the APK.
- **Hindi: a port of Wikimedia's transliteration rules (jquery.ime) to Kotlin.** They're short regex tables (GPL-3.0 for
  Hindi's, which ships), so it's days of work and a few KB. Word suggestions then come from the word list, as for other
  languages.
- **Thai needs no engine:** its Kedmanee layout is a keymap with Shift. Word suggestions are the hard part, since Thai
  writes no spaces between words.
- **Right-to-left needs no engine:** Android shapes Arabic letters itself. It's the bar, the word menu and the arrow keys
  that must learn right to left.
- **Proposed order:** Thai, Hindi, Japanese (Mozc), then right to left, starting with Hebrew.

## 1. Japanese

What a Japanese engine does on the Titan: romaji typed on QWERTY becomes kana as you type (`nihongo` → にほんご), and the
engine offers kanji conversions on the bar (日本語, 日本後, ニホンゴ...), as Rime does for Chinese. A sentence is converted in
segments (今日は / いい / 天気ですね), and a good engine picks the segments and their kanji from grammar and word costs.

### The candidates

| Engine | Code licence | Data licence | Ships in GPL-3.0? | Language | Maintained | Android |
| --- | --- | --- | --- | --- | --- | --- |
| [Mozc](https://github.com/google/mozc) | BSD-3-Clause | IPAdic (NAIST/ICOT terms: free to use, copy, change and distribute, keeping the notices), Okinawa dictionary (public domain), Google's own additions | Yes | C++, Bazel 9 | Yes: commits most days, last 2026-11-09 | `libmozc.so` built by its own Android CI; the APK client was removed |
| Rime + [rime-jaroomaji](https://github.com/lazyfoxchan/rime-jaroomaji) | Apache-2.0 (schema) | Mozc's (as above), JMdict and KANJIDIC2 (CC BY-SA 4.0), Mozc's emoji | Yes. CC BY-SA 4.0 is one-way compatible with GPL-3.0 | YAML on librime | Yes: dictionaries updated weekly, last 2026-11-12 | Any librime host, TitanKeys' included |
| Rime + [gkovacs/rime-japanese](https://github.com/gkovacs/rime-japanese) | None (its issue #6 asks for one) | Mozc and JMdict | **No**: no licence | YAML | No: last 2023-08 | |
| [anthy-unicode](https://github.com/fujiwarat/anthy-unicode) | LGPL-2.1+ | alt-cannadic, GPL-2.0+ | Yes, if "or later" holds | C | Fixes only, last 2026-09-10 | A plugin of fcitx5-android |
| [libkkc](https://github.com/ueno/libkkc) | GPL-3.0+ | not stated | Unclear | Vala | No: last 2024-09 | None found |
| [libskk](https://github.com/ueno/libskk) + [SKK dictionaries](https://github.com/skk-dev/dict) | GPL-3.0+ | GPL-2.0+, some CC BY-SA 3.0 | Yes | Vala | Yes | None found |
| [AzooKey's converter](https://github.com/azooKey/AzooKeyKanaKanjiConverter) | MIT | not stated | Unclear | Swift | Yes | No Android or Kotlin port |
| [Sumire](https://github.com/KazumaProject/JapaneseKeyboard)'s engine | MIT | Mozc's plus many sources, some "unconfirmed" in its own list | Only after curating its data | Kotlin | Yes | Its own keyboard app |

Not candidates:
- **gkovacs/rime-japanese** has no licence, so it can't ship. It also needs Rime's terra_pinyin and stroke schemas for its
  reverse lookup.
- **SKK** is a different way of typing, not automatic conversion: you mark with Shift where each kanji word and its
  okurigana start.
- **libkkc** is dormant.
- **AzooKey** would need Swift on Android.
- **Anthy** is generally judged weaker than Mozc, and is in fixes-only mode.

Kotlin keyboards built on Mozc's data exist and are active: Zinna (Apache-2.0) and Nacre (Apache-2.0, young). Sumire (MIT)
uses its own engine.

### Rime, tried on the PC

Both Rime schemas were compiled by TitanKeys' own `rime_tool` (`native/rime`, the same librime as the app), with
rime-prelude's shared files and 8 choices a page, then typed key by key the way `data.sh` tries Chinese.

- **Both run on TitanKeys' librime with no plugins.** jaroomaji's engine list is Rime's built-in processors, segmentors and
  translators only.
- **jaroomaji starts in English mode** (`ascii_mode` reset 1, so the PC can switch with Shift). The app would set it off,
  as it sets the Chinese variant. The test copy was changed to start in Japanese.
- **Compiling:** jaroomaji 9 s on the PC, rime-japanese 19 s.

First choices (jaroomaji, the one that can ship). The right answer is in bold where it isn't first:

| Romaji | First choices |
| --- | --- |
| watashi | わたし **私** 渡し 渡 ワタシ |
| nihongo | 日本語 ニホンゴ 日本 にほん |
| kaisha | 会社 かいしゃ 会しゃ |
| kisha | 記者 帰しゃ 貴社 キシャ 汽車 |
| hashi | 派し ハシ はし **橋 箸** |
| shinkansen | 新幹線 新感線 新感染 |
| denwabangou | 電話番号 電話番 電話 |
| arigatougozaimasu | ありがとうございます 有難う御座います |
| tabemashita | 食べました 食べ たべ |
| kawaikatta | 可愛かった かわいかった |
| yomitai | よみたい 読み ヨミ (no 読みたい) |
| konnichiha | 今イチは 今イチ 坤為地 (no こんにちは) |
| konpyu-ta- | コンピューター ⌨️ 💻️ |
| suma-tofon | スマートフォン 📱 |
| watashihagakuseidesu | ワタシハ学生デス (expected 私は学生です) |
| toukyouniikimasu | 東京新木ます (expected 東京に行きます) |
| kyouhaiitenkidesune | 教派いい天気ですね (expected 今日はいい天気ですね) |
| ashitaameganrusoudesu | 明日あメガンるそうです (expected 明日雨が降るそうです) |

rime-japanese did about the same: 私, こんにちは and 可愛かった right, 食べました wrong (食べ増田), and the same broken sentences.

Why: Rime builds a sentence from dictionary words by their weights, which works for Chinese, where most syllables are
words. Japanese needs grammar: particles (は, に, が), verb endings and okurigana, and costs for which words may follow
which. Mozc has a connection matrix for exactly that (`connection_single_column.txt`), and Rime has nothing like it.
Typing word by word, with a choice after each, would work, but that's slow on a phone.

Sizes of jaroomaji's compiled table, the bulk of it:

| File | Raw | Deflated (APK) | xz -9 |
| --- | ---: | ---: | ---: |
| `jaroomaji.table.bin` | 70.4 MB | 26.4 MB | 17.7 MB |
| `jaroomaji.prism.bin` and `reverse.bin` | 0.07 MB | 0.02 MB | 0.02 MB |

That's bigger than Chinese's three schemas together (17.2 MB). Dropping JMdict, KANJIDIC2 and the emoji list would shrink
it, since Mozc's data is most of the words, but it wouldn't fix the sentences.

### Mozc

**Licence.** The code is BSD-3-Clause. The open-source dictionary (`src/data/dictionary_oss`, 93 MB of text) is IPAdic's
words plus the Okinawa dictionary and Google's additions. IPAdic's terms allow use, copying, changes and distribution,
provided the notice and ICOT's "no warranty" section travel with it. That fits a GPL-3.0 app as BSD does; the notices go
in `licences/`, as Rime's did. Google's own additions name no separate licence and ship under the repository's.

**The Android build.** Mozc dropped its Android app but kept the library: `docs/build_mozc_for_android.md`, and a CI job
on every push ("CI for Android") that builds `native_libs.zip` for arm64, arm32 and x86:

```bash
python3 build_tools/update_deps.py      # downloads its pinned dependencies, NDK r29 among them
bazelisk build package --config oss_android --config release_build
```

- **Bazel 9.0.2** (`src/.bazeliskrc`), Python 3.12+, Linux or macOS. WSL atlas-build is Linux.
- **The JNI layer is Mozc's own** (`src/android/jni/mozcjni.cc`). It has three methods:
  - `evalCommand(byte[]) → byte[]`: everything goes through it as Mozc's protobuf `commands.proto`, a key in and a
    candidate list out.
  - `onPostLoad(...)` loads the data file.
  - `getDataVersion()`.
- **Its JNI class name is Google's** (`com.google.android.apps.inputmethod.libs.mozc.session.MozcJni`). So TitanKeys
  either has a class of that name or patches the name, as Zinna does (Zinna applies 4 small patches to upstream).
- **The NDK:** Mozc downloads its own NDK r29. TitanKeys' `libc++_shared.so` is from r27c. Rime's rule applies: share it
  only when the NDKs match, else link `c++_static`.
- **Data:** a single `mozc.data` file, compiled on the PC by Mozc's own tool. Zinna's is about 18 MB (from its F-Droid
  listing; not measured here). Mozc opens it from a path, so the app copies it out once, as it does Rime's tables.

**Input on the Titan's keys.** Mozc takes key events (letters, Space, Enter, arrows, Backspace) and returns the
composition and a candidate list, so it fits the model Rime already uses:
- Letters type romaji.
- Space converts.
- The bar shows the choices.
- Left and Right move between segments.
- Shift+Left and Shift+Right resize a segment, which Mozc does itself.
- Mozc also predicts the next word after a commit.

Mozc's docs point to a removed keyboard layout file for physical Japanese keyboards; the Titan's QWERTY needs none, since
romaji is plain letters.

**Quality.** Mozc is the open-source version of Google Japanese Input. Its open dictionary lacks the web vocabulary of
Google's own, but the grammar and segmenting are the same engine. Fcitx, IBus and several Android keyboards use it as
their Japanese engine.

**What it would take (estimate):**

| Step | Days |
| --- | ---: |
| Mozc's Android library in WSL with Bazel 9, pinned to a commit, as `native/rime/build.sh` does for Rime; `mozc.data` from the same build | 2–3 |
| JNI glue: the class name, and `commands.proto` from Kotlin (protobuf-lite, or a small hand-written encoder for the few messages used) | 2–3 |
| The Japanese input method: composition on the bar, segments, choices, Space and arrows, the variant switch's place for Hiragana/Katakana/English | 3–5 |
| Copying the data out, Settings, the guide, tests (`tests/` and a `data.sh`-style check: romaji in, first choice out) | 2–3 |
| **Total** | **about 2 weeks** |

**Size:** `libmozc.so` (arm64, not measured; Zinna's whole APK is 36 MB) plus about 18 MB of data. Estimated 20 to 25 MB
in the APK and about 25 to 30 MB on the phone. With the language study's total (about 160 MB), that's still under Google
Play's 200 MB.

### What wasn't tried

- **Building Mozc.** Its build downloads and runs Bazel (bazelisk) and its own pinned dependencies. This session's
  permission check refused that, so neither the Android library nor a PC build for testing was made. The evidence that it
  builds is Mozc's own Android CI and Zinna's F-Droid builds from upstream. The next step is a session allowed to run
  Bazel in WSL: build `libmozc.so` and a PC helper (`mozc_emacs_helper`), then type the table above through it.
- **Mozc's quality on the sentences above.** It can't be measured until it's built.

## 2. The other scripts

### Indic scripts (Hindi first): an engine, but a small one

| Engine | Licence | What it is | Fit |
| --- | --- | --- | --- |
| [jquery.ime](https://github.com/wikimedia/jquery.ime) (Wikimedia) | Library GPL-2.0+ or MIT; each rule file its own (Hindi's transliteration: GPL-3.0) | Over 220 input methods for 120+ languages, each a list of `[regex, replacement]` pairs | **Best fit:** the rules port to Kotlin's `Regex` almost as they are |
| [m17n-db](https://github.com/mike-fabian/m17n-db) (`hi-itrans.mim`, `hi-inscript.mim`) | LGPL-2.1+ | Rule files read by m17n-lib (C) | Fine, but needs a reader for its Lisp-like format |
| [govarnam](https://github.com/varnamproject/govarnam) | AGPL-3.0 | Go, learns words as you type | AGPL, Go, no Android port |
| [IndicXlit](https://github.com/AI4Bharat/IndicXlit) (AI4Bharat) | MIT | A neural model, 21 languages, top-k words | Needs converting to run on a phone; nobody has |
| [Indic Keyboard](https://github.com/smc/Indic-Keyboard) (SMC) | Apache-2.0 | An Android keyboard built on AOSP's | A reference, not an engine to take |
| InScript | Indian standard (IS 16350) | A fixed layout | A keymap, but it uses keys beyond the 26 letters (`[ ] ; ' , . /` and digits), so it needs Alt on the Titan |

What Hindi takes:
- jquery.ime's Hindi transliteration rules, ported to a small Kotlin rule runner (`namaste` → नमस्ते).
- The word list the language study found (GPL-2.0, "or later" to check).
- Suggestions work on the converted Devanagari.
- The same runner then serves Bengali, Tamil and the others from their own rule files.

**About 3 to 5 days for the runner and Hindi. Size: a few KB of rules plus the word list (about 0.3 MB).**

### Thai: keyboard work

Kedmanee is a fixed keymap with a Shift layer, already in Android's files; it needs Shift and holds on the Titan's 26 keys.
Thai writes no spaces between words, so suggestions need the current word found by a word-breaking step (libthai, LGPL-2.1+,
or a dictionary walk over the word list).

### Right to left (Hebrew, Arabic, Persian, Urdu): keyboard and UI work

Android's text stack shapes Arabic letters (HarfBuzz through Minikin); the keyboard only sends the letters. The work is
TitanKeys' own UI:
- the bar and the word menu ordered right to left;
- the arrow keys and steering with Space in right-to-left text;
- mixed Hebrew and Latin text.

Hebrew first: 27 letters on 26 keys, and an AOSP word list.

## Recommendation

1. **Thai** (keymap, Shift layer, word breaking): no engine, the smallest step.
2. **Hindi** (the jquery.ime rule runner): about a week; the runner opens the other Indic scripts.
3. **Japanese with Mozc:** about 2 weeks and 20 to 25 MB, after a build in WSL proves the size and quality. Not Rime: it's
   free to add but fails at sentences and is larger.
4. **Right to left, Hebrew first:** UI work across the bar, the menus and steering.

## Calls to confirm

- **Mozc over Rime for Japanese,** though Rime is already in the app. Rime's Japanese gets words but breaks sentences.
- **Permission to run Bazel in WSL** for the Mozc build: it downloads bazelisk, Bazel 9 and Mozc's pinned dependencies
  (NDK r29, Abseil, protobuf) from their official sources.
- **jquery.ime's rules for Hindi** instead of m17n's, because the regex tables port directly to Kotlin.
- **The order:** Thai and Hindi before Japanese, as smaller steps, or Japanese first if it matters more to the owner.
