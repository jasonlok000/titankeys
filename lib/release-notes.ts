import text from "../release-notes.txt";
import history from "../release-history.txt";
import { compareVersions, parseReleaseHistory, parseReleaseNotes, type ReleaseNotes } from "./release-notes-format";

export const releaseNotes = parseReleaseNotes(text);

/** Every version's notes that ship in the app, newest first: the current one, then release-history.txt. */
export const allReleaseNotes: ReleaseNotes[] = [releaseNotes, ...parseReleaseHistory(history)];

/** The versions newer than `from`, up to the current one, newest first, with notes to show (#121). */
export const notesSince = (from: string | null | undefined): ReleaseNotes[] => allReleaseNotes
  .filter((notes, index) => index === 0 || (from != null && compareVersions(notes.version, from) > 0))
  .filter(notes => !notes.skip && notes.sections.length > 0);

export const releaseNotesURL = (version: string) => `https://github.com/jasonlok000/titankeys/releases/tag/v${version}`;

/** Whether there is anything to show for the installed version. */
export const hasReleaseNotes = (version: string) => releaseNotes.version === version && !releaseNotes.skip
  && releaseNotes.sections.length > 0;
