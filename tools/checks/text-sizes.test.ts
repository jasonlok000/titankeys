// The bar and the panels must read on a 9 cm screen (owner, 2026-11-13): no text under 13 dp at TitanKeys' fixed scale, and the
// bar's best word, in the middle, is the biggest. These read the source, where no Kotlin check can lay out a view.
import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

const root = join(import.meta.dir, "../..");
const read = (name: string) => readFileSync(join(root, "android/kotlin/com/atlas/keyboard", name), "utf8");
const files = ["Strip.kt", "SymPanel.kt", "ExactBar.kt"];
const smallest = 13;

test("no text on the bar or the panels is set under 13", () => {
  const found: string[] = [];
  for (const file of files) {
    const source = read(file);
    // A text size set straight: textSize = 12f.
    for (const match of source.matchAll(/textSize\s*=\s*(\d+(?:\.\d+)?)f/g)) if (Number(match[1]) < smallest) found.push(`${file}: ${match[0]}`);
    // A size or autosize limit in a constant: CHIP_SIZE = 13f, LABEL_SMALLEST = 8.
    for (const match of source.matchAll(/const val (\w*(?:SIZE|SMALLEST|LARGEST)\w*) = (\d+(?:\.\d+)?)f?\b/g))
      if (Number(match[2]) < smallest) found.push(`${file}: ${match[1]} = ${match[2]}`);
  }
  expect(found).toEqual([]);
});

test("the bar's middle word is the biggest, and its words are bigger than before", () => {
  const strip = read("Strip.kt");
  const middle = Number(/const val WORD_SIZE = (\d+)f/.exec(strip)?.[1]);
  const side = Number(/const val SIDE_WORD_SIZE = (\d+)f/.exec(strip)?.[1]);
  expect(middle).toBe(20);
  expect(side).toBeGreaterThan(17);
  expect(side).toBeLessThan(middle);
  expect(strip).toContain("view.typeface = if (index == 0) boldTypeface else typeface");
});
