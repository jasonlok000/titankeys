// Checks that the Haptics lab is only in TitanKeys Debug: the row, the page and every native operation need a ".debug" package.
import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

const root = join(import.meta.dir, "../..");
const read = (path: string) => readFileSync(join(root, path), "utf8");

test("only a debug package has the lab", () => {
  expect(read("android/kotlin/com/atlas/keyboard/HapticsLab.kt"))
    .toContain('fun available(context: Context) = context.packageName.endsWith(".debug")');
  // Every other build sends null, which hides the row and empties the page.
  expect(read("android/kotlin/com/vandam/ink/AppNativeAdapters.kt"))
    .toContain('.put("hapticsLab", if (HapticsLab.available(activity)) hapticsLab.value.state() else JSONObject.NULL)');
});

test("every lab operation refuses outside a debug package", () => {
  const adapter = read("android/kotlin/com/vandam/ink/AppNativeAdapters.kt");
  const operations = [...adapter.matchAll(/^\s+"(lab\w+)" -> \{?\s*(.*)$/gm)];
  expect(operations.map(match => match[1]).sort()).toEqual(["labGraph"]);
  for (const [line, , body] of operations) {
    const first = body || adapter.slice(adapter.indexOf(line) + line.length).trimStart().split("\n")[0];
    expect(first.startsWith("lab().")).toBe(true);
  }
  expect(adapter).toContain('check(HapticsLab.available(activity)) { "The Haptics lab is only in TitanKeys Debug" }');
  // Nothing else reaches the lab without that check.
  expect(adapter.match(/hapticsLab\.value/g)?.length).toBe(3);
});

test("Settings shows the lab only when the build has it", () => {
  const settings = read("app/settings/index.tsx");
  expect(settings).toContain('{settings.hapticsLab && <Field label="Haptics lab" href="/settings/haptics-lab" />}');
  expect(settings.match(/haptics-lab/g)?.length).toBe(1);
  const page = read("app/settings/haptics-lab.tsx");
  expect(page).toContain('if (!lab) return <Screen title={title} subtitle="The Haptics lab is only in TitanKeys Debug." />;');
  expect(page.indexOf("if (!lab) return")).toBeLessThan(page.indexOf("<Lab "));
});

test("the graph screen shows nothing outside a debug package", () => {
  const screen = read("android/kotlin/com/atlas/keyboard/HapticsLabActivity.kt");
  const onCreate = screen.slice(screen.indexOf("override fun onCreate("));
  // The check comes before anything is built or shown, and a build without the lab closes the screen.
  expect(onCreate).toMatch(/^override fun onCreate\(savedInstanceState: Bundle\?\) \{\s+super\.onCreate\(savedInstanceState\)\s+\/\/[^\n]*\n\s+if \(!HapticsLab\.available\(this\)\) \{\s+finish\(\)\s+return\s+\}/);
  expect(onCreate.indexOf("HapticsLab.available(this)")).toBeLessThan(onCreate.indexOf("setContentView"));
  // Other apps can't open it, and only the lab, which the adapter reaches through lab(), starts it.
  const manifest = read("android/src/main/AndroidManifest.xml");
  expect(manifest).toMatch(/android:name="com\.atlas\.keyboard\.HapticsLabActivity"\s+android:exported="false"/);
  const users = [...new Bun.Glob("**/*.{kt,java}").scanSync(join(root, "android"))].filter(path =>
    !path.endsWith("HapticsLabActivity.kt") && read(join("android", path)).includes("HapticsLabActivity"));
  expect(users.map(path => path.replaceAll("\\", "/"))).toEqual(["kotlin/com/atlas/keyboard/HapticsLab.kt"]);
});

test("only a debug package reads or saves a tried pattern", () => {
  const haptics = read("android/kotlin/com/atlas/keyboard/Haptics.kt");
  expect(haptics).toContain('private fun debug(context: Context) = context.packageName.endsWith(".debug")');
  // Every other build plays the rulebook's pattern before it looks at the stored ones.
  expect(haptics).toMatch(/fun pattern\(context: Context, family: Family\): List<Long> \{\s+if \(!debug\(context\)\) return family\.pattern\n/);
  expect(haptics).toMatch(/fun tryPattern\(context: Context, family: Family, pattern: List<Long>\?\) \{\s+check\(debug\(context\)\)/);
  // The stored patterns are read in one place, and only the lab tries one.
  expect(haptics.match(/getString\(patternKey/g)?.length).toBe(1);
  const users = [...new Bun.Glob("**/*.{kt,java}").scanSync(join(root, "android"))].filter(path =>
    read(join("android", path)).includes("Haptics.tryPattern"));
  expect(users.map(path => path.replaceAll("\\", "/"))).toEqual(["kotlin/com/atlas/keyboard/HapticsLab.kt"]);
});

test("every haptic is our own waveform, with none of the phone's ready-made effects", () => {
  for (const path of new Bun.Glob("**/*.{kt,java}").scanSync(join(root, "android"))) {
    const source = read(join("android", path));
    expect(source.includes("createPredefined"), path).toBe(false);
    expect(source.includes("startComposition"), path).toBe(false);
    expect(/VibrationEffect\.EFFECT_/.test(source), path).toBe(false);
  }
  const haptics = read("android/kotlin/com/atlas/keyboard/Haptics.kt");
  expect(haptics).toContain("VibrationEffect.createWaveform(");
  expect(haptics).toContain("VibrationEffect.createOneShot(pattern[0], VibrationEffect.DEFAULT_AMPLITUDE)");
});

test("the old adb length receivers are gone", () => {
  const ime = read("android/kotlin/com/atlas/keyboard/AtlasIme.kt");
  expect(ime).not.toContain("MOVING_MILLIS");
  expect(ime).not.toContain("STEP_MILLIS");
});

test("the lab shows what the keyboard really plays, from a feed only the debug build fills", () => {
  const haptics = read("android/kotlin/com/atlas/keyboard/Haptics.kt");
  // The keyboard and the lab share one process, so the feed is a plain object. Nothing else may run them apart.
  expect(read("android/src/main/AndroidManifest.xml")).not.toContain("android:process");
  // The feed starts off, and only a debug package turns it on and records into it.
  expect(haptics).toMatch(/object HapticFeed \{\s+@Volatile\s+var enabled = false/);
  expect(haptics).toMatch(/fun record\(event: HapticEvent\) \{\s+if \(!enabled\) return/);
  expect(haptics).toContain("private val feeds = debug(context)");
  expect(haptics).toContain("if (feeds) HapticFeed.enabled = true");
  expect(haptics.match(/HapticFeed\.record\(/g)?.length).toBe(1);
  expect(haptics).toContain("if (feeds) HapticFeed.record(");
  const users = [...new Bun.Glob("**/*.{kt,java}").scanSync(join(root, "android"))].filter(path =>
    !path.endsWith("Haptics.kt") && read(join("android", path)).includes("HapticFeed"));
  expect(users.map(path => path.replaceAll("\\", "/"))).toEqual(["kotlin/com/atlas/keyboard/HapticsLabActivity.kt"]);
  // The lab stops listening as it goes, so the keyboard never holds on to a closed screen.
  const screen = read("android/kotlin/com/atlas/keyboard/HapticsLabActivity.kt");
  expect(screen).toMatch(/override fun onPause\(\) \{[^}]*HapticFeed\.stop\(\)/);
  expect(screen).toContain("HapticFeed.listen {");
});

test("the lab has a practice field, a live timeline and an editor with small Play and Reset", () => {
  const screen = read("android/kotlin/com/atlas/keyboard/HapticsLabActivity.kt");
  expect(screen).toContain('val practice = field("Type, swipe, steer, open panels and menus here.", 3)');
  expect(screen).toContain('card("Last 5 seconds", timeline, key())');
  expect(screen).toContain('setOnClickListener { edit(event.haptic.family, event.haptic) }');
  expect(screen).toContain('button("Play", "Play ${family.label} alone") { lab.preview(family) }');
  expect(screen).toContain('button("Reset", "Reset ${family.label}")');
  // Nothing plays a haptic alone but the editor's Play.
  expect(screen.match(/lab\.preview\(/g)?.length).toBe(1);
  expect(screen).not.toContain("lab.play(");
  // The sliders keep every gap at the rulebook's 35 ms or more.
  expect(read("android/kotlin/com/atlas/keyboard/Haptics.kt")).toContain("val GAP_MILLIS = MIN_GAP_MILLIS..200L");
});

test("two taps above Space play no only in the guide's practice field", () => {
  const ime = read("android/kotlin/com/atlas/keyboard/AtlasIme.kt");
  expect(ime.match(/Haptic\.TwoTapsAbove/g)?.length).toBe(1);
  // The play sits after the check that returns outside the guide's field.
  const block = ime.slice(ime.indexOf("private val confirmTwoAbove = Runnable {"));
  const body = block.slice(0, block.indexOf("\n    }\n"));
  expect(body).toMatch(/if \(!inGuide \|\| !cursorGesture\.confirmTwoAbove\(\)\) return@Runnable[\s\S]*haptics\.play\(Haptic\.TwoTapsAbove\)/);
  // And the runnable is only ever posted in the guide's field.
  expect(ime).toContain("else if (inGuide && cursorGesture.twoAbove) main.postAtTime(confirmTwoAbove,");
  expect(ime.match(/confirmTwoAbove,/g)?.length).toBe(1);
});

test("steering ticks when it picks up after a slip, and exact typing's chips play", () => {
  const ime = read("android/kotlin/com/atlas/keyboard/AtlasIme.kt");
  expect(ime).toMatch(/if \(cursor\.resume\(first, composer\.liftOffMillis\.toLong\(\)\)\) \{[^}]*haptics\.play\(Haptic\.SteerResumed\)/);
  expect(ime).toContain("haptics.play(Haptic.chipTapped(wasLocked = sticky.level(key) == ExactTyping.Level.Locked))");
  // The level is read before the tap changes it.
  expect(ime.indexOf("Haptic.chipTapped(")).toBeLessThan(ime.indexOf("sticky.tap(key)"));
});
