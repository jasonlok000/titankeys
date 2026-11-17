import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

// Corrections in every language with a word list (owner, 2026-11-17): tests/corrections measures them on each list; these keep
// the keyboard wired to them, and English's corrector as it was.
const root = join(import.meta.dir, "../..");
const read = (path: string) => readFileSync(join(root, path), "utf8");

test("another input method with a word list gets its own corrector, as strict as Only fix when sure", () => {
  const ime = read("android/kotlin/com/titankeys/keyboard/TitanKeysIme.kt");
  expect(ime).toContain("languageCorrection.use(languages.current, languages.list)");
  expect(ime).toContain("composer.autocorrect = autocorrect.autocorrect.takeIf { corrects } ?: language");
  const corrections = read("android/kotlin/com/titankeys/keyboard/LanguageCorrections.kt");
  expect(corrections).toContain("override fun onlyWhenSure() = true");
  // Only slips on the keys: none of English's other fixes.
  expect(corrections).toContain("override fun fixes(kind: FixKind) = kind == FixKind.TYPOS && typos()");
  expect(corrections).toContain("!method.isEnglish && method.words != null && method.engine == null");
  // The bar shows the likeliest word even where Space keeps what was typed.
  expect(read("android/kotlin/com/titankeys/keyboard/LanguageWords.kt")).toContain("suggest(prefix, before.dropLast(prefix.length))");
});

test("English's corrector keeps its own keys and rules", () => {
  const channel = read("android/kotlin/com/titankeys/autocorrect/NoisyChannel.kt");
  // English's keys are its letters: the same costs, and its shorthand rule.
  expect(channel).toContain("val ENGLISH = Keys({ it }, { it }, shorthand = true)");
  expect(channel).toContain("private val keys: Keys = Keys.ENGLISH,");
  const corrector = read("android/kotlin/com/titankeys/autocorrect/TypedCorrector.kt");
  expect(corrector).toContain("private val keys: NoisyChannel.Keys = NoisyChannel.Keys.ENGLISH,");
  expect(corrector).toContain("if (english) twoLetterFix(");
  // English's loader passes no keys, so it gets English's.
  expect(read("android/kotlin/com/titankeys/keyboard/AutocorrectLoader.kt")).not.toContain("keys =");
});
