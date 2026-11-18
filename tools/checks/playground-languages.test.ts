import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

// The playground types in the input methods that are on, and a held Sym opens the switcher there; the guide's lessons stay English
// (owner, 2026-11-17: "long press for sym doesn't trigger the language selection in the playground").
const root = join(import.meta.dir, "../..");
const read = (path: string) => readFileSync(join(root, path), "utf8");

test("only the guide's lessons type English: the playground types in the ones that are on", () => {
  const ime = read("android/kotlin/com/titankeys/keyboard/TitanKeysIme.kt");
  expect(ime).toContain("languages.englishOnly = inGuide\n");
  expect(ime).not.toContain("languages.englishOnly = inGuide || inPractice");
  // inGuide is the guide's own field only, never the playground's.
  expect(ime).toContain("private val inGuide get() = editing && currentInputEditorInfo?.privateImeOptions?.contains(GuideHooks.FIELD) == true");
  expect(read("android/kotlin/com/titankeys/keyboard/GuideHooks.kt")).toContain('const val PLAYGROUND = "com.titankeys.playground"');
  // So a held Sym in the playground isn't refused for its field.
  expect(read("android/kotlin/com/titankeys/keyboard/InputMethods.kt")).toContain('englishOnly -> "this field types English: the guide\'s lessons"');
});
