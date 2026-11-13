import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

// TextView.setSingleLine replaces the text's transformation, so an isAllCaps set before it is wiped: the guide's titles showed in
// sentence case on the phone while every render showed them upper case (2026-11-13). The capitals must come after it.
const activity = readFileSync(join(import.meta.dir, "../../android/kotlin/com/atlas/keyboard/GuideActivity.kt"), "utf8");

test("the title's capitals are set after its single line, in every look", () => {
  const looks = activity.split(/\n    private fun /).filter((body) => body.includes("title.isSingleLine"));
  expect(looks.length).toBeGreaterThanOrEqual(2);
  for (const body of looks) {
    const single = body.indexOf("title.isSingleLine");
    const caps = body.indexOf("title.isAllCaps = true");
    expect(caps).toBeGreaterThan(single);
    expect(body.indexOf("title.isAllCaps = true", 0)).toBe(caps);
  }
});
