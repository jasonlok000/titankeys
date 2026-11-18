import { expect, test } from "bun:test";
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";

// After Reset literally everything, TitanKeys starts as a fresh install does (phone check, 2026-11-16: it opened on the
// playground, not Setup, because the guide's seen mark and Back's Not now survived the wipe). This lists every preference and
// every data file a fresh install doesn't have that the wipe leaves, each with its reason. Anything else the code keeps fails here.
const root = join(import.meta.dir, "../..");
const read = (path: string) => readFileSync(join(root, path), "utf8");
const fresh = read("android/kotlin/com/titankeys/keyboard/FreshStart.kt");

function files(folder: string): string[] {
  return readdirSync(join(root, folder), { withFileTypes: true }).flatMap(entry =>
    entry.isDirectory() ? files(join(folder, entry.name)) : /\.(kt|java)$/.test(entry.name) ? [join(folder, entry.name)] : []);
}
const sources = files("android/kotlin").map(path => ({ path, source: read(path) }));

/** The preferences a fresh install doesn't have that the wipe keeps, and why. */
const KEPT_PREFERENCES: Record<string, string> = {
  "key_calibration": "only TitanKeys Debug has it, and it measures this phone's keys",
  "connected/contributor": "Share swipes' ID, so swipes already shared can still be deleted",
  "connected/contributor_to_delete": "an ID whose shared swipes are still waiting to be deleted",
  "connected/contributions_sent": "what was shared, for the Share swipes page's delete",
  "connected/contributions_sent_at": "when it was shared, for the same",
  "connected/counts_to_delete": "Sharing's counts IDs whose totals are still waiting to be deleted",
};

/** Data files the code makes that the wipe leaves, and why a fresh install's are the same or none. */
const KEPT_FILES: Record<string, string> = {
  "swipe-models-v1": "the swipe models ship with the app: a fresh install unpacks the same",
  "swipe-models-v2": "the same, newer",
  "swipe-calibration-v1": "an old file the engine deletes every time it starts",
  "swipe-accuracy-v1": "the same",
  "sharing": "Sharing's counts IDs, so totals already sent can still be deleted; the wipe empties its totals first (Counts.clear)",
  "support-ledger.json": "the private supporter key still removes the board entry and the saved Play receipts prevent duplicate credit",
};

test("the wipe keeps only the listed preferences, so the guide, Back's disclosure and the release notes go as on a new install", () => {
  const kept = [...fresh.match(/val KEPT = setOf\(([^)]*)\)/)![1].matchAll(/"([a-z_]+)"/g)].map(match => match[1]);
  const partly = [...fresh.match(/val PARTLY_KEPT = mapOf\(([\s\S]*?)\n    \)/)![1].matchAll(/"([a-z_]+)" to setOf\(([^)]*)\)/g)]
    .flatMap(([, file, keys]) => [...keys.matchAll(/"([a-z_]+)"/g)].map(key => `${file}/${key[1]}`));
  expect([...kept, ...partly].sort()).toEqual(Object.keys(KEPT_PREFERENCES).sort());
  // The three that sent the reset to the playground.
  const wiped = fresh.match(/val WIPED = setOf\(([^)]*)\)/)![1];
  for (const name of ["app", "back_disclosure", "phone_checks"]) expect(wiped).toContain(`"${name}"`);
  expect(read("android/kotlin/com/titankeys/keyboard/GuideActivity.kt")).toContain('private const val SHOWN = "guide_shown"');
  expect(read("android/kotlin/com/titankeys/keyboard/BackDisclosureActivity.kt")).toContain('private const val PREFERENCES = "back_disclosure"');
});

test("the wipe writes back only what a fresh install has: this version's release notes seen, and the front page's note", () => {
  const wipe = fresh.slice(fresh.indexOf("fun wipe(activity: Activity)"));
  const written = wipe.slice(wipe.indexOf('getSharedPreferences("app"'), wipe.indexOf("delete(activity.noBackupFilesDir"));
  expect([...written.matchAll(/\.put\w+\((\w+)/g)].map(match => match[1]).sort()).toEqual(["NOTE", "SEEN_NOTES"]);
  expect(written).toContain("getPackageInfo(activity.packageName, 0).versionName");
  // The release notes read the same key, and a fresh install marks this version's as seen.
  const adapters = read("android/kotlin/com/vandam/ink/AppNativeAdapters.kt");
  expect(adapters).toContain("private const val SEEN_NOTES = FreshStart.SEEN_NOTES");
  expect(adapters).toContain("if (info.firstInstallTime == info.lastUpdateTime)");
});

test("every data file the code makes in its own folders is wiped, or listed here with why it stays", () => {
  const lists = ["NO_BACKUP_DATA", "FILES_DATA", "CACHE_DATA"].flatMap(list =>
    [...fresh.match(new RegExp(`val ${list} = listOf\(([^)]*)\)`))![1].matchAll(/"([^"]+)"/g)].map(match => match[1]));
  const found = new Set<string>();
  for (const { source } of sources) {
    const constant = (name: string) => source.match(new RegExp(`(?:const val|static final String) ${name} = "([^"]+)"`))?.[1];
    const made = /File\(\s*[\w.]*?(?:noBackupFilesDir|filesDir|cacheDir|getNoBackupFilesDir\(\)|getFilesDir\(\)|getCacheDir\(\))\s*,\s*("[^"$]+|[A-Z_]{3,})/g;
    for (const [, name] of source.matchAll(made)) {
      const value = name.startsWith('"') ? name.slice(1) : constant(name);
      expect({ name, resolved: value !== undefined }).toEqual({ name, resolved: true });
      found.add(value!.split("/")[0]);
    }
  }
  expect(found.size).toBeGreaterThan(10);
  for (const name of found) {
    const known = lists.some(start => name.startsWith(start)) || name in KEPT_FILES;
    expect({ name, known }).toEqual({ name, known: true });
  }
  // And nothing listed here is gone from the code.
  for (const name of Object.keys(KEPT_FILES)) expect({ name, used: found.has(name) }).toEqual({ name, used: true });
});
