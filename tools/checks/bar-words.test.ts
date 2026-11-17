import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

// The bar's words in every language (owner, 2026-11-16): another input method's starters, next words and learning sit
// in LanguageWords' "other" branch, and English's stay exactly as they were; the fields without words keep an empty bar.
const root = join(import.meta.dir, "../..");
const read = (path: string) => readFileSync(join(root, path), "utf8");

test("English's words are untouched: every Words call goes to English's own when English types", () => {
  const words = read("android/kotlin/com/titankeys/keyboard/LanguageWords.kt");
  for (const call of ["english.complete(before, cancelled, sentenceCapitalisation, result)", "english.keptPair(previous, next)",
    "english.untyped(word)", "english.sentenceStarts()", "english.allowed(word)", "english.known(word)"])
    expect(words).toContain(call);
  expect(words).toContain("if (!other) return english.complete(");
  expect(words).toContain("if (!other) return english.keptPair(");
});

test("passwords and the other fields without words keep an empty bar, in every language", () => {
  const field = read("android/kotlin/com/titankeys/keyboard/Field.kt");
  expect(field).toContain("words = !password && !email");
  const composer = read("android/kotlin/com/titankeys/keyboard/Composer.kt");
  expect(composer).toContain("val starts = if (!field.suggestions) emptyList()");
});

test("what other input methods learn is backed up, kept by Reset most things, cleared by Reset literally everything", () => {
  expect(read("android/kotlin/com/titankeys/keyboard/BackupFile.kt")).toContain('"language_learning" to Store.Backed()');
  expect(read("android/kotlin/com/titankeys/keyboard/FreshStart.kt")).toContain('"language_learning"');
  expect(read("android/kotlin/com/titankeys/keyboard/SettingsReset.kt")).not.toContain("language_learning");
});
