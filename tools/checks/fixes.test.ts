// Checks that Settings' What gets fixed page has a switch for every kind of fix in FixKind.kt, and that its switches reach the keyboard.
import { expect, test } from "bun:test";
import { fixKinds } from "../../lib/fixes";

const root = new URL("../../", import.meta.url);
const read = (path: string) => Bun.file(new URL(path, root)).text();

test("the page lists every kind of fix in FixKind.kt, in its order", async () => {
  const source = await read("android/kotlin/com/atlas/autocorrect/FixKind.kt");
  const native = [...source.matchAll(/^\s+[A-Z_]+\("(\w+)"\)[,;]/gm)].map(match => match[1]);
  expect(native.length).toBeGreaterThan(10);
  expect(fixKinds.map(({ id }) => id)).toEqual(native);
  for (const { id, label, example } of fixKinds) {
    expect(label.length, id).toBeLessThanOrEqual(34);
    expect(example, id).toContain("→");
  }
});

test("Settings opens the page, and its switches are ones the bridge handles", async () => {
  expect(await read("app/settings/index.tsx")).toContain('href="/settings/fixes"');
  const page = await read("app/settings/fixes.tsx");
  expect(page).toContain('href="/settings/shorthand"');
  const bridge = await read("android/kotlin/com/vandam/ink/AppNativeAdapters.kt");
  for (const key of ["fixToggles", "fixOnlyWhenSure"]) {
    expect(page).toContain(key);
    expect(bridge).toContain(`"${key}" ->`);
    expect(bridge).toContain(`.put("${key}"`);
  }
});
