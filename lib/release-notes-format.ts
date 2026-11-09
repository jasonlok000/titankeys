/**
 * release-notes.txt holds the notes for the current version. The app shows a short version after an
 * update and the release workflow publishes the full notes on GitHub. The format:
 *
 *   # 1.0.2
 *
 *   ## App
 *   - Space cursor: Move the cursor with the space bar.
 *
 *   ## New
 *   - Move the cursor with space: Rest your finger lightly on the space bar, then move it left or right.
 *
 *   ## Fixed
 *   - Launcher search: The suggestion bar stays hidden on launchers like Niagara.
 *
 * The app shows the App section, or the New section when there is no App section. GitHub gets every
 * section except App. Do not write a "Bug fixes" line: the release workflow adds it to the end of the
 * App section with the number of notes in the Fixed section.
 * Writing `skip` instead of sections releases the version without notes.
 */

export type ReleaseNote = { title: string; detail?: string };
export type ReleaseSection = { title: string; notes: ReleaseNote[] };
export type ReleaseNotes = {
  version: string; skip: boolean; draft: boolean; sections: ReleaseSection[];
  /** Lines that are neither a heading nor a note, which would otherwise vanish from the release. */
  unknown: string[];
};

/** Left in a new draft so a release cannot ship before the notes are written. */
export const DRAFT_MARKER = "<!-- draft:";

export function parseReleaseNotes(text: string): ReleaseNotes {
  const draft = text.includes(DRAFT_MARKER);
  const lines = text.replace(/<!--[\s\S]*?-->/g, "").split("\n").map(line => line.trim()).filter(Boolean);
  const version = lines[0]?.match(/^#\s+v?(\S+)$/)?.[1] ?? "";
  const body = lines.slice(1);
  const skip = body.length === 1 && body[0].toLowerCase() === "skip";
  const sections: ReleaseSection[] = [];
  const unknown: string[] = [];
  for (const line of skip ? [] : body) {
    const heading = line.match(/^##\s+(.+)$/);
    if (heading) {
      sections.push({ title: heading[1], notes: [] });
      continue;
    }
    const item = line.match(/^[-*]\s+(.+)$/);
    if (!item) {
      unknown.push(line);
      continue;
    }
    if (sections.length === 0) sections.push({ title: "Changes", notes: [] });
    const separator = item[1].indexOf(": ");
    sections[sections.length - 1].notes.push(separator > 0
      ? { title: item[1].slice(0, separator).trim(), detail: item[1].slice(separator + 2).trim() }
      : { title: item[1] });
  }
  return { version, skip, draft, sections: sections.filter(section => section.notes.length > 0), unknown };
}

const APP = "app";
const NEW = "new";
const FIXED = "fixed";
const named = (notes: ReleaseNotes, name: string) => notes.sections.find(section => section.title.toLowerCase() === name);

/** The short notes that the app shows. */
export function appReleaseNotes(notes: ReleaseNotes): ReleaseNote[] {
  return (named(notes, APP) ?? named(notes, NEW))?.notes ?? [];
}

const isBugFixes = (line: string) => /^[-*]\s+bug fixes(:|$)/i.test(line.trim());

/**
 * The notes with a "Bug fixes" line at the end of the App section that counts the Fixed notes. The release
 * workflow writes this into release-notes.txt before it builds the app. Any older "Bug fixes" line goes.
 */
export function withBugFixes(text: string): string {
  const fixes = named(parseReleaseNotes(text), FIXED)?.notes.length ?? 0;
  const lines = text.split("\n").filter(line => !isBugFixes(line));
  const start = lines.findIndex(line => /^##\s+app\s*$/i.test(line.trim()));
  if (start < 0 || fixes === 0) return lines.join("\n");
  let last = start;
  for (let i = start + 1; i < lines.length && !/^#/.test(lines[i].trim()); i++)
    if (/^[-*]\s+/.test(lines[i].trim())) last = i;
  lines.splice(last + 1, 0, `- Bug fixes: This update fixes ${fixes} ${fixes === 1 ? "problem" : "problems"}.`);
  return lines.join("\n");
}

/** Why these notes cannot ship with `version`, or null when they are ready. */
export function releaseNotesProblem(notes: ReleaseNotes, version: string): string | null {
  if (notes.version !== version)
    return `release-notes.txt is for ${notes.version || "no version"}, but the app is ${version}. Write the notes for ${version}, starting with "# ${version}".`;
  if (notes.draft) return "release-notes.txt is still a draft. Rewrite the notes, then delete the draft line.";
  if (notes.unknown.length)
    return `release-notes.txt has lines that are not a "## " heading or a "- " note: ${notes.unknown.map(line => JSON.stringify(line)).join(", ")}.`;
  if (!notes.skip && notes.sections.length === 0)
    return `release-notes.txt has no notes. Add some, or write "skip" under "# ${version}" to release without them.`;
  return null;
}

/** The notes as GitHub release markdown, without the version heading. */
export function releaseNotesMarkdown(notes: ReleaseNotes): string {
  return notes.sections.filter(section => section.title.toLowerCase() !== APP).map(section => `## ${section.title}\n\n` + section.notes
    .map(note => note.detail ? `- **${note.title}**: ${note.detail}` : `- ${note.title}`).join("\n")).join("\n\n");
}
