import { expect, test } from "bun:test";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative } from "node:path";

// The phone and the builds have a lock each (tools/phone-lock.sh): a phone run doesn't hold up a build, nor a build a phone run.
// Every script that talks to the phone takes the phone lock; none takes the build lock, except the two that build and then use
// the phone, which take the build lock first. A new phone script fails here until it takes the phone lock.
const root = join(import.meta.dir, "../..");
const read = (path: string) => readFileSync(join(root, path), "utf8");
/** A script without its comment lines, so a comment that names a lock isn't taken for taking it. */
const code = (path: string) => read(path).split("\n").filter(line => !/^\s*#/.test(line)).join("\n");

function scripts(folder: string): string[] {
  return readdirSync(join(root, folder)).flatMap(name => {
    const path = join(folder, name);
    if (name === "node_modules" || name.startsWith(".")) return [];
    if (statSync(join(root, path)).isDirectory()) return scripts(path);
    return /\.(sh|py)$/.test(name) ? [relative(root, join(root, path)).replaceAll("\\", "/")] : [];
  });
}
const all = ["tools", "tests", "docs"].flatMap(scripts);

/** A command run on the phone: adb (or the tools' tk_adb, or a $adb variable) with one of adb's phone commands. */
const ADB = /(^|[\s;&|(])(tk_adb|adb|"\$adb"|\$adb|"\$\{adb\}")\s+(-s\s+\S+\s+)?(shell|install|push|pull|exec-out|reverse|logcat|get-state|connect|uninstall)\b|\["adb"|"adb", "shell"/m;
const USES_PHONE_TOOLS = /safety\.sh|touchlib\.sh|^from phone import|^import phone\b/m;
/** Scripts that name adb but never talk to the phone themselves, each with why. */
const NOT_PHONE: Record<string, string> = {
  "tools/dev/env.sh": "defines tk_adb for the others",
  "tools/phone-lock.sh": "the lock itself; it only starts the local adb server",
  "tools/phone_lock.py": "the lock itself; it only starts the local adb server",
  "tools/corner_check.py": "reads a saved dumpsys file",
  "tools/leak-paths.py": "reads a saved heap dump; its help only shows the adb commands that make one",
  "tools/dev/preview/server.py": "serves pages on this computer; open.sh sets up adb reverse",
};
const TAKES_PHONE_LOCK = /phone-lock\.sh|phone_lock|safety\.sh|touchlib\.sh|^from phone import|^import phone\b/m;
const BUILD_LOCK = /tk-tools\/\.lock\b|"\$S\/\.lock"/;
/** The scripts that build in the shared copy and then use the phone: the build lock first, then the phone lock. */
const BUILD_THEN_PHONE = ["tools/dev/build/verify.sh", "tools/dev/phone/preview-test.sh"];
const BUILD_ONLY = ["tools/dev/build/play-bundle.sh", "tools/dev/build/try.sh"];

const phoneScripts = all.filter(path => !(path in NOT_PHONE) && (ADB.test(code(path)) || USES_PHONE_TOOLS.test(code(path))));

test("the scan finds the phone scripts it should", () => {
  for (const path of ["tests/swipe/run.sh", "tests/preview/run.sh", "tests/ime-window/run.py", "tools/checks/about.sh",
    "tools/checks/phone.py", "tools/dev/build/verify.sh", "tools/dev/phone/shot.sh", "tools/dev/swipe-baseline/tools/replay.sh"])
    expect(phoneScripts).toContain(path);
  for (const path of Object.keys(NOT_PHONE)) expect(all).toContain(path);
});

test("every script that talks to the phone takes the phone lock", () => {
  expect(phoneScripts.filter(path => !TAKES_PHONE_LOCK.test(code(path)))).toEqual([]);
});

test("no phone script takes the build lock, except the two that build first, and they take it first", () => {
  const using = phoneScripts.filter(path => BUILD_LOCK.test(code(path)));
  expect(using.sort()).toEqual([...BUILD_THEN_PHONE].sort());
  for (const path of BUILD_THEN_PHONE) {
    const text = code(path);
    expect(text.search(BUILD_LOCK)).toBeLessThan(text.indexOf("phone-lock.sh"));
  }
});

test("builds take the build lock and never the phone lock", () => {
  for (const path of BUILD_ONLY) expect(TAKES_PHONE_LOCK.test(code(path))).toBe(false);
  expect(code("tools/dev/build/play-bundle.sh")).toMatch(BUILD_LOCK);
});

test("the two locks are two files", () => {
  const lock = code("tools/phone-lock.sh");
  expect(lock).toContain("tk-tools/.phone.lock");
  expect(lock).not.toMatch(BUILD_LOCK);
  expect(read("tools/phone_lock.py")).toContain('".phone.lock"');
});

test("every Python script that uses sys or os imports it (the lock split once dropped them from tests/ime-window/run.py)", () => {
  // The swipe baseline's *_append.py are pieces appended to a script that imports them, not scripts of their own.
  const missing = all.filter(path => path.endsWith(".py") && !path.endsWith("_append.py")).flatMap(path => {
    const text = code(path);
    return ["sys", "os"].filter(name => new RegExp(`(^|[^\\w.])${name}\\.\\w`, "m").test(text)
      && !new RegExp(`^\\s*(import ([\\w, ]+, )?${name}\\b|from ${name} import)`, "m").test(text)).map(name => `${path}: ${name}`);
  });
  expect(missing).toEqual([]);
});
