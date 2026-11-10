# Build tasks for the Google Play build

For the build agent of workstream G (#36, #55). Research only so far: nothing here is built or changed yet. Inspected read-only on 2026-11-10: the Ink build copy in WSL (`~/build/ink`, commit `8a833ad`) and the TitanKeys repo (`X:\Atlas\.dev\titankeys`, branch `overnight` at `05696d9`, the same commit as `2.1.0`).

**Ground rules** (from `AGENTS.md` and #55):

- Build only in WSL `atlas-build` as `dev`. All sessions share one build lock (`~/build/ink/target/ink-android.lock`), and each build takes 4 to 5 minutes.
- `~/build/ink` is shared by every session. Any Ink change must leave today's behaviour exactly as it is, and must survive an Ink update (keep it as a commit or a patch file).
- No phone without the orchestrator. Never force-stop TitanKeys. Keep tests silent.
- Never commit keys or passwords. Don't read or print the passwords; scripts source them from `signing/credentials.env` as `tools/release.sh` does.
- Run `bun run check` and the tests in `AGENTS.md` for the parts you change.

## Facts found

| What | Value | Where |
|---|---|---|
| Ink's release task | `:app:assembleRelease` (debug: `:app:assembleDebug`) | `crates/ink-cli/src/android.rs`, `Profile::gradle_task` |
| Build command | `ink build [--debug]`, an enum in `crates/ink-cli/src/cli.rs` (`InkCommand::Build { debug }`), handled in `main.rs` line 140 | |
| How Ink runs | `scripts/ink` runs `cargo run -p ink-cli` from source, so a CLI change works on the next build without an install step | `~/build/ink/scripts/ink` |
| Android Gradle Plugin | 8.12.3 (Gradle 8.14.3) | `platform/android/build.gradle.kts` |
| NDK | 29.0.14206865 (16 KB aligned by default) | `platform/android/app/build.gradle.kts` |
| ABIs | `arm64-v8a` only | same |
| SDK levels | compile 36, target 36, min 34 | same |
| Release signing | `[signing]` in `ink.toml` (keystore, alias), passwords from `INK_KEYSTORE_PASSWORD` and `INK_KEY_PASSWORD` | `android.rs::gradle_command`, `app/build.gradle.kts` |
| Version | `version` and `version_code` in `ink.toml` (2.0.0 / 55), passed as `-PinkVersionCode` | `ink.toml` |
| Output | `.ink/build/android/outputs/apk/release/app-release.apk`, copied to `dist/<slug>-<version>.apk`, then checked with `apksigner verify` | `android.rs::build_artifact`, `copy_build`, `verify_apk` |
| R8 | on for release (`isMinifyEnabled`, `isShrinkResources`) | `app/build.gradle.kts` |
| Tools in WSL | Java 17, `keytool`, build-tools 35.0.0 and 36.0.0 (`apksigner`, `zipalign`), NDK `llvm-readelf`. **No `bundletool`.** | `~/android-sdk` |
| How TitanKeys makes a variant | `tools/debug.sh` edits `ink.toml` and the manifest, builds with `tools/release.sh`, then restores them | `tools/debug.sh` |
| How 2.1 knows it is the Play build | `Connected.build()` reads manifest meta-data `com.titankeys.build` (`"play"`, `"github"`, `"debug"`) | branch `claude/connected-ba75d6`, `android/kotlin/com/atlas/connected/Connected.kt` (untracked, in progress) |

## A. Make an App Bundle with Ink

Play needs an Android App Bundle (AAB) for new apps. The 113 MB app is far below the 500 MB limit for the base module ([size limits](https://support.google.com/googleplay/android-developer/answer/9859372)).

**The smallest change: a `--bundle` flag** that runs `:app:bundleRelease` instead of `:app:assembleRelease`. Everything else stays the same. (Running both tasks in every release build would be a smaller diff, but `tools/debug.sh` also builds through the release path, so every debug build would pay for a bundle it doesn't need.)

**In `~/build/ink/crates/ink-cli/src/cli.rs`**, `InkCommand::Build`:

```rust
Build {
    /// Build a development APK instead
    #[arg(long)]
    debug: bool,
    /// Build a release-signed Android App Bundle (.aab) for Google Play instead of an APK
    #[arg(long, conflicts_with = "debug")]
    bundle: bool,
},
```

**In `crates/ink-cli/src/android.rs`:**

1. Add `Profile::Bundle`:
   - `gradle_task` → `":app:bundleRelease"`
   - output path → `.ink/build/android/outputs/bundle/release/app-release.aab` (AGP's standard place under the build folder that Ink sets with `-PinkBuildRoot`). Replace `directory()` and `apk_name()` with one `output()` that returns the relative path for each profile.
   - `label` → `"release bundle"`
2. `gradle_command`: treat `Bundle` like `Release`. Signing: `if profile != Profile::Debug`. Assets: `Profile::Debug => android_assets_path, _ => release_assets_path`.
3. `compile`: no change (the `else` branch already covers it).
4. `build`: skip `verify_apk` for `Bundle`, because `apksigner` can't check an `.aab`. Instead, run `jarsigner -verify` on it (the JDK has it), or leave the check to bundletool in task C9.
5. `copy_build`: use the `.aab` extension for `Bundle`, so the file is `dist/titankeys-<version>.aab`.
6. `assemble`: the progress text says "Building … APK". Make it say "bundle" for `Bundle`.

**In `crates/ink-cli/src/main.rs`** (line 140): pick `Profile::Bundle` when `bundle` is set, and print "AAB" instead of "APK" in the result line.

**On the TitanKeys side:** `tools/release.sh` already passes its arguments on (`exec bun run build "$@"`), so `bash tools/release.sh --bundle` works with no change.

**Check:** `ink build` and `ink build --debug` behave exactly as before (same files in `dist/`, same verification). Then `bash tools/release.sh --bundle` makes `dist/titankeys-<version>.aab`.

**Optional, for Play Console:** upload the R8 mapping file (`.ink/build/android/outputs/mapping/release/mapping.txt`) with each release, so crash reports are readable. Play will also warn that native debug symbols are missing. That is a warning only.

## B. 16 KB memory page alignment

**The rule.** "All apps targeting Android 15 (API level 35) and higher must support 16 KB memory page sizes on 64-bit devices on Google Play. Starting February 1, 2027, if your app updates don't support 16 KB memory page sizes, you won't be able to release these updates." ([source](https://developer.android.com/guide/practices/page-sizes)) Fix it before the first upload. **Not verified:** whether Play Console already blocks a brand-new app today, or only warns.

**What is wrong.** Per #36, `android/jniLibs/arm64-v8a/libonnxruntime4j_jni.so` (ONNX Runtime 1.20.0) has 4 KB `LOAD` alignment. `libonnxruntime.so`, `libswipe_jni.so`, `libpersonal_dictionary.so` and `libc++_shared.so` are 16 KB aligned. Ink's own runtime library is built with NDK r29, which aligns to 16 KB by default.

**Why it happened, and which version fixes it.** ONNX Runtime built the Java JNI library as a CMake MODULE, which didn't get the 16 KB linker flag. Issue [#24902](https://github.com/microsoft/onnxruntime/issues/24902) reported exactly this for 1.20.0. The fix, [PR #24947](https://github.com/microsoft/onnxruntime/pull/24947), merged into `main` on **4 June 2025**.

| Version | 16 KB JNI library? | Evidence |
|---|---|---|
| 1.20.0 (TitanKeys now) | No | #24902, and #36's check |
| 1.21.0 | No | Warnings reported in [#26228](https://github.com/microsoft/onnxruntime/issues/26228) |
| 1.22.0 (9 May 2025) | Unclear | Released before the fix. One user in [#25859](https://github.com/microsoft/onnxruntime/issues/25859) says it passed Play's check, but another says 1.22.2 lacks the fix. Avoid. |
| **1.23.0 (25 Sep 2025) and later** | **Should be yes** | First release cut from `main` after the fix. A maintainer pointed users to 1.23.0 for it in #26228. **Not verified on the binary.** |
| Latest on Maven Central | 1.31.1 (26 Oct 2026) | [Maven Central listing](https://repo1.maven.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/) |

**Option 1 (recommended): upgrade ONNX Runtime to 1.23.2**, the smallest step that should include the fix. If its JNI library is still 4 KB aligned, try the latest (1.31.1).

1. Ask the orchestrator before downloading. Download `onnxruntime-android-1.23.2.aar` from `https://repo1.maven.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/1.23.2/` into a new empty folder, and check it against the `.sha1` file next to it.
2. Unzip it there. Check both libraries before using them:

   ```bash
   READELF=~/android-sdk/ndk/29.0.14206865/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf
   for f in jni/arm64-v8a/*.so; do echo "$f"; "$READELF" -lW "$f" | grep -E 'LOAD|GNU_RELRO'; done
   ```

   Every `LOAD` line must have `Align 0x4000` (or larger). Also apply Google's RELRO rule: either no `GNU_RELRO`, or `(VirtAddr + MemSiz) % 0x4000 == 0`, or the `GNU_RELRO` ends where its `LOAD` segment ends ([source](https://developer.android.com/guide/practices/page-sizes)).
3. Also check what it links against: `"$READELF" -d jni/arm64-v8a/libonnxruntime.so | grep NEEDED`. If it needs `libc++_shared.so`, make sure the one in `jniLibs` is compatible.
4. If both pass, replace in TitanKeys:
   - `android/jniLibs/arm64-v8a/libonnxruntime.so` and `libonnxruntime4j_jni.so`
   - `android/libs/onnxruntime-android-1.20.0.jar` with the AAR's `classes.jar`, renamed `onnxruntime-android-1.23.2.jar`
   - the path and the comment in `android/build.gradle.kts`
   - "ONNX Runtime 1.20.0" in `licences/Swipe-engine-2-models.md`
   - `android/proguard-rules.pro` already keeps `ai.onnxruntime.**`, so no change there.
5. **Coordinate with the Swiping engine session** (workstream B) before merging. A new runtime can change float results slightly, and engine changes ship only if they hold the held-back test (78.9% / 85.7%). Run `bash tests/swipe/run.sh`, `bash tests/words/run.sh` and the held-back replay, and compare with 1.20.0. The models stay as they are: a newer runtime runs older model files.

**Option 2: rebuild only the JNI library of 1.20.0.** It keeps the runtime exactly the same, but leaves a custom binary to maintain.

- Take the `v1.20.0` tag of onnxruntime. The JNI library is the C code in `java/src/main/native/`, built against the C API headers (`include/onnxruntime/core/session/`) and JNI headers made with `javac -h` from `java/src/main/java/ai/onnxruntime/`.
- Compile it with NDK r29 for `aarch64-linux-android` and link it to the prebuilt 1.20.0 `libonnxruntime.so` (`-lonnxruntime -llog`). r29 aligns to 16 KB by default; add `-Wl,-z,max-page-size=16384` to be explicit. Copy any compile flags from ORT's `cmake/onnxruntime_java.cmake`.
- Check it with the commands in step 2, then test as in step 5.

**Not an option:** changing the alignment of the existing 4 KB binary. Its segments have to be laid out again, which needs a new link.

**Check the result** in the built bundle and APK (task C9):

```bash
bundletool dump config --bundle=dist/titankeys-<version>.aab | grep -i alignment   # expect PAGE_ALIGNMENT_16K
zipalign -c -P 16 -v 4 <universal>.apk                                              # expect "Verification successful"
```

Play Console also shows 16 KB support for each bundle in the App bundle explorer.

## C. Everything else the Play build needs

Target SDK 36 is already done. The rest, in rough order. Items marked **(F)** belong to workstream F's code; the build agent only checks them.

**C1. A Play build script, `tools/play.sh`.** Follow `tools/debug.sh`: back up, edit, build, restore on exit.

- Set `version_code` in `ink.toml` to the Play code given as an argument (`signing.md`: higher than every GitHub APK, for example 57 for 2.1.0).
- Point `[signing]` at the upload key (`signing/upload.keystore`, alias `upload`), with its passwords from a separate ignored file, for example `signing/upload-credentials.env`. Don't print them.
- Add `<meta-data android:name="com.titankeys.build" android:value="play" />` inside `<application>` in `android/src/main/AndroidManifest.xml`, so `Connected.build()` returns `"play"`.
- Run `bash tools/release.sh --bundle`.
- Restore every file it changed, even if the build fails (`trap restore EXIT`, as in `debug.sh`).

**C2. Accessibility: optional, with its own disclosure** (`accessibility.md`).

- A disclosure screen with "No thanks" and "Agree" before the guide opens Android's Accessibility page (the "Back" step in `GuideSteps.kt`), and the same screen from Settings > Setup ("Turn on TitanKeys in Accessibility", `app/settings/setup.tsx`).
- The guide must let the user go on after "No thanks". Today its front page waits until the service is on (#36).
- Back, Home or tapping outside never counts as agreement. The screen never closes by itself.
- Shorten `back_service_description` in `android/src/main/res/values/strings.xml` to match.
- Do it in both builds, so there is one flow to test and film.

**C3. The typing log (#16) in the Play build.** Decision for the owner (see `summary.md`). If it is left out, the simplest way: when `Connected.build()` is `"play"`, hide the Typing log row in Settings and never start recording. Keep `RawLogExportActivity` harmless (it is not exported). If it stays, it needs its own disclosure and consent before it turns on.

**C4. Check the final permissions and manifest** of the built bundle:

```bash
bundletool dump manifest --bundle=dist/titankeys-<version>.aab
```

Expected `uses-permission`: `VIBRATE`, `SYSTEM_ALERT_WINDOW`, `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, and nothing else. In particular, no `com.google.android.gms.permission.AD_ID`, no `QUERY_ALL_PACKAGES`, no `RECEIVE_BOOT_COMPLETED` or foreground-service permissions unless 2.1 really uses them. Ink adds permissions per capability (`crates/ink-compiler/capabilities-v1.json`): its `network` capability adds `INTERNET` and `ACCESS_NETWORK_STATE`, and `notification-permission` adds `POST_NOTIFICATIONS`. If F adds them through Ink capabilities instead of the app manifest, check that no other capability came with them. Also check `com.titankeys.build` is `play`, `versionCode` is the Play code, and `targetSdkVersion` is 36.

**C5. Privacy policy link in the app (F).** Google wants it "within the app itself". The 2.1 code has `Connected.PRIVACY_URL` (`https://jasonlok000.github.io/titankeys/privacy/`). Check it is reachable from Settings (for example About) and from the contributions disclosure, and that the page is live before the first upload.

**C6. Feed entries for Play (F).** The Play build shows only entries marked for `"play"`, and none of them may link to an APK. Google Play doesn't allow an app to "modify, replace, or update itself using any method other than Google Play's update mechanism" ([source](https://support.google.com/googleplay/android-developer/answer/9888379)). The same goes for models: never download new model files in the Play build. Ship them in app updates.

**C7. Contributions disclosure (F).** Its own screen, with "I agree" and "No thanks", shown right before collection starts, separate from the accessibility one. Nothing is queued for sending before "I agree".

**C8. Notification permission (F).** Ask with a reason (`permissions.md`) when there is news to show or when the user turns News on, not on first launch.

**C9. Test the bundle locally before uploading.** Ask the orchestrator before downloading `bundletool` (from Google's [bundletool releases](https://github.com/google/bundletool/releases)) and before using the phone.

```bash
java -jar bundletool.jar validate --bundle=dist/titankeys-<version>.aab
jarsigner -verify -verbose -certs dist/titankeys-<version>.aab | tail   # signed with the upload key
java -jar bundletool.jar build-apks --bundle=dist/titankeys-<version>.aab --output=/tmp/tk.apks --mode=universal \
  --ks=signing/atlas.keystore --ks-key-alias=atlas   # signs like Play will; passwords via --ks-pass=file:...
unzip -o /tmp/tk.apks universal.apk -d /tmp/tk && zipalign -c -P 16 -v 4 /tmp/tk/universal.apk
```

Then, with the orchestrator's go-ahead, install the universal APK over the GitHub version on the phone and check that saved words survive and that swiping, symbols, emoji and the accessibility flow work.

**C10. "No network access" text (F, #53).** Check that the README, the app and the 2.1 notes no longer say it.

**C11. Licence check (#36, not a build step).** The source of `android/assets/swipe/english.combined` is still unknown (#36). Compare its hash with AOSP's word list and fix the credits before launch.

**C12. Pre-launch report.** Play runs the closed-test build on Google's test phones. They have no Titan 2 Elite keys, so expect little to see. Look only for crashes on start, and turn the report off if it gets in the way.

## Done when

- `bash tools/play.sh <code>` makes a signed `.aab` with the Play meta-data and the Play version code.
- Every native library in it passes the 16 KB check, and `bundletool dump config` shows `PAGE_ALIGNMENT_16K`.
- `ink build` and `ink build --debug` work exactly as before for every other session.
- The swipe and word tests and the held-back replay are no worse than with ONNX Runtime 1.20.0.
- The accessibility disclosure works with "No thanks" and "Agree", and the guide goes on after "No thanks".
- The bundle installs over the GitHub 2.0.0 APK and keeps saved words (after the owner has set Play's signing key, `signing.md`).
