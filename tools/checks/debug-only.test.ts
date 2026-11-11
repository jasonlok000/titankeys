import { expect, test } from "bun:test";
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";

// The raw typing log holds what was typed, so the receiver that copies it for adb exists only in TitanKeys Debug: its code is in
// android/debug/recorder, which tools/debug.sh copies in and removes again, and release never compiles.
const root = join(import.meta.dir, "../..");
const read = (path: string) => readFileSync(join(root, path), "utf8");

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
