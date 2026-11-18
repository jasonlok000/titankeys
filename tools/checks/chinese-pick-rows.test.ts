import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

const root = join(import.meta.dir, "../..", "android/kotlin/com/titankeys/keyboard");
const ime = readFileSync(join(root, "TitanKeysIme.kt"), "utf8");
const strip = readFileSync(join(root, "Strip.kt"), "utf8");
const body = (name: string) => ime.slice(ime.indexOf("private fun " + name + "(")).split(/\n    private (?:fun|val|var) /, 1)[0];

test("picking a Chinese row replaces it without displaying its underlying words first", () => {
  expect(body("pick")).toContain("if (open !is LanguagePicker.Choices || !ChineseKeys.isChinese(languages.current)) closePicker()");
  const state = body("engineState");
  expect(state.indexOf("chineseOffers.begin()")).toBeGreaterThan(-1);
  expect(state.indexOf("strip?.words = emptyList()")).toBeLessThan(state.indexOf("composer.paste(text"));
  expect(state.indexOf("composer.paste(text")).toBeLessThan(state.indexOf("chineseOffers.submitted("));
  expect(body("showComposerWords")).toContain("!chineseOffers.allows(composer.hasPendingInput)");
});

test("a screen pick cannot reuse the same index from a replaced row", () => {
  expect(strip).toMatch(/var notice: Notice\? = null[\s\S]*?pickerTouched = -1\s+if \(field == value\) return/);
  expect(ime).toContain("if (strip !== words || openPicker() !== open) return@enqueue");
  expect(body("pickChoice")).toContain("engine.pickDisplayed(index, open.items[item])");
  expect(body("pickFromMenu")).toContain("engine.pickDisplayed(index, word)");
});

test("Debug can distinguish the drawn pending and prediction rows", () => {
  expect(strip).toMatch(/override fun dispatchDraw[\s\S]*?choiceRowLog\?\.let/);
  expect(strip).toContain("BarLog.choiceRow(shown.lead.isNotEmpty(), shown.items, shown.columns)");
  expect(ime).toContain("BarLog.choiceLine(languages.current.id, shown)");
});

test("a cancelled Chinese code restores the cached words through the wide engine row", () => {
  const state = body("engineState");
  expect(state).toContain("!choicesMenuOpen && !composer.hasPendingInput && ChineseKeys.isChinese(languages.current)");
  expect(state).toContain("val before = (openPicker() as? LanguagePicker.Choices)?.state");
  const restore = state.indexOf("restoreOffersAfterCode(before, result, barWords)");
  expect(restore).toBeGreaterThan(-1);
  expect(restore).toBeLessThan(state.indexOf("closePicker()"));
});
