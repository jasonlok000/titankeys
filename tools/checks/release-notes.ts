// Stops a build or release whose release-notes.txt isn't written for the current version, and writes the keyboard's list of
// versions and summaries (android/assets/release-summaries.txt) from release-notes.txt and release-history.txt (#121).
import { parseReleaseHistory, parseReleaseNotes, releaseNotesProblem, releaseSummaries } from "../../lib/release-notes-format";

const root = new URL("../../", import.meta.url);
const { version } = Bun.TOML.parse(await Bun.file(new URL("ink.toml", root)).text()) as { version: string };
const file = Bun.file(new URL("release-notes.txt", root));
const notes = await file.exists() ? parseReleaseNotes(await file.text()) : null;
const problem = notes
  ? releaseNotesProblem(notes, version)
  : `release-notes.txt is missing. Write the notes for ${version}, starting with "# ${version}".`;
if (problem || !notes) {
  console.error(`Release notes: ${problem}`);
  process.exit(1);
}
const history = parseReleaseHistory(await Bun.file(new URL("release-history.txt", root)).text());
const summaries = Bun.file(new URL("android/assets/release-summaries.txt", root));
const text = releaseSummaries([notes, ...history]);
if (!(await summaries.exists()) || await summaries.text() !== text) {
  await Bun.write(summaries, text);
  console.log("Release notes: wrote android/assets/release-summaries.txt. Commit it.");
}
