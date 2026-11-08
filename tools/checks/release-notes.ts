// Stops a build or release whose release-notes.txt isn't written for the current version.
import { parseReleaseNotes, releaseNotesProblem } from "../../lib/release-notes-format";

const root = new URL("../../", import.meta.url);
const { version } = Bun.TOML.parse(await Bun.file(new URL("ink.toml", root)).text()) as { version: string };
const file = Bun.file(new URL("release-notes.txt", root));
const problem = await file.exists()
  ? releaseNotesProblem(parseReleaseNotes(await file.text()), version)
  : `release-notes.txt is missing. Write the notes for ${version}, starting with "# ${version}".`;
if (problem) {
  console.error(`Release notes: ${problem}`);
  process.exit(1);
}
