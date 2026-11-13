import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

// Scroll assistant (owner, 2026-11-12): no note under the switch, and until it's changed it follows the phone's own.
const root = join(import.meta.dir, "../..");
const read = (path: string) => readFileSync(join(root, path), "utf8");
const settings = read("android/kotlin/com/atlas/engine/TypingSettings.java");

test("the switch has no note under it", () => {
  const page = read("app/settings/index.tsx");
  const toggle = page.slice(page.indexOf("<Toggle label={scrollLabel}"), page.indexOf("/>", page.indexOf("<Toggle label={scrollLabel}")));
  expect(toggle).not.toContain("subtitle");
  expect(read("lib/setting-names.ts")).not.toContain("scrollNote");
});

test("unset, it follows the phone's own Scroll assistant; a stored choice wins", () => {
  const start = settings.indexOf("public static boolean touchpadOnlyInFields(Context context)");
  const body = settings.slice(start, settings.indexOf("\n    }\n", start));
  expect(body).toContain("if (!preferences.contains(TOUCHPAD_ONLY_IN_FIELDS)) return !phoneScrollAssistant();");
  expect(body).toContain("return preferences.getBoolean(TOUCHPAD_ONLY_IN_FIELDS, false);");
  expect(settings).toContain('"persist.sys.scroll_assistant"');
  // Setup reads the same property in the same way.
  expect(read("android/kotlin/com/atlas/keyboard/PhoneSetup.kt")).toContain("fun scrollAssistant() = TypingSettings.phoneScrollAssistant()");
});

test("only the user's own change writes the choice, and nothing removes it", () => {
  expect(settings.match(/TOUCHPAD_ONLY_IN_FIELDS, enabled/g)?.length).toBe(1);
  expect(settings).not.toMatch(/remove\(TOUCHPAD_ONLY_IN_FIELDS\)/);
  // The phone's own setting is read, never set.
  expect(settings).not.toMatch(/SystemProperties[^;]*"set"/);
});
