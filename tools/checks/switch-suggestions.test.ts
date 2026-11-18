import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

const ime = readFileSync(join(import.meta.dir, "../../android/kotlin/com/titankeys/keyboard/TitanKeysIme.kt"), "utf8");

test("switching clears the outgoing suggestions before revealing the base strip", () => {
  const change = ime.match(/    private fun inputMethodChanged\(method: InputMethod\) \{[\s\S]*?\n    \}/)?.[0] ?? "";
  const steps = [
    "barWords = emptyList()",
    "strip?.words = emptyList()",
    "preparingNotice?.let",
    "closePicker()",
    "if (editing) composer.wordsChanged()",
    "loadEnded()",
  ];
  let previous = -1;
  for (const step of steps) {
    const current = change.indexOf(step);
    expect(current, step).toBeGreaterThan(previous);
    previous = current;
  }
});
