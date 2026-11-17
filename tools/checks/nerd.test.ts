// The boring guide opens straight away: no full-screen nerd screen before it (owner, 2026-11-16: "Remove the full screen popup
// nerd for the boring guide").
import { expect, test } from "bun:test";
import { existsSync } from "node:fs";

const root = new URL("../../", import.meta.url);
const text = (path: string) => Bun.file(new URL(path, root)).text();

test("the boring guide has no nerd screen, and nothing keeps one", async () => {
  const page = await text("app/settings/guide-boring.tsx");
  expect(page).not.toContain("We know you're a nerd.");
  expect(page).not.toContain("<Screen centered");
  expect(page).not.toContain("splash");
  expect(existsSync(new URL("lib/nerd.ts", root))).toBe(false);
  expect(await text("lib/titankeys.ts")).not.toContain("nerdSeen");
  expect(await text("android/kotlin/com/vandam/ink/AppNativeAdapters.kt")).not.toContain("NERD_SEEN");
  // The boring guide itself is still the page.
  expect(page).toContain('<Screen title="The boring guide" subtitle="Every feature and every setting, in words. Enjoy, nerd.">');
});
