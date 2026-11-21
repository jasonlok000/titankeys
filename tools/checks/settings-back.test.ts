// Back at the top of Settings, opened from the playground, goes to the front page and its playground instead of closing the app
// (owner, 2026-11-21: "backing from settings shdn't quit the app. it shd go back to playground as that's the home page").
import { expect, test } from "bun:test";

const root = new URL("../../", import.meta.url);
const read = (path: string) => Bun.file(new URL(path, root)).text();

test("Ink asks the app before closing on Back with no page left, and the top of Settings answers with the front page", async () => {
  const patch = await read("tools/ink/titankeys.patch");
  expect(patch).toContain("+            if (!appBackAtRoot(this)) finish()");
  expect(patch).toContain("+internal fun appBackAtRoot(activity: MainActivity): Boolean = false");
  const adapters = await read("android/kotlin/com/vandam/ink/AppNativeAdapters.kt");
  expect(adapters).toContain("internal fun appBackAtRoot(activity: MainActivity): Boolean {");
  expect(adapters).toContain('"backToFront" -> {');
  const settings = await read("app/settings/index.tsx");
  expect(settings).toContain('useEffect(() => backToFrontPage(() => replace("/")), []);');
});
