import { expect, test } from "bun:test";
import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";

// Every input method in android/assets/im is whole: its file has what docs/languages.md says, its label is its own, it names
// its sources, and Android's keyboard list has one entry for it with a fixed id. tests/languages/run.sh checks the word lists'
// letters on the JVM, with the keyboard's own reader.
const root = join(import.meta.dir, "../..");
const read = (path: string) => readFileSync(join(root, path), "utf8");
const folder = "android/assets/im";
const ids = readdirSync(join(root, folder)).filter(name => statSync(join(root, folder, name)).isDirectory()).sort();
const ALLOWED = /Apache License 2\.0|CC BY 4\.0|CC BY-SA 4\.0|LGPL|MIT|BSD|CC0|Unicode License|GPL-3\.0/;

type Im = {
  id: string; name: string; nameEnglish: string; label: string; locale: string; script: string;
  keys: "qwerty" | Record<string, string>; accents: Record<string, string>; words: string | null;
  variants: { id: string; name: string; words: string }[] | null; engine: unknown;
};
const ims: Im[] = ids.map(id => JSON.parse(read(`${folder}/${id}/im.json`)));

test("each input method's file has every field, and its folder is its id", () => {
  for (const im of ims) {
    expect(ids).toContain(im.id);
    expect({ id: im.id, ok: /^[a-z]{2,3}(-[a-z0-9]{2,8})*$/.test(im.id) }).toEqual({ id: im.id, ok: true });
    for (const field of ["name", "nameEnglish", "label", "locale", "script"] as const) expect(typeof im[field]).toBe("string");
    expect([...im.label].length).toBeLessThanOrEqual(3);
    if (im.keys !== "qwerty") expect(Object.keys(im.keys).sort().join("")).toBe("abcdefghijklmnopqrstuvwxyz");
    for (const [key, held] of Object.entries(im.accents)) {
      expect(key).toMatch(/^[a-z]$/);
      expect([...held].length).toBeGreaterThan(0);
      expect([...held].length).toBeLessThanOrEqual(10);
    }
    for (const words of [im.words, ...(im.variants ?? []).map(v => v.words)].filter(Boolean))
      expect({ id: im.id, words, there: existsSync(join(root, folder, im.id, words!)) }).toEqual({ id: im.id, words, there: true });
  }
});

type Lang = { id: string; name: string; nameEnglish: string; label: string; script: string; experimental: boolean };
const indexed: { id: string; name: string; language: Lang }[] = JSON.parse(read(`${folder}/index.json`));

