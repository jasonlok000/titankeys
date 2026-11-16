// The rename to TitanKeys (#81): the code says TitanKeys, Atlas stays only as credit, and the update that turns the keyboard
// and Back off once says so with one quiet notification.
import { expect, test } from "bun:test";
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";

const root = join(import.meta.dir, "../..");
const read = (path: string) => readFileSync(join(root, path), "utf8");

/** Lines that may still say atlas, and why. */
const allowed: [RegExp, string][] = [
  [/github\.com\/juleshall4\/atlas/, "credit: Atlas's own address"],
  [/Based on Atlas|modified version of Atlas/, "credit, in About and Licences"],
  [/atlas\.com|atlas\.co\.uk|"atlas\."/, "an address in a comment or a typing check, not a name"],
  [/\.put\("atlas", version\)|\.put\("atlas", packageManager/, "the typing log's version field: old logs and the tools that read them use it"],
  [/from Atlas's dictionary/, "history: where the recorder's prompts came from"],
];

test("the code names nothing Atlas, except credit", () => {
  const leftovers: string[] = [];
  for (const folder of ["android/kotlin", "android/src", "android/debug", "app", "lib", "server/contributions/src"]) {
    for (const path of new Bun.Glob("**/*.{kt,java,xml,ts,tsx,js}").scanSync(join(root, folder))) {
      read(join(folder, path)).split("\n").forEach((line, index) => {
        if (/atlas/i.test(line) && !allowed.some(([pattern]) => pattern.test(line))) leftovers.push(`${folder}/${path}:${index + 1}: ${line.trim()}`);
      });
    }
  }
  expect(leftovers).toEqual([]);
});

test("the packages, the keyboard's class and the Gradle namespace have their new names", () => {
  expect(existsSync(join(root, "android/kotlin/com/atlas"))).toBe(false);
  expect(existsSync(join(root, "android/kotlin/com/titankeys/keyboard/TitanKeysIme.kt"))).toBe(true);
  expect(read("android/build.gradle.kts")).toContain('namespace = "com.titankeys.keyboard"');
  const manifest = read("android/src/main/AndroidManifest.xml");
  expect(manifest).toContain('android:name="com.titankeys.keyboard.TitanKeysIme"');
  expect(manifest).toContain('android:name="com.titankeys.keyboard.BackService"');
  expect(read("package.json")).toContain('"name": "titankeys"');
});

test("the app ID and the settings files keep their names, so words and settings survive the update", () => {
  expect(read("ink.toml")).toMatch(/^package = "com\.titankeys"$/m);
  expect(read("tools/debug.sh")).toContain('package = "com.titankeys.debug"');
});

test("Ink's and FUTO's packages keep theirs, as the native libraries' JNI names depend on them", () => {
  expect(existsSync(join(root, "android/kotlin/org/futo/ml/inference/NativeDictionary.java"))).toBe(true);
  expect(existsSync(join(root, "android/kotlin/com/vandam/ink"))).toBe(true);
});

test("after the update, one quiet notification opens Setup if the keyboard or Back is off", () => {
  const manifest = read("android/src/main/AndroidManifest.xml");
  expect(manifest).toMatch(/android:name="com\.titankeys\.keyboard\.UpdateReceiver"\s+android:exported="false">\s+<intent-filter>\s+<action android:name="android\.intent\.action\.MY_PACKAGE_REPLACED" \/>/);
  const notice = read("android/kotlin/com/titankeys/keyboard/UpdateNotice.kt");
  expect(notice).toContain("NotificationManager.IMPORTANCE_LOW");
  expect(notice).toContain("setSound(null, null)");
  expect(notice).toContain("enableVibration(false)");
  expect(notice).toContain('const val PAGE = "/settings/setup"');
  expect(existsSync(join(root, "app/settings/setup.tsx"))).toBe(true);
  // Once: the mark is written before anything shows, and any run on the new names sets it.
  const updated = notice.slice(notice.indexOf("fun updated("));
  expect(updated.indexOf("putBoolean(CHECKED, true).commit()")).toBeLessThan(updated.indexOf("show(context)"));
  expect(read("android/kotlin/com/titankeys/keyboard/TitanKeysIme.kt")).toContain("UpdateNotice.ran(this)");
  expect(read("android/kotlin/com/vandam/ink/AppNativeAdapters.kt")).toContain("UpdateNotice.ran(activity)");
});

test("a background job set by 2.x is set again under the new class name", () => {
  expect(read("android/kotlin/com/titankeys/connected/Connected.kt")).toContain("pending == null || pending.service != service");
});
