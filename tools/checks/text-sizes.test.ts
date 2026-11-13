// The bar and the panels must read on a 9 cm screen (owner, 2026-11-13): no text under 13 dp at TitanKeys' fixed scale, and the
// bar's three words are one size, the best one bold. These read the source, where no Kotlin check can lay out a view.
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

test("the bar's words are all one size, 20, and the best one in the middle is bold", () => {
  const strip = read("Strip.kt");
  expect(Number(/const val WORD_SIZE = (\d+)f/.exec(strip)?.[1])).toBe(20);
  expect(strip).not.toContain("SIDE_WORD_SIZE");
  expect(strip).toContain("view.typeface = if (index == 0) bestTypeface else typeface");
  // StripFit measures in the best word's weight, the widest, so no fitted word runs over.
  expect(strip).toContain("measurePaint.typeface = bestTypeface");
  // The words, the notices and the menu are centred on the middle of their small letters.
  for (const centred of ["centreLetters(view)", "centreLetters(noticeLabel)", "centreLetters(button)",
    "StripFit.baseline(height / 2f, menuWordText.textSize)", "StripFit.baseline(height / 2f, pillText.textSize)"])
    expect(strip).toContain(centred);
});

test("StripFit's x-height is Inter's, in every weight", () => {
  const fit = read("StripFit.kt");
  const [units, em] = /const val X_HEIGHT = (\d+)f \/ (\d+)f/.exec(fit)!.slice(1).map(Number);
  for (const weight of ["regular", "medium", "bold"]) {
    const font = readFileSync(join(root, `android/src/main/res/font/inter_${weight}.otf`));
    const tables = font.readUInt16BE(4);
    const table = (tag: string) => {
      for (let i = 0; i < tables; i++) if (font.toString("latin1", 12 + 16 * i, 16 + 16 * i) === tag) return font.readUInt32BE(20 + 16 * i);
      throw new Error(`no ${tag}`);
    };
    expect(font.readUInt16BE(table("head") + 18)).toBe(em);
    expect(font.readInt16BE(table("OS/2") + 86)).toBe(units);
  }
});
