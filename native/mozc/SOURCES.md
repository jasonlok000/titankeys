# Mozc in TitanKeys: sources, versions and licences

TitanKeys types Japanese with [Mozc](https://github.com/google/mozc) (docs/languages.md, "Japanese"). Everything here is
rebuilt from the exact commit below by one script, run in WSL (Linux):

```bash
bash native/mozc/build.sh   # android/jniLibs/arm64-v8a/libmozc_tk.so, android/assets/mozc/mozc.data, and the PC tool
```

`build.sh` needs git, curl, python3 3.12 or newer and a C++ compiler. Mozc's own `build_tools/update_deps.py` downloads the
rest at the versions Mozc pins (the Android NDK r29 among them), and bazelisk runs the Bazel version Mozc pins
(`src/.bazeliskrc`). Sources and build trees go to `~/build/tk-mozc` (or `$TK_MOZC_WORK`), never into the repo.
`native/mozc/library.sha256` and `android/assets/mozc.sha256` record what was built, and `tools/checks/mozc.test.ts` checks
the shipped files against them on every `bun run check`.

| Part | Version | Licence |
| --- | --- | --- |
| [Mozc](https://github.com/google/mozc) | `a4541df29db443b49f81cf54665873b68fde9efa` (2026-11-09) | BSD-3-Clause |
| [bazelisk](https://github.com/bazelbuild/bazelisk) (build only, not shipped) | 1.25.0, SHA-256 checked | Apache-2.0 |
| Abseil, Protocol Buffers and the rest Mozc pins (in the library) | as `update_deps.py` and Mozc's `MODULE.bazel` pin them | Apache-2.0, BSD-3-Clause |

**The library:** Mozc's engine and session handler with TitanKeys' own layer, `native/mozc/tk` (tk_core, tk_jni, tk_tool:
GPL-3.0-or-later, TitanKeys' own, based on Mozc's `android/jni/mozcjni.cc`). `build.sh` copies it into Mozc's tree as
`src/android/tk`, with no change to Mozc's own files; Bazel's `--nocheck_visibility` lets it use the session handler, which
Mozc keeps visible to its own Android and iOS packages only. Built for arm64 with 16 KB pages, Mozc's libc++ linked in and
hidden: only `JNI_OnLoad` is exported (`tk.map`).

**The data:** `mozc.data`, Mozc's open-source dataset (`//data_manager/oss:mozc_dataset_for_oss`), built by Mozc's own tools
from `src/data/dictionary_oss` and its models. Its sources, as Mozc credits them (`src/data/installer/credits_en.html`):

| Source | Licence |
| --- | --- |
| IPAdic (mecab-ipadic-2.7.0-20070801), most of the words | NAIST's and ICOT's terms: use, copying, changes and distribution allowed, with the notice and ICOT's "no warranty" |
| Okinawa dictionary, place and other names | Public domain |
| Japanese Usage Dictionary | BSD-2-Clause |
| Tamachi Phonetic Kanji Alphabet | MIT |
| Google's own additions (verbs, katakana words, compounds) | Mozc's BSD-3-Clause |

Their licences: `licences/Mozc-BSD-3-Clause.txt` and `licences/Mozc-data.txt` (to add from Mozc's `LICENSE` and
`credits_en.html` with the first build).

## The checks

`build.sh` fails unless the library exports only `JNI_OnLoad`. `tests/japanese` checks the engine's keys with a stand-in for
Mozc; `tests/japanese/corpus.sh` types Tatoeba's reviewed Japanese sentences through `mozc_tool`, the app's own path, and
compares the first choices with the sentences as written and with Mozc's own desktop conversion.
