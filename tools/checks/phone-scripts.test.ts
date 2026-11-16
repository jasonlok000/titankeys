import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

// The phone checks run on someone's daily phone, which may be in use: every press waits for the app's own window, typing waits
// for the app's own field, nothing selects all, and the app's keyboard is chosen only around its own steps (phone.py).
const root = join(import.meta.dir, "../..");
const read = (path: string) => readFileSync(join(root, path), "utf8");
const phone = read("tools/checks/phone.py");

/** A Python function's body, by its name. */
function body(source: string, name: string): string {
  const start = source.indexOf(`def ${name}(`);
  expect(start).toBeGreaterThan(-1);
  const next = source.indexOf("\ndef ", start + 1);
  return source.slice(start, next === -1 ? undefined : next);
}

test("each press checks the front window first, and typing checks the app's own field", () => {
  for (const name of ["tap", "key"]) {
    const fn = body(phone, name);
    expect(fn.indexOf("guard(")).toBeGreaterThan(-1);
    expect(fn.indexOf("guard(")).toBeLessThan(fn.indexOf('shell("input"'));
  }
  for (const name of ["type_text", "delete"]) {
    const fn = body(phone, name);
    expect(fn.indexOf("guard_field(")).toBeGreaterThan(-1);
    expect(fn.indexOf("guard_field(")).toBeLessThan(fn.indexOf('shell("input"'));
  }
  // The front window must be the app's own: a window of another app stops the check.
  expect(body(phone, "in_front")).toContain('front_window().startswith(f"{APP}/")');
  expect(body(phone, "guard")).toContain("raise NotInFront(");
});

test("only phone.py presses anything; no check selects all or deletes more than it typed", () => {
  for (const file of ["tools/checks/feedback.py", "tools/checks/home-focus.py"]) {
    const source = read(file);
    expect(`${file}: ${/shell\("input"/.test(source)}`).toBe(`${file}: false`);
    expect(`${file}: ${/keycombination|KEYCODE_MOVE|"113"/.test(source)}`).toBe(`${file}: false`);
  }
  expect(phone).not.toMatch(/keycombination|"113"/);
  // Home-focus takes out exactly what it typed, and types only into a field that was empty.
  const home = read("tools/checks/home-focus.py");
  expect(home).toContain("delete(len(typed))");
  expect(home).toContain('typed = TYPED if text_of(initial) == "" else ""');
  // verify.sh's retry presses Home through the guard.
  const verify = read("tools/dev/build/verify.sh");
  expect(verify).not.toContain("adb shell input keyevent HOME");
  expect(verify).toContain("python3 tools/checks/phone.py home");
});

test("the app's keyboard only around the checks that type, and the phone's keyboard back after", () => {
  const keyboard = body(phone, "app_keyboard");
  expect(keyboard).toContain("before = current_keyboard()");
  expect(keyboard).toMatch(/finally:\s*\n\s*if before != KEYBOARD and before not in \("", "null"\):\s*\n\s*shell\("ime", "set", before\)/);
  expect(body(read("tools/checks/home-focus.py"), "main")).toContain("with app_keyboard():");
  // Feedback types nothing, so it never changes the keyboard.
  expect(read("tools/checks/feedback.py")).not.toContain("app_keyboard");
});

test("feedback checks that both rows open the report form, by the form's own hints", () => {
  const check = read("tools/checks/feedback.py");
  const pattern = new RegExp(check.match(/IDEA_HINT, BUG_HINT = re\.search\(r'(.+)', FORM\)/)![1]);
  const form = read("app/settings/report.tsx");
  const found = form.match(pattern);
  expect(found).not.toBeNull();
  expect(found![1]).toBe("What should TitanKeys do?");
  expect(check).toContain('("Report a bug", 455, BUG_HINT)');
  expect(check).toContain('("Suggest an idea", 598, IDEA_HINT)');
  expect(read("tools/checks/feedback.sh")).toContain('exec python3 "$(dirname "$0")/feedback.py"');
});
