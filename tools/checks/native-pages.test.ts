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
    for (const source of [sources.page.tryIt, sources.page.typeHere, sources.page.how, sources.page.trySample, sources.settings.keyPreview]) {
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
  const shared = textFor("zh").points.chinese;
  for (const [index, source] of sources.points.chinese.entries()) expect(shared[index]).not.toBe(source);
  expect(textFor("zh").page.tryIt).toBe("試試看");
  expect(unavailableNotes(languagePage("zh")!.settings, "中文", textFor("zh")).join(" ")).not.toContain("English only.");
});

test("every localized page is free of unresolved draft markers", () => {
  for (const strings of Object.values(data)) {
    for (const result of Object.values(strings)) expect(result).not.toMatch(/^\s*F:/);
  }
});

test("each method has local copy for its actual key legend, role descriptions and instructions", () => {
  const dictionaries: Record<string, Record<string, string>> = data;
  const roles: Record<string, Record<string, string>> = sources.roles;
  const points: Record<string, string[]> = sources.points;
  for (const method of methods) {
    const language = method.language.id;
    if (language === "en") continue;
    const base = dictionaries[language];
    const overrides = dictionaries[method.id] ?? {};
    const guide = method.guide;
    const visible = [
      ...guide.points.length ? guide.points : (points[guide.family] ?? []),
      ...guide.keys.roles.map(role => roles[role.key][role.role]),
      guide.keys.legend,
    ].filter(Boolean);
    for (const source of visible) {
      expect({ id: method.id, source, translated: !!(overrides[source] ?? base[source]) })
        .toEqual({ id: method.id, source, translated: true });
    }
  }
});
