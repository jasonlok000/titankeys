import { expect, test } from "bun:test";
import { appReleaseNotes, parseReleaseNotes, releaseNotesMarkdown, releaseNotesProblem } from "../../lib/release-notes-format";
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

test("the app shows the short notes and one row for all fixes", () => {
  const notes = parseReleaseNotes("# 1.0.2\n## App\n- Cursor: Hold space.\n## New\n- Cursor: Hold space, then move.\n## Fixed\n- Search\n- Emoji");
  expect(appReleaseNotes(notes)).toEqual({ notes: [{ title: "Cursor", detail: "Hold space." }], fixes: true });
});

test("without an app section the app shows the new notes", () => {
  const notes = parseReleaseNotes("# 1.0.2\n## New\n- Cursor: Hold space.");
  expect(appReleaseNotes(notes)).toEqual({ notes: [{ title: "Cursor", detail: "Hold space." }], fixes: false });
});

test("the app shows What's new for its current version unless the notes say skip", async () => {
  const { version } = Bun.TOML.parse(await Bun.file(new URL("../../ink.toml", import.meta.url)).text()) as { version: string };
  expect(hasReleaseNotes(version)).toBe(!releaseNotes.skip);
});
