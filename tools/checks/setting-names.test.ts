import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { barLabel, cursorLabel, scrollLabel } from "../../lib/setting-names";

// A setting has one name everywhere: Settings, the keyboard's quick settings and the fun guide's "Turn on" steps.
const root = join(import.meta.dir, "../..");
const read = (path: string) => readFileSync(join(root, path), "utf8");
const settings = read("app/settings/index.tsx");
const labels = new Set([...settings.matchAll(/label="([^"]+)"/g)].map(match => match[1]));
labels.add(barLabel).add(cursorLabel).add(scrollLabel);

test("each quick setting has its name in Settings", () => {
  const quick = [...read("android/kotlin/com/atlas/keyboard/QuickSetting.kt").matchAll(/^\s+(\w+)\("\w", "[^"]+", "([^"]+)"\)/gm)];
  expect(quick.length).toBeGreaterThan(8);
  const notSettings = new Set(["OpenSettings", "Clipboard", "ChangeKeyboard"]);
  for (const [, name, title] of quick) if (!notSettings.has(name)) expect(`${name}: ${labels.has(title)}`).toBe(`${name}: true`);
});

test("the fun guide's steps name settings as Settings does", () => {
  const steps = [...read("android/kotlin/com/atlas/keyboard/GuideSteps.kt").matchAll(/\("Turn on ([^"]+)"\)/g)].map(match => match[1]);
  expect(steps.length).toBeGreaterThan(0);
  for (const step of steps) expect(`${step}: ${labels.has(step)}`).toBe(`${step}: true`);
});

test("the bar is turned off in one place, and the cursor setting uses its one name", () => {
  expect(settings).toContain("label={barLabel}");
  expect(settings).toContain("label={cursorLabel}");
  expect(read("app/settings/hidden-bar.tsx")).not.toContain("<Toggle");
  expect(read("app/settings/guide-boring.tsx")).toContain(cursorLabel);
  expect(read("app/settings/guide-boring.tsx")).toContain(`turn off ${barLabel}`);
  expect(settings).toContain("label={scrollLabel}");
  expect(read("app/settings/guide-boring.tsx")).toContain(`Turn it off with ${scrollLabel} in Settings`);
});

test("Record a bug has one way in: Settings > Help", () => {
  expect(read("app/settings/feedback.tsx")).not.toContain("/settings/bug");
  expect(settings).toContain('href="/settings/bug"');
});
