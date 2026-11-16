# Study: open-source engines for the languages that need one (#56)

M3's engine study, 2026-11-16. No app code, no phone. The owner: "for the languages, if it needs a new engine, like
Japanese, we could find open source engines like how we adapt rime for chinese, as a shortcut." Everything downloaded sits
in WSL under `~/build/lang/ja/`, and nothing downloaded is in the repo. It follows [56-languages.md](56-languages.md),
which put Japanese, Indic and right-to-left scripts outside 3.0. Mozc was then built and measured with the owner's go to
download what it needs ("Yes please download whatever you need").

## In short

- **Japanese: Mozc. Yes.**
  - It's Google's engine, BSD-3-Clause. Its dictionary data is under permissive terms too (IPAdic's, BSD, MIT, public domain).
  - Its Android library was built here the way its CI builds it. That took 8.5 minutes for four ABIs.
  - **The library:** 5.3 MB for arm64, stripped. It carries its own libc++ and already aligns to 16 KB pages.
  - **The data:** 19.0 MB, 13.2 MB compressed in the APK.
  - **In all, about 18.5 MB in the APK, plus the 19 MB of data copied out on the phone.**
  - **On the PC test, with a fresh profile, 20 of 22 first choices were right.** Rime's Japanese got 12 of 22.
  - Both of Mozc's misses (古そう for 降る, 機能は for 昨日は) are the second choice of their part. Mozc learns them once
    picked.
  - **Work: about 8 to 10 days.**
- **Rime with a Japanese schema is not the shortcut it looks like.**
  - It was tried: the maintained schema, rime-jaroomaji (Apache-2.0), runs on TitanKeys' own librime with no plugins.
  - It gets single words right, but has no Japanese grammar, so sentences break: 教派いい天気ですね, 東京新木ます.
  - Its table is 26 MB in the APK, more than Mozc's data.
- **Hindi: a port of Wikimedia's transliteration rules (jquery.ime) to Kotlin.** They're short regex tables (GPL-3.0 for
  Hindi's, which ships), so it's days of work and a few KB. Word suggestions then come from the word list, as for other
  languages.
- **Thai needs no engine:** its Kedmanee layout is a keymap with Shift. Word suggestions are the hard part, since Thai
  writes no spaces between words.
- **Right-to-left needs no engine:** Android shapes Arabic letters itself. It's the bar, the word menu and the arrow keys
  that must learn right to left.
- **Proposed order:** Thai, Hindi, Japanese (Mozc), then right to left, starting with Hebrew.

## 1. Japanese

What a Japanese engine does on the Titan:
- Romaji typed on QWERTY becomes kana as you type (`nihongo` → にほんご).
- The engine offers kanji conversions on the bar (日本語, ニホンゴ...), as Rime does for Chinese.
- A sentence is converted in parts (今日は / いい天気ですね). A good engine picks the parts and their kanji from grammar
  and word costs.

### The candidates

| Engine | Code licence | Data licence | Ships in GPL-3.0? | Language | Maintained | Android |
| --- | --- | --- | --- | --- | --- | --- |
| [Mozc](https://github.com/google/mozc) | BSD-3-Clause | IPAdic (NAIST/ICOT terms), Okinawa dictionary (public domain), Japanese Usage Dictionary (BSD-2), Tamachi Phonetic Kanji Alphabet (MIT), Google's own additions | Yes | C++, Bazel 9 | Yes: commits most days | `libmozc.so` built by its own Android CI; the APK client was removed |
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

### Mozc and Rime, side by side

Each phrase was typed into a fresh profile, so nothing was learned between phrases.
- **Mozc:** Mozc's own `session_handler_main`, which drives the same `SessionHandler` the Android library does. It ran in
  two modes:
  - **Space:** the desktop way. Type, press Space, and Mozc shows the sentence in parts.
  - **As you type:** Android's mobile mode, with the QWERTY-to-hiragana table. The bar shows whole-sentence choices
    before any key is pressed.
- **Rime:** jaroomaji compiled by TitanKeys' own `rime_tool` (`native/rime`, the same librime as the app). It ran with
  rime-prelude's shared files, 8 choices a page, and set to start in Japanese, as the app would set it.

First choices; the parts Mozc split a sentence into are shown with `|`:

| Romaji | Mozc, Space | Mozc, as you type | Rime (jaroomaji) |
| --- | --- | --- | --- |
| watashi | 私 | 私 | わたし (私 2nd) |
| nihongo | 日本語 | 日本語 | 日本語 |
| kaisha | 会社 | 会社 | 会社 |
| hashi | 橋 (箸, 端 next) | 橋 | 派し |
| kisha | 記者 | 記者 | 記者 |
| konnichiha | こんにちは | こんにちは | 今イチは |
| arigatougozaimasu | ありがとうございます | ありがとうございます | ありがとうございます |
| denwabangou | 電話番号 | 電話番号 | 電話番号 |
| shinkansen | 新幹線 | 新幹線 | 新幹線 |
| tabemashita | 食べました | 食べました | 食べました |
| yomitai | 読みたい | 読みたい | よみたい |
| kawaikatta | 可愛かった | 可愛かった | 可愛かった |
| konpyu-ta- | コンピューター | コンピューター | コンピューター |
| suma-tofon | スマートフォン | スマートフォン | スマートフォン |
| moushiwakegozaimasen | 申し訳ございません | 申し訳ございませ (ん comes with the next key) | 申し訳ございません |
| watashihagakuseidesu | 私は\|学生です | 私は学生です | ワタシハ学生デス |
| toukyouniikimasu | 東京に\|行きます | 東京に行きます | 東京新木ます |
| kyouhaiitenkidesune | 今日は\|いい天気ですね | 今日はいい天気ですね | 教派いい天気ですね |
| ekimadearukimasu | 駅まで\|歩きます | 駅まで歩きます | 駅ま出歩きます |
| ashitanoasanikaigigaarimasu | 明日の朝に\|会議が\|あります | 明日の朝に会議があります | 明日の朝二階ギガアリマス |
| ashitaamegafurusoudesu | 明日\|雨が\|**古そうです** | 明日雨が**古そうです** | 明日雨が降るそうです |
| kinouhatomodachitoeigawomimashita | **機能は**\|友達と\|映画を\|見ました | **機能は**友達と映画を見ました | 機能は友達都営合御三間下 |
| **Right** | **20 of 22** | **20 of 22** | **12 of 22** |

- **Mozc's misses are one part each, with the right word next.**
  - In Mozc's mode where choices show as you type, picking 明日 types it and leaves あめがふるそうです, now offering 雨が古そうです,
    雨が, and so on.
  - After 昨日は is picked once, the whole sentence comes out 昨日は友達と映画を見ました. Mozc learns from what's picked.
- **Rime's misses are whole sentences,** because Rime has no Japanese grammar. Mozc has a connection matrix for exactly
  that: costs for which words may follow which. Rime's one sentence win, 降るそうです, is weights, not grammar.
- **Speed:** each Mozc run, starting the engine and loading its data included, took 30 to 45 ms on the PC.

The earlier version of this study had "ashitaameganrusoudesu" for 明日雨が降るそうです: the romaji was mistyped. The table above
uses "ashitaamegafurusoudesu".

Rime's sizes (jaroomaji's compiled table is nearly all of it):

| File | Raw | Deflated (APK) | xz -9 |
| --- | ---: | ---: | ---: |
| `jaroomaji.table.bin` | 70.4 MB | 26.4 MB | 17.7 MB |
| `jaroomaji.prism.bin` and `reverse.bin` | 0.07 MB | 0.02 MB | 0.02 MB |

### Mozc, built

Mozc `a4541df29db443b49f81cf54665873b68fde9efa` (2026-11-09), built in WSL atlas-build exactly as its CI does
(`.github/workflows/android.yaml`):

```bash
python3 build_tools/update_deps.py      # its pinned dependencies, NDK r29 among them
bazelisk build --config oss_android package --config release_build
```

**Build:**

| What | Time |
| --- | ---: |
| `update_deps.py` | 28 s |
| `libmozc.so` for arm64, arm32, x86-64 and x86 | 512 s |
| `mozc.data` and the PC test programs | about 120 s each |

- **Tools:** bazelisk 1.25.0, which fetched Bazel 9.0.2 as Mozc pins it, and Python 3.12.3.
- **Disk:** the build takes 4.4 GB, all in `~/build/lang/ja`: 2.4 GB of dependencies (the NDK r29 included) and 1.9 GB of
  Bazel's cache.

**Sizes:**

| File | Raw | Deflated | xz -9 |
| --- | ---: | ---: | ---: |
| `libmozc.so`, arm64, as built (with debug info) | 16.1 MB | 4.0 MB | |
| `libmozc.so`, arm64, stripped | **5.33 MB** | 1.85 MB | |
| `mozc.data` (`//data_manager/oss:mozc_dataset_for_oss`) | **18.96 MB** | **13.18 MB** | 11.73 MB |

- **In the APK:**
  - TitanKeys stores native libraries uncompressed, so the library is 5.3 MB.
  - The data, compressed, is 13.2 MB.
  - **About 18.5 MB in all.**
- **On the phone:**
  - Mozc opens its data by path (`DataManager::CreateFromFile`), so the app copies it out once, as it does Rime's tables:
    19 MB in the app's files.
  - Mozc's user history is small, in its profile folder.
  - The library stays in the APK, as Rime's does, uncompressed and not extracted.
- **The library needs only Android's own libraries** (`liblog`, `libc`, `libm`, `libdl`). It links libc++ statically, so
  NDK r29 against the app's r27c `libc++_shared.so` doesn't matter.
- **It aligns to 16 KB pages,** as Android 15+ wants.
- **It exports 7,750 symbols,** about 550 of them from libc++. TitanKeys' build exports only `JNI_OnLoad`, with a version
  script, as Rime's library does. That also keeps its libc++ from clashing with the app's.

**Licences:** everything Mozc credits for the data and library it ships (`data/installer/credits_en.html`) is permissive.
- **Mozc:** BSD-3.
- **Abseil:** Apache-2.0.
- **Protocol Buffers:** BSD-3.
- **IPAdic:** free use, copying and changing, keeping NAIST's notice and ICOT's "no warranty".
- **Okinawa dictionary:** public domain.
- **Japanese Usage Dictionary:** BSD-2.
- **Tamachi Phonetic Kanji Alphabet:** MIT.

Their texts go in `licences/`, as Rime's did. Qt, the Windows library and the zip-code data are credited but not in the
Android build.

### How it plugs into `InputEngine`

Mozc's mobile mode fits `InputEngine` as Rime does:
- `code` is the hiragana being typed.
- `choices` are Mozc's candidates.
- `space` picks the first.
- `pick(index)` picks one.
- `enter` types the hiragana as it is.
- `backspace` deletes from the hiragana.
- `reset` drops it.

A part picked commits that part and leaves the rest, which Mozc handles itself (tested above). No segments or arrow keys
are needed in `InputEngine`. The bar's pages, word menu and settings work as they do for Chinese.

**The JNI layer: TitanKeys' own, not Mozc's.**
- **Why not Mozc's own `mozcjni.cc`:**
  - It passes everything as protobuf bytes (`evalCommand(byte[]) → byte[]`, Mozc's `commands.proto`, 1,466 lines). The
    Kotlin side would need protobuf too.
  - Its class name is Google's (`com.google.android.apps.inputmethod.libs.mozc.session.MozcJni`).
- **Instead, a `mozc_jni.cc` like `native/rime/rime_jni.cc`:**
  - It's built inside Mozc's Bazel tree as one more target, from a small overlay copied in by the build script, with no
    patches to Mozc.
  - It builds the protobuf commands in C++ and gives Kotlin the same shape of calls as `RimeNative`:
    - `start(dataFile, profileDir)` creates the `SessionHandler` from `mozc.data`, then a session with the mobile request
      and the QWERTY-to-hiragana table.
    - `key(char)`, `special(space | enter | backspace)`, `pick(id)`, `clear()` and `stop()`.
    - `state()` returns the hiragana, the candidates and whether a commit happened.
  - Kotlin keeps no protobuf, and the library exports only `JNI_OnLoad`.
- **Options:** Katakana and half-width come as candidates already (アメガフルソウデス, nihongo). So the variant switch's place
  needs no option at first.

### What it would take

| Step | Days |
| --- | ---: |
| `native/mozc/build.sh`, as `native/rime/build.sh`: Mozc pinned to a commit, `update_deps.py`, Bazel for arm64 only, the JNI overlay, the version script, stripping, `library.sha256`; `mozc.data` and its hash from the same build | 2 |
| `mozc_jni.cc` and `MozcNative`/`MozcEngine` in Kotlin, after Rime's | 2–3 |
| The data copied out once (`MozcData`, as `RimeData`), the input method in Settings > Languages, the licences | 2 |
| Tests: a `tests/mozc/` suite like `tests/rime/`, and a data check, romaji in and first choice out, from the table above | 1–2 |
| Phone checks: speed of first load, typing, learning, Back up everything for its history | 1 |
| **Total** | **8 to 10 days** |

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
3. **Japanese with Mozc: yes.** 8 to 10 days, about 18.5 MB in the APK and 19 MB copied out on the phone. Not Rime: it's
   free to add, but fails at sentences and is larger.
4. **Right to left, Hebrew first:** UI work across the bar, the menus and steering.

## Calls to confirm

- **Mozc for Japanese,** at 18.5 MB in the APK. That brings the language study's total to about 180 MB, under Google
  Play's 200 MB.
- **Mozc's Android mode** (choices as you type, whole sentences first) rather than its desktop mode (Space converts and
  shows parts). It fits the bar and `InputEngine` as they are.
- **Our own JNI layer** rather than Mozc's protobuf one.
- **jquery.ime's rules for Hindi** instead of m17n's, because the regex tables port directly to Kotlin.
- **The order:** Thai and Hindi before Japanese, as smaller steps, or Japanese first if it matters more to the owner.

## How to rebuild (study only)

In WSL, under `~/build/lang/ja/` (nothing in the repo):

```bash
git clone --depth 1 https://github.com/google/mozc.git && cd mozc/src
python3 build_tools/update_deps.py
bazelisk build --config oss_android package --config release_build             # bazel-bin/android/jni/native_libs.zip
bazelisk build --config oss_linux --config release_build //data_manager/oss:mozc_dataset_for_oss //session:session_handler_main
```

The side-by-side table feeds each phrase to `session_handler_main --input <file> --profile <fresh folder> --dictionary oss`
with `SEND_KEY ON`, `SEND_KEYS <romaji>`, `SEND_KEY Space`, `SHOW` (Space). For choices as you type, it adds
`SET_MOBILE_REQUEST`, `UPDATE_MOBILE_KEYBOARD QWERTY_MOBILE_TO_HIRAGANA COMMIT` and `SWITCH_COMPOSITION_MODE HIRAGANA`
before the keys.
