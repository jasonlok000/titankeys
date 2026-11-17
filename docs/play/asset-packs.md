# Play build: install-time asset packs for the big data

A prototype, measured on 2026-11-17: nothing here ships, and the GitHub APK doesn't change. The owner: "Don't worry about
size yet. That's kinda easily solvable." This is that solution, ready for when it's needed.

## Answer

**Move the big data into five install-time asset packs.**

- **Base module:** 41.8 MB to download, from 290 MB (re-measured on main 4080d2d8, after the next-word tables and Mandarin's grammar came in).
- **Each pack:** 11 to 98 MB.
- **What Play sends a Titan:** 283.7 MB in all, the same as today's one APK, since nothing is removed, only split.
- **App code:** no change. Install-time packs arrive with the app as split APKs. Their assets keep the same paths and are read through the app's `AssetManager`, which is how every big file is read today:
  - Rime's and Mozc's data: `RimeData`, copied out with `assets.open`.
  - The swipe models: `ModelAssets.install`.

**Google's limit is higher than 200 MB.** Google's size-limits page ([Maximum size limits](https://support.google.com/googleplay/android-developer/answer/9859372), read 2026-11-17) gives:

| Limit | Size |
|---|---|
| Base module | 500 MB |
| Each feature module | 500 MB |
| Each asset pack | 1.5 GB |
| All modules and install-time packs together | 4 GB |

So today's 290 MB bundle may already be within the base limit. The 200 MB figure (docs/todo-3.0.md) may be older guidance. The packs keep the base small either way, which helps a first download on mobile data.

## The packs

| Pack | Takes | To download (compressed) | On the phone |
|---|---|---:|---:|
| `swipe2_pack` | `assets/swipe2/`: engine 2.0's models, the predictor among them. The files are moved, not changed | 74.0 MB | 74.0 MB |
| `rime_pack` | `assets/rime/` (the tables, both grammars `tk_yue.gram` and `zh-hant-t-essay-bgw.gram`, Chinese's next-word tables) and `assets/rime.sha256` | 97.8 MB | 98.2 MB |
| `next_words_pack` | every input method's next-word tables, `assets/im/*/pairs.txt` (docs/next-words.md) | 45.8 MB | 45.8 MB |
| `mozc_pack` | `assets/mozc/` (`mozc.data`) and `assets/mozc.sha256` | 13.2 MB | 13.2 MB |
| `swipe_pack` | `assets/swipe/` (the swipe models and English's list) | 10.9 MB | 10.9 MB |
| **Base module** | everything else: code, native libraries (32 MB), the input methods' word lists, autocorrect | **41.8 MB** | 62.4 MB (two APKs: the master, and the arm64 libraries) |
| **All, on a Titan** (arm64, Android 15, 440 dpi) | | **283.7 MB** | 297.6 MB |

The figures:
- **To download:** bundletool's `get-size total` for the Titan's device spec. A pack's figure is its module's total minus the base's.
- **On the phone:** the APKs that `extract-apks` gives for that spec.
- **The source:** TitanKeys Debug from main 4080d2d8 (304.5 MB as one APK). The first measure, on main a329c913 before the
  next-word tables and Mandarin's grammar, had a 41.4 MB base and 201.8 MB in all.

Each pack is compressed (`installTimeAssetModuleDefaultCompression: COMPRESSED`). bundletool leaves install-time packs uncompressed unless told to, and that would have nearly doubled them on the phone (the Rime pack would be 120.7 MB). Nothing reads these files in place: the app copies them out, so compressing them costs nothing.

## Checks

- **Bundle:** `bundletool build-bundle` makes the `.aab` (284.0 MB) from the base and the five packs.
- **APK sets:** `build-apks` makes the full APK set and the Titan's set, signed with a throwaway key made for the run. No release key is involved.
- **Assets:** all 304 assets of the APK are in the Titan's set exactly once, byte for byte the same. So every `assets.open` path the app uses is still there, now spread across the split APKs.
- **Not done:** an install on a phone. `install-apks` needs a device, and phone work goes through the phone slot. That check is to install the Titan's set and type in Chinese, Japanese and with a swipe; everything should work on first use as it does now.

## How it would go into the Play build

`tools/dev/build/play-packs.sh` runs this prototype from any built APK. It:
1. converts the APK to the bundle's protobuf form (`aapt2 convert`);
2. moves the packs' assets into their own modules (`play-packs.py`);
3. builds the bundle and measures it.

For the real Play build there are two ways. Neither touches signing or the private Ink patch:

1. **Post-process the bundle that `tools/dev/build/play-bundle.sh` makes**:
   - Unzip its base module, move the same folders into pack modules with the pack manifests that `play-packs.sh` writes, and run `build-bundle` again.
   - The owner then signs the new `.aab` with the upload key, as today (docs/play/signing.md).
   - No Gradle change.
2. **Or Gradle asset-pack modules in Ink's Android project:** a `com.android.asset-pack` module per pack, with `assetPacks += [...]` in the app module. That's a change to Ink's build, so it would sit with the owner's Play patch.

Way 1 keeps Ink as it is, so it's the one to try first.
