import { expect, test } from "bun:test";
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";

// Every window TitanKeys draws keeps the same sizes at any display size or font size (owner, 2026-11-12), through FixedScale.
// tests/fixed-scale checks the sizes themselves; these check that each window uses it.
const root = join(import.meta.dir, "../..");
const read = (path: string) => readFileSync(join(root, path), "utf8");

function kotlinFiles(folder: string): string[] {
  return readdirSync(join(root, folder), { withFileTypes: true }).flatMap(entry =>
    entry.isDirectory() ? kotlinFiles(join(folder, entry.name)) : entry.name.endsWith(".kt") ? [join(folder, entry.name)] : []);
}

const activities = kotlinFiles("android/kotlin").map(path => ({ path, source: read(path) }))
  .filter(({ source }) => /class \w+ : Activity\(\)/.test(source));

test("every activity wraps its base context in FixedScale, at Normal", () => {
  expect(activities.length).toBeGreaterThanOrEqual(8);
  const wrap = "override fun attachBaseContext(base: Context) = super.attachBaseContext(FixedScale.wrap(base))";
  for (const { path, source } of activities) expect({ path, wrapped: source.includes(wrap) }).toEqual({ path, wrapped: true });
  // No window can be sized: Bar text size grows only the keyboard's bar (owner, 2026-11-13).
  expect(read("android/kotlin/com/atlas/keyboard/FixedScale.kt")).toContain("    fun wrap(base: Context): Context {");
});

test("Bar text size grows only the keyboard's bar, the next time it shows", () => {
  // Settings, the boring guide and every other Ink screen stay at Normal: the patch's user scale stays 1.
  const adapter = read("android/kotlin/com/vandam/ink/AppNativeAdapters.kt");
  expect(adapter).toContain('internal fun appUserScale(@Suppress("UNUSED_PARAMETER") context: Context): Float = FixedScale.NORMAL');
  expect(adapter).not.toContain("activity.applyUserScale()");
  expect(adapter).toContain('"barTextSize" -> FixedScale.size(activity, FixedScale.Size.of(value as String))');
  expect(adapter).toContain('.put("barTextSizePicked", FixedScale.picked(activity) != null)');
  const ime = read("android/kotlin/com/atlas/keyboard/AtlasIme.kt");
  expect(ime).toContain("if (FixedScale.size(this) != viewSize) setInputView(onCreateInputView())");
  // The page lists the sizes FixedScale knows, Normal first.
  const kotlin = read("android/kotlin/com/atlas/keyboard/FixedScale.kt");
  const ids = [...kotlin.matchAll(/^\s+\w+\("(\w+)", (NORMAL|BIGGER|BIGGEST)\)/gm)].map(match => match[1]);
  expect(ids).toEqual(["normal", "bigger", "biggest"]);
  expect(read("app/settings/bar-text-size.tsx")).toContain('(["normal", "bigger", "biggest"] as const)');
});

/** A section of Settings' top page, by its title. */
function section(index: string, title: string): string {
  const start = index.indexOf(`<Section title="${title}">`);
  expect(start).toBeGreaterThan(-1);
  return index.slice(start, index.indexOf("</Section>", start));
}

test("Bar text size lives in Settings > The bar, and no Size is left in Look and feel", () => {
  const index = read("app/settings/index.tsx");
  expect(section(index, "The bar"))
    .toContain('<Field label={barTextSizeTitle} href="/settings/bar-text-size">{barTextSizeLabel(settings)}</Field>');
  expect(section(index, "Look and feel")).not.toMatch(/[Ss]ize/);
  expect(index).not.toContain("/settings/size");
  const page = read("app/settings/bar-text-size.tsx");
  expect(page).toContain('export const barTextSizeTitle = "Bar text size";');
  expect(page).toContain('export const barTextSizeNote = "Words, menu and chips. The bar keeps its height.";');
  // Until you pick, the page says it follows the phone, and the row says so too.
  expect(page).toContain('export const followsPhoneNote = "Follows your phone\'s text size until you pick one.";');
  expect(page).toContain("subtitle={following ? `${barTextSizeNote}\\n${followsPhoneNote}` : barTextSizeNote}");
  expect(page).toContain('(barTextSizePicked ? "" : " (phone)")');
  expect(read("app/settings/guide-boring.tsx")).toContain('<Topic title="Bar text size">');
  const notes = read("release-notes.txt");
  expect(notes).toContain("Settings > Bar text size");
  expect(notes).not.toContain("Settings > Size");
});

