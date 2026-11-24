import { expect, test } from "bun:test";
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";

// Accent colour (#111; before it, Orange accents) off means "literally everything globally" (owner, 2026-11-13): every accent
// comes from one source that the choice sets. Native: Accent.kt. The app's drawings: lib/brand.ts's useAccent. Ink's own colours: theme.ts, overridden at
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
  expect(brand).toContain('return result.status === "ready" && result.data.accent ? result.data.accent : ORANGE;');
});

test("Accent reads the choice where TypingSettings keeps it, every window loads it, and a pick sets it at once", () => {
  const accent = read("android/kotlin/com/titankeys/keyboard/Accent.kt");
  const typing = read("android/kotlin/com/titankeys/engine/TypingSettings.java");
  const file = accent.match(/PREFERENCES = "(\w+)"/)![1];
  const key = accent.match(/ KEY = "(\w+)"/)![1];
  const old = accent.match(/OLD_KEY = "(\w+)"/)![1];
  expect(typing).toContain(`getSharedPreferences("${file}"`);
  expect(typing).toContain(`getString("${key}", null)`);
  // The old switch migrates: off is Off, on or never set is Orange.
  expect(typing).toContain(`return preferences.getBoolean("${old}", true) ? "orange" : "off";`);
  const reset = read("android/kotlin/com/titankeys/keyboard/SettingsReset.kt");
  expect(reset).toContain(`"${key}"`);
  expect(reset).toContain(`"${old}"`);
  expect(read("android/kotlin/com/titankeys/keyboard/RawLog.kt")).toContain('"accentColour" to TypingSettings.accentColour(context),');
  expect(accent).toContain("private const val CREAM = 0xFFFFF1E6.toInt()");
  expect(read("lib/brand.ts")).toContain('export const CREAM = "#FFF1E6";');
  const scale = read("android/kotlin/com/titankeys/keyboard/FixedScale.kt");
  expect(scale.match(/Accent\.load\(base\)/g)?.length).toBe(2);
  const adapter = read("android/kotlin/com/vandam/ink/AppNativeAdapters.kt");
  expect(adapter).toContain("internal fun appAccent(context: Context): Int = if (Accent.isDefault) 0 else Accent.colour");
  expect(adapter).toMatch(/"accentColour" -> \{[^}]*Accent\.set\(activity, value\)\s*activity\.applyAccent\(\)/);
  expect(adapter).toContain('.put("accent", Accent.hex())');
  const patch = read("tools/ink/titankeys.patch");
  expect(patch).toContain("+    pub fn set_accent(&mut self, accent: Option<Colour>) -> bool {");
  expect(patch).toContain("+internal fun appAccent(context: android.content.Context): Int = 0");
});

test("the keyboard is always dark, whatever the phone's dark mode", () => {
  // Owner, 2026-11-13: "I never know it follows light mode. Make it always black. The app is always in its current dark mode
  // settings." The bar, the panels, the word menu and exact typing's bar all take Look, which reads nothing of the phone's mode.
  const look = read("android/kotlin/com/titankeys/keyboard/Look.kt");
  expect(look).toContain("internal class Look(val orange: Boolean) {");
  expect(look).toContain("val ink = Color.WHITE");
  expect(look).toContain("val paper = Color.BLACK");
  expect(look).toContain("fun of(context: Context) = Look(Accent.load(context).let { Accent.on })");
  const keyboard = files("android/kotlin", /\.(kt|java)$/).map(read).join("\n");
  expect(keyboard).not.toMatch(/UI_MODE_NIGHT|uiMode|Palette\.LIGHT|look\.dark|0x35312E/);
  expect(read("android/kotlin/com/titankeys/keyboard/MenuScene.kt")).not.toContain("val LIGHT");
  // The renders draw the one look.
  for (const path of ["tests/menu-render/MenuRender.kt", "tests/gesture-render/GestureRender.kt", "tests/gesture-display/render/Stand.kt"]) {
    expect({ path, light: /Palette\.LIGHT|-light|dark: Boolean/.test(read(path)) }).toEqual({ path, light: false });
  }
});

