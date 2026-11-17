// Checks that Settings > English spelling offers every spelling the keyboard knows, saved as the keyboard reads it.
import { expect, test } from "bun:test";

const root = new URL("../../", import.meta.url);
const read = (path: string) => Bun.file(new URL(path, root)).text();

/** The keyboard's spellings, as EnglishSpelling.Variant saves them: "british", "american", "canadian". */
async function variants() {
  const found = (await read("android/kotlin/com/titankeys/engine/EnglishSpelling.java")).match(/enum Variant \{\s*([A-Z_,\s]+);/);
  expect(found).not.toBeNull();
  return found![1].split(",").map(name => name.trim().toLowerCase());
}

test("the keyboard knows British, American and Canadian", async () => {
  expect(await variants()).toEqual(["british", "american", "canadian"]);
});

test("Settings offers each spelling the keyboard knows", async () => {
  const lib = await read("lib/titankeys.ts");
  const page = await read("app/settings/spelling.tsx");
  for (const variant of await variants()) {
    expect(lib).toMatch(new RegExp(`export type Spelling = [^;]*"${variant}"`));
    expect(page).toContain(`"${variant}"`);
  }
});

test("the default stays American", async () => {
  expect(await read("android/kotlin/com/titankeys/engine/TypingSettings.java"))
    .toContain('getString("spelling", "american")');
});

test("Settings lists American first, and says American when it can't tell (#80)", async () => {
  const page = await read("app/settings/spelling.tsx");
  expect(page).toContain('(["american", "british", "canadian"] as const)');
  expect(page).toContain('?? "American"');
});

test("Settings' Languages sits between Typing and Shortcuts, and English spelling is on English's page", async () => {
  const settings = await read("app/settings/index.tsx");
  const typing = settings.indexOf('<Section title="Typing">');
  const languages = settings.indexOf('href="/settings/languages"');
  const shortcuts = settings.indexOf('<Section title="Shortcuts">');
  expect(typing).toBeLessThan(languages);
  expect(languages).toBeLessThan(shortcuts);
  const page = await read("app/settings/languages/language.tsx");
  expect(page).toMatch(/language\.id === "en" && [^]*href="\/settings\/spelling"/);
  expect(page).toMatch(/language\.id === "zh" && [^]*href="\/settings\/chinese"/);
});
