import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

// Language-dependent settings live in the language (owner, 2026-11-17). With English alone, Settings looks as it did. With more,
// Typing is a door to the languages, and each language's page shows the rows that work in it (LanguageSettings.kt, checked in
// tests/languages), with its own values (TypingSettings.PER_LANGUAGE).
const root = join(import.meta.dir, "../..");
const read = (path: string) => readFileSync(join(root, path), "utf8");
const main = read("app/settings/index.tsx");
const page = read("app/settings/languages/language.tsx");
const adapters = read("android/kotlin/com/vandam/ink/AppNativeAdapters.kt");
const typing = read("android/kotlin/com/titankeys/engine/TypingSettings.java");

test("with English alone nothing moves; with more, Typing is a door that names the languages on", () => {
  expect(read("app/settings/languages/index.tsx")).toContain('return !(on.length === 1 && on[0].language.id === "en");');
  expect(main).toContain('<Field label="Typing in each language" href="/settings/languages">{languagesLabel(settings)}</Field>');
  expect(main).toContain("{!perLanguage && <Field label=\"Spell out shorthand\"");
  expect(main).toContain("{!perLanguage && <Toggle label=\"Swipe to type words\"");
});

test("a language's page shows only its own rows, writes only its own values, and says once that they moved", () => {
  for (const row of ["fixes", "capitals", "sentenceSpaces", "commaSpace", "doubleSpace", "hold", "swipe", "shorthand"])
    expect(page).toContain(`has("${row}")`);
  expect(page).toContain("save.run({ languageSettings: { [language.id]: values } })");
  expect(page).toContain("Typing settings for each language now live on its page.");
  expect(page).toContain("void markLanguageNoteSeen();");
});

test("Fix typos shows where a language corrects, by the same rule the keyboard corrects by", () => {
  const corrects = read("android/kotlin/com/titankeys/keyboard/LanguageCorrections.kt");
  expect(corrects).toContain("fun corrects(method: InputMethod) = !method.isEnglish && method.words != null && method.engine == null");
  expect(read("android/kotlin/com/titankeys/keyboard/LanguageSettings.kt"))
    .toContain("if (method.isEnglish || method.words != null && method.engine == null) add(FIXES)");
  // The keyboard reads the language's own switch for its corrections.
  expect(read("android/kotlin/com/titankeys/keyboard/LanguageCorrection.kt"))
    .toContain("fun on(language: String): Boolean = TypingSettings.autocorrectIn(context, language)");
});

test("a language reads its own value, or today's single one; the main page's writes reach English's own too", () => {
  expect(typing).toContain("return settings.contains(own) ? settings.getBoolean(own, otherwise) : settings.getBoolean(key, otherwise);");
  for (const name of ["autocorrect", "autoCapitalise", "autoPunctuation", "commaSpace", "holdSymbols", "sentenceSpaces"])
    expect(adapters).toMatch(new RegExp(`"${name}" -> TypingSettings\\.${name}\\(activity, value as \\w+\\)\\.also \\{ TypingSettings\\.${name}In\\(activity, ENGLISH, value\\) \\}`));
  // The keyboard reads the values of the language typing now.
  const ime = read("android/kotlin/com/titankeys/keyboard/TitanKeysIme.kt");
  expect(ime).toContain("TypingSettings.autocorrectIn(this, languages.languageId) && english");
  expect(ime).toContain("composer.commaSpace = { TypingSettings.commaSpaceIn(this, languages.languageId) }");
});
