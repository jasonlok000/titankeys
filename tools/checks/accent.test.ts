import { expect, test } from "bun:test";
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";

// Orange accents off means "literally everything globally" (owner, 2026-11-13): every orange comes from one source that the
// switch flips. Native: Accent.kt. The app's drawings: lib/brand.ts's useAccent. Ink's own colours: theme.ts, overridden at
// run time through the Ink patch's accent.
const root = join(import.meta.dir, "../..");
const read = (path: string) => readFileSync(join(root, path), "utf8");

function files(folder: string, ending: RegExp): string[] {
  return readdirSync(join(root, folder), { withFileTypes: true }).flatMap(entry =>
    entry.isDirectory() ? files(join(folder, entry.name), ending) : ending.test(entry.name) ? [join(folder, entry.name)] : []);
}
const name = (path: string) => path.split(/[\/]/).at(-1)!;

test("no native code names the orange but Accent.kt", () => {
  for (const path of files("android/kotlin", /\.(kt|java)$/)) {
    if (name(path) === "Accent.kt") continue;
    const source = read(path);
    expect({ path, orange: /EF804E/i.test(source) || /\.ORANGE\b|val ORANGE\b/.test(source) }).toEqual({ path, orange: false });
  }
});

test("no screen of the app names the orange but lib/brand.ts and theme.ts", () => {
  for (const path of [...files("app", /\.tsx?$/), ...files("lib", /\.tsx?$/)]) {
    if (name(path) === "brand.ts") continue;
    const source = read(path);
    expect({ path, orange: /EF804E/i.test(source) || /\bORANGE\b/.test(source) }).toEqual({ path, orange: false });
  }
  const brand = read("lib/brand.ts");
  expect(brand).not.toMatch(/export const ORANGE/);
  expect(brand).toContain('return result.status === "ready" && !result.data.orangeAccents ? CREAM : ORANGE;');
});

test("Accent reads the switch where TypingSettings keeps it, every window loads it, and the switch flips it at once", () => {
  const accent = read("android/kotlin/com/atlas/keyboard/Accent.kt");
  const typing = read("android/kotlin/com/atlas/engine/TypingSettings.java");
  const file = accent.match(/PREFERENCES = "(\w+)"/)![1];
  const key = accent.match(/KEY = "(\w+)"/)![1];
  expect(typing).toContain(`getSharedPreferences("${file}"`);
  expect(typing).toContain(`getBoolean("${key}", true)`);
  expect(accent).toContain("private const val CREAM = 0xFFFFF1E6.toInt()");
  expect(read("lib/brand.ts")).toContain('export const CREAM = "#FFF1E6";');
  const scale = read("android/kotlin/com/atlas/keyboard/FixedScale.kt");
  expect(scale.match(/Accent\.load\(base\)/g)?.length).toBe(2);
  const adapter = read("android/kotlin/com/vandam/ink/AppNativeAdapters.kt");
  expect(adapter).toContain("internal fun appAccent(context: Context): Int = if (Accent.on) 0 else Accent.colour");
  expect(adapter).toMatch(/"orangeAccents" -> \{[^}]*Accent\.on = value\s*activity\.applyAccent\(\)/);
  const patch = read("tools/ink/titankeys.patch");
  expect(patch).toContain("+    pub fn set_accent(&mut self, accent: Option<Colour>) -> bool {");
  expect(patch).toContain("+internal fun appAccent(context: android.content.Context): Int = 0");
});

test("the keyboard's marks take the text's colour, as the keyboard has a light look too", () => {
  const look = read("android/kotlin/com/atlas/keyboard/Look.kt");
  expect(look).toContain("val accent = Accent.of(orange, off = ink)");
  expect(look).toContain("TypingSettings.orangeAccents(context).also { Accent.on = it }");
});

test("a button on an accent background stays visible with accents off", () => {
  const guide = read("android/kotlin/com/atlas/keyboard/GuideActivity.kt");
  expect(guide).not.toContain("if (onInk) Accent.colour else GuideColours.CREAM");
  expect(guide.match(/if \(onInk\) Accent\.colour else Accent\.raised/g)?.length).toBe(4);
});

test("turned off, the switch roasts you; turned back on, it says what it does", () => {
  const names = read("lib/setting-names.ts");
  expect(names).toContain('"Orange is off everywhere. Boring, just like you."');
  expect(read("app/settings/index.tsx")).toContain('<Toggle label="Orange accents" subtitle={accentsNote(settings.orangeAccents)}');
});
