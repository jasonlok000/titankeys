<div align="center">

<img src="assets/icon.png" width="112" alt="Atlas app icon">

### Atlas

![Version 2.0.0](https://img.shields.io/badge/version-2.0.0-111111?style=flat-square)

**A toolkit to power up your Unihertz T2E!**

</div>

> **Update, November 2026:** Swipe typing is the first feature in Atlas, and it's ready to try on the Unihertz Titan 2 Elite. Slide across the physical keys without pressing them, and Atlas types the word. I'm now working on more features.

## Features

### Swipe typing

- Swipe across the physical keys to type.
- Get suggestions for both swiped and typed words.
- Lightly tap configured tap keys to type the letter and a space.
- Atlas learns new words the more you use them.

## Install

I highly recommend installing through [Obtainium](https://github.com/ImranR98/Obtainium) so you can receive updates.

You can also download and install the APK from [Releases](https://github.com/juleshall4/atlas/releases). 

1. Install [Obtainium](https://github.com/ImranR98/Obtainium/releases).
2. In Obtainium, tap **Add App**.
3. Paste the following URL as the app source, then tap **Add**:

   ```text
   https://github.com/juleshall4/atlas
   ```

4. Tap **Install**.

## Privacy

Everything runs and is stored locally on your device. No data is stored or monitored by me or any third party.

Atlas doesn't learn from fields marked as passwords, email addresses, URLs, or private by the app.

Atlas asks to display over other apps because of how the Titan 2 sends touchpad input. The phone gives the touchpad to the keyboard's window, unless another window sits above it. When you select text, apps show their Cut and Copy toolbar above the keyboard, and that toolbar would take your swipes. Atlas adds an invisible, one-pixel window above other apps so your swipes still reach the keyboard. It never shows anything, never reads the screen, and lets every screen touch pass through. Android shows a silent "Atlas is displaying over other apps" notification while it's there, which you can turn off.

## Credits

Atlas is powered by and built with these projects:

- [Ink](https://github.com/No-Scrolling/ink): the app framework, an awesome tool built by my best friend, [vandamd](https://github.com/vandamd) (MIT)
- [FUTO Swipe](https://gitlab.futo.org/keyboard/swipe-library): swipe recognition (GPL-3.0)
- [FUTO Keyboard](https://github.com/futo-org/android-keyboard): contextual prediction (FUTO Source First)
- [FUTO Swipe models](https://huggingface.co/futo-org/futo-swipe): swipe typing models (FUTO Model Weights)
- [AOSP dictionaries](https://codeberg.org/Helium314/aosp-dictionaries/src/branch/main/dictionaries/main_en_gb.dict): British English dictionary (Apache 2.0)
- [Inter](https://github.com/rsms/inter): typeface (SIL Open Font Licence)
