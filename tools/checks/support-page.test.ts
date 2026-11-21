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

test("the page's first messages wait for its start, and no screen waits for an answer for good", async () => {
  // Blank 6 opens in 15 (2026-11-20): the board's request reached the main thread before onPageStarted, and was dropped.
  const activity = await read("android/kotlin/com/titankeys/support/SupportWebActivity.kt");
  expect(activity).toContain("if (early.size < EARLY_MAX) early.addLast(message)");
  expect(activity.match(/takeEarly\(\)/g)?.length).toBeGreaterThanOrEqual(3);
  const page = await read("android/assets/support/index.html");
  expect(page).not.toMatch(/board = await api\.board\(\)|state = await api\.state\(\)/);
  expect(page).toContain("board = await within(api.board(), OFFLINE_BOARD)");
});

test("the support page makes its decor before it asks for the insets controller", async () => {
  // Before setContentView the decor may not exist, and the page crashed as it opened (dropbox, 2026-11-20).
  const activity = await read("android/kotlin/com/titankeys/support/SupportWebActivity.kt");
  const decor = activity.indexOf("window.decorView\n");
  expect(decor).toBeGreaterThan(0);
  expect(decor).toBeLessThan(activity.indexOf("window.insetsController"));
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

test("the docks' fade keeps its size: it changes colour, never opacity", async () => {
  const page = await read("android/assets/support/index.html");
  const rule = page.slice(page.indexOf(".dock::before, .boardbar::before {"));
  const body = rule.slice(0, rule.indexOf("}"));
  expect(body).toContain("var(--cover)");
  expect(body).not.toContain("opacity");
  expect(page).toContain(".dock.covering::before, .boardbar.covering::before { --cover: #000; }");
});

test("the board lists only names", async () => {
  const worker = await read("server/support/src/worker.js");
  expect(worker).toContain("WHERE s.name IS NOT NULL");
  const page = await read("android/assets/support/index.html");
  expect(page).toContain('$("#optin [data-act=add]").disabled = !name.value.trim();');
  expect(page).not.toContain('placeholder="Anonymous"');
  expect(await read("app/support.tsx")).toContain("disabled={saving || !name.trim()}");
});
