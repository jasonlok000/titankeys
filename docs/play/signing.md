# Signing: use the GitHub key on Google Play

Owner instructions, for #36 and #42. Checked against [Use Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756) and [How app updates work](https://developer.android.com/google/play/app-updates) on 2026-11-10. This is not legal advice.

## Why

Android installs an update over an app only if the package name matches and the signing certificate matches (or has a valid proof of rotation), and the version code is the same or higher. Otherwise the user must uninstall first, which erases their saved words. ([source](https://developer.android.com/google/play/app-updates))

So, for GitHub users to move to Play without losing anything, **Play must sign TitanKeys with the same key as the GitHub APKs**: `signing/atlas.keystore`, alias `atlas` (RSA 3072, valid to 2054, per #36). Google allows this: "Provide a copy of your app signing key". Custom keys must be RSA 2048 or larger, so this key qualifies.

Google's page also confirms the other direction: with the same package name and key, "each app store has the ability to update installations of your app regardless of where the user downloaded the app initially." Play updates an app only once it is in the user's Play library, which happens when they tap Install or Update on Play once.

## The one deadline that matters

**"Signing keys can only be changed before the app is released to open testing or production tracks."**

New apps are now enrolled by default in Google's "quantum-ready hybrid signing" with keys Google makes. So you must change the key on purpose. **Do it right after you create the app, before you upload any bundle**, even to internal or closed testing. Otherwise the first test builds are signed with Google's key, and testers who have the GitHub version can't install them without uninstalling.

## Steps

### 1. Before you start

1. Make sure the backups of `signing/atlas.keystore` and its passwords exist, on two drives outside the repo (#55, decision 3). **If this key is lost, it can't be reset**, and GitHub users can never update again.
2. Note the key's fingerprint. On the build machine, from the TitanKeys folder:

   ```
   keytool -printcert -jarfile dist/titankeys-2.0.0.apk
   ```

   (or `apksigner verify --print-certs dist/titankeys-2.0.0.apk`). Write down the **SHA-256** line. This needs no password.
3. Use Java 17 or newer for the tools below. (**Not verified**, from user reports: Google's PEPK tool can fail with "BadPaddingException" on some keystores with older Java. Java 21 is reported to fix it.)

### 2. Create the app and change the key

4. In Play Console, create the app (name TitanKeys, App, Free).
5. Open the app signing page. In 2026, Google's help names it **Protected with Play > Play Store protection > Manage Play app signing**. Click **Change key**.
6. Choose **Provide a copy of your app signing key** (the option may say "Export and upload a key from Java keystore").
7. The page gives you the **PEPK tool** (`pepk.jar`) and an **encryption public key** file. Download both from that page, not from anywhere else.
8. Run the exact command the page shows. It looks like this (**example only**, copy the console's version):

   ```
   java -jar pepk.jar --keystore=signing/atlas.keystore --alias=atlas --output=atlas-for-play.zip --include-cert --rsa-aes-encryption --encryption-key-path=encryption_public_key.pem
   ```

   It asks for the keystore and key passwords. Type them in yourself. Nobody else, and no agent, should handle them.
9. Upload `atlas-for-play.zip` on the same page and save.
10. **Delete `atlas-for-play.zip`** after the upload. It holds the private key, encrypted for Google.

### 3. Make and register an upload key

Google signs what users install. You sign what you upload with a separate **upload key**. Google says: "For maximum security, your upload key and app signing key should be different." If an upload key is lost or leaks, Google can reset it. The app signing key can't be reset.

11. Make the upload key (RSA 2048 or larger, in a Java keystore). Keep it next to the app signing key, outside the repo, with the same backups:

    ```
    keytool -genkeypair -v -keystore upload.keystore -alias upload -keyalg RSA -keysize 4096 -validity 10000
    ```

12. Export its certificate:

    ```
    keytool -export -rfc -keystore upload.keystore -alias upload -file upload_certificate.pem
    ```

13. Register `upload_certificate.pem` on the app signing page (the console offers this while you change the key, or later under "Upload key certificate"). **Not verified:** the exact place of this step in the 2026 console. If it isn't offered, Google uses the key that signs your first uploaded bundle as the upload key, so make sure the first bundle is signed with `upload.keystore`.

### 4. Check it

14. On the app signing page, compare the **App signing key certificate SHA-256** with the fingerprint from step 2. They must match.
15. After the first closed-test release, install it from Play on your own Titan 2 Elite **over** the GitHub version. Your saved words must still be there.

## Version codes

The GitHub 2.0.0 APK is version code **55**. Every Play build must have a version code **higher than any GitHub APK that a tester or user has installed**, or Play won't offer it as an update.

- **The scheme (decided):** for each release, the GitHub build gets an odd code and the Play build the even one after it. 2.1.0 is 57 on GitHub and 58 on Play; 2.1.1 is 59 and 60. Then Play always offers its build to GitHub users of the same version.
- `ink.toml` holds the GitHub code. `tools/play.sh` checks that it is odd and builds with the next one, so the Play code is never typed by hand.
- Never reuse or lower a code on Play. The maximum is 2,100,000,000.

## Warnings

- **Never lose `atlas.keystore`.** Play keeps a copy, but you need it to sign the GitHub APKs, and it can't be reset.
- **Don't change the key after testing starts.** It is fixed once the app reaches open testing or production.
- **Be careful with "key upgrade".** Google offers a yearly key upgrade, and its March 2026 post says Play will prompt developers to upgrade "at least every two years". On Android 13 to 16, the phone then only accepts updates signed with the newest key, so GitHub APKs signed with `atlas.keystore` would no longer install over a Play install. Don't accept an upgrade unless you are ready to stop GitHub APKs or sign them with the new key. ([Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756), [Google's post-quantum post](https://blog.google/security/security-for-the-quantum-era-implementing-post-quantum-cryptography-in-android/))
- **Android 17 and hybrid signing (uncertain).** Android 17 checks a new "v3.2" hybrid signature. Google's page doesn't say how a key you provide works with hybrid signing. The Titan 2 Elite runs Android 15 or 16 today, where the classic signature is checked, so this doesn't affect launch. Test a Play update over a GitHub install again if the phone ever gets Android 17.
- **Keep keys out of git.** `/signing/` is already ignored. Put `upload.keystore` there too, or outside the repo.
- **Developer verification bonus.** From 2027, Android checks that apps on certified phones come from verified developers, also for APKs from GitHub. Apps that use Play App Signing are registered automatically, and the Play Console account can also register the key used outside Play. With the same key on both, the GitHub APK is covered too. ([FAQ](https://developer.android.com/developer-verification/guides/faq))
