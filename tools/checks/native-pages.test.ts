import { expect, test } from "bun:test";
import methods from "../../android/assets/im/index.json";
import sources from "../im/page-text.json";
import data from "../im/native-pages.json";
import { textFor, translator } from "../../lib/language-copy";
import { languagePage, unavailableNotes } from "../../lib/language-page";

test("every non-English language has native settings and practice instructions", () => {
  const dictionaries: Record<string, Record<string, string>> = data;
  for (const id of new Set(methods.map(method => method.language.id))) {
    if (id === "en") continue;
    const dict = dictionaries[id];
    expect(dict).toBeDefined();
    for (const source of [sources.page.tryIt, sources.page.typeHere, sources.page.how, sources.page.trySample]) {
      expect(dict[source]?.trim().length).toBeGreaterThan(0);
    }
    expect(textFor(id).page.trySample).toContain("{sample}");
  }
});

test("Cantonese methods can explain the same setting differently and inherit shared Chinese labels", () => {
  const yale = translator("yue-yale");
  const jyutping = translator("yue-jyutping");
  expect(yale(sources.settings.lazySounds)).not.toBe(jyutping(sources.settings.lazySounds));
  expect(yale("Options")).toBe(textFor("zh").page.options);
  expect(textFor("zh").page.tryIt).toBe("試試看");
  expect(unavailableNotes(languagePage("zh")!.settings, "中文", textFor("zh")).join(" ")).not.toContain("English only.");
});

test("every localized page is free of unresolved draft markers", () => {
  for (const strings of Object.values(data)) {
    for (const result of Object.values(strings)) expect(result).not.toMatch(/^\s*F:/);
  }
});
