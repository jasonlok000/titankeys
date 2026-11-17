import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { SHARED_COUNTS, meaningOf } from "../../lib/sharing-counts";

// Sharing's daily totals (#96, docs/analytics.md): one switch for swipes and totals, every count the keyboard sends listed on the
// What's shared page, nothing from TitanKeys Debug, nothing in private fields, no words.
const root = join(import.meta.dir, "../..");
const read = (path: string) => readFileSync(join(root, path), "utf8");
const composer = read("android/kotlin/com/titankeys/keyboard/Composer.kt");
const ime = read("android/kotlin/com/titankeys/keyboard/TitanKeysIme.kt");
const counts = read("android/kotlin/com/titankeys/connected/Counts.kt");
const daily = read("android/kotlin/com/titankeys/connected/DailyCounts.kt");

/** Every count the code can send, with its bands as the list writes them. */
function sentKeys(): string[] {
  const keys = new Set<string>();
  // Plain keys: count("…") in the composer, tally("…") in the keyboard, crashed(…, "…") from the crash report.
  for (const source of [composer, ime, read("android/kotlin/com/titankeys/keyboard/CrashReport.kt")])
    for (const [, key] of source.matchAll(/(?:count|tally|crashed)\((?:\w+, )?(?:if \([^)]*\) )?"([a-z][\w.]*)"/g)) if (!key.endsWith(".")) keys.add(key);
  for (const [, a, b] of ime.matchAll(/tally\(if \([^)]*\) "([\w.]+)" else "([\w.]+)"\)/g)) { keys.add(a); keys.add(b); }
  for (const [, a, b] of read("android/kotlin/com/titankeys/keyboard/CrashReport.kt").matchAll(/"(\w+\.count)" else "(\w+\.count)"/g)) { keys.add(a); keys.add(b); }
  // Chinese and Japanese: tallyChoices("…") under zh. and ja.
  for (const [, key] of ime.matchAll(/tallyChoices\("([\w.]+)"\)/g)) { keys.add(`zh.${key}`); keys.add(`ja.${key}`); }
  // The ones built from parts.
  for (const how of ["kept", "picked", "retyped"]) expect(composer).toContain(`"${how}"`);
  for (const how of ["kept", "picked", "retyped"]) keys.add(`swipe.${how}.<length>`);
  expect(composer).toContain('count("swipe.$how.${lengthBand(word)}")');
  for (const kind of ["autocorrect", "apostrophe", "capital"]) { keys.add(`${kind}.made`); keys.add(`${kind}.undone`); }
  expect(composer).toContain('count("${fixKind(replaced, restored)}.made")');
  expect(composer).toContain('count("${fixKind(pending.replaced, pending.restored)}.undone")');
  expect(composer).toContain('count("bar.place.${shown + 1}")');
  keys.add("bar.place.<place>");
  expect(ime).toContain('Counts.add("speed.swipe." + Counts.swipeBand(millis))');
  keys.add("speed.swipe.<band>");
  expect(ime).toContain('tally("speed.open." + Counts.openBand(SystemClock.uptimeMillis() - started))');
  keys.add("speed.open.<band>");
  return [...keys].sort();
}

test("every count the keyboard sends is listed, with its meaning, on What's shared", () => {
  const listed = SHARED_COUNTS.flatMap(group => group.counts.map(count => count.key));
  const sent = sentKeys();
  expect(sent.length).toBeGreaterThan(30);
  for (const key of sent) expect({ key, listed: listed.includes(key) }).toEqual({ key, listed: true });
  // And nothing listed that the code doesn't send.
  for (const key of listed) expect({ key, sent: sent.includes(key) }).toEqual({ key, sent: true });
  // Banded keys as the payload has them find their meaning.
  expect(meaningOf("swipe.kept.5-7")).toBe("Swiped words kept as they came out.");
  expect(meaningOf("speed.open.<50")).not.toBeNull();
  expect(meaningOf("speed.open.500+")).not.toBeNull();
  expect(meaningOf("hello")).toBeNull();
});

