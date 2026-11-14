import { expect, test } from "bun:test";
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";

// Two resets (owner, 2026-11-13: "Reset most things and reset literally everything"). Reset most things puts every switch,
// stepper and choice back (SettingsReset.kt) and keeps what's yours. Reset literally everything wipes that too, like a fresh
// install (FreshStart.kt). Every key and every store says which reset clears it, so a new one has to choose.
const root = join(import.meta.dir, "../..");
const read = (path: string) => readFileSync(join(root, path), "utf8");
const reset = read("android/kotlin/com/atlas/keyboard/SettingsReset.kt");
const fresh = read("android/kotlin/com/atlas/keyboard/FreshStart.kt");

function files(folder: string): string[] {
  return readdirSync(join(root, folder), { withFileTypes: true }).flatMap(entry =>
    entry.isDirectory() ? files(join(folder, entry.name)) : /\.(kt|java)$/.test(entry.name) ? [join(folder, entry.name)] : []);
}
const sources = files("android/kotlin").map(path => ({ path, source: read(path) }));

/** The strings in [source]'s set [name]. */
function listed(source: string, name: string): string[] {
  const body = source.match(new RegExp(`val ${name} = setOf\\(([^)]*)\\)`))![1];
  return [...body.matchAll(/"([a-z_]+)"/g)].map(match => match[1]);
}
const settings = listed(reset, "TYPING_SETTINGS");
const data = listed(reset, "TYPING_DATA");

