import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import indexed from "../../android/assets/im/index.json";
import { languageValues } from "../../lib/language-page";
import type { InputMethodEntry, LanguageValues } from "../../lib/titankeys";
import { typingLanguages, typingRows, typingUpdate } from "../../lib/typing-settings";

// Typing uses the same preferences as the keyboard and shows each enabled language's supported controls.
const root = join(import.meta.dir, "../..");
const read = (path: string) => readFileSync(join(root, path), "utf8");
const main = read("app/settings/index.tsx");
const page = read("app/settings/languages/language.tsx");
const typingPage = read("app/settings/typing.tsx");
const adapters = read("android/kotlin/com/vandam/ink/AppNativeAdapters.kt");
const typing = read("android/kotlin/com/titankeys/engine/TypingSettings.java");

const availableInputMethods = [
  { id: "en", language: { id: "en" } },
  ...indexed,
] as InputMethodEntry[];
const methods = (...inputMethods: string[]) => ({ inputMethods, availableInputMethods });

test("Typing is always a destination; the picker lists enabled languages once each when several are on", () => {
  expect(main).toContain('<Field label="Typing" href="/settings/typing" />');
  expect(typingPage).toContain("enabled.length > 1");
  const english = typingLanguages(methods("en"));
  expect(english.enabled.map(row => row.id)).toEqual(["en"]);
  expect(english.editing?.id).toBe("en");
  const multilingual = methods("zh-pinyin", "en", "yue-yale");
  expect(typingLanguages(multilingual, "en").enabled.map(row => row.id)).toEqual(["zh", "en"]);
  expect(typingLanguages(multilingual, "en").editing?.id).toBe("en");
  expect(typingLanguages(multilingual, "fr").editing?.id).toBe("zh");
  expect(typingLanguages(methods("zh-pinyin", "yue-yale"), "en").enabled.map(row => row.id)).toEqual(["zh"]);
});

test("Typing shows supported controls while Languages retains spelling, characters, and guide entry", () => {
  expect(typingRows("en")).toEqual(["fixes", "capitals", "sentenceSpaces", "commaSpace", "doubleSpace", "hold", "swipe", "shorthand"]);
  expect(typingRows("zh")).toEqual(["doubleSpace", "hold"]);
  expect(typingRows("fr")).toEqual(["fixes", "capitals", "sentenceSpaces", "commaSpace", "doubleSpace", "hold"]);
  expect(typingRows("th")).toEqual(["fixes", "commaSpace"]);
  expect(page).toContain('href="/settings/spelling"');
  expect(page).toContain('href="/settings/chinese"');
  expect(page).toContain('path: "/settings/languages/guide"');
  expect(page).not.toContain("typingUpdate");
  expect(typingPage).toContain("<Section title={pageText.list.all}>");
  expect(typingPage).toContain("value={settings.learning}");
});

test("Settings uses the same enabled methods as the keyboard, including after a restore", () => {
  expect(adapters).toContain('.put("inputMethods", JSONArray(inputMethodIndex().on(TypingSettings.inputMethods(activity)).map { it.id }))');
});

test("English-only writes still update the legacy defaults, while multilingual writes stay in the chosen language", () => {
  const values = { autoCapitalise: false, sentenceSpaces: "two", holdSymbols: true } as const;
  expect(typingUpdate(methods("en"), "en", values)).toEqual(values);
  expect(typingUpdate(methods("en", "zh-pinyin"), "en", values)).toEqual({ languageSettings: { en: values } });
  expect(typingUpdate(methods("zh-pinyin", "yue-yale"), "zh", values)).toEqual({ languageSettings: { zh: values } });
  const fallback: LanguageValues = { autocorrect: true, autoCapitalise: true, autoPunctuation: true,
    sentenceSpaces: "one", commaSpace: true, holdSymbols: false };
  const own = { ...fallback, ...values };
  expect(languageValues({ ...fallback, languageSettings: { en: own } }, "en")).toEqual(own);
  expect(languageValues({ ...fallback, languageSettings: {} }, "fr").sentenceSpaces).toBe("one");
  for (const route of ["typing", "hold", "sentence-spaces"])
    expect(read(`app/settings/${route}.tsx`)).toContain("typingUpdate(");
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

test("a quick setting writes the plain value only while English alone is on, as Typing does", () => {
  const ime = read("android/kotlin/com/titankeys/keyboard/TitanKeysIme.kt");
  expect(ime).toContain("if (languages.on.all { it.language.id == InputMethod.ENGLISH_ID }) plain()");
  expect(ime).not.toContain("distinct().size <= 1) plain()");
});

test("the quick settings panel leaves out what can't work in the typing language, and the raw log records its own values", () => {
  const ime = read("android/kotlin/com/titankeys/keyboard/TitanKeysIme.kt");
  expect(ime).toContain("panel?.hiddenKeys = QuickSetting.hiddenIn(LanguageSettings.rows(method)).map { it.key }.toSet()");
  expect(ime).toContain("showQuickSettingsFor(languages.current)");
  const panel = read("android/kotlin/com/titankeys/keyboard/SymPanel.kt");
  expect(panel).toContain("private fun quickItem(key: String) = QuickSetting.on(key)?.takeIf { key !in hiddenKeys }");
  expect(panel).toContain("quick -> quickItem(key)?.let(useQuickSetting)");
  const log = read("android/kotlin/com/titankeys/keyboard/RawLog.kt");
  expect(log).toContain('"language" to language,');
  for (const key of ["holdSymbolsIn", "autoCapitaliseIn", "autoPunctuationIn", "sentenceSpacesIn", "commaSpaceIn", "autocorrectIn"])
    expect(log).toContain(`TypingSettings.${key}(context, language)`);
});
