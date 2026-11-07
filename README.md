<div align="center">

<img src="assets/icon.svg" width="112" alt="Atlas app icon">

### Atlas

![Version 2.0.0](https://img.shields.io/badge/version-2.0.0-111111?style=flat-square)

**A toolkit to power up your Unihertz T2E!**

</div>

> **Update, November 2026:** Swipe typing is the first feature in Atlas, and it's ready to try on the Unihertz Titan 2 Elite. Slide across the physical keys without pressing them, and Atlas types the word. I'm now working on more features.

## Features

### Swipe typing

<p align="center">
  <img src="assets/home.png" width="32%" alt="Atlas home screen: a swipe trail across the keyboard types hello, with suggestions below">
  <img src="assets/spelling.png" width="32%" alt="Spelling settings: British or American">
  <img src="assets/vibration.png" width="32%" alt="Swipe vibration settings: off, normal, or strong">
</p>

- Swipe across the physical keys to type.
- Get suggestions for both swiped and typed words.
- Lightly tap configured tap keys to type the letter and a space.
- Atlas learns new words the more you use them.

## Install

Atlas installs through [Obtainium](https://github.com/ImranR98/Obtainium), which also keeps it up to date.

1. Install [Obtainium](https://github.com/ImranR98/Obtainium/releases).
2. In Obtainium, tap **Add App**.
3. Paste the following URL as the app source, then tap **Add**:

   ```text
   https://github.com/juleshall4/atlas
   ```

4. Tap **Install**.

## Build from source

The app uses the local [Ink SDK](https://github.com/No-Scrolling/ink). Keep its checkout next to this repository as `../ink`; the package dependency and build commands use that path. Use the SDK version that supports `CanvasPath` and the theme options in `theme.ts` (currently Ink `0.1.0-alpha.11`).

Install the build tools required by that SDK, including Bun, JDK 21, and the Android SDK. Set `JAVA_HOME` to JDK 21 and `ANDROID_HOME` to the Android SDK, then run:

```sh
bun install --frozen-lockfile
bun run check
bun run build --debug
```

The APK is written to `dist/`. Install it on a connected phone with:

```sh
adb install -r dist/atlas-2.0.0-debug.apk
```

The checked-in native libraries and model files are part of the working app. To rebuild the native libraries, see [the native build instructions](native/futo/README.md).

### Release signing

Release signing is configured in `ink.toml` with the `atlas` key alias. The private keystore is `signing/atlas.keystore`, and local credentials are loaded from `signing/credentials.env`. The entire `signing/` folder is ignored by Git.

Build the signed release APK with:

```sh
bun run release
```

The result is `dist/atlas-2.0.0.apk`. On another build machine, restore the keystore and supply `INK_KEYSTORE_PASSWORD` and, if different, `INK_KEY_PASSWORD` through the environment. The build script also accepts those environment variables locally.

Back up the keystore and its password securely. Future Atlas updates must use the same release key. The release key differs from the debug key, so a release APK cannot update an installed debug build directly. Do not uninstall a debug build without first exporting any learned words you want to keep.

## Privacy

Everything runs and is stored locally on your device. No data is stored or monitored by me or any third party.

## Credits

Atlas is powered by and built with these projects:

- [Ink](https://github.com/No-Scrolling/ink): the app framework, an awesome tool built by my best friend, [vandamd](https://github.com/vandamd) (MIT)
- [FUTO Swipe](https://gitlab.futo.org/keyboard/swipe-library): swipe recognition (GPL-3.0)
- [FUTO Keyboard](https://github.com/futo-org/android-keyboard): contextual prediction (FUTO Source First)
- [FUTO Swipe models](https://huggingface.co/futo-org/futo-swipe): swipe typing models (FUTO Model Weights)
- [AOSP dictionaries](https://codeberg.org/Helium314/aosp-dictionaries/src/branch/main/dictionaries/main_en_gb.dict): British English dictionary (Apache 2.0)
- [Inter](https://github.com/rsms/inter): typeface (SIL Open Font Licence)