/** Every key TypingSettings and ExactTyping keep in typing_settings. A fix's "fix_" + id comes out as "fix_". */
function typingKeys(): string[] {
  const typing = read("android/kotlin/com/atlas/engine/TypingSettings.java");
  const exact = read("android/kotlin/com/atlas/keyboard/ExactTyping.kt");
  const literal = [...typing.matchAll(/preferences(?:\(context\))?\.(?:get\w+|put\w+|remove|contains)\("([a-z_]+)"/g)].map(m => m[1]);
  const constants = [...typing.matchAll(/static final String \w+ = "([a-z_]+)";/g)].map(m => m[1]);
  const exactKeys = [...exact.matchAll(/private const val (?:APPS|REAL_ALT) = "([a-z_]+)"/g)].map(m => m[1]);
  return [...new Set([...literal, ...constants, ...exactKeys])];
}

/** Every SharedPreferences file the code opens, by name: literal, or a constant from the same file or the one it names. */
function storeNames(): string[] {
  const names = new Set<string>();
  const constant = (source: string, name: string) =>
    source.match(new RegExp(`(?:const val|static final String) ${name} = "([a-z_]+)"`))?.[1];
  for (const { source } of sources) {
    for (const [, literal] of source.matchAll(/getSharedPreferences\("([a-z_]+)"/g)) names.add(literal);
    for (const [, reference] of source.matchAll(/getSharedPreferences\(([A-Za-z_.]*[A-Z_]{3,})\s*,/g)) {
      const [owner, name] = reference.includes(".") ? reference.split(".").slice(-2) : [null, reference];
      const home = owner ? sources.find(file => file.path.endsWith(`${owner}.kt`) || file.path.endsWith(`${owner}.java`))!.source : source;
      const value = constant(home, name);
      expect({ reference, resolved: value !== undefined }).toEqual({ reference, resolved: true });
      names.add(value!);
    }
  }
  return [...names];
}

test("every key in typing_settings is a setting both resets clear, or something of yours only the full wipe clears", () => {
  const keys = typingKeys();
  expect(keys.length).toBeGreaterThan(30);
  for (const key of keys) {
    const known = settings.includes(key) || data.includes(key) || key === "fix_";
    expect({ key, known }).toEqual({ key, known: true });
  }
  expect(settings.filter(key => data.includes(key))).toEqual([]);
  for (const key of [...settings, ...data]) expect({ key, used: keys.includes(key) }).toEqual({ key, used: true });
  // Reset most things removes only the settings; the full wipe deletes the whole file.
  expect(reset).toContain("(TYPING_SETTINGS + fixes).forEach { remove(it) }");
  expect(fresh).toMatch(/val WIPED = setOf\(\s*SettingsReset\.TYPING, SettingsReset\.HAPTICS, "fixed_scale"/);
  // The full wipe resets every setting the way Reset most things does, first.
  expect(read("android/kotlin/com/vandam/ink/AppNativeAdapters.kt"))
    .toMatch(/"wipeEverything" -> \{\s*SettingsReset\.reset\(activity\)\s*FreshStart\.wipe\(activity\)/);
});

test("every store is wiped, kept, or kept in part by the full wipe, and Reset most things touches only the settings files", () => {
  const wiped = listed(fresh, "WIPED").concat(["typing_settings", "swipe_haptics"]);
  const kept = listed(fresh, "KEPT");
  const partly = [...fresh.matchAll(/"([a-z_]+)" to setOf\(/g)].map(m => m[1]);
  for (const name of storeNames()) {
    const known = wiped.includes(name) || kept.includes(name) || partly.includes(name);
    expect({ name, known }).toEqual({ name, known: true });
  }
  expect(kept.sort()).toEqual(["back_disclosure", "key_calibration"]);
  expect(partly.sort()).toEqual(["app", "connected"]);
  const resetFiles = [...reset.matchAll(/getSharedPreferences\((\w+)/g)].map(m => m[1]);
  expect(resetFiles.sort()).toEqual(["HAPTICS", "TYPING"]);
  expect(reset).toContain('const val TYPING = "typing_settings"');
  expect(reset).toContain('const val HAPTICS = "swipe_haptics"');
  expect(read("android/kotlin/com/atlas/keyboard/Haptics.kt")).toContain('val key get() = "haptic_$switch"');
});

test("the full wipe clears each file with commit() before deleting it, so no queued apply() writes it back", () => {
  // Bug hunt 2.1, #2: the wipe runs right after Reset most things, whose apply() is still queued on the cached typing_settings.
  // deleteSharedPreferences alone left that cache, data and all, to be written back about 100 ms later, so shorthand, the app
  // lists, recent apps and the snippet start survived the wipe. commit() waits behind the queued write, so the empty file lands
  // last. Any queued apply() on a wiped file is a hazard, not only the reset's.
  const wipe = fresh.slice(fresh.indexOf("fun wipe(activity: Activity)"));
  expect(wipe).toMatch(/for \(name in WIPED\) \{\s*(?:\/\/[^\n]*\s*)*activity\.getSharedPreferences\(name, Context\.MODE_PRIVATE\)\.edit\(\)\.clear\(\)\.commit\(\)\s*activity\.deleteSharedPreferences\(name\)\s*\}/);
  expect(wipe.match(/deleteSharedPreferences/g)?.length).toBe(1);
  const queued = reset.match(/\.apply\(\)/g)?.length ?? 0;
  expect(queued).toBeGreaterThan(0); // the reason the clear must come first
  // Every partly kept file is trimmed with commit() too.
  expect(wipe).toMatch(/preferences\.edit\(\)\.apply \{ preferences\.all\.keys\.filter \{ it !in kept \}\.forEach \{ remove\(it\) \} \}\.commit\(\)/);
});

test("the full wipe keeps Share swipes' ID and what was sent, so shared swipes can still be deleted", () => {
  const contributions = read("android/kotlin/com/atlas/connected/Contributions.kt");
  const keptConnected = fresh.match(/"connected" to setOf\(([^)]*)\)/)![1];
  for (const name of ["CONTRIBUTOR", "PENDING_DELETE", "SENT", "SENT_AT"]) {
    const key = contributions.match(new RegExp(`const val ${name} = "([a-z_]+)"`))![1];
    expect(keptConnected).toContain(`"${key}"`);
  }
  // Share swipes itself is not kept, so it goes back to off.
  expect(keptConnected).not.toContain('"contribute"');
  // It restarts the process as a crash would, never a force-stop, which would turn the Accessibility service off.
  expect(fresh + read("android/kotlin/com/atlas/keyboard/FreshStartActivity.kt")).not.toMatch(/forceStopPackage|killBackgroundProcesses|clearApplicationUserData/);
  expect(read("android/src/main/AndroidManifest.xml")).toMatch(/FreshStartActivity"[^>]*android:process=":restart"/);
});

test("a reset removes values, so every default stays where its setting defines it", () => {
  expect(reset).not.toMatch(/\.put(Boolean|String|Int|Long|Float|StringSet)\(/);
  expect(reset).not.toMatch(/\.clear\(\)/);
  const engine = read("android/kotlin/com/atlas/engine/SwipeTyping.java");
  const learning = engine.slice(engine.indexOf("public void resetLearning()"), engine.indexOf("public void resetLearning()") + 300);
  expect(learning).toContain('getSharedPreferences("word_learning", 0).edit().remove("enabled").apply()');
});

test("Settings has both, each asking first on the app's own confirm page, with the rulebook's haptic", () => {
  const index = read("app/settings/index.tsx");
  expect(index).toContain('<Field label="Reset most things" href="/settings/reset-most" />');
  expect(index).toContain('<Field label="Reset literally everything" href="/settings/reset-everything" />');
  const most = read("app/settings/reset-most.tsx");
  expect(most).toContain('title="Reset most things?"');
  expect(most).toContain('body="Every switch and choice goes back to how it started. Your words, snippets and app lists stay."');
  expect(most).toContain('action="Reset" cancel="Keep mine"');
  expect(most).toContain('playHaptic("app_picked")');
  expect(most).not.toContain("app_deleted");
  expect(most).toContain('"Back to factory. Try not to break it again."');
  const all = read("app/settings/reset-everything.tsx");
  expect(all).toContain('title="Reset literally everything?"');
  expect(all).toContain('"Settings, your words, snippets, shorthand, app lists and clipboard. All gone, like a fresh install. It can\'t be undone. "');
  expect(all).toContain('+ "Swipes you already shared are deleted on the Share swipes page."');
  expect(all).toContain('action="Wipe it all" cancel="Never mind"');
  expect(all).toContain('playHaptic("app_deleted")');
  expect(fresh).toContain('const val NOTE_TEXT = "Fresh start. Don\'t waste it."');
  expect(read("android/kotlin/com/atlas/keyboard/GuideActivity.kt")).toContain("swapIn(body, FreshStart.take(this) ?: PLAYGROUND_BODY, 0)");
});

test("the boring guide and the release notes say where they are", () => {
  const guide = read("app/settings/guide-boring.tsx");
  expect(guide).toContain("Reset most things");
  expect(guide).toContain("Reset literally everything");
  expect(read("release-notes.txt")).toContain("- Two resets:");
});
