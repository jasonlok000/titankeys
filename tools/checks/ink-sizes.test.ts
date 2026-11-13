import { expect, test } from "bun:test";
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";

// The owner's floor for TitanKeys' screens at the fixed scale (2026-11-13): rows at least 18 dp, notes and secondary text at
// least 15, section labels at least 14, and titles clearly bigger. A dp is 1.5625 screen pixels (FixedScale). Ink draws a theme
// pixel as 1076 / 1080 of a screen pixel, and a size given in Ink units, such as <Text size>, as 1.5 theme pixels.
const root = join(import.meta.dir, "../..");
const read = (path: string) => readFileSync(join(root, path), "utf8");
const DP = 1.5625;
const themePixel = 1076 / 1080;
const dpOfThemePixels = (px: number) => px * themePixel / DP;
const dpOfUnits = (units: number) => dpOfThemePixels(units * 1.5);

const theme = read("theme.ts");
const size = (pattern: RegExp) => Number(theme.match(pattern)?.[1]);

test("the theme's text sizes are at or above the owner's floor, with titles clearly bigger", () => {
  const row = dpOfThemePixels(size(/text: \{ size: (\d+) \}/));
  const note = dpOfThemePixels(size(/subtitle: \{ size: (\d+)/));
  const section = dpOfThemePixels(size(/section: \{[^}]*size: (\d+)/));
  const title = dpOfThemePixels(size(/header: \{[^}]*size: (\d+)/));
  expect(row).toBeGreaterThanOrEqual(18);
  expect(note).toBeGreaterThanOrEqual(15);
  expect(section).toBeGreaterThanOrEqual(14);
  expect(title).toBeGreaterThanOrEqual(row * 1.5);
});

function files(folder: string): string[] {
  return readdirSync(join(root, folder), { withFileTypes: true }).flatMap(entry =>
    entry.isDirectory() ? files(join(folder, entry.name)) : entry.name.endsWith(".tsx") ? [join(folder, entry.name)] : []);
}

test("no text on a TitanKeys screen is set smaller than a note", () => {
  for (const path of [...files("app"), ...files("lib")]) {
    for (const match of read(path).matchAll(/\b(?:size=\{|fontSize: )(\d+)/g)) {
      const dp = dpOfUnits(Number(match[1]));
      expect({ path, size: match[1], readable: dp >= 15 }).toEqual({ path, size: match[1], readable: true });
    }
  }
});
