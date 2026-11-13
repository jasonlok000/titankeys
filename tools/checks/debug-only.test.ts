import { expect, test } from "bun:test";
import { existsSync, readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";

// The raw typing log holds what was typed, so the receiver that copies it for adb exists only in TitanKeys Debug: its code is in
// android/debug/recorder, which tools/debug.sh copies in and removes again, and release never compiles.
const root = join(import.meta.dir, "../..");
const read = (path: string) => readFileSync(join(root, path), "utf8");

test("the release is TitanKeys Keyboard outside the app, and the debug build TitanKeys Debug", () => {
  // Owner, 2026-11-13: "TitanKeys Keyboard in the app name so that it's easier to search in the launchers". The launcher and
  // app info take ink.toml's name; the keyboard list and Accessibility take the services' labels.
  const ink = read("ink.toml");
  const manifest = read("android/src/main/AndroidManifest.xml");
  expect(ink).toMatch(/^name = "TitanKeys Keyboard"$/m);
  const labels = [...manifest.matchAll(/android:label="([^"]*)"/g)].map(match => match[1]);
  expect(labels).toEqual(["TitanKeys Keyboard", "TitanKeys Keyboard"]);
  // tools/debug.sh's own edits, applied here as its sed applies them, give TitanKeys Debug everywhere instead.
  const debug = read("tools/debug.sh");
  expect(debug).toContain(`edit ink.toml -e 's/^name = .*/name = "TitanKeys Debug"/'`);
  const label = debug.match(/edit android\/src\/main\/AndroidManifest\.xml 's\/(android:label="[^"]*")\/(android:label="[^"]*")\/'/);
  expect(label).not.toBeNull();
  const debugManifest = manifest.split("\n").map(line => line.replace(label![1], label![2])).join("\n");
  expect([...debugManifest.matchAll(/android:label="([^"]*)"/g)].map(match => match[1])).toEqual(["TitanKeys Debug", "TitanKeys Debug"]);
});

test("Settings > Setup and the boring guide name the list entries TitanKeys Keyboard", () => {
  const setup = read("app/settings/setup.tsx");
  expect(setup).toContain('<Field label="Turn on TitanKeys Keyboard" onPress={enableKeyboard}>');
  expect(setup).toContain('<Field label="Turn on TitanKeys Keyboard in Accessibility" onPress={allowBack}>');
  expect(read("app/settings/guide-boring.tsx")).toContain("Turn TitanKeys Keyboard on in Accessibility.");
  // Ink names the build's files after the app name, so Play's bundle is titankeys-keyboard-<version>.aab.
  expect(read("tools/play.sh")).toContain('bundle="dist/titankeys-keyboard-$version.aab"');
});

test("release has no way to reach the raw log copy", () => {
  expect(existsSync(join(root, "android/kotlin/com/atlas/keyboard/RawLogPull.kt"))).toBe(false);
  expect(read("android/src/main/AndroidManifest.xml")).not.toContain("RawLogPull");
  expect(read("android/src/main/AndroidManifest.xml")).not.toContain("EXPORT_RAWLOG");
});

test("only the Debug build adds it, and only adb can send to it", () => {
  const manifest = read("android/debug/recorder/manifest.xml");
  const entry = manifest.match(/<receiver\s+android:name="com\.atlas\.keyboard\.RawLogPull"[^>]*\/>/)?.[0];
  expect(entry).toBeDefined();
  expect(entry).toContain('android:permission="android.permission.DUMP"');
  expect(read("tools/debug.sh")).toContain('cp "$recorder"/*.kt "$keyboard/"');
  // A second gate, in case it ever reaches another build: it does nothing outside a package that ends in ".debug".
  expect(read("android/debug/recorder/RawLogPull.kt")).toContain('packageName.endsWith(".debug")');
});

test("the panel check for screenshots is only in the Debug build's package", () => {
  // It opens panel pages, marks keys and holds modifiers from adb, so it must never answer in a release.
  const ime = read("android/kotlin/com/atlas/keyboard/AtlasIme.kt");
  const gate = ime.indexOf("if (packageName.endsWith(DEBUG_PACKAGE_SUFFIX)) {");
  const register = ime.indexOf("registerReceiver(panelTrigger, IntentFilter(PANEL_ACTION), RECEIVER_EXPORTED)");
  expect(gate).toBeGreaterThan(0);
  expect(register).toBeGreaterThan(gate);
  expect(ime.slice(gate, register)).not.toContain("\n    }\n");
  expect(ime.match(/(?<!un)registerReceiver\(panelTrigger/g)?.length).toBe(1);
  expect(ime).toContain('PANEL_ACTION = "com.titankeys.debug.PANEL"');
  expect(read("android/src/main/AndroidManifest.xml")).not.toContain("com.titankeys.debug.PANEL");
});

// The release audit (docs/release-audit-2.1.md): every dev-only feature that reaches the release build is gated, and the release
// manifest has exactly the components it should.

const manifest = () => read("android/src/main/AndroidManifest.xml");
const DEBUG = 'packageName.endsWith(".debug")';

/** Each component the manifest declares: its tag, name and exported flag. */
function components(xml: string) {
  return [...xml.matchAll(/<(activity|service|receiver|provider)\s([\s\S]*?)(?:\/>|>)/g)].map(([, tag, attributes]) => ({
    tag,
    name: attributes.match(/android:name="([^"]+)"/)?.[1],
    exported: attributes.match(/android:exported="(true|false)"/)?.[1],
  }));
}

test("the release manifest has exactly the expected components", () => {
  // Ink adds one more when it builds: its MainActivity, exported as the launcher. Its com.vandam.ink.InkLightSdkReceiver, the
  // Light Phone SDK's marker, is taken out by tools/ink/titankeys.patch. See the audit and its re-audit.
  expect(components(manifest())).toEqual([
    { tag: "activity", name: "com.vandam.ink.LearningTransferActivity", exported: "false" },
    { tag: "activity", name: "com.vandam.ink.ContactsPermissionActivity", exported: "false" },
    { tag: "activity", name: "com.atlas.keyboard.ShareDisclosureActivity", exported: "false" },
    { tag: "activity", name: "com.atlas.keyboard.BackDisclosureActivity", exported: "false" },
    { tag: "activity", name: "com.vandam.ink.RawLogExportActivity", exported: "false" },
    { tag: "activity", name: "com.atlas.keyboard.HapticsLabActivity", exported: "false" },
    { tag: "activity", name: "com.atlas.keyboard.KeyCalibrationActivity", exported: "false" },
    { tag: "activity", name: "com.atlas.keyboard.GuideFrameActivity", exported: "false" },
    { tag: "activity", name: "com.atlas.keyboard.FreshStartActivity", exported: "false" },
    { tag: "activity", name: "com.atlas.keyboard.GuideActivity", exported: "false" },
    { tag: "service", name: "com.atlas.keyboard.AtlasIme", exported: "true" },
    { tag: "service", name: "com.atlas.keyboard.BackService", exported: "true" },
    { tag: "service", name: "com.atlas.connected.ConnectedJob", exported: "false" },
    { tag: "receiver", name: "com.atlas.connected.NewsReceiver", exported: "false" },
    { tag: "receiver", name: "com.atlas.keyboard.BugStopReceiver", exported: "false" },
    { tag: "provider", name: "com.atlas.keyboard.BugRecordingProvider", exported: "false" },
  ]);
  // The two exported services answer only the system: binding them needs its own permission.
  expect(manifest()).toMatch(/AtlasIme"[\s\S]*?android:permission="android\.permission\.BIND_INPUT_METHOD"/);
  expect(manifest()).toMatch(/BackService"[\s\S]*?android:permission="android\.permission\.BIND_ACCESSIBILITY_SERVICE"/);
  // And the permissions, no more.
  expect([...manifest().matchAll(/<uses-permission android:name="([^"]+)"/g)].map(match => match[1])).toEqual([
    "android.permission.VIBRATE",
    "android.permission.SYSTEM_ALERT_WINDOW",
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.READ_CONTACTS",
  ]);
  expect(manifest()).not.toContain("android:debuggable");
  expect(manifest()).not.toContain("android:testOnly");
});

test("the guide's still frames are exported only in TitanKeys Debug, for adb", () => {
  expect(read("tools/debug.sh")).toContain(
    `'/android:name="com.atlas.keyboard.GuideFrameActivity"/{n;s/android:exported="false"/android:exported="true"/;}'`);
  expect(read("android/kotlin/com/atlas/keyboard/GuideFrameActivity.kt")).toMatch(/if \(!HapticsLab\.available\(this\)\) \{\s+finish\(\)/);
});

test("the Haptics lab and Key calibration close at once, and refuse every call, outside TitanKeys Debug", () => {
  expect(read("android/kotlin/com/atlas/keyboard/HapticsLab.kt")).toContain(`fun available(context: Context) = context.${DEBUG}`);
  for (const screen of ["HapticsLabActivity", "KeyCalibrationActivity"])
    expect(read(`android/kotlin/com/atlas/keyboard/${screen}.kt`)).toMatch(/if \(!HapticsLab\.available\(this\)\) \{\s+finish\(\)/);
  const adapters = read("android/kotlin/com/vandam/ink/AppNativeAdapters.kt");
  expect(adapters).toContain('check(HapticsLab.available(activity)) { "Key calibration is only in TitanKeys Debug" }');
  expect(adapters).toContain('check(HapticsLab.available(activity)) { "The Haptics lab is only in TitanKeys Debug" }');
  // Every lab call goes through lab(), which refuses.
  for (const call of ["labGraph", "labExperiment", "labFlickHold", "labSelectionEnd", "labPlayFlick"]) {
    const start = adapters.indexOf(`"${call}" -> {`);
    expect(start).toBeGreaterThan(0);
    expect(adapters.slice(start, adapters.indexOf("\n                }", start))).toContain("lab()");
  }
  // Settings shows neither row without the lab's state, which other builds send as null.
  expect(adapters).toContain('.put("hapticsLab", if (HapticsLab.available(activity)) hapticsLab.value.state() else JSONObject.NULL)');
  const settings = read("app/settings/index.tsx");
  expect(settings).toContain('{settings.hapticsLab && <Field label="Key calibration"');
  expect(settings).toContain('{settings.hapticsLab && <Field label="Haptics lab"');
  // Patterns tried in the lab play only in Debug: a release plays the rulebook's.
  expect(read("android/kotlin/com/atlas/keyboard/Haptics.kt")).toContain(`private fun debug(context: Context) = context.${DEBUG}`);
});

test("the full typing log, its export and its screen are only in TitanKeys Debug", () => {
  const gate = read("android/kotlin/com/atlas/keyboard/RawLogGate.kt");
  expect(gate).toContain("fun allows(packageName: String): Boolean = packageName.endsWith(DEBUG_SUFFIX)");
  expect(gate).toContain('const val DEBUG_SUFFIX = ".debug"');
  expect(read("android/kotlin/com/atlas/keyboard/RawLog.kt")).toContain("fun on(context: Context) = available(context) && TypingSettings.rawLog(context)");
  expect(read("android/kotlin/com/vandam/ink/RawLogExportActivity.kt")).toContain("if (!RawLog.available(this)) {");
  expect(read("android/kotlin/com/vandam/ink/AppNativeAdapters.kt")).toContain('check(RawLog.available(activity)) { "The typing log is only in TitanKeys Debug" }');
  expect(read("app/settings/index.tsx")).toContain('{settings.rawLogAvailable && <Field label="Typing log"');
});

test("Record a bug ships, but nothing outside TitanKeys can reach it", () => {
  const xml = manifest();
  expect(xml).toMatch(/BugStopReceiver"\s+android:exported="false"/);
  expect(xml).toMatch(/BugRecordingProvider"\s+android:authorities="\$\{applicationId\}\.bugrecording"\s+android:exported="false"/);
  expect(read("android/kotlin/com/atlas/keyboard/BugRecording.kt")).toContain("It never holds the text around the cursor");
});

test("the adb broadcasts, the idle bar's and the panel check's, are registered only in TitanKeys Debug", () => {
  const ime = read("android/kotlin/com/atlas/keyboard/AtlasIme.kt");
  const gate = ime.indexOf("if (packageName.endsWith(DEBUG_PACKAGE_SUFFIX)) {");
  expect(gate).toBeGreaterThan(0);
  const block = ime.slice(gate, ime.indexOf("\n        }\n", gate));
  expect(block).toContain("registerReceiver(idleBarTrigger, IntentFilter(IDLE_BAR_ACTION), RECEIVER_EXPORTED)");
  expect(block).toContain("registerReceiver(panelTrigger, IntentFilter(PANEL_ACTION), RECEIVER_EXPORTED)");
  expect(ime.match(/(?<!un)registerReceiver\(idleBarTrigger/g)?.length).toBe(1);
  expect(ime).toContain('DEBUG_PACKAGE_SUFFIX = ".debug"');
  // Every other receiver it registers is for the system's own broadcasts, and not exported.
  for (const match of ime.matchAll(/(?<!un)registerReceiver\((\w+), [^\n]*\)/g))
    if (!["idleBarTrigger", "panelTrigger"].includes(match[1])) expect(match[0]).toContain("RECEIVER_NOT_EXPORTED");
  // None of the debug build's own code is in the main source.
  for (const file of ["RawLogPull", "RecorderActivity", "RecorderWords", "ReplayRunner", "SpaceGestureActivity", "TouchSizeActivity", "ConnectedReset"])
    expect(existsSync(join(root, `android/kotlin/com/atlas/keyboard/${file}.kt`))).toBe(false);
});

test("no log line can print what was typed", () => {
  // Exact typing's lines hold key codes and states only, and only Debug writes them.
  const ime = read("android/kotlin/com/atlas/keyboard/AtlasIme.kt");
  expect(ime).toContain('private val exactLogging by lazy { packageName.endsWith(".debug") }');
  expect(ime).toContain('if (exactLogging) Log.i("AtlasExact", line)');
  // No log line anywhere puts the field's text, a typed word, a suggestion or a clip into the log.
  const sources = (dir: string): string[] => readdirSync(join(root, dir), { withFileTypes: true }).flatMap(entry =>
    entry.isDirectory() ? sources(`${dir}/${entry.name}`) : /\.(kt|java)$/.test(entry.name) ? [`${dir}/${entry.name}`] : []);
  const typed = /\$\{?(text|typed|word|before|after|composing|committed|suggestion|candidate|clip)\b|\$\{held\.word\}|\+ (text|typed|word|before|after|composing|suggestion)\b/;
  const leaks = sources("android/kotlin").flatMap(file => read(file).split("\n")
    .filter(line => /\bLog\.[dviwe]\(/.test(line) && typed.test(line)).map(line => `${file}: ${line.trim()}`));
  expect(leaks).toEqual([]);
});
