import { expect, test } from "bun:test";
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";

// Back up everything (#65): every SharedPreferences file and every data file says whether it's in the backup, as
// settings-reset.test.ts makes each say which reset clears it. A new store fails here until BackupFile.kt lists it.
const root = join(import.meta.dir, "../..");
const read = (path: string) => readFileSync(join(root, path), "utf8");
const backup = read("android/kotlin/com/titankeys/keyboard/BackupFile.kt");
const fresh = read("android/kotlin/com/titankeys/keyboard/FreshStart.kt");
const activity = read("android/kotlin/com/vandam/ink/BackupActivity.kt");

function files(folder: string): string[] {
  return readdirSync(join(root, folder), { withFileTypes: true }).flatMap(entry =>
    entry.isDirectory() ? files(join(folder, entry.name)) : /\.(kt|java)$/.test(entry.name) ? [join(folder, entry.name)] : []);
}
const sources = files("android/kotlin").map(path => ({ path, source: read(path) }));

/** Every SharedPreferences file the code opens, by name, as settings-reset.test.ts finds them. */
function storeNames(): string[] {
  const names = new Set<string>();
  const constant = (source: string, name: string) =>
    source.match(new RegExp(`(?:const val|static final String) ${name} = "([a-z_]+)"`))?.[1];
  for (const { source } of sources) {
    for (const [, literal] of source.matchAll(/getSharedPreferences\("([a-z_]+)"/g)) names.add(literal);
    for (const [, reference] of source.matchAll(/getSharedPreferences\(([A-Za-z_.]*[A-Z_]{3,})\s*,/g)) {
      const [owner, name] = reference.includes(".") ? reference.split(".").slice(-2) : [null, reference];
      const home = owner ? sources.find(file => file.path.endsWith(`${owner}.kt`) || file.path.endsWith(`${owner}.java`))!.source : source;
      names.add(constant(home, name)!);
    }
  }
  return [...names];
}

/** The keys of the map [name] in BackupFile.kt, each `"key" to`. */
function keys(name: string): string[] {
  const start = backup.indexOf(`val ${name}: Map<`);
  const body = backup.slice(start, backup.indexOf("\n    )\n", start));
  return [...body.matchAll(/^ {8}"([a-z_.\-0-9]+)" to /gm)].map(match => match[1]);
}
const stores = keys("STORES");
const dataFiles = keys("FILES");

test("every SharedPreferences file is in the backup or left out with a reason", () => {
  const names = storeNames();
  expect(names.length).toBeGreaterThan(15);
  for (const name of names) expect({ name, listed: stores.includes(name) }).toEqual({ name, listed: true });
  // And nothing listed is gone from the code.
  for (const name of stores) expect({ name, used: names.includes(name) }).toEqual({ name, used: true });
  // Reset literally everything knows the same files.
  const resetFiles = [...fresh.matchAll(/"([a-z_]+)"/g)].map(match => match[1]);
  for (const name of stores) expect({ name, inFreshStart: resetFiles.includes(name) || name === "typing_settings" || name === "swipe_haptics" })
    .toEqual({ name, inFreshStart: true });
});

test("every data file Reset literally everything deletes is in the backup or left out with a reason", () => {
  const lists = ["NO_BACKUP_DATA", "FILES_DATA", "CACHE_DATA"].flatMap(list =>
    [...fresh.match(new RegExp(`val ${list} = listOf\\(([^)]*)\\)`))![1].matchAll(/"([^"]+)"/g)].map(match => match[1]));
  expect(lists.length).toBeGreaterThan(5);
  for (const name of lists) {
    const known = dataFiles.some(file => name.startsWith(file));
    expect({ name, known }).toEqual({ name, known: true });
  }
});

test("what the owner left out stays out", () => {
  for (const [name, why] of [["clipboard", "passwords"], ["back_disclosure", "Accessibility"], ["phone_checks", "this phone"]])
    expect(backup).toMatch(new RegExp(`"${name}" to Store\\.Left\\("[^"]*${why}`));
  // Share swipes' ID, consent and sent list stay: only the News switch goes from connected.
  expect(backup).toContain('"connected" to Store.Backed(only = setOf("announcements"))');
  expect(backup).toContain('"key_calibration" to Store.Backed(debugOnly = true)');
  for (const name of ["clipboard-history", "rawlog", "bug-recording", "contributions"]) expect(backup).toMatch(new RegExp(`"${name}" to "`));
  // The word pairs and the words are in.
  expect(backup).toContain('"user_ngrams.db" to null');
  expect(backup).toContain('"config" to null');
});

test("the file is titankeys-backup-YYYY-MM-DD.json with a format name and a version", () => {
  expect(backup).toContain('const val FORMAT = "titankeys-backup"');
  expect(backup).toContain("const val VERSION = 1");
  expect(backup).toContain('"titankeys-backup-%04d-%02d-%02d.json"');
  expect(backup).toContain("if (version > VERSION) throw Problem(TOO_NEW)");
});

test("restore restarts TitanKeys' process as Reset literally everything does, never with a force-stop", () => {
  expect(activity).toContain("Intent(this, FreshStartActivity::class.java)");
  expect(activity).toContain(".putExtra(FreshStartActivity.PID, Process.myPid())");
  expect(activity).not.toMatch(/forceStopPackage|killBackgroundProcesses|clearApplicationUserData/);
  // Everything is on disk before the restart.
  expect(activity).toContain("if (!editor.commit())");
  expect(activity).not.toContain(".apply()");
});

test("Settings > Your words has Back up everything and Restore, and Snippets' own pair is gone", () => {
  const data = read("app/settings/data/index.tsx");
  expect(data).toContain('<Field label="Back up everything"');
  expect(data).toContain('<Field label="Restore"');
  expect(data).not.toContain("Export words");
  expect(data).not.toContain("Import words");
  const snippets = read("app/settings/snippets/index.tsx");
  expect(snippets).not.toContain("Export snippets");
  expect(snippets).not.toContain("Import snippets");
  expect(read("lib/titankeys.ts")).not.toContain("transferSnippets");
});

test("everything restore writes is on disk before the restart: commit(), never apply()", () => {
  const restore = activity.slice(activity.indexOf("private fun restoreAll"), activity.indexOf("private fun restart"));
  expect(restore).toContain(".commit()");
  expect(restore).not.toContain(".apply()");
  // TypingSettings.contactNames(context, value) writes with apply(), so restore doesn't call it.
  expect(restore).not.toMatch(/TypingSettings\.contactNames\(this,/);
});
