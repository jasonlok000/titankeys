import { expect, test } from "bun:test";
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { END, parentOf } from "../../lib/settings-tree";

// Back from any page the guide's end screen opens goes back to the end screen, as it was left (owner, 2026-11-13: "When going
// back from the boring guide triggered from the end screen it should go back to the end screen. Applies to everything else for
// end screen."). Each page comes with the app's /end page under it, which opens the guide at its end again as Back uncovers it.
const root = join(import.meta.dir, "../..");
const read = (path: string) => readFileSync(join(root, path), "utf8");
const guide = read("android/kotlin/com/atlas/keyboard/GuideActivity.kt");
const constant = (name: string) => guide.match(new RegExp(`const val ${name} = "([^"]+)"`))![1];

/** Each way out of the end screen: the page it opens, and the button. Share swipes opens its own policy and comes back by itself. */
const links = [
  { page: constant("END_BORING_GUIDE"), button: "The boring guide", file: "app/end/guide-boring.tsx" },
  { page: constant("END_PLAY"), button: "Go type something", file: "app/end/play.tsx" },
];

test("the end's pages sit under /end, which has nothing under it", () => {
  expect(parentOf(END)).toBeNull();
  for (const link of links) {
    expect(link.page.startsWith(`${END}/`)).toBe(true);
    expect(parentOf(link.page)).toBe(END);
    expect(existsSync(join(root, link.file))).toBe(true);
  }
});

test("The boring guide, from the end, opens the boring guide's page under /end", () => {
  const next = guide.slice(guide.indexOf("private fun nextPressed()"), guide.indexOf("if (!passed) {", guide.indexOf("private fun nextPressed()")));
  expect(next).toContain("openFromEnd(END_BORING_GUIDE)");
  expect(read("app/end/guide-boring.tsx")).toContain('export { default } from "../settings/guide-boring";');
});

test("Go type something opens the playground under /end, whose Back goes back to the end", () => {
  const button = guide.slice(guide.indexOf("boringButton = bigButton(END_NEXT)"), guide.indexOf("againLink = bigButton("));
  expect(button).toContain("openFromEnd(END_PLAY)");
  expect(read("app/end/play.tsx")).toContain("<FrontPage fromEnd />");
  const front = read("app/index.tsx");
  expect(front).toContain('else if (action === "back") back();');
  expect(front).toContain("}, fromEnd);");
  expect(read("lib/atlas.ts")).toContain('void atlas(fromEnd ? "playgroundFromEnd" : "playground");');
  expect(guide).toContain("if (playgroundFromEnd && !settingUp) leavePlayground(PLAYGROUND_BACK)");
});

test("Share swipes opens its policy over the end, which comes back by itself", () => {
  expect(guide).toContain("private fun openShare() = startActivityForResult(ShareDisclosureActivity.intent(this), SHARE_REQUEST)");
});

test("/end opens the guide at its end again only once Back uncovers it, as it was left", () => {
  const end = read("app/end/index.tsx");
  // Ink keeps a covered page's effects off, so the effect runs as Back uncovers the page.
  expect(end).toContain("void openGuideEnd();");
  expect(end).toContain('replace("/")');
  expect(read("android/kotlin/com/vandam/ink/AppNativeAdapters.kt")).toContain("GuideActivity.startAtEnd(activity)");
  expect(guide).toContain("if (opensAtEnd) {\n            showEndAgain()");
  expect(guide).toContain("if (settled) finaleView.settle() else finaleView.play()");
});

test("the end opens no other page of the app", () => {
  const pages = [...guide.matchAll(/Connected\.pageIntent\(this, (\w+|"[^"]*")\)/g)].map(match => match[1]);
  // The playground's own Guide button is the one other page link, and it's the playground's, not the end's.
  expect(pages.sort()).toEqual(["BORING_GUIDE", "page"].sort());
});
