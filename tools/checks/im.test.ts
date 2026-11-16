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

test("every input method's script has a header in Settings > Input methods, not its four-letter code", () => {
  const block = read("app/settings/languages.tsx").split("const SCRIPTS", 2)[1]!.split("};", 1)[0]!;
  const headers = new Set([...block.matchAll(/(\w{4}): "[^"]+"/g)].map(m => m[1]));
  for (const im of ims) expect({ id: im.id, script: im.script, header: headers.has(im.script) })
    .toEqual({ id: im.id, script: im.script, header: true });
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
    const cldr = im.engine ? true : notice.includes("CLDR");
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
