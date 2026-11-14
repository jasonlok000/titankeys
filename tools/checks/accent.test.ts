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

test("the keyboard is always dark, whatever the phone's dark mode", () => {
  // Owner, 2026-11-13: "I never know it follows light mode. Make it always black. The app is always in its current dark mode
  // settings." The bar, the panels, the word menu and exact typing's bar all take Look, which reads nothing of the phone's mode.
  const look = read("android/kotlin/com/atlas/keyboard/Look.kt");
  expect(look).toContain("internal class Look(val orange: Boolean) {");
  expect(look).toContain("val ink = Color.WHITE");
  expect(look).toContain("val paper = Color.BLACK");
  expect(look).toContain("fun of(context: Context) = Look(TypingSettings.orangeAccents(context).also { Accent.on = it })");
  const keyboard = files("android/kotlin", /\.(kt|java)$/).map(read).join("\n");
  expect(keyboard).not.toMatch(/UI_MODE_NIGHT|uiMode|Palette\.LIGHT|look\.dark|0x35312E/);
  expect(read("android/kotlin/com/atlas/keyboard/MenuScene.kt")).not.toContain("val LIGHT");
  // The renders draw the one look.
  for (const path of ["tests/menu-render/MenuRender.kt", "tests/gesture-render/GestureRender.kt", "tests/gesture-display/render/Stand.kt"]) {
    expect({ path, light: /Palette\.LIGHT|-light|dark: Boolean/.test(read(path)) }).toEqual({ path, light: false });
  }
});

test("with Orange accents off, the bar's marks and the menu's effects agree on cream", () => {
  // Bug hunt 2.1, #36: the bar's marks went white (Look's off = ink) while the menu's effects went cream.
  const look = read("android/kotlin/com/atlas/keyboard/Look.kt");
  expect(look).toContain("val accent = Accent.of(orange)");
  const accent = read("android/kotlin/com/atlas/keyboard/Accent.kt");
  expect(accent).toContain("fun of(orange: Boolean) = if (orange) ORANGE else CREAM");
  expect(read("android/kotlin/com/atlas/keyboard/Strip.kt")).toContain("private fun palette() = MenuScene.Palette.DARK.copy(accent = look.accent and 0xFFFFFF)");
  expect(read("tests/gesture-display/render/Stand.kt")).toContain("val accent = if (orange) 0xFFEF804E.toInt() else 0xFFFFF1E6.toInt()");
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
