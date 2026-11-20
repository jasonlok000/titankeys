// The support page as it ships (owner, 2026-11-20): the WebView draws without a software layer and without debug switches,
// fades never animate opacity itself, and the board lists only names ("if they are anonymous they don't get on the board").
import { expect, test } from "bun:test";

const root = new URL("../../", import.meta.url);
const read = (path: string) => Bun.file(new URL(path, root)).text();

test("the support WebView keeps its own drawing, with no debug switches", async () => {
  const activity = await read("android/kotlin/com/titankeys/support/SupportWebActivity.kt");
  expect(activity).not.toContain("setLayerType(");
  expect(activity).not.toContain("SystemProperties");
  expect(activity).toContain("web = WebView(this)");
});

test("no transition or animation on the page moves opacity or transform directly", async () => {
  const page = await read("android/assets/support/index.html");
  // The cups' fill is an SVG shape, which Chromium never gives a layer of its own, so its scaleY may move.
  const style = page.slice(page.indexOf("<style>"), page.indexOf("</style>"))
    .replace("transform-origin: 50% 100%; transition: transform .7s", "");
  expect(style).not.toMatch(/transition:[^;}]*\b(opacity|transform)\b/);
  expect(style).not.toMatch(/@keyframes [\w-]+ \{[^}]*transform:/);
  expect(style).toContain('@property --fade { syntax: "<number>"');
});

test("the board lists only names", async () => {
  const worker = await read("server/support/src/worker.js");
  expect(worker).toContain("WHERE s.name IS NOT NULL");
  const page = await read("android/assets/support/index.html");
  expect(page).toContain('$("#optin [data-act=add]").disabled = !name.value.trim();');
  expect(page).not.toContain('placeholder="Anonymous"');
  expect(await read("app/support.tsx")).toContain("disabled={saving || !name.trim()}");
});
