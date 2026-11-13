import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

// Settings > Reset all settings (owner, 2026-11-13): every switch, stepper and choice goes back to how it started, and what's
// yours stays. SettingsReset.kt lists every key of the settings files as reset or kept. A new key must be one or the other.
const root = join(import.meta.dir, "../..");
const read = (path: string) => readFileSync(join(root, path), "utf8");
const reset = read("android/kotlin/com/atlas/keyboard/SettingsReset.kt");

/** The strings in SettingsReset's set [name]. */
function listed(name: string): string[] {
  const body = reset.match(new RegExp(`val ${name} = setOf\\(([^)]*)\\)`))![1];
  return [...body.matchAll(/"([a-z_]+)"/g)].map(match => match[1]);
}
const settings = listed("TYPING_SETTINGS");
const data = listed("TYPING_DATA");

/** Every key TypingSettings and ExactTyping keep in typing_settings. A fix's "fix_" + id comes out as "fix_". */
function typingKeys(): string[] {
  const typing = read("android/kotlin/com/atlas/engine/TypingSettings.java");
  const exact = read("android/kotlin/com/atlas/keyboard/ExactTyping.kt");
  const literal = [...typing.matchAll(/preferences(?:\(context\))?\.(?:get\w+|put\w+|remove|contains)\("([a-z_]+)"/g)].map(m => m[1]);
  const constants = [...typing.matchAll(/static final String \w+ = "([a-z_]+)";/g)].map(m => m[1]);
  const exactKeys = [...exact.matchAll(/private const val (?:APPS|REAL_ALT) = "([a-z_]+)"/g)].map(m => m[1]);
  return [...new Set([...literal, ...constants, ...exactKeys])];
}

test("every key in typing_settings is a setting that resets or something of yours that stays", () => {
  const keys = typingKeys();
  expect(keys.length).toBeGreaterThan(30);
  for (const key of keys) {
    const known = settings.includes(key) || data.includes(key) || key === "fix_";
    expect({ key, known }).toEqual({ key, known: true });
  }
  // Nothing is both, and nothing listed is gone from the code.
  expect(settings.filter(key => data.includes(key))).toEqual([]);
  for (const key of [...settings, ...data]) expect({ key, used: keys.includes(key) }).toEqual({ key, used: true });
});

test("what's yours stays: the reset touches only the settings files, and only their settings", () => {
  expect(data).toEqual(expect.arrayContaining(["enter_apps", "exact_apps", "hidden_bar_apps", "own_shorthand", "snippet_start"]));
  const files = [...reset.matchAll(/getSharedPreferences\((\w+)/g)].map(m => m[1]);
  expect(files.sort()).toEqual(["HAPTICS", "TYPING"]);
  expect(reset).toContain('const val TYPING = "typing_settings"');
  expect(read("android/kotlin/com/atlas/engine/TypingSettings.java")).toContain('getSharedPreferences("typing_settings"');
  // Haptics: All haptics and each haptic's switch, by the names Haptics.kt gives them. The lab's tuning stays.
  const haptics = read("android/kotlin/com/atlas/keyboard/Haptics.kt");
  expect(reset).toContain('const val HAPTICS = "swipe_haptics"');
  expect(haptics).toContain('getSharedPreferences("swipe_haptics"');
  expect(reset).toContain('const val HAPTIC_PREFIX = "haptic_"');
  expect(haptics).toContain('val key get() = "haptic_$switch"');
  // Size, and Learn new words' switch alone: the words stay.
  expect(read("android/kotlin/com/atlas/keyboard/FixedScale.kt")).toContain("preferences(context).edit().remove(SIZE).apply()");
  const engine = read("android/kotlin/com/atlas/engine/SwipeTyping.java");
  const learning = engine.slice(engine.indexOf("public void resetLearning()"), engine.indexOf("public void resetLearning()") + 300);
  expect(learning).toContain('getSharedPreferences("word_learning", 0).edit().remove("enabled").apply()');
  expect(learning).not.toMatch(/clear\(\)/);
});

test("a reset removes values, so every default stays where its setting defines it", () => {
  expect(reset).not.toMatch(/\.put(Boolean|String|Int|Long|Float|StringSet)\(/);
  expect(reset).not.toMatch(/\.clear\(\)/);
});

test("Settings asks first, on the app's own confirm page, and confirms with Confirm", () => {
  const index = read("app/settings/index.tsx");
  expect(index).toContain('<Field label="Reset all settings" href="/settings/reset-all" />');
  const page = read("app/settings/reset-all.tsx");
  expect(page).toContain('<Screen title="Reset all settings?"');
  expect(page).toContain('subtitle="Every switch and choice goes back to how it started. Your words, snippets and app lists stay."');
  expect(page).toContain(">Reset</CanvasText>");
  expect(page).toContain('<Field label="Keep mine" onPress={back} />');
  expect(page).toContain('playHaptic("app_picked")');
  expect(page).not.toContain("app_deleted");
  expect(page).toContain('"Back to factory. Try not to break it again."');
  expect(read("android/kotlin/com/vandam/ink/AppNativeAdapters.kt")).toContain("SettingsReset.reset(activity)");
});

test("the boring guide and the release notes say where it is", () => {
  expect(read("app/settings/guide-boring.tsx")).toContain("Settings > Reset all settings");
  expect(read("release-notes.txt")).toContain("- Reset all settings:");
});
