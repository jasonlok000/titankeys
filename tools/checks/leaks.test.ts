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
  // The text kept for the next playground only tells whether the same one came back, so it holds that one weakly.
  expect(guide).toContain("val from: java.lang.ref.WeakReference<GuideActivity>)");
  expect(guide).not.toMatch(/PlaygroundKept\([^)]*, this\)/);
});

test("the leak check is only in TitanKeys Debug, and only adb can send to it", () => {
  expect(existsSync(join(root, "android/kotlin/com/titankeys/keyboard/LeakCheck.kt"))).toBe(false);
  expect(read("android/src/main/AndroidManifest.xml")).not.toContain("LeakCheck");
  const entry = read("android/debug/recorder/manifest.xml").match(/<receiver\s+android:name="com\.titankeys\.keyboard\.LeakCheck"[^>]*\/>/)?.[0];
  expect(entry).toBeDefined();
  expect(entry).toContain('android:permission="android.permission.DUMP"');
  const check = read("android/debug/recorder/LeakCheck.kt");
  expect(check).toContain('packageName.endsWith(".debug")');
  // Its heap dump goes to the app's own folder on the phone, and never into git: it holds typed text.
  expect(check).toContain('getExternalFilesDir("leaks")');
  expect(read(".gitignore")).toMatch(/^\*\.hprof$/m);
});

test("the crash offer's screen in front is held weakly, so a playground closed after the app's screen went never stays (#85)", () => {
  const adapter = read("android/kotlin/com/vandam/ink/AppNativeAdapters.kt");
  expect(adapter).toContain("private var frontScreen = java.lang.ref.WeakReference<Activity>(null)");
  expect(adapter).toContain("frontScreen = java.lang.ref.WeakReference(target)");
  expect(adapter).not.toMatch(/frontScreen: Activity\?/);
});

test("a held playground that none of the guide's statics holds says how to name its holder (#85)", () => {
  const guide = read("android/kotlin/com/titankeys/keyboard/GuideActivity.kt");
  const memory = guide.slice(guide.indexOf("fun inMemory()"), guide.indexOf("/** Playgrounds that are open now."));
  expect(memory).toContain("guide.isDestroyed -> \", held from outside the guide's statics: run the check with --ez dump true and \"");
  expect(memory).toContain("tools/leak-paths.py on the dump to name the holder");
});

test("the open playgrounds' list holds them weakly and drops closed ones as it's read (#85, leak-85 on the phone)", () => {
  const guide = read("android/kotlin/com/titankeys/keyboard/GuideActivity.kt");
  expect(guide).toContain("private val playgrounds = ArrayList<java.lang.ref.WeakReference<GuideActivity>>()");
  expect(guide).toContain("playgrounds += java.lang.ref.WeakReference(this)");
  expect(guide).toContain("playgrounds.removeAll { it.get()?.isDestroyed != false }");
  expect(guide).not.toMatch(/playgrounds \+= this\b/);
});

test("the front page's Settings and Guide buttons have their names for accessibility, which the phone checks find them by", () => {
  const guide = read("android/kotlin/com/titankeys/keyboard/GuideActivity.kt");
  const action = guide.slice(guide.indexOf("private fun topAction("), guide.indexOf("onPress(haptics) { press() }", guide.indexOf("private fun topAction(")));
  expect(action).toContain("contentDescription = text");
  expect(action).toContain("importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES");
  expect(guide).toContain('topAction("Settings")');
});
