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

test("every activity wraps its base context in FixedScale", () => {
  expect(activities.length).toBeGreaterThanOrEqual(8);
  for (const { path, source } of activities) {
    expect({ path, wrapped: source.includes("override fun attachBaseContext(base: Context) = super.attachBaseContext(FixedScale.wrap(base))") })
      .toEqual({ path, wrapped: true });
  }
});

test("every activity in the manifest is one that's checked, or Ink's own", () => {
  const manifest = read("android/src/main/AndroidManifest.xml");
  const names = [...manifest.matchAll(/<activity\s+android:name="[\w.]+\.(\w+)"/g)].map(match => match[1]);
  const checked = new Set(activities.map(({ path }) => path.split(/[\\/]/).at(-1)!.replace(".kt", "")));
  for (const name of names) expect({ name, checked: checked.has(name) }).toEqual({ name, checked: true });
});

test("the keyboard's bar, panels and exact typing's bar are made from the fixed-scale context", () => {
  const ime = read("android/kotlin/com/atlas/keyboard/AtlasIme.kt");
  const start = ime.indexOf("override fun onCreateInputView(): View {");
  const body = ime.slice(start, ime.indexOf("\n    }\n", start));
  expect(body).toContain("val ui = FixedScale.themed(this, android.R.style.Theme_DeviceDefault_InputMethod)");
  for (const view of ["Strip(ui,", "SymPanel(ui,", "ExactBar(ui,", "LinearLayout(ui)"]) expect(body).toContain(view);
  expect(body).not.toMatch(/(Strip|SymPanel|ExactBar|LinearLayout)\(this/);
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
  for (const path of kotlinFiles("android/kotlin")) {
    const source = read(path).replace(/^\s*(\/\/|\*).*$/gm, "");
    expect({ path, fontScale: /configuration\.fontScale|\.scaledDensity/.test(source) }).toEqual({ path, fontScale: false });
  }
});
