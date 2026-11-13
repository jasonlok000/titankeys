import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

// Settings > Setup checks the phone's own settings for the Sym key, the fn key and each app's Sliding Mode. TitanKeys only
// reads them: changing system settings is never its job.
const root = join(import.meta.dir, "../..");
const read = (path: string) => readFileSync(join(root, path), "utf8");
const checks = read("android/kotlin/com/atlas/keyboard/PhoneChecks.kt");

test("the phone checks only read", () => {
  expect(checks).not.toMatch(/Settings\.(System|Global|Secure)\.put/);
  expect(checks).not.toMatch(/getMethod\("set"/);
  expect(checks).toContain('persist.sys.agui_smart_${key.id}_key');
  expect(checks).toContain('"persist.sys.touchpad_forcescroll"');
});

test("Setup shows each phone setting as a step that opens its page", () => {
  const setup = read("app/settings/setup.tsx");
  for (const step of ["Set Sym to Programmable key", "Set fn to Programmable key", "Sliding Mode 1 in your apps"]) expect(setup).toContain(step);
  expect(setup).toContain("onPress={openShortcutKeys}");
  expect(setup).toContain("onPress={openScrollAssistant}");
  const adapter = read("android/kotlin/com/vandam/ink/AppNativeAdapters.kt");
  for (const field of ['"symKey"', '"fnKey"', '"outsideSlidingMode"', '"shortcutKeys"']) expect(adapter).toContain(field);
});

test("the keyboard notes each app's Sliding Mode, and the app can see the Shortcut keys page", () => {
  expect(read("android/kotlin/com/atlas/keyboard/AtlasIme.kt")).toContain("PhoneChecks.noteSlidingMode(this, app)");
  expect(read("android/src/main/AndroidManifest.xml")).toContain('<package android:name="com.agui.shortcutsettings" />');
});