test("with Accent colour off, the bar's marks and the menu's effects agree on cream", () => {
  // Bug hunt 2.1, #36: the bar's marks went white (Look's off = ink) while the menu's effects went cream.
  const look = read("android/kotlin/com/titankeys/keyboard/Look.kt");
  expect(look).toContain("val accent = Accent.of(orange)");
  const accent = read("android/kotlin/com/titankeys/keyboard/Accent.kt");
  expect(accent).toContain("fun of(on: Boolean) = if (on) current else CREAM");
  expect(read("android/kotlin/com/titankeys/keyboard/Strip.kt")).toContain("private fun palette() = MenuScene.Palette.DARK.copy(accent = look.accent and 0xFFFFFF)");
  expect(read("tests/gesture-display/render/Stand.kt")).toContain("val accent = if (orange) 0xFFEF804E.toInt() else 0xFFFFF1E6.toInt()");
});

test("a button on an accent background stays visible with accents off", () => {
  const guide = read("android/kotlin/com/titankeys/keyboard/GuideActivity.kt");
  expect(guide).not.toContain("if (onInk) Accent.colour else GuideColours.CREAM");
  expect(guide.match(/if \(onInk\) Accent\.colour else Accent\.raised/g)?.length).toBe(4);
});

test("turned off, the choice roasts you; Settings and Accent.kt offer the same choices", () => {
  const names = read("lib/setting-names.ts");
  expect(names).toContain('"No accents anywhere. Boring, just like you."');
  expect(read("app/settings/index.tsx")).toContain('<Field label={accentTitle} href="/settings/accent">{accentLabel(settings.accentColour)}</Field>');
  const ids = [...names.matchAll(/\{ value: "(\w+)", label: "[^"]+" \}/g)].map(match => match[1]);
  expect(ids).toEqual(["off", ...Object.keys(presets()), "phone"]);
});

// Must stay readable (owner, #111). Everything TitanKeys draws is dark: the keyboard and Settings on black, the guide on black
// with cards on SURFACE, the support page on its panels. The accent is text or a mark on each of them, and a background behind
// ink text (the guide's floods and pills, the support page's buttons, the bar's locked chip in black).
function presets(): Record<string, string> {
  const accent = read("android/kotlin/com/titankeys/keyboard/Accent.kt");
  const block = accent.slice(accent.indexOf("val PRESETS = linkedMapOf("), accent.indexOf(")\n", accent.indexOf("val PRESETS")));
  return Object.fromEntries([...block.matchAll(/"(\w+)" to (?:0xFF([0-9A-F]{6})|ORANGE)/g)]
    .map(match => [match[1], "#" + (match[2] ?? "EF804E")]));
}
const channel = (value: number) => { const c = value / 255; return c <= .04045 ? c / 12.92 : ((c + .055) / 1.055) ** 2.4; };
const rgb = (hex: string) => [1, 3, 5].map(at => parseInt(hex.slice(at, at + 2), 16));
const luminance = (hex: string) => { const [r, g, b] = rgb(hex).map(channel); return .2126 * r + .7152 * g + .0722 * b; };
export const contrast = (a: string, b: string) => {
  const [x, y] = [luminance(a), luminance(b)].sort((p, q) => q - p);
  return (x + .05) / (y + .05);
};
/** [colour] at [alpha] over black, as a faded accent draws on the keyboard. */
const overBlack = (hex: string, alpha: number) =>
  "#" + rgb(hex).map(c => Math.round(c * alpha).toString(16).padStart(2, "0")).join("");

const backgrounds = {
  black: "#000000",           // the keyboard, the bar, the Sym panel, the word menu, Settings, the guide, the support page
  surface: "#161210",         // the guide's fields and cards (GuideColours.SURFACE)
  supportPanel: "#0F0B09",    // the support page's --s1
  supportCard: "#1A1310",     // its --s2
  supportRaised: "#1E1410",   // its --s3
};
const onAccent = { ink: "#160A04", supportOnAccent: "#1A0F0A", keyboardOnAccent: "#000000" };

test("every accent preset reads, as text and under text, on every surface it's drawn on", () => {
  const all = presets();
  expect(Object.keys(all).length).toBeGreaterThanOrEqual(6);
  const rows: string[] = [];
  const orange = all.orange;
  for (const [id, hex] of Object.entries(all)) {
    const ratios: Record<string, number> = {};
    for (const [name, background] of Object.entries(backgrounds)) ratios[name] = contrast(hex, background);
    for (const [name, text] of Object.entries(onAccent)) ratios[name] = contrast(text, hex);
    for (const [name, ratio] of Object.entries(ratios)) expect({ id, name, passes: ratio >= 4.5 }).toEqual({ id, name, passes: true });
    // The bar's open group: white text on a 35% wash of the accent (Look.wash).
    ratios.whiteOnWash = contrast("#FFFFFF", overBlack(hex, .35));
    expect({ id, passes: ratios.whiteOnWash >= 4.5 }).toEqual({ id, passes: true });
    // A cream button on an accent flood (Accent.raised), and the Sym panel's marked glyph on its 35% pill (ChoicesMenu): graphics,
    // at least 3:1 for the pill's glyph, and no worse than orange's for the button, whose ink text reads on cream anyway.
    ratios.creamButton = contrast("#FFF1E6", hex);
    ratios.markOnPill = contrast(hex, overBlack(hex, .35));
    expect({ id, passes: ratios.creamButton >= contrast("#FFF1E6", orange) - .3 }).toEqual({ id, passes: true });
    expect({ id, passes: ratios.markOnPill >= 2.5 }).toEqual({ id, passes: true });
    rows.push(`${id} ${hex} ` + Object.entries(ratios).map(([name, ratio]) => `${name} ${ratio.toFixed(2)}`).join(", "));
  }
  if (process.env.ACCENT_TABLE) console.log(rows.join("\n"));
});

test("Off's cream reads too, as text and under ink", () => {
  for (const background of Object.values(backgrounds)) expect(contrast("#FFF1E6", background)).toBeGreaterThanOrEqual(4.5);
  for (const text of Object.values(onAccent)) expect(contrast(text, "#FFF1E6")).toBeGreaterThanOrEqual(4.5);
});

test("the phone's colour is pulled into the presets' band", () => {
  const accent = read("android/kotlin/com/titankeys/keyboard/Accent.kt");
  const low = +accent.match(/MIN_LIGHTNESS = ([\d.]+)/)![1];
  const high = +accent.match(/MAX_LIGHTNESS = ([\d.]+)/)![1];
  expect(accent).toContain("PHONE -> phone(context)");
  expect(accent).toContain("return fit(system)");
  // Every preset sits in the band, so a phone colour fitted into it reads as they do. The band's ends pass on their own:
  // the darkest it allows on the lightest surface, ink on the lightest it allows.
  for (const hex of Object.values(presets())) {
    expect(luminance(hex)).toBeGreaterThanOrEqual(low);
    expect(luminance(hex)).toBeLessThanOrEqual(high);
  }
  const ratio = (a: number, b: number) => (Math.max(a, b) + .05) / (Math.min(a, b) + .05);
  for (const background of Object.values(backgrounds)) expect(ratio(low, luminance(background))).toBeGreaterThanOrEqual(4.5);
  for (const text of Object.values(onAccent)) expect(ratio(high, luminance(text))).toBeGreaterThanOrEqual(4.5);
});

test("the support page takes the accent from the app", () => {
  const page = read("android/assets/support/index.html");
  expect(page).toContain("window.titankeysAccent = hex =>");
  expect(page).not.toMatch(/#F07C4A[0-9A-F]{2}\b(?!;)/i);
  expect(read("android/kotlin/com/titankeys/support/SupportWebActivity.kt")).toContain("window.titankeysAccent('${Accent.hex()}')");
});