test("every language's script has a header in Settings > Languages, not its four-letter code", () => {
  const block = read("app/settings/languages.tsx").split("const SCRIPTS", 2)[1]!.split("};", 1)[0]!;
  const headers = new Set([...block.matchAll(/(\w{4}): "[^"]+"/g)].map(m => m[1]));
  for (const { language } of indexed) expect({ id: language.id, script: language.script, header: headers.has(language.script) })
    .toEqual({ id: language.id, script: language.script, header: true });
});

test("each input method has a language: one per language, Serbian and Chinese grouping theirs, all but Chinese experimental", () => {
  const byId = new Map<string, Lang>();
  for (const { id, language } of indexed) {
    for (const field of ["id", "name", "nameEnglish", "label", "script"] as const) expect({ id, field, text: typeof language[field] })
      .toEqual({ id, field, text: "string" });
    // A language is the same wherever it's given, and not experimental only for Chinese (owner, 2026-11-16).
    expect(byId.get(language.id) ?? language).toEqual(language);
    byId.set(language.id, language);
    expect({ id, experimental: language.experimental }).toEqual({ id, experimental: language.id !== "zh" });
  }
  const methods = (language: string) => indexed.filter(e => e.language.id === language).map(e => e.id).sort();
  expect(methods("sr")).toEqual(["sr", "sr-latn"]);
  expect(methods("zh")).toEqual(["yue-jyutping", "yue-yale", "zh-pinyin"]);
  expect(new Set([...byId.values()].map(l => l.label)).size).toBe(byId.size);
});

test("no two input methods share a label, and none takes English's", () => {
  const labels = ims.map(im => im.label);
  expect(new Set(labels).size).toBe(labels.length);
  expect(labels).not.toContain("EN");
});

test("each names where its letters and words come from, under a licence that can ship in TitanKeys", () => {
  for (const im of ims) {
    const notice = read(`${folder}/${im.id}/NOTICE`);
    // An engine's input method takes its letters from the engine (Rime's schemas, credited in native/rime/SOURCES.md), not CLDR.
    // Thai's come from its national standard, Kedmanee (TIS 820-2538), as xkeyboard-config gives it.
    const cldr = im.engine ? true : notice.includes("CLDR") || notice.includes("TIS 820-2538");
    expect({ id: im.id, allowed: ALLOWED.test(notice), cldr }).toEqual({ id: im.id, allowed: true, cldr: true });
  }
});

test("the index lists every input method, and Android's keyboard list has English and each one, with fixed ids", () => {
  const index: { id: string; label: string }[] = JSON.parse(read(`${folder}/index.json`));
  expect(index.map(e => e.id).sort()).toEqual(ids);
  const list = read("android/src/main/res/xml/input_method.xml");
  const strings = read("android/src/main/res/values/strings_im.xml");
  const extras = [...list.matchAll(/imeSubtypeExtraValue="im=([^"]+)"/g)].map(m => m[1]);
  expect(extras[0]).toBe("en");
  expect(extras.slice(1).sort()).toEqual(ids);
  const subtypeIds = [...list.matchAll(/subtypeId="(-?\d+)"/g)].map(m => m[1]);
  expect(subtypeIds.length).toBe(ids.length + 1);
  expect(new Set(subtypeIds).size).toBe(subtypeIds.length);
  expect(strings).toContain('<string name="im_en" translatable="false">English</string>');
  for (const id of ids) expect(strings).toContain(`name="im_${id.replace(/-/g, "_")}"`);
});

test("each entry in Android's keyboard list is whole: an id of its own, a valid locale that is its input method's, a label", () => {
  // Android keeps the enabled entries by id (its hash), so an id shared or out of range would lose one; a locale it can't
  // read would show the entry under the wrong language.
  const list = read("android/src/main/res/xml/input_method.xml");
  const strings = read("android/src/main/res/values/strings_im.xml");
  const entries = [...list.matchAll(/<subtype ([^>]*)\/>/g)].map(m => Object.fromEntries([...m[1]!.matchAll(/android:(\w+)="([^"]*)"/g)]
    .map(a => [a[1]!, a[2]!])));
  const locales = new Map(ims.map(im => [im.id, im.locale]));
  locales.set("en", "en-US");
  for (const e of entries) {
    const id = e.imeSubtypeExtraValue?.replace(/^im=/, "") ?? "";
    const subtypeId = Number(e.subtypeId);
    const tag = e.languageTag ?? "";
    const label = new RegExp(`<string name="${(e.label ?? "").replace("@string/", "")}"[^>]*>([^<]+)</string>`).exec(strings)?.[1];
    expect({
      id, mode: e.imeSubtypeMode, idInRange: Number.isInteger(subtypeId) && subtypeId !== 0 && Math.abs(subtypeId) < 2 ** 31,
      tagValid: Intl.getCanonicalLocales(tag)[0] === tag, localeIsTag: e.imeSubtypeLocale?.replace(/_/g, "-") === tag,
      sameLanguage: tag.split("-")[0] === locales.get(id)?.split("-")[0], label: Boolean(label?.trim()),
    }).toEqual({ id, mode: "keyboard", idInRange: true, tagValid: true, localeIsTag: true, sameLanguage: true, label: true });
  }
  expect(new Set(entries.map(e => e.subtypeId)).size).toBe(entries.length);
  expect(new Set(entries.map(e => e.imeSubtypeExtraValue)).size).toBe(entries.length);
});
