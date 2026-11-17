import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

// The gestures in every language (owner, 2026-11-16): double space, three Space taps and the delete flick. The JVM checks run the
// rules (tests/delete-hold, tests/cursor, tests/autocorrect, tests/languages); these keep the keyboard wired to them.
const root = join(import.meta.dir, "../..");
const read = (path: string) => readFileSync(join(root, path), "utf8");
const ime = read("android/kotlin/com/titankeys/keyboard/TitanKeysIme.kt");

test("a flick with a code pending clears the code and leaves the field alone", () => {
  const start = ime.indexOf("if (outcome.deletesWord) {");
  const flick = ime.slice(start, ime.indexOf("val before = currentInputConnection?.getTextBeforeCursor(FLICK_REACH, 0)", start));
  expect(flick).toContain("val choices = openPicker() as? LanguagePicker.Choices");
  expect(flick).toMatch(/languages\.engine\?\.reset\(\)\s*closePicker\(\)\s*if \(choices\.state\.composing\) \{[\s\S]*?return\s*\}/);
});

test("double space types the language's own full stop, and the words come from the language's breaks", () => {
  expect(ime).toContain("composer.fullStopMark = { languages.current.rules.fullStop }");
  // The words come from one place, [wordBreaker]: ICU's, and in Chinese and Japanese the units as picked first (PickedWords).
  expect(ime).toContain("editor.wordBreaker = ::wordBreaker");
  expect(ime).toContain("ChineseKeys.isChinese(languages.current)) return PickedWords.CHARACTERS");
  expect(ime).toContain("val icu = IcuWords.of(languages.current.javaLocale)");
  const composer = read("android/kotlin/com/titankeys/keyboard/Composer.kt");
  // The full stop is the only place double Space's mark comes from: no "." of its own.
  expect(composer).not.toContain('".$spaces"');
  expect(composer).toContain("if (mark.endsWith(\" \")) mark.trimEnd() + spaces else mark");
  // Three Space taps pick a word with the same breaks.
  expect(ime).toContain("WordSelection.atOrBefore(text, start, end, breaks)");
  expect(ime).toContain("val breaks = wordBreaker()");
  // A slide starts from the same word and steps by the same words.
  expect(ime).toContain("WordSelection.at(text, start, end, wordBreaker())");
  expect(ime).toContain("direction, wordBreaker())");
});
