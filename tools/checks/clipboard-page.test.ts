import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

// The clipboard page (Sym, then A), read from its Kotlin source: the panel's views need a phone to run.
const keyboard = join(import.meta.dir, "../../android/kotlin/com/atlas/keyboard");
const panel = readFileSync(join(keyboard, "SymPanel.kt"), "utf8");
const marquee = readFileSync(join(keyboard, "BandMarquee.kt"), "utf8");

function block(text: string, start: string): string {
  const at = text.indexOf(start);
  expect(at).toBeGreaterThanOrEqual(0);
  return text.slice(at, text.indexOf("\n    }\n", at));
}

test("each clip's key shows one line, ending in an ellipsis", () => {
  const field = block(panel, "private inner class ClipField");
  expect(field).toContain("isSingleLine = true");
  expect(field).toContain("maxLines = 1");
  expect(field).toContain("ellipsize = TextUtils.TruncateAt.END");
  expect(field).not.toMatch(/maxLines = [2-9]/);
});

test("the preview at the bottom says only the clip, not that it is pinned", () => {
  expect(panel).not.toContain("clipboard_pinned,");
  expect(panel).not.toMatch(/R\.string\.clipboard_pinned\b(?!_)/);
});

test("a long preview starts to scroll after about 300 ms", () => {
  const start = Number(marquee.match(/const val START_MILLIS = (\d+)L/)?.[1]);
  expect(start).toBeGreaterThan(0);
  expect(start).toBeLessThanOrEqual(300);
});

test("a short preview is laid out to the strip's width, so it is centred in sight", () => {
  // Laid out to scroll, a centred line sits in the middle of a very wide line, and the scroll back to its start hides it.
  expect(marquee).not.toContain("setHorizontallyScrolling(true)");
  expect(marquee).toContain("setHorizontallyScrolling(long)");
  const place = block(marquee, "private fun place()");
  expect(place.indexOf("setHorizontallyScrolling(long)")).toBeLessThan(place.indexOf("scrollTo(0, 0)"));
});

test("five clips to a page, each four keys wide; Sym's ✕, the page, Clear and Pin stay on their keys", () => {
  const history = readFileSync(join(keyboard, "ClipboardHistory.kt"), "utf8");
  expect(history).toContain("const val PER_PAGE = 5");
  // No field past the fifth: the bottom row's right half, B, N and M, by Sym, holds none.
  expect(panel).toMatch(/index \* 2 \+ half < ClipboardHistory\.PER_PAGE/);
  expect(history).toMatch(/takeIf \{ it < PER_PAGE \}/);
  // The keys around the clips keep their jobs.
  const label = block(panel, "private fun label(key: String)");
  expect(label).toContain("Layout.BACKSPACE -> context.getString(R.string.clipboard_clear)");
  expect(label).toContain("Layout.ENTER ->");
  expect(label).toContain("showsPosition(key)");
});
