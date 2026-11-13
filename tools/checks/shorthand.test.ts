// Checks that Settings' shorthand page reaches the keyboard: its page, its calls, and the list it shows.
import { expect, test } from "bun:test";

const root = new URL("../../", import.meta.url);
const read = (path: string) => Bun.file(new URL(path, root)).text();

test("Settings opens the shorthand page, and it can add its own", async () => {
  expect(await read("app/settings/index.tsx")).toContain('href="/settings/shorthand"');
  const page = await read("app/settings/shorthand/index.tsx");
  expect(page).toContain('href="/settings/shorthand/edit"');
  expect(page).toContain("shorthandToggles");
  expect(page).toContain("removeShorthand");
  expect(await read("app/settings/shorthand/edit.tsx")).toContain("putShorthand");
});

test("the app's calls for shorthand are the ones the bridge handles", async () => {
  const lib = await read("lib/atlas.ts");
  const bridge = await read("android/kotlin/com/vandam/ink/AppNativeAdapters.kt");
  for (const operation of ["putShorthand", "removeShorthand"]) {
    expect(lib).toContain(`atlas("${operation}"`);
    expect(bridge).toContain(`"${operation}" ->`);
  }
  expect(bridge).toContain('"shorthandToggles" ->');
  for (const key of ["shorthand", "ownShorthand"]) expect(bridge).toContain(`state.put("${key}"`);
});

test("each listed shorthand fits what Settings takes as the user's own", async () => {
  const lines = (await read("android/assets/autocorrect/en_shorthand.txt")).split("\n")
    .filter(line => line.trim() && !line.startsWith("#"));
  expect(lines.length).toBeGreaterThan(50);
  const seen = new Set<string>();
  for (const line of lines) {
    const [shorthand, full] = line.split("\t");
    expect(shorthand, line).toMatch(/^[a-z0-9']{1,12}$/);
    expect(seen.has(shorthand), shorthand).toBe(false);
    seen.add(shorthand);
    if (full === undefined) continue;
    expect(full.trim().length, line).toBeGreaterThan(0);
    expect(full.length, line).toBeLessThanOrEqual(40);
  }
});
