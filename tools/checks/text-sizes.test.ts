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
  // The words, the notices and the menu have the middle of their small letters on the bar's line. The words all sit on the
  // full-size words' line, a shrunk one too (owner, 2026-11-13: one bar baseline).
  for (const centred of ["centreLetters(view, wordPixels())", "centreLetters(selectedView, wordPixels())",
    "centreLetters(noticeLabel)", "centreLetters(button)", "StripFit.top(line, lineSize, view.paint.fontMetricsInt.ascent, floor)",
    "StripFit.baseline(line, menuWordText.textSize, floor)", "StripFit.baseline(line, pillText.textSize, floor)"])
    expect(strip).toContain(centred);
});

test("everything with text in the bar sits on one line, the middle of the bar and the black border under the screen", () => {
  // The owner's 11 dp (2026-11-13: "I measured with a ruler and I think 11dp lower is right"), built as the middle of the bar's 56 dp
  // and the 22 dp border: one named border, never a loose 11.
  const fit = read("StripFit.kt");
  expect(fit).toContain("const val BORDER_DP = 22f");
  expect(fit).toContain("the owner measured it with a ruler");
  expect(fit).toContain("const val LINE_DP = (BAR_DP + BORDER_DP) / 2");
  expect(fit).not.toMatch(/11f|= 39f/);
  const strip = read("Strip.kt");
  expect(strip).toContain("private val line = FixedScale.fixedPx(context, StripFit.LINE_DP)");
  expect(strip).not.toContain("StripFit.baseline(height / 2f");
  expect(strip).not.toContain("(height - pillHeight) / 2");
  // The hint and a clip alone shrink or wrap their own way, so they draw their letters on the line wherever they're laid out.
  expect(strip).toContain("private val hint = LineText(context)");
  expect(strip).toContain("private val clipText = LineText(context)");
  expect(strip).toContain("onLine(hint, modifierRow.top + hint.top)");
  // Nothing in the bar is moved to another's baseline: the modifier row lined the chips up with its hint's and cut them off the
  // screen on build #13, and the bar's own places all put their text on the line themselves.
  expect(strip.match(/isBaselineAligned = false/g)?.length).toBe(2);
  // The chips and the automatic capital's arrow, the pill and the icons, all round the line.
  expect(strip).toContain("return StripFit.boxTopPx(line, middle, height, floor).toFloat() to height");
  expect(strip).toContain("menuBox.set(pill.first, line - pillHeight / 2, pill.second, line + pillHeight / 2)");
  expect(strip).toContain("val iconTop = minOf(line - icon / 2f, height - inside - icon)");
  // Exact typing's bar and the Sym panel's band, by the same border.
  const exact = read("ExactBar.kt");
  expect(exact).toContain("private val line = density * StripFit.LINE_DP");
  expect(exact).toContain("private fun label(text: String) = LineText(context)");
  const sym = read("SymPanel.kt");
  // The band has the bar's line only at the screen's edge, with the bar hidden; with the bar under the panel, its own middle.
  expect(sym).toContain("private fun bandLine() = if (below <= dp(2)) dp(BAND_HEIGHT) - StripFit.LINE_ABOVE_DP * resources.displayMetrics.density");
  expect(sym).toContain("else dp(BAND_HEIGHT) / 2f");
  // The clip offer with its hint, two lines, moves together as near the line as fits (owner, 2026-11-13: "the hint for that
  // should also follow the visible border thing"), and the guide's ghost finger is on the word's line.
  expect(strip).toContain("val shift = StripFit.pairShiftPx(clipBaseline - clipText.textSize * StripFit.X_HEIGHT, hintBaseline, top + high, top + low,");
  expect(strip).toContain("drawing.finger(canvas, view.left + view.width / 2f, line, floor, fingerAlpha, fingerHold, look, FixedScale.fixedPx(context, 1f))");
  for (const view of ["private val preview = LineText(context)", "private val clipLine = LineText(context)",
    "private fun categoryTab(sample: String, name: String, index: Int) = LineText(context)"]) expect(sym).toContain(view);
});

test("StripFit's x-height is Inter's, in every weight", () => {
  const fit = read("StripFit.kt");
  const [units, em] = /const val X_HEIGHT = (\d+)f \/ (\d+)f/.exec(fit)!.slice(1).map(Number);
  const descent = Number(/const val DESCENT = (\d+)f \/ 2048f/.exec(fit)![1]);
  for (const weight of ["regular", "medium", "bold"]) {
    const font = readFileSync(join(root, `android/src/main/res/font/inter_${weight}.otf`));
    const tables = font.readUInt16BE(4);
    const table = (tag: string) => {
      for (let i = 0; i < tables; i++) if (font.toString("latin1", 12 + 16 * i, 16 + 16 * i) === tag) return font.readUInt32BE(20 + 16 * i);
      throw new Error(`no ${tag}`);
    };
    expect(font.readUInt16BE(table("head") + 18)).toBe(em);
    expect(font.readInt16BE(table("OS/2") + 86)).toBe(units);
    // Its descent, how far its tails may reach, as Android lays its lines out: hhea's descender.
    expect(-font.readInt16BE(table("hhea") + 6)).toBe(descent);
  }
});
