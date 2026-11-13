import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

// A line of full-strength content showed just under Settings' fixed title while scrolling (owner, 2026-11-13: "a little bit
// bugged at a line of pixel below settings"). Ink cuts scrolled content at the fade band's top rounded down, so the band must
// start on that whole pixel too. Ink's own test (fade_band_tests) checks the sums; this checks the patch keeps them.
const patch = readFileSync(join(import.meta.dir, "../../tools/ink/titankeys.patch"), "utf8");

test("every fixed title's fade starts on the whole pixel where scrolled content is cut", () => {
  expect(patch).toContain("+fn fade_band(x: f32, width: f32, top: f32, bottom: f32) -> Rect {");
  expect(patch).toContain("+    let top = top.floor();");
  expect(patch).toContain("+            fade_band(rect.x, rect.width, content.y - height, content.y)");
  expect(patch).toContain("+    fn the_fade_starts_on_the_row_where_scrolled_content_is_cut() {");
});
