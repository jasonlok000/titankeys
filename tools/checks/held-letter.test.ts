import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

// The owner, 2026-11-17: "For languages with hold for accent, if the key doesnt have an accent, it still shdnt spam the key. It shd
// just type one and do nth else." The key path runs in the input method service, which the JVM suites can't start, so this reads
// it; tests/languages checks which letters hold accents.
const ime = readFileSync(join(import.meta.dir, "../../android/kotlin/com/titankeys/keyboard/TitanKeysIme.kt"), "utf8");

test("a held letter with no accents types once in an input method with accents", () => {
  const check = ime.slice(ime.indexOf("private fun accentlessRepeat"), ime.indexOf("/** The letter printed on a letter key"));
  expect(check).toContain("event.repeatCount > 0 && languages.current.hasAccents");
  expect(check).toContain("languages.current.accentsOf(printedLetter(event.keyCode), false).isEmpty()");
  // Exact typing sends keys, and Alt, Ctrl or a panel make the key something else: those keep repeating as before.
  expect(check).toContain("!field.exact");
  expect(check).toContain("!altOrPanel && !event.isAltPressed && !event.isCtrlPressed && !event.isMetaPressed");
});

test("its repeats are taken before anything types, plays or opens", () => {
  const hold = ime.indexOf("val repeatOfHold = trackHold(carried)");
  const taken = ime.indexOf("if (accentlessRepeat(carried)) return true");
  expect(hold).toBeGreaterThan(0);
  expect(taken).toBeGreaterThan(hold);
  // The next thing the key path does after the hold's own repeats is take these, before panels, Ctrl or typing.
  expect(ime.slice(hold, taken)).not.toMatch(/commit|haptics\.play|showAccents|ctrlKey\(/);
  expect(ime.indexOf("if (ctrlKey(event))", hold)).toBeGreaterThan(taken);
});
