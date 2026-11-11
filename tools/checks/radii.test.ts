import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { RADIUS } from "../../lib/radii";

// The keyboard and the app must round their corners the same, so Radii.kt and lib/radii.ts hold the same numbers.
test("the keyboard's radii match the app's", () => {
  const kotlin = readFileSync(join(import.meta.dir, "../../android/kotlin/com/atlas/keyboard/Radii.kt"), "utf8");
  const read = (name: string) => Number(kotlin.match(new RegExp(`const val ${name} = (\\d+)`))?.[1]);
  expect({ small: read("SMALL"), medium: read("MEDIUM"), large: read("LARGE") }).toEqual({ ...RADIUS });
});

test("the radii go from small to large", () => {
  expect(RADIUS.small).toBeLessThan(RADIUS.medium);
  expect(RADIUS.medium).toBeLessThan(RADIUS.large);
});

test("the app's text fields use the medium radius", () => {
  const theme = readFileSync(join(import.meta.dir, "../../theme.ts"), "utf8");
  expect(theme).toContain(`textInput: { radius: ${RADIUS.medium} }`);
});
