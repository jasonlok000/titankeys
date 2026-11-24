import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { GET_URL, PLAY_URL, previousVersion, releaseEntries } from "../announcements/release";
import { problems, type Entry } from "../announcements/feed";
import { parseReleaseHistory, parseReleaseNotes, releaseSummaries, SUMMARY_MAX } from "../../lib/release-notes-format";
import { notesSince } from "../../lib/release-notes";

// #121: the release step for the News, and the summaries that the keyboard's bar and What's new read.
const root = join(import.meta.dir, "../..");
const read = (path: string) => readFileSync(join(root, path), "utf8");
const now = Date.parse("2026-12-01T10:00:00Z");
const other: Entry = { id: "test-2026-11-10", title: "Test", text: "Testing.", builds: ["debug"] };
const old: Entry = { id: "release-3-0-2-play", title: "TitanKeys 3.0.2 is out", text: "Old.", builds: ["play"] };

test("the release entries: ids, builds, texts, actions, times and maxVersion", () => {
  const entries = releaseEntries([old, other], "3.0.3", "Sym can stay open.", "3.0.2", now);
  expect(entries.map(entry => entry.id)).toEqual(["release-3-0-3-play", "release-3-0-3-github", "test-2026-11-10"]);
  const [play, github] = entries;
  expect(play.builds).toEqual(["play"]);
  expect(github.builds).toEqual(["github"]);
  expect(play.title).toBe("TitanKeys 3.0.3 is out");
  expect(github.title).toBe(play.title);
  expect(play.text).toBe("Sym can stay open. Auto-updates off? Update it in Play.");
  expect(github.text).toBe("Sym can stay open. Get it from Google Play and updates come by themselves.");
  expect(play.action).toEqual({ label: "Open Play", url: PLAY_URL });
  expect(github.action).toEqual({ label: "How to get it", url: GET_URL });
  for (const entry of [play, github]) {
    expect(entry.maxVersion).toBe("3.0.2");
    expect(entry.minVersion).toBeUndefined();
    expect(entry.from).toBe("2026-12-01T10:00:00Z");
    expect(entry.until).toBe("2026-12-31T10:00:00Z");
    expect(entry.notify).toBe(true);
  }
  expect(problems({ version: 1, issued: "2026-12-01T10:00:00Z", entries })).toEqual([]);
});

test("a summary without a full stop gets one, and a missing summary stops the step", () => {
  expect(releaseEntries([], "3.0.3", "Sym can stay open", "3.0.2", now)[0].text).toStartWith("Sym can stay open. ");
  expect(() => releaseEntries([], "3.0.3", "", "3.0.2", now)).toThrow("No summary");
});

test("the longest summary still fits the News limits", () => {
  const entries = releaseEntries([], "3.0.3", "x".repeat(SUMMARY_MAX), "3.0.2", now);
  expect(problems({ version: 1, issued: "2026-12-01T10:00:00Z", entries })).toEqual([]);
});

test("maxVersion is the highest version below the release, so every older version matches", () => {
  expect(previousVersion("3.0.3", ["3.0.3", "3.0.1", "3.0.2", "2.1.0", "3.0.10"])).toBe("3.0.2");
  expect(previousVersion("3.1.0", ["3.0.10", "3.0.9"])).toBe("3.0.10");
  expect(previousVersion("1.0.0", ["1.0.0"])).toBeNull();
});

test("this release has a summary, and its News entries are valid", () => {
  const notes = parseReleaseNotes(read("release-notes.txt"));
  expect(notes.summary.length).toBeGreaterThan(0);
  expect(notes.summary.length).toBeLessThanOrEqual(SUMMARY_MAX);
  const previous = previousVersion(notes.version, parseReleaseHistory(read("release-history.txt")).map(item => item.version));
  expect(previous).not.toBeNull();
  releaseEntries([], notes.version, notes.summary, previous!, now);
});

test("the keyboard's summaries match the notes; run `bun run notes` after changing them", () => {
  const all = [parseReleaseNotes(read("release-notes.txt")), ...parseReleaseHistory(read("release-history.txt"))];
  expect(read("android/assets/release-summaries.txt")).toBe(releaseSummaries(all));
  const versions = all.map(item => item.version);
  expect(new Set(versions).size).toBe(versions.length);
});

test("the summary line stays out of the notes and the GitHub release body", () => {
  const notes = parseReleaseNotes("# 1.0.2\n\n> Search works.\n\n## Fixed\n- Search");
  expect(notes.summary).toBe("Search works.");
  expect(notes.unknown).toEqual([]);
  expect(notes.sections).toEqual([{ title: "Fixed", notes: [{ title: "Search" }] }]);
});

test("What's new shows every version since the one someone had, newest first", () => {
  const current = parseReleaseNotes(read("release-notes.txt")).version;
  expect(notesSince(null).map(item => item.version)).toEqual([current]);
  expect(notesSince(current).map(item => item.version)).toEqual([current]);
  expect(notesSince("3.0.1").map(item => item.version)).toEqual([current, "3.0.2"]);
  expect(notesSince("3.0.0").map(item => item.version)).toEqual([current, "3.0.2", "3.0.1"]);
});
