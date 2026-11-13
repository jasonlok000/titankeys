import { expect, test } from "bun:test";
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";

// TitanKeys' own screens play the keyboard's haptics: the controls that change something one meaning per pattern, and every
// other press Press, a light click (docs/haptics.md, TitanKeys' own screens; the audit is docs/haptics-audit.md).
const root = join(import.meta.dir, "../..");
const read = (path: string) => readFileSync(join(root, path), "utf8");

function files(folder: string): string[] {
  return readdirSync(join(root, folder), { withFileTypes: true }).flatMap(entry =>
    entry.isDirectory() ? files(join(folder, entry.name)) : entry.name.endsWith(".tsx") ? [join(folder, entry.name)] : []);
}
const screens = [...files("app"), ...files("lib")].map(path => ({ path, source: read(path) }));

test("each app haptic has the family its meaning needs", () => {
  const haptics = read("android/kotlin/com/atlas/keyboard/Haptics.kt");
  const families: Record<string, string> = {
    app_press: "Texture", app_switch_on: "Lock", app_switch_off: "Release", app_step: "Texture",
    app_picked: "Confirm", app_deleted: "Gone", app_refused: "No",
  };
  for (const [id, family] of Object.entries(families))
    expect(haptics).toMatch(new RegExp(`\\("${id}", HapticGroup\\.App, Family\\.${family}\\)`));
});

test("every switch in the app plays Lock and Release, through the wrapper", () => {
  for (const { path, source } of screens) {
    if (path.endsWith("app-haptics.tsx") || !source.includes("<Toggle")) continue;
    expect({ path, wrapped: /import \{[^}]*\bToggle\b[^}]*\} from "[./]+\/?(lib\/)?app-haptics";/.test(source) })
      .toEqual({ path, wrapped: true });
    const inkImport = source.match(/import \{([^}]*)\} from "ink";/)?.[1] ?? "";
    expect({ path, fromInk: /\bToggle\b/.test(inkImport) }).toEqual({ path, fromInk: false });
  }
  expect(read("lib/app-haptics.tsx")).toContain('playHaptic(on ? "app_switch_on" : "app_switch_off")');
});

test("pickers confirm, steps tick, deletes are gone and refusals say no", () => {
  expect(read("lib/choice.tsx")).toContain('playHaptic("app_picked")');
  expect(read("lib/duration.tsx")).toContain('playHaptic("app_picked")');
  expect(read("lib/duration.tsx")).toContain('playHaptic("app_step")');
  expect(read("app/settings/snippets/start.tsx")).toContain('playHaptic("app_picked")');
  for (const page of ["app/settings/data/reset.tsx", "app/settings/rawlog-delete.tsx", "app/settings/contribute-delete.tsx",
    "app/settings/bug.tsx", "app/settings/contribute.tsx"]) expect({ page, gone: read(page).includes('playHaptic("app_deleted")') })
    .toEqual({ page, gone: true });
  for (const page of ["lib/word-list.tsx", "app/settings/snippets/edit.tsx", "app/settings/shorthand/edit.tsx"])
    expect({ page, refused: read(page).includes("refusedOnError(") }).toEqual({ page, refused: true });
});

test("every press in Ink's pages plays Press, unless its control plays its own, and Ink's stock click is off", () => {
  const adapter = read("android/kotlin/com/vandam/ink/AppNativeAdapters.kt");
  expect(adapter).toContain("require(haptic?.group == HapticGroup.App)");
  // Ink sends every press here, ✕ and ← in its header too. It always takes the press, so Android's click never plays.
  expect(adapter).toMatch(/internal fun appPressHaptic\(view: android\.view\.View\): Boolean \{\s*inkPress\?\.pressed\(\)\s*return true\s*\}/);
  expect(adapter).toContain("haptics.play(Haptic.AppPress)");
  // A control's own haptic takes the place of the waiting Press.
  expect(adapter).toMatch(/press\.meaning\(\)\s*haptics\.play\(haptic!!\)/);
  const patch = read("tools/ink/titankeys.patch");
  expect(patch).toContain("if (!appPressHaptic(view)) lightSdkAdapter.performHaptic(view)");
  expect(patch).toContain("if (!appPressHaptic(inkView)) lightSdkAdapter.performHaptic(inkView)");
});

test("no press in Ink's pages turns its haptic off, so none can skip Press", () => {
  for (const { path, source } of screens) expect({ path, quiet: /haptic[=:]\s*\{?\s*false/.test(source) }).toEqual({ path, quiet: false });
});

// The native screens: the guide, the playground, the share and Back disclosures, key calibration, the Haptics lab and the
// recorder's screens. The keyboard's own views (the bar, the panels, exact typing's bar) play the keyboard's haptics instead.
const nativeScreens = [
  ...readdirSync(join(root, "android/kotlin/com/atlas/keyboard")).filter(name => name.endsWith("Activity.kt"))
    .map(name => `android/kotlin/com/atlas/keyboard/${name}`),
  ...readdirSync(join(root, "android/kotlin/com/vandam/ink")).filter(name => name.endsWith("Activity.kt"))
    .map(name => `android/kotlin/com/vandam/ink/${name}`),
  ...readdirSync(join(root, "android/debug/recorder")).filter(name => name.endsWith("Activity.kt"))
    .map(name => `android/debug/recorder/${name}`),
];

test("every press on a native screen goes through onPress, which plays its haptic", () => {
  expect(nativeScreens).toContain("android/kotlin/com/atlas/keyboard/GuideActivity.kt");
  for (const path of nativeScreens) {
    const source = read(path);
    // A click listener set any other way would play nothing. Clearing one, with null, is fine.
    const loose = [...source.matchAll(/\.?setOn(Long)?ClickListener\s*[({](?!null\))/g)].map(match => match[0]);
    expect({ path, loose }).toEqual({ path, loose: [] });
  }
  const press = read("android/kotlin/com/atlas/keyboard/AppPress.kt");
  expect(press).toContain("fun View.onPress(haptics: Haptics, haptic: Haptic? = Haptic.AppPress, action: () -> Unit)");
  expect(press).toContain("isHapticFeedbackEnabled = false");
});

test("the guide's own switches lock and let go", () => {
  const guide = read("android/kotlin/com/atlas/keyboard/GuideActivity.kt");
  expect(guide).toContain("haptics.play(if (pasteDeclined) Haptic.AppSwitchOn else Haptic.AppSwitchOff)");
  expect(guide).toContain("haptics.play(Haptic.AppSwitchOff)");
  expect(read("lib/contribute.tsx")).toContain("switched(true);");
});

test("the rulebook says what each control plays, and what stays quiet", () => {
  const doc = read("docs/haptics.md");
  for (const line of ["| A switch turned on | **Lock**", "| A switch turned off | **Release**", "| Shorter or Longer on a stepper | **Texture**",
    "**Confirm**", "**Gone** | Gone means gone", "| Something the app refuses: a word, a snippet or a shorthand it can't take | **No**",
    "| Any other press: ✕, ←, a row, a button, a pill, a link, Next, Skip, a topic opening or closing | **Press**"]) expect(doc).toContain(line);
  expect(doc).not.toContain("**Plain buttons there:**");
  expect(read("docs/haptics-audit.md")).toContain("| Control | Plays now | Played before |");
});
