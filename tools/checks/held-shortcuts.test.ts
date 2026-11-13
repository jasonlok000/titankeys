import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

// Shift + Backspace and Alt + Sym are built in, so they work with Key Mapper off (owner, 2026-11-12). tests/modifiers checks
// the held, tapped and automatic modifiers; these check how the keyboard uses them.
const root = join(import.meta.dir, "../..");
const ime = readFileSync(join(root, "android/kotlin/com/atlas/keyboard/AtlasIme.kt"), "utf8");

test("Backspace asks the modifiers whether it deletes a word, after exact typing has taken its keys", () => {
  expect(ime).toContain("composer.backspace(modifiers.backspaceDeletesWord(event.isCtrlPressed))");
  expect(ime.indexOf("if (field.exact) return exactKey(event)")).toBeLessThan(ime.indexOf("modifiers.backspaceDeletesWord("));
});

test("Alt + Sym goes to the next keyboard, never the picker, and never from the guide's field", () => {
  const start = ime.indexOf("private fun switchKeyboardIfAsked()");
  const body = ime.slice(start, ime.indexOf("\n    }\n", start));
  expect(body).toContain("!modifiers.takeKeyboardSwitch() || inGuide");
  expect(body).toContain("switchToNextInputMethod(false)");
  expect(body).not.toContain("showInputMethodPicker");
});

test("both are listed in Settings and the boring guide", () => {
  const settings = readFileSync(join(root, "app/settings/index.tsx"), "utf8");
  expect(settings).toContain("Hold Shift + Backspace");
  expect(settings).toContain("Hold Alt + Sym");
  const guide = readFileSync(join(root, "app/settings/guide-boring.tsx"), "utf8");
  expect(guide).toContain("Backspace while you hold Shift");
  expect(guide).toContain("Hold Alt and press Sym");
});
