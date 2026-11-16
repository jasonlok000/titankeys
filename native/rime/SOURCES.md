# Rime in TitanKeys: sources, versions and licences

TitanKeys types Chinese with [Rime](https://rime.im) (#56, [docs/languages.md](../../docs/languages.md)). Everything here is
rebuilt from the exact commits below by two scripts, run in WSL (Linux):

```bash
bash native/rime/build.sh   # android/jniLibs/arm64-v8a/librime_tk.so, and the PC tools for data.sh
bash native/rime/data.sh    # android/assets/rime/ and its list, android/assets/rime.sha256
```

`build.sh` needs git, cmake 3.18 or newer, clang, curl and the Android NDK r27c (`sdkmanager "ndk;27.2.12479018"`).
Sources and build trees go to `~/build/tk-rime` (or `$TK_RIME_WORK`), never into the repo. Two runs give the same bytes:
`native/rime/library.sha256` and `android/assets/rime.sha256` record them, and `tools/checks/rime.test.ts` checks the
shipped files against them on every `bun run check`.

## The library: `librime_tk.so`

librime and OpenCC, built for arm64 with [Trime](https://github.com/osfans/trime)'s CMake files and Trime's pins, without
its plugins (librime-lua, librime-octagram, librime-predict), and with TitanKeys' own JNI layer
([`rime_jni.cc`](rime_jni.cc)). It's linked with the NDK r27c the app's `libc++_shared.so` comes from, so it shares it
(the study's call: shared only when the NDKs match, else static). 16 KB pages, as Android 15 and newer want. Only
`JNI_OnLoad` is exported.

| Part | Version | Licence |
| --- | --- | --- |
| [librime](https://github.com/rime/librime) | `bb56876890ba60f446fa2640c244b1794b965eda` (2026-11-11) | BSD-3-Clause |
| [OpenCC](https://github.com/BYVoid/OpenCC), librime's `deps/opencc` | `025f371dc76b598d77384fbdab90c937471844d8` | Apache-2.0 |
| [marisa-trie](https://github.com/s-yata/marisa-trie), librime's `deps/marisa-trie` | `3e87d53b78e15f2f43783d5e376561a8c9722051` | BSD-2-Clause or LGPL-2.1-or-later |
| [LevelDB](https://github.com/google/leveldb), librime's `deps/leveldb` | `bb74ef739973a70ca9f0d90788a1da89b788c0ad` | BSD-3-Clause |
| [yaml-cpp](https://github.com/jbeder/yaml-cpp), librime's `deps/yaml-cpp` | `56e3bb550c91fd7005566f19c079cb7a503223cf` | MIT |
| [spdlog](https://github.com/gabime/spdlog), librime's `deps/spdlog`, with its bundled {fmt} | `79524ddd08a4ec981b7fea76afd08ee05f83755d` | MIT |
| [Snappy](https://github.com/google/snappy) | `9c28114a38866f6deeaa826db918293bc28ae410` | BSD-3-Clause |
| [Boost](https://github.com/boostorg/boost) (CMake release, SHA-256 checked) | 1.92.0 | Boost Software License 1.0 |
| [Trime](https://github.com/osfans/trime): its `cmake/` helpers and its CMakeLists.txt, which ours is based on (not shipped) | `2e2b4d5394297745bf42ff6260bb547a271f378a` (2026-11-12) | GPL-3.0-or-later |

Their licences: `licences/Rime-librime-BSD-3-Clause.txt`, `licences/Rime-OpenCC-Apache-2.0.txt` and
`licences/Rime-dependencies.txt`.

## The data: `android/assets/rime/`

TitanKeys' three schemas, in [`schemas/`](schemas), compiled on the PC by the same librime, with the compiled tables
shipped and the word lists' sources left out. The app copies them to its files on the first use of a Chinese input
method (`RimeData`), since Rime opens its tables as files. Rime records its sources' file times in what it compiles, so
`data.sh` gives every file one fixed time before compiling, and `RimeData` gives its copies the same time: two runs give
the same bytes, and Rime on the phone uses the shipped tables without compiling anything again.

| Part | Version | Licence |
| --- | --- | --- |
| [rime-cantonese](https://github.com/rime/rime-cantonese): Jyutping's word list (shared by Yale), spelling rules, Cantonese punctuation and its Hong Kong OpenCC set | `259f0e48bba840c3a2e0d117539e96937f3d89bc` (2026-08-13) | CC BY 4.0; its place names (`jyut6ping3.maps`) ODbL 1.0 |
| [rime-jyutping](https://github.com/rime/rime-jyutping)'s `yale.schema.yaml`, by 幸延: the Yale spelling rules, copied into `tk_yale.schema.yaml` | `ee5e72b2b50a6708162a0402b91f257323bf852b` (2025-12-30) | LGPL-3.0 |
| [rime-luna-pinyin](https://github.com/rime/rime-luna-pinyin): Pinyin's word list and spelling rules | `56b934b099dfbeab842320f13aa8b461a6ab3e42` | LGPL-3.0 |
| [rime-essay](https://github.com/rime/rime-essay): word counts for Pinyin | `d95eeae056df429ecf993038c325a216f0b28c5b` | LGPL-3.0 |
| [rime-prelude](https://github.com/rime/rime-prelude): Rime's shared files, punctuation and key bindings | `082425ea0684bca36474415d4a0e8db9b016487e` | LGPL-3.0 |
| OpenCC's conversion dictionaries and configs (from librime's `deps/opencc` above) | `025f371` | Apache-2.0 |

Their licences: `licences/Rime-cantonese-CC-BY-4.0.txt`, `licences/Rime-cantonese-ODbL-1.0.txt`,
`licences/Rime-data-LGPL-3.0.txt` and `licences/Rime-OpenCC-Apache-2.0.txt`.

**The LGPL-3.0 data:** its sources are the repositories at the commits above, and `data.sh` is how TitanKeys builds the
shipped tables from them, so anyone can change the sources and rebuild. The schemas in `schemas/` are TitanKeys' own
changes, as text.

## The schemas

- `tk_yale`: Cantonese in Yale, the owner's input method. rime-jyutping's Yale rules on rime-cantonese's word list, with
  one rule added to drop rime-cantonese's tone digits first. No tones are typed.
- `tk_jyutping`: Cantonese in Jyutping, rime-cantonese's `jyut6ping3` without its reverse lookups, emoji and grammar
  model. Tones are optional letters: v, x, q, and vv, xx, qq for the low tones.
- `tk_pinyin`: Mandarin in Pinyin, rime-luna-pinyin's `luna_pinyin` without its stroke lookup.
- `tk_variants`: the Chinese variant, one option of `zh_hk`, `zh_tw`, `zh_hans` that the app sets on every session.

| Variant | Cantonese (Yale, Jyutping) | Mandarin (Pinyin) |
| --- | --- | --- |
| Simplified (mainland China) | OpenCC `t2s` | OpenCC `t2s` |
| Traditional (Hong Kong) | rime-cantonese's `t2hkf` | OpenCC `t2hk` |
| Traditional (Taiwan) | OpenCC `t2tw` | `tk_t2twp`: `t2tw` with Taiwan's phrases |

`data.sh` tries the shipped data the way the phone uses it, with the compiled tables only and a fresh user folder, and
fails unless the first choices are right in each variant (Pinyin "ruan jian" gives 软件, 軟件 and 軟體, for instance), and
unless Rime uses the shipped tables instead of compiling its own.
