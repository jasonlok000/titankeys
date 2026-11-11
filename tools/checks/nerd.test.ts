// Checks the boring guide's nerd screen: it shows the first time only, and the app remembers that until its data is cleared.
import { expect, test } from "bun:test";
import { nerdSplash } from "../../lib/nerd";

const root = new URL("../../", import.meta.url);
const text = (path: string) => Bun.file(new URL(path, root)).text();

test("the nerd screen shows the first time only", () => {
  expect(nerdSplash(false)).toBe(true);
  expect(nerdSplash(true)).toBe(false);
});

test("the boring guide marks the nerd screen as seen, and only then shows the guide", async () => {
  const page = await text("app/settings/guide-boring.tsx");
  expect(page).toContain("We know you're a nerd.");
  // A big centred splash, not a page title that cuts the line short.
  expect(page).toContain("<Screen centered");
  expect(page).toContain("markNerdSeen()");
  expect(page).toContain("nerdSplash(result.data.nerdSeen)");
});

test("the app keeps the flag in its own preferences, which only clearing its data removes", async () => {
  const native = await text("android/kotlin/com/vandam/ink/AppNativeAdapters.kt");
  expect(native).toContain('.put("nerdSeen", appPreferences().getBoolean(NERD_SEEN, false))');
  expect(native).toContain('"nerdSeen" -> {');
  expect(native).toContain("putBoolean(NERD_SEEN, true)");
});