test("only the bar and the menu's effects are made at Bar text size; the panels and exact typing's bar stay at Normal", () => {
  const ime = read("android/kotlin/com/atlas/keyboard/AtlasIme.kt");
  const start = ime.indexOf("override fun onCreateInputView(): View {");
  const body = ime.slice(start, ime.indexOf("\n    }\n", start));
  expect(body).toContain("val ui = FixedScale.themed(this, android.R.style.Theme_DeviceDefault_InputMethod)");
  expect(body).toContain("val barUi = FixedScale.themed(this, android.R.style.Theme_DeviceDefault_InputMethod, viewSize.factor)");
  for (const view of ["Strip(barUi,", "MenuEffects(barUi,", "SymPanel(ui,", "ExactBar(ui,", "LinearLayout(ui)", "FrameLayout(ui)"]) {
    expect(body).toContain(view);
  }
  expect(body.match(/barUi/g)?.length).toBe(3);
});

test("the bar keeps Normal's height at every size, and the menu's pill stays inside it", () => {
  const strip = read("android/kotlin/com/atlas/keyboard/Strip.kt");
  expect(strip).toContain("private val barHeight = Math.round(FixedScale.normalPx(context, BAR_DP))");
  expect(strip).not.toContain("dp(56)");
  expect(strip).toContain("val pillHeight = minOf(dp(MENU_PILL_DP), barHeight * MENU_PILL_DP / BAR_DP.toInt()).toFloat()");
  expect(strip).toContain("fitClipLines(wordPixels() * clipScale)");
  // normalPx works from the screen, not the window's own density, so it's right from a window at any size.
  expect(read("android/kotlin/com/atlas/keyboard/FixedScale.kt"))
    .toContain("return dp * densityDpi(metrics.widthPixels, metrics.heightPixels) / DisplayMetrics.DENSITY_DEFAULT");
  expect(read("android/kotlin/com/atlas/keyboard/ExactBar.kt")).toContain("minimumHeight = dp(HEIGHT).toInt() + navigationInset");
});

test("Bar text size starts from the phone's text size until you pick one, and a reset goes back to it", () => {
  const kotlin = read("android/kotlin/com/atlas/keyboard/FixedScale.kt");
  expect(kotlin).toContain("const val BIGGER_FROM = 1.15f");
  expect(kotlin).toContain("const val BIGGEST_ABOVE = 1.5f");
  expect(kotlin).toContain("fun size(context: Context): Size = picked(context) ?: forFontScale(phoneFontScale())");
  expect(kotlin).toContain("private fun phoneFontScale(): Float = Resources.getSystem().configuration.fontScale");
  expect(kotlin).toContain("preferences(context).edit().remove(SIZE).remove(OLD_SIZE).apply()");
  expect(read("android/kotlin/com/atlas/keyboard/SettingsReset.kt")).toContain("FixedScale.resetSize(context)");
  // Reset literally everything wipes FixedScale's whole file, the pick with it.
  expect(read("android/kotlin/com/atlas/keyboard/FreshStart.kt")).toContain('"fixed_scale"');
});

test("every activity in the manifest is one that's checked, or Ink's own", () => {
  const manifest = read("android/src/main/AndroidManifest.xml");
  const names = [...manifest.matchAll(/<activity\s+android:name="[\w.]+\.(\w+)"/g)].map(match => match[1]);
  const checked = new Set(activities.map(({ path }) => path.split(/[\\/]/).at(-1)!.replace(".kt", "")));
  for (const name of names) expect({ name, checked: checked.has(name) }).toEqual({ name, checked: true });
});

test("the keyboard's bar, panels and exact typing's bar are made from a fixed-scale context", () => {
  const ime = read("android/kotlin/com/atlas/keyboard/AtlasIme.kt");
  const start = ime.indexOf("override fun onCreateInputView(): View {");
  const body = ime.slice(start, ime.indexOf("\n    }\n", start));
  expect(body).not.toMatch(/(Strip|SymPanel|ExactBar|LinearLayout|MenuEffects)\(this/);
});

test("Ink's activity takes TitanKeys' fixed-scale context through the patch's hook", () => {
  expect(read("android/kotlin/com/vandam/ink/AppNativeAdapters.kt"))
    .toContain("internal fun appBaseContext(base: Context): Context = FixedScale.wrap(base)");
  const patch = read("tools/ink/titankeys.patch");
  expect(patch).toContain("+        super.attachBaseContext(appBaseContext(base))");
  // Ink's own builds, without TitanKeys, keep their context as it is.
  expect(patch).toContain("+internal fun appBaseContext(base: Context): Context = base");
});

test("nothing TitanKeys draws reads the system font scale directly", () => {
  // Bar text size's starting size reads the phone's own text size, on purpose, in this one line.
  const allowed = "private fun phoneFontScale(): Float = Resources.getSystem().configuration.fontScale";
  for (const path of kotlinFiles("android/kotlin")) {
    const source = read(path).replace(/^\s*(\/\/|\*).*$/gm, "").replace(allowed, "");
    expect({ path, fontScale: /configuration\.fontScale|\.scaledDensity/.test(source) }).toEqual({ path, fontScale: false });
  }
});
