import { expect, test } from "bun:test";
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";

// #85: nothing long-lived keeps a closed screen, with all its views, and TitanKeys Debug can check that on the phone.
const root = join(import.meta.dir, "../..");
const read = (path: string) => readFileSync(join(root, path), "utf8");

test("a closed app screen leaves nothing in the playground's statics", () => {
  const adapter = read("android/kotlin/com/vandam/ink/AppNativeAdapters.kt");
  const destroyed = adapter.slice(adapter.indexOf("override fun onActivityDestroyed"), adapter.indexOf("override fun onActivityCreated"));
  expect(destroyed).toContain("GuideActivity.appScreenClosed(activity)");
  const guide = read("android/kotlin/com/titankeys/keyboard/GuideActivity.kt");
  // The answer and the wait both hold the screen that asked, so both go, and only for that screen.
  expect(guide).toMatch(/playgroundExit = exit\s*playgroundAsker = activity/);
  expect(guide).toMatch(/fun appScreenClosed\(activity: Activity\) \{\s*if \(playgroundAsker !== activity\) return\s*playgroundAsker = null\s*playgroundExit = null\s*waitingPlayground = null/);
  // The leak check's list holds the guides weakly, so it can never keep one itself.
  expect(guide).toContain("private val made = ArrayList<java.lang.ref.WeakReference<GuideActivity>>()");
});

test("the leak check is only in TitanKeys Debug, and only adb can send to it", () => {
  expect(existsSync(join(root, "android/kotlin/com/titankeys/keyboard/LeakCheck.kt"))).toBe(false);
  expect(read("android/src/main/AndroidManifest.xml")).not.toContain("LeakCheck");
  const entry = read("android/debug/recorder/manifest.xml").match(/<receiver\s+android:name="com\.titankeys\.keyboard\.LeakCheck"[^>]*\/>/)?.[0];
  expect(entry).toBeDefined();
  expect(entry).toContain('android:permission="android.permission.DUMP"');
  expect(read("android/debug/recorder/LeakCheck.kt")).toContain('packageName.endsWith(".debug")');
});