test("a count's key can't carry a word: only the listed shapes pass, and the keys are fixed strings in the code", () => {
  expect(daily).toContain('val KEY = Regex("[a-z][a-zA-Z0-9]*(\\\\.[a-zA-Z0-9<+-]+){1,3}")');
  expect(daily).toContain("if (by <= 0 || !KEY.matches(key)) return");
  // The composer counts only where it learns, so never in password, email, web address or private fields.
  expect(composer).toContain("private fun settled(swiped: Composition.Swiped, how: String, word: String) {\n        if (!field.learning) return");
  expect(composer).toContain('if (field.learning && composition !is Composition.Expanding) count("bar.place.${shown + 1}")');
  expect(composer).toContain('if (field.learning) count("${fixKind(replaced, restored)}.made")');
  // The keyboard's own counts: the same fields, never the guide's.
  expect(ime).toContain("if (editing && field.learning && !inGuide) Counts.add(key)");
  expect(ime).toContain("composer.count = { key -> if (!inGuide) Counts.add(key) }");
});

test("one switch: Sharing on counts and sends, off deletes what waits; nothing from TitanKeys Debug", () => {
  const contributions = read("android/kotlin/com/titankeys/connected/Contributions.kt");
  expect(contributions).toMatch(/if \(on\) \{\s+Counts\.clear\(context\)\s+Counts\.start\(context\)\s+\} else \{\s+queue\(context\)\.clear\(\)\s+TouchSizes\.clear\(\)\s+Counts\.clear\(context\)/);
  expect(counts).toContain("fun add(key: String) {\n        if (!on) return");
  expect(counts).toContain('if (Connected.build(context) == "debug") return true');
  // Sent sealed, to the Worker's counts route, under a hash of the counts ID; never the contributor ID.
  expect(counts).toContain("Seal.seal(day.json.toByteArray(Charsets.UTF_8), Connected.key(Connected.CONTRIBUTION_KEY))");
  expect(counts).toContain('"${Connected.CONTRIBUTIONS_URL}/v1/counts"');
  expect(counts).toContain('setRequestProperty("X-Counts", Contributions.folder(day.id))');
  expect(counts).not.toContain("contributorId");
  expect(daily).not.toMatch(/Contributions\.|contributorId/);
  // Delete what I've sent asks for every counts ID of the year too.
  expect(contributions).toContain("Counts.forget(context)");
  expect(contributions).toContain('"\\"counts\\":"');
  // Kept out of Android's backup: the no-backup folder.
  expect(counts).toContain("File(app.noBackupFilesDir, FILE)");
});

test("the consent screen is short, one switch for everything, with the full policy one tap away (owner, #96)", () => {
  const text = read("lib/connected-text.ts");
  const policy = text.slice(text.indexOf("export const sharePolicyText"), text.indexOf("export const contributeText"));
  expect(policy).toContain('agree: "Turn on sharing"');
  expect(policy).toContain('decline: "Not now"');
  expect(policy).toContain('policy: "Read the full policy"');
  // The never-list in one line.
  expect(policy.match(/never: \[\s+"[^"]+",\s+\]/)).not.toBeNull();
  expect(policy.length).toBeLessThan(1500);
  // The guide's own copy of the screen says the same (tests/guide/run.sh checks it word for word).
  expect(read("android/kotlin/com/titankeys/keyboard/ShareDisclosureActivity.kt")).toContain('const val AGREE = "Turn on sharing"');
});

test("the policy and the analytics doc name the same retention and route as the code", () => {
  const worker = read("server/contributions/src/worker.js");
  expect(worker).toContain("export const COUNTS_KEEP_MILLIS = 365 * 24 * 60 * 60 * 1000;");
  expect(daily).toContain("const val ID_DAYS = 30");
  expect(daily).toContain("const val MAX_DAYS = 7");
  for (const file of ["site/privacy/index.html", "docs/analytics.md"]) {
    const words = read(file);
    expect(`${file}: ${words.includes("1 year")}`).toBe(`${file}: true`);
    expect(`${file}: ${words.includes("30 days")}`).toBe(`${file}: true`);
  }
  expect(read("docs/analytics.md")).toContain("POST /v1/counts");
});
