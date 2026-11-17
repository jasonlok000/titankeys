import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

// Spelling variants (Portuguese's Brasil and Portugal): Settings shows the field on the language's page, opens a choice page,
// and the app saves the pick for the keyboard, which loads that variant's list and next words (Languages.load).
const root = join(import.meta.dir, "../..");
const read = (path: string) => readFileSync(join(root, path), "utf8");

test("the index lists Portuguese's variants, Brasil first", () => {
  const index: { id: string; variants?: { id: string; name: string }[] }[] = JSON.parse(read("android/assets/im/index.json"));
  expect(index.find(entry => entry.id === "pt")?.variants).toEqual([{ id: "pt-BR", name: "Brasil" }, { id: "pt-PT", name: "Portugal" }]);
});

test("the language's page opens the variant page, which saves the pick, and the app keeps it", () => {
  expect(read("app/settings/languages/language.tsx")).toContain('path: "/settings/variant", params: { inputMethod: im.id }');
  const page = read("app/settings/variant.tsx");
  expect(page).toContain("useRouteParams().inputMethod");
  expect(page).toContain("write={variant => ({ inputMethodVariants: { [im.id]: variant } })}");
  const adapters = read("android/kotlin/com/vandam/ink/AppNativeAdapters.kt");
  expect(adapters).toContain('"inputMethodVariants" -> (value as JSONObject)');
  expect(adapters).toContain('.put("inputMethodVariants"');
  // The keyboard loads the picked variant, not the first.
  const languages = read("android/kotlin/com/titankeys/keyboard/Languages.kt");
  expect(languages).not.toContain("variants.firstOrNull()?.id");
  expect(languages).toContain("InputMethodList.words(context, it, variant)");
  expect(languages).toContain("InputMethodList.nextWords(context, it, variant)");
});

test("Reset most things resets the pick", () => {
  expect(read("android/kotlin/com/titankeys/keyboard/SettingsReset.kt")).toContain('"input_method_variants"');
});
