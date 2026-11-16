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
