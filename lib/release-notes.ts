import text from "../release-notes.txt";
import { parseReleaseNotes } from "./release-notes-format";

export const releaseNotes = parseReleaseNotes(text);

export const releaseNotesURL = (version: string) => `https://github.com/jasonlok000/titankeys/releases/tag/v${version}`;

/** Whether there is anything to show for the installed version. */
export const hasReleaseNotes = (version: string) => releaseNotes.version === version && !releaseNotes.skip
  && releaseNotes.sections.length > 0;
