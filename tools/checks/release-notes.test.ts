import { expect, test } from "bun:test";
import { appReleaseNotes, parseReleaseNotes, releaseNotesMarkdown, releaseNotesProblem, withBugFixes } from "../../lib/release-notes-format";
import { hasReleaseNotes, releaseNotes } from "../../lib/release-notes";

const cases: [name: string, text: string, problem: string | null][] = [
  ["ready notes", "# 1.0.2\n\n## Fixed\n- Search: It works.", null],
  ["notes with a v prefix and no section", "# v1.0.2\n- Search", null],
  ["skip", "# 1.0.2\n\nSKIP", null],
  ["another version", "# 1.0.1\n\n## Fixed\n- Search", "is for 1.0.1, but the app is 1.0.2"],
  ["no version heading", "## Fixed\n- Search", "is for no version"],
  ["a draft", "# 1.0.2\n<!-- draft: rewrite these -->\n## New\n- Search", "still a draft"],
  ["a heading without notes", "# 1.0.2\n\n## New\n", "has no notes"],
  ["a note without a dash", "# 1.0.2\n\n## Fixed\n- Search\nForgot the dash", '"Forgot the dash"'],
  ["a heading without a space", "# 1.0.2\n\n##Fixed\n- Search", '"##Fixed"'],
];

for (const [name, text, problem] of cases) {
  test(`release notes: ${name}`, () => {
    const result = releaseNotesProblem(parseReleaseNotes(text), "1.0.2");
    if (problem === null) expect(result).toBeNull();
    else expect(result).toContain(problem);
  });
}

test("release notes markdown keeps sections, titles and details", () => {
  const notes = parseReleaseNotes("# 1.0.2\n## New\n- Cursor: Hold space.\n## Fixed\n- Search");
  expect(releaseNotesMarkdown(notes)).toBe("## New\n\n- **Cursor**: Hold space.\n\n## Fixed\n\n- Search");
});

test("github notes leave out the short app section", () => {
  const notes = parseReleaseNotes("# 1.0.2\n## App\n- Cursor\n## New\n- Cursor: Hold space.\n## Fixed\n- Search");
  expect(releaseNotesMarkdown(notes)).toBe("## New\n\n- **Cursor**: Hold space.\n\n## Fixed\n\n- Search");
});

test("the app shows the short notes", () => {
  const notes = parseReleaseNotes("# 1.0.2\n## App\n- Cursor: Hold space.\n## New\n- Cursor: Hold space, then move.\n## Fixed\n- Search\n- Emoji");
  expect(appReleaseNotes(notes)).toEqual([{ title: "Cursor", detail: "Hold space." }]);
});

test("the release adds a bug fixes line that counts the fixes to the end of the app notes", () => {
  const text = "# 1.0.2\n\n## App\n- Cursor: Hold space.\n- Emoji: Push Sym.\n\n## New\n- Cursor\n\n## Fixed\n- Search\n- Emoji\n- Tap keys\n";
  expect(withBugFixes(text)).toBe("# 1.0.2\n\n## App\n- Cursor: Hold space.\n- Emoji: Push Sym.\n- Bug fixes: This update fixes 3 problems.\n\n## New\n- Cursor\n\n## Fixed\n- Search\n- Emoji\n- Tap keys\n");
});

test("one fix is one problem", () => {
  expect(withBugFixes("# 1.0.2\n## App\n- Cursor\n## Fixed\n- Search")).toBe("# 1.0.2\n## App\n- Cursor\n- Bug fixes: This update fixes 1 problem.\n## Fixed\n- Search");
});

test("a bug fixes line that is already there is counted again", () => {
  const text = "# 1.0.2\n## App\n- Bug fixes: This update fixes 9 problems.\n- Cursor\n## Fixed\n- Search\n- Emoji";
  expect(withBugFixes(text)).toBe("# 1.0.2\n## App\n- Cursor\n- Bug fixes: This update fixes 2 problems.\n## Fixed\n- Search\n- Emoji");
  expect(withBugFixes(withBugFixes(text))).toBe(withBugFixes(text));
});

test("no bug fixes line without fixes or without an app section", () => {
  expect(withBugFixes("# 1.0.2\n## App\n- Cursor\n## New\n- Cursor")).toBe("# 1.0.2\n## App\n- Cursor\n## New\n- Cursor");
  expect(withBugFixes("# 1.0.2\n## New\n- Cursor\n## Fixed\n- Search")).toBe("# 1.0.2\n## New\n- Cursor\n## Fixed\n- Search");
});

test("without an app section the app shows the new notes", () => {
  const notes = parseReleaseNotes("# 1.0.2\n## New\n- Cursor: Hold space.");
  expect(appReleaseNotes(notes)).toEqual([{ title: "Cursor", detail: "Hold space." }]);
});

test("the app shows What's new for its current version unless the notes say skip", async () => {
  const { version } = Bun.TOML.parse(await Bun.file(new URL("../../ink.toml", import.meta.url)).text()) as { version: string };
  expect(hasReleaseNotes(version)).toBe(!releaseNotes.skip);
});
