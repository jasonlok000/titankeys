// Checks that Settings > English spelling offers every spelling the keyboard knows, saved as the keyboard reads it.
import { expect, test } from "bun:test";

const root = new URL("../../", import.meta.url);
const read = (path: string) => Bun.file(new URL(path, root)).text();

/** The keyboard's spellings, as EnglishSpelling.Variant saves them: "british", "american", "canadian". */
async function variants() {
  const found = (await read("android/kotlin/com/atlas/engine/EnglishSpelling.java")).match(/enum Variant \{\s*([A-Z_,\s]+);/);
  expect(found).not.toBeNull();
  return found![1].split(",").map(name => name.trim().toLowerCase());
}

test("the keyboard knows British, American and Canadian", async () => {
  expect(await variants()).toEqual(["british", "american", "canadian"]);
});

test("Settings offers each spelling the keyboard knows", async () => {
  const atlas = await read("lib/atlas.ts");
  const page = await read("app/settings/spelling.tsx");
  for (const variant of await variants()) {
    expect(atlas).toMatch(new RegExp(`export type Spelling = [^;]*"${variant}"`));
    expect(page).toContain(`"${variant}"`);
  }
});

test("the default stays American", async () => {
  expect(await read("android/kotlin/com/atlas/engine/TypingSettings.java"))
    .toContain('getString("spelling", "american")');
});
