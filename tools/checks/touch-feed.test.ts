import { expect, test } from "bun:test";
import { existsSync, readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";

// The touch simulator's touchpad (tools/dev/touch): a feed of built touchpad events into the keyboard. It's TitanKeys Debug's
// alone, and only adb can use it: in TitanKeys it doesn't exist, and the hook it hands events to is never set.
const root = join(import.meta.dir, "../..");
const read = (path: string) => readFileSync(join(root, path), "utf8");

test("the feed is only in TitanKeys Debug, and only adb can send to it", () => {
  expect(existsSync(join(root, "android/kotlin/com/titankeys/keyboard/TouchFeed.kt"))).toBe(false);
  expect(read("android/src/main/AndroidManifest.xml")).not.toContain("TouchFeed");
  const entry = read("android/debug/recorder/manifest.xml").match(/<receiver\s+android:name="com\.titankeys\.keyboard\.TouchFeed"[^>]*\/>/)?.[0];
  expect(entry).toBeDefined();
  expect(entry).toContain('android:permission="android.permission.DUMP"');
  expect(read("android/debug/recorder/TouchFeed.kt")).toContain('packageName.endsWith(".debug")');
});

test("TitanKeys never sets the hook: only TitanKeys Debug's block does, and the keyboard clears it as it goes", () => {
  const ime = read("android/kotlin/com/titankeys/keyboard/TitanKeysIme.kt");
  const sets = [...ime.matchAll(/TouchpadFeedHook\.dispatch = \{/g)].map(match => match.index!);
  expect(sets.length).toBe(1);
  const debugBlock = ime.lastIndexOf("if (packageName.endsWith(DEBUG_PACKAGE_SUFFIX)) {", sets[0]);
  expect(debugBlock).toBeGreaterThan(-1);
  // Nothing closes that block between its start and the hook.
  expect(ime.slice(debugBlock, sets[0]).split("{").length).toBe(ime.slice(debugBlock, sets[0]).split("}").length + 1);
  expect(ime).toContain("TouchpadFeedHook.dispatch = null");
  // Nothing else in the app uses it: only its own file and the keyboard, which sets and clears it.
  const files = (folder: string): string[] => readdirSync(join(root, folder), { withFileTypes: true }).flatMap(entry =>
    entry.isDirectory() ? files(join(folder, entry.name)) : /\.(kt|java)$/.test(entry.name) ? [join(folder, entry.name).replaceAll("\\", "/")] : []);
  expect(files("android/kotlin").filter(path => read(path).includes("TouchpadFeedHook")).sort()).toEqual([
    "android/kotlin/com/titankeys/keyboard/TitanKeysIme.kt", "android/kotlin/com/titankeys/keyboard/TouchpadFeedHook.kt"]);
});

test("the feed shifts its events as the firmware shifts real ones, by Touchpad.kt's own rule", () => {
  const feed = read("android/debug/recorder/TouchFeed.kt");
  const touchpad = read("android/kotlin/com/titankeys/keyboard/Touchpad.kt");
  for (const part of ['"persist.sys.touchpad_forcescroll"', "1200 -> 240f", "1440 -> 360f", "Surface.ROTATION_0", '"Titan_2"'])
    for (const source of [feed, touchpad]) expect(source).toContain(part);
  expect(feed).toContain("y = at.y * yScale + yOffset");
});

test("only TitanKeys Debug opens the guide at a slide from adb; TitanKeys always starts at the first", () => {
  expect(existsSync(join(root, "android/kotlin/com/titankeys/keyboard/GuideStart.kt"))).toBe(false);
  expect(read("android/src/main/AndroidManifest.xml")).not.toContain("GuideStart");
  const entry = read("android/debug/recorder/manifest.xml").match(/<receiver\s+android:name="com\.titankeys\.keyboard\.GuideStart"[^>]*\/>/)?.[0];
  expect(entry).toContain('android:permission="android.permission.DUMP"');
  expect(read("android/debug/recorder/GuideStart.kt")).toContain('packageName.endsWith(".debug")');
  const guide = read("android/kotlin/com/titankeys/keyboard/GuideActivity.kt");
  expect(guide).toMatch(/private fun debugStartStep\(\): String\? =\s+if \(!packageName\.endsWith\("\.debug"\)\) null/);
  // The one place the guide starts afresh uses it, and nothing else reads the extra.
  expect(guide.match(/DEBUG_START_STEP/g)?.length).toBe(2);
  expect(guide).toContain("flow.start(debugStartStep())");
});
