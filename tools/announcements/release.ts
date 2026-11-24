// The release step for the News (#121): `bun tools/announcements/release.ts <version>` writes the two entries that tell people
// on older versions about <version>, one for Play and one for GitHub, into announcements/entries.json, from the summary under
// "# <version>" in release-notes.txt (or release-history.txt). Older release entries go: only the latest release matters.
// It never signs or publishes: signing stays a step on the build machine (announcements/README.md).
import { spawnSync } from "node:child_process";
import { compareVersions, parseReleaseHistory, type ReleaseNotes } from "../../lib/release-notes-format";
import { type Entry, problems } from "./feed";

export const PLAY_URL = "https://play.google.com/store/apps/details?id=com.titankeys";
export const GET_URL = "https://everyones-niche.com/titankeys/#get";
const DAY_MILLIS = 24 * 60 * 60 * 1000;
const SHOW_DAYS = 30;

const utc = (millis: number) => new Date(millis).toISOString().replace(/\.\d{3}Z$/, "Z");

/** The highest version below `version`, so that every older version matches `maxVersion` (Feed.applies compares with <=). */
export function previousVersion(version: string, known: string[]): string | null {
  return known.filter(other => compareVersions(other, version) < 0)
    .sort((a, b) => compareVersions(b, a))[0] ?? null;
}

/** The release entries for `version`, in place of every older `release-*` entry. */
export function releaseEntries(entries: Entry[], version: string, summary: string, previous: string, now: number): Entry[] {
  if (!summary) throw new Error(`No summary for ${version}: write a "> " line under "# ${version}" in release-notes.txt.`);
  const id = `release-${version.replaceAll(".", "-")}`;
  const title = `TitanKeys ${version} is out`;
  const from = utc(now);
  const until = utc(now + SHOW_DAYS * DAY_MILLIS);
  const sentence = /[.!?]$/.test(summary) ? summary : `${summary}.`;
  const release: Entry[] = [
    { id: `${id}-play`, title, text: `${sentence} Auto-updates off? Update it in Play.`,
      action: { label: "Open Play", url: PLAY_URL }, from, until, maxVersion: previous, builds: ["play"], notify: true },
    { id: `${id}-github`, title, text: `${sentence} Get it from Google Play and updates come by themselves.`,
      action: { label: "How to get it", url: GET_URL }, from, until, maxVersion: previous, builds: ["github"], notify: true },
  ];
  const result = [...release, ...entries.filter(entry => !entry.id.startsWith("release-"))];
  const found = problems({ version: 1, issued: from, entries: result });
  if (found.length) throw new Error(`The entries would not be valid:\n- ${found.join("\n- ")}`);
  return result;
}

if (import.meta.main) {
  const version = process.argv[2];
  if (!version || !/^\d+\.\d+\.\d+$/.test(version)) {
    console.error("Usage: bun tools/announcements/release.ts <version>, such as 3.0.3");
    process.exit(1);
  }
  const root = new URL("../../", import.meta.url);
  const read = (path: string) => Bun.file(new URL(path, root)).text();
  const notes: ReleaseNotes[] = parseReleaseHistory(`${await read("release-notes.txt")}\n${await read("release-history.txt")}`);
  const summary = notes.find(item => item.version === version)?.summary ?? "";
  const tags = spawnSync("git", ["tag", "--list", "v*"], { encoding: "utf8", cwd: root.pathname.replace(/^\/([A-Za-z]:)/, "$1") })
    .stdout?.split("\n").map(tag => tag.trim().replace(/^v/, "")).filter(tag => /^\d+(\.\d+)*$/.test(tag)) ?? [];
  const previous = previousVersion(version, [...notes.map(item => item.version), ...tags]);
  if (!previous) {
    console.error(`No version older than ${version} found in the notes or the tags.`);
    process.exit(1);
  }
  const file = new URL("announcements/entries.json", root);
  const source = await Bun.file(file).json() as { entries: Entry[] };
  const entries = releaseEntries(source.entries, version, summary, previous, Date.now());
  await Bun.write(file, JSON.stringify({ ...source, entries }, null, 2) + "\n");
  console.log(`Wrote release-${version.replaceAll(".", "-")}-play and -github, for ${previous} and older, to announcements/entries.json.`);
  console.log("Next: sign it on the build machine and publish the site (announcements/README.md).");
}
