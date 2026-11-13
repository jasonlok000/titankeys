import { expect, test } from "bun:test";
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";

// TitanKeys' own screens play the keyboard's haptics for the controls that change something, one meaning per pattern
// (docs/haptics.md, TitanKeys' own screens). Navigation and plain buttons stay quiet.
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
    app_switch_on: "Lock", app_switch_off: "Release", app_step: "Texture",
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

test("the keyboard plays only the app's own haptics for the app, and Ink's stock click is off", () => {
  const adapter = read("android/kotlin/com/vandam/ink/AppNativeAdapters.kt");
  expect(adapter).toContain("require(haptic?.group == HapticGroup.App)");
  expect(adapter).toContain("internal fun appPressHaptic(view: android.view.View): Boolean = true");
  const patch = read("tools/ink/titankeys.patch");
  expect(patch).toContain("if (!appPressHaptic(view)) lightSdkAdapter.performHaptic(view)");
  expect(patch).toContain("if (!appPressHaptic(inkView)) lightSdkAdapter.performHaptic(inkView)");
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
    "**Navigation in TitanKeys' own screens:**", "**Plain buttons there:**"]) expect(doc).toContain(line);
});
